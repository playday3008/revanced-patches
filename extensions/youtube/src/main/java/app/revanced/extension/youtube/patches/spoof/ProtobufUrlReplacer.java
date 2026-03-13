package app.revanced.extension.youtube.patches.spoof;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import app.revanced.extension.shared.Logger;

/**
 * Replaces streaming URLs in a StreamingData protobuf with URLs from NewPipe Extractor.
 * <p>
 * Uses raw protobuf wire-format parsing to avoid classpath conflicts with YouTube's
 * bundled protobuf runtime.
 * <p>
 * StreamingData schema:
 * <pre>
 * message StreamingData {
 *     int64 expiresInSeconds = 1;
 *     repeated Format formats = 2;
 *     repeated Format adaptiveFormats = 3;
 *     string dashManifestUrl = 4;
 *     string hlsManifestUrl = 5;
 * }
 * message Format {
 *     int32 itag = 1;
 *     string url = 2;
 *     string mimeType = 3;
 *     int32 bitrate = 4;
 *     ... more fields
 * }
 * </pre>
 */
public final class ProtobufUrlReplacer {

    private static final int WIRE_TYPE_VARINT = 0;
    private static final int WIRE_TYPE_LENGTH_DELIMITED = 2;

    /**
     * Replaces URLs in StreamingData protobuf with NewPipe URLs matched by itag.
     *
     * @param streamingData The raw protobuf ByteBuffer.
     * @param itagUrlMap    Map of itag to replacement URL.
     * @return Modified ByteBuffer, or original on failure or if map is empty.
     */
    @NonNull
    public static ByteBuffer replaceStreamingDataUrls(@NonNull ByteBuffer streamingData,
                                                      @NonNull Map<Integer, String> itagUrlMap) {
        if (itagUrlMap.isEmpty()) {
            return streamingData;
        }

        byte[] data;
        if (streamingData.hasArray()) {
            data = streamingData.array();
        } else {
            data = new byte[streamingData.remaining()];
            streamingData.get(data);
            streamingData.rewind();
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(data.length + 2048);
            int pos = 0;
            int replacedCount = 0;

            while (pos < data.length) {
                int fieldStart = pos;
                int[] tagResult = readVarint(data, pos);
                int tag = tagResult[0];
                pos = tagResult[1];

                int fieldNumber = tag >>> 3;
                int wireType = tag & 0x7;

                if (wireType == WIRE_TYPE_LENGTH_DELIMITED && (fieldNumber == 2 || fieldNumber == 3)) {
                    // Format sub-message — process it.
                    int[] lenResult = readVarint(data, pos);
                    int length = lenResult[0];
                    pos = lenResult[1];

                    byte[] replacedFormat = replaceUrlInFormat(data, pos, pos + length, itagUrlMap);

                    if (replacedFormat.length != length
                            || !arraysEqual(data, pos, replacedFormat, 0, Math.min(length, replacedFormat.length))) {
                        replacedCount++;
                    }

                    writeRawVarint32(output, tag);
                    writeRawVarint32(output, replacedFormat.length);
                    output.write(replacedFormat);

                    pos += length;
                } else {
                    // Copy field as-is.
                    int fieldEnd = skipFieldValue(data, pos, wireType);
                    output.write(data, fieldStart, fieldEnd - fieldStart);
                    pos = fieldEnd;
                }
            }

            final int count = replacedCount;
            Logger.printDebug(() -> "Replaced " + count + " URLs in StreamingData protobuf");

            return ByteBuffer.wrap(output.toByteArray());

        } catch (Exception ex) {
            Logger.printException(() -> "Failed to replace StreamingData URLs", ex);
            return streamingData;
        }
    }

    /**
     * Processes a Format message: extracts itag, and if a replacement URL exists,
     * replaces the url field (field 2). All other fields are copied as-is.
     */
    private static byte[] replaceUrlInFormat(byte[] data, int offset, int limit,
                                             Map<Integer, String> itagUrlMap) throws IOException {
        // First pass: extract itag (field 1).
        int itag = -1;
        int pos = offset;
        while (pos < limit) {
            int[] tagResult = readVarint(data, pos);
            int tag = tagResult[0];
            pos = tagResult[1];
            int fieldNumber = tag >>> 3;
            int wireType = tag & 0x7;

            if (fieldNumber == 1 && wireType == WIRE_TYPE_VARINT) {
                int[] itagResult = readVarint(data, pos);
                itag = itagResult[0];
                pos = itagResult[1];
            } else {
                pos = skipFieldValue(data, pos, wireType);
            }
        }

        String replacementUrl = (itag > 0) ? itagUrlMap.get(itag) : null;
        if (replacementUrl == null) {
            // No replacement; return original bytes.
            byte[] original = new byte[limit - offset];
            System.arraycopy(data, offset, original, 0, limit - offset);
            return original;
        }

        final int finalItag = itag;
        Logger.printDebug(() -> "Replacing URL for itag " + finalItag);

        // Second pass: copy all fields, replacing url (field 2).
        ByteArrayOutputStream result = new ByteArrayOutputStream(limit - offset + 256);
        pos = offset;
        while (pos < limit) {
            int fieldStart = pos;
            int[] tagResult = readVarint(data, pos);
            int tag = tagResult[0];
            pos = tagResult[1];
            int fieldNumber = tag >>> 3;
            int wireType = tag & 0x7;

            if (fieldNumber == 2 && wireType == WIRE_TYPE_LENGTH_DELIMITED) {
                // Replace url field.
                int[] lenResult = readVarint(data, pos);
                pos = lenResult[1] + lenResult[0]; // skip past length + string data

                byte[] urlBytes = replacementUrl.getBytes(StandardCharsets.UTF_8);
                writeRawVarint32(result, tag);
                writeRawVarint32(result, urlBytes.length);
                result.write(urlBytes);
            } else {
                // Copy field as-is.
                int fieldEnd = skipFieldValue(data, pos, wireType);
                result.write(data, fieldStart, fieldEnd - fieldStart);
                pos = fieldEnd;
            }
        }

        return result.toByteArray();
    }

    // ---- Protobuf wire format helpers ----

    /** Reads a varint, returns [value, newPosition]. */
    private static int[] readVarint(byte[] data, int pos) {
        int result = 0;
        int shift = 0;
        while (pos < data.length) {
            byte b = data[pos++];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new int[]{result, pos};
            }
            shift += 7;
            if (shift > 35) {
                throw new IllegalArgumentException("Varint too long");
            }
        }
        throw new IllegalArgumentException("Truncated varint");
    }

    /** Skips a field value and returns new position. */
    private static int skipFieldValue(byte[] data, int pos, int wireType) {
        switch (wireType) {
            case 0: // Varint
                while (pos < data.length && (data[pos++] & 0x80) != 0) { }
                return pos;
            case 1: // 64-bit
                return pos + 8;
            case 2: // Length-delimited
                int[] lenResult = readVarint(data, pos);
                return lenResult[1] + lenResult[0];
            case 5: // 32-bit
                return pos + 4;
            default:
                throw new IllegalArgumentException("Unknown wire type: " + wireType);
        }
    }

    /** Writes a varint to the output stream. */
    private static void writeRawVarint32(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static boolean arraysEqual(byte[] a, int aOff, byte[] b, int bOff, int length) {
        for (int i = 0; i < length; i++) {
            if (a[aOff + i] != b[bOff + i]) return false;
        }
        return true;
    }
}
