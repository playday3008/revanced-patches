package app.revanced.extension.gamehub.filemanager;

import android.content.pm.ProviderInfo;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import com.blankj.utilcode.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public class MTDataFilesProvider extends DocumentsProvider {

    private static final String[] DEFAULT_ROOT_PROJECTION = {
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            "mt_extras",
    };

    private static final String DIR_MIME = "vnd.android.document/directory";
    private static final String DIR_DATA = "data";
    private static final String DIR_ANDROID_DATA = "android_data";
    private static final String DIR_ANDROID_OBB = "android_obb";
    private static final String DIR_USER_DE = "user_de_data";

    private String packageName;
    private File dataDir;        // /data/data/{pkg}
    private File userDeDir;      // /data/user_de/0/{pkg}
    private File androidDataDir; // external /Android/data/{pkg}
    private File obbDir;         // external /Android/obb/{pkg}

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        try {
            Context ctx = Utils.a();
            packageName = ctx.getPackageName();
            dataDir = ctx.getFilesDir().getParentFile(); // /data/data/{pkg}

            userDeDir = new File("/data/user_de/0/" + packageName);

            File[] externalDirs = ctx.getExternalFilesDirs(null);
            if (externalDirs != null && externalDirs.length > 0 && externalDirs[0] != null) {
                // External data: /sdcard/Android/data/{pkg}
                androidDataDir = externalDirs[0].getParentFile();
                // OBB: /sdcard/Android/obb/{pkg}
                obbDir = ctx.getObbDir();
            }
        } catch (Exception e) {
            // Ignore initialization errors
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        if (packageName == null) return result;
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, packageName);
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*");
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
        try {
            Context ctx = Utils.a();
            row.add(DocumentsContract.Root.COLUMN_ICON, ctx.getApplicationInfo().icon);
        } catch (Exception e) {
            row.add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_manage);
        }
        row.add(DocumentsContract.Root.COLUMN_TITLE, packageName);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, packageName);
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, documentId, resolveDocIdToFile(documentId));
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        if (parentDocumentId.equals(packageName)) {
            // Root: list virtual directories
            addVirtualDir(result, parentDocumentId, DIR_DATA, dataDir);
            addVirtualDir(result, parentDocumentId, DIR_USER_DE, userDeDir);
            if (androidDataDir != null) {
                addVirtualDir(result, parentDocumentId, DIR_ANDROID_DATA, androidDataDir);
            }
            if (obbDir != null) {
                addVirtualDir(result, parentDocumentId, DIR_ANDROID_OBB, obbDir);
            }
        } else {
            File parentFile = resolveDocIdToFile(parentDocumentId);
            if (parentFile != null && parentFile.isDirectory()) {
                File[] files = parentFile.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String childId = parentDocumentId + "/" + file.getName();
                        includeFile(result, childId, file);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        File file = resolveDocIdToFile(documentId);
        if (file == null) throw new FileNotFoundException("File not found: " + documentId);
        int parseMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, parseMode);
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parentDir = resolveDocIdToFile(parentDocumentId);
        if (parentDir == null || !parentDir.isDirectory()) {
            throw new FileNotFoundException("Parent not found: " + parentDocumentId);
        }
        File newFile = new File(parentDir, displayName);
        // Handle name collision
        if (newFile.exists()) {
            String name = displayName;
            String ext = "";
            int dot = displayName.lastIndexOf('.');
            if (dot > 0) {
                name = displayName.substring(0, dot);
                ext = displayName.substring(dot);
            }
            int n = 2;
            while (newFile.exists()) {
                newFile = new File(parentDir, name + " (" + n + ")" + ext);
                n++;
            }
        }
        try {
            if (DIR_MIME.equals(mimeType)) {
                newFile.mkdir();
            } else {
                newFile.createNewFile();
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Cannot create: " + displayName);
        }
        return parentDocumentId + "/" + newFile.getName();
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = resolveDocIdToFile(documentId);
        if (file == null || !deleteRecursive(file)) {
            throw new FileNotFoundException("Delete failed: " + documentId);
        }
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File file = resolveDocIdToFile(documentId);
        if (file == null) throw new FileNotFoundException("Not found: " + documentId);
        File dest = new File(file.getParentFile(), displayName);
        if (!file.renameTo(dest)) throw new FileNotFoundException("Rename failed: " + documentId);
        String parentId = documentId.substring(0, documentId.lastIndexOf('/'));
        return parentId + "/" + displayName;
    }

    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId,
                               String targetParentDocumentId) throws FileNotFoundException {
        File source = resolveDocIdToFile(sourceDocumentId);
        File targetParent = resolveDocIdToFile(targetParentDocumentId);
        if (source == null || targetParent == null) throw new FileNotFoundException("Not found");
        File dest = new File(targetParent, source.getName());
        if (!source.renameTo(dest)) throw new FileNotFoundException("Move failed");
        return targetParentDocumentId + "/" + source.getName();
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return documentId.startsWith(parentDocumentId + "/");
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        try {
            if ("mt:setLastModified".equals(method) && arg != null) {
                new File(arg).setLastModified(extras != null ? extras.getLong("time") : 0);
            } else if ("mt:setPermissions".equals(method) && arg != null) {
                android.system.Os.chmod(arg, extras != null ? extras.getInt("mode") : 0);
            } else if ("mt:createSymlink".equals(method) && arg != null) {
                android.system.Os.symlink(
                        extras != null ? extras.getString("target", "") : "", arg);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // --- Helpers ---

    private String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    private void addVirtualDir(MatrixCursor cursor, String parentId, String name, File realFile) {
        String docId = parentId + "/" + name;
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId);
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DIR_MIME);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name);
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                realFile != null ? realFile.lastModified() : 0);
        row.add(DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE |
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE);
        row.add(DocumentsContract.Document.COLUMN_SIZE, null);
        row.add("mt_extras", realFile != null ? realFile.getAbsolutePath() : "");
    }

    private void includeFile(MatrixCursor cursor, String documentId, File file) {
        if (file == null) return;
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        boolean isDir = file.isDirectory();
        String mime = isDir ? DIR_MIME : getMimeType(file);
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mime);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        int flags = 0;
        if (file.canWrite()) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                   | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                   | DocumentsContract.Document.FLAG_SUPPORTS_MOVE;
            if (isDir) {
                flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
            } else {
                flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
            }
        }
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
        row.add(DocumentsContract.Document.COLUMN_SIZE, isDir ? null : file.length());
        // mt_extras: absolute path for MT Manager compatibility
        row.add("mt_extras", file.getAbsolutePath());
    }

    private File resolveDocIdToFile(String documentId) throws FileNotFoundException {
        if (packageName == null) throw new FileNotFoundException("Provider not initialized");
        if (!documentId.startsWith(packageName)) {
            throw new FileNotFoundException("Invalid document ID: " + documentId);
        }
        if (documentId.equals(packageName)) {
            return dataDir;
        }
        // Strip "{packageName}/" prefix
        String rest = documentId.substring(packageName.length() + 1);
        int slashIdx = rest.indexOf('/');
        String virtualDir = slashIdx >= 0 ? rest.substring(0, slashIdx) : rest;
        String relPath = slashIdx >= 0 ? rest.substring(slashIdx + 1) : "";

        File base;
        switch (virtualDir) {
            case DIR_DATA:         base = dataDir;        break;
            case DIR_USER_DE:      base = userDeDir;      break;
            case DIR_ANDROID_DATA: base = androidDataDir; break;
            case DIR_ANDROID_OBB:  base = obbDir;         break;
            default: throw new FileNotFoundException("Unknown virtual dir: " + virtualDir);
        }
        if (base == null) throw new FileNotFoundException("Directory unavailable: " + virtualDir);
        return relPath.isEmpty() ? base : new File(base, relPath);
    }

    private static String getMimeType(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase();
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) return false;
                }
            }
        }
        return file.delete();
    }
}
