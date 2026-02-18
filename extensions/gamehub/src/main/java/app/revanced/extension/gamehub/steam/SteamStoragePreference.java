package app.revanced.extension.gamehub.steam;

import android.content.SharedPreferences;
import android.os.Environment;

import com.blankj.utilcode.util.Utils;

@SuppressWarnings("unused")
public class SteamStoragePreference {

    private static final String PREFS_NAME = "steam_storage_pref";
    private static final String KEY_OFFICIAL_API = "use_official_api";
    private static final String KEY_CUSTOM_STORAGE = "use_custom_storage";
    private static final String KEY_STORAGE_PATH = "steam_storage_path";

    private static SharedPreferences getPrefs() {
        return Utils.a().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
    }

    public static boolean isOfficialAPI() {
        return getPrefs().getBoolean(KEY_OFFICIAL_API, false);
    }

    public static void toggleAPI() {
        SharedPreferences prefs = getPrefs();
        prefs.edit().putBoolean(KEY_OFFICIAL_API, !prefs.getBoolean(KEY_OFFICIAL_API, false)).apply();
    }

    public static boolean isCustomStorageEnabled() {
        return getPrefs().getBoolean(KEY_CUSTOM_STORAGE, false);
    }

    public static void toggleStorageLocation() {
        SharedPreferences prefs = getPrefs();
        prefs.edit().putBoolean(KEY_CUSTOM_STORAGE, !prefs.getBoolean(KEY_CUSTOM_STORAGE, false)).apply();
    }

    public static String getCustomStoragePath() {
        return getPrefs().getString(KEY_STORAGE_PATH, "");
    }

    /**
     * Translates an internal path to the SD card path if custom storage is enabled.
     *
     * @param originalPath the original install path from SteamDownloadInfoHelper
     * @return the effective path (SD card or original)
     */
    public static String getEffectiveStoragePath(String originalPath) {
        if (!isCustomStorageEnabled()) return originalPath;
        if (originalPath == null || originalPath.isEmpty()) return originalPath;

        String customPath = getCustomStoragePath();
        if (customPath == null || customPath.isEmpty()) return originalPath;

        java.io.File customDir = new java.io.File(customPath);
        if (!customDir.exists() || !customDir.isDirectory()) return originalPath;

        if (originalPath.startsWith(customPath)) return originalPath;

        int steamIdx = originalPath.indexOf("/files/Steam");
        if (steamIdx < 0) return originalPath;

        return customPath + originalPath.substring(steamIdx);
    }

    private static final String EMUREADY_URL = "https://gamehub-lite-api.emuready.workers.dev/";

    /**
     * Returns the effective API URL: EmuReady when not using the official API, original otherwise.
     */
    public static String getEffectiveApiUrl(String officialUrl) {
        return isOfficialAPI() ? officialUrl : EMUREADY_URL;
    }

    /**
     * Dispatches a settings-switch toggle for the two Steam-related content types.
     * Called from SettingSwitchHolder.w() before the existing notification checks.
     */
    public static void handleSettingToggle(int contentType) {
        if (contentType == 0x18) {
            toggleStorageLocation();
        } else if (contentType == 0x1a) {
            toggleAPI();
        }
    }

    /**
     * Returns true if the given LauncherConfig content type should be blocked.
     * Content types 0x57a (Discover), 0x579 (Free), and 0x9 (some feature) are blocked.
     */
    public static boolean shouldBlockFeature(int contentType) {
        return contentType == 0x57a || contentType == 0x579 || contentType == 0x9;
    }

    /**
     * Scans external storage volumes for a writable /GHL folder and saves the path.
     */
    public static void autoDetectSDCardStorage() {
        try {
            android.content.Context ctx = Utils.a();
            java.io.File[] externalDirs = ctx.getExternalFilesDirs(null);
            for (java.io.File dir : externalDirs) {
                if (dir == null) continue;
                // Find the storage root (up to Android/data)
                String path = dir.getAbsolutePath();
                int androidIdx = path.indexOf("/Android/data");
                if (androidIdx < 0) continue;
                String storageRoot = path.substring(0, androidIdx);
                java.io.File ghlDir = new java.io.File(storageRoot, "GHL");
                if (ghlDir.exists() && ghlDir.isDirectory() && ghlDir.canWrite()) {
                    getPrefs().edit().putString(KEY_STORAGE_PATH, storageRoot).apply();
                    return;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
