package app.revanced.extension.youtube.patches.spoof;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import app.revanced.extension.shared.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

/**
 * HTTP downloader for NewPipe Extractor using HttpURLConnection.
 */
public final class ExtractorDownloader extends Downloader {

    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 10_000;

    private static ExtractorDownloader instance;

    private ExtractorDownloader() {
    }

    public static ExtractorDownloader getInstance() {
        if (instance == null) {
            instance = new ExtractorDownloader();
        }
        return instance;
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        Logger.printDebug(() -> "ExtractorDownloader: " + request.httpMethod() + " " + request.url());
        final HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();

        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod(request.httpMethod());

        // Set request headers.
        final Map<String, List<String>> headers = request.headers();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            final String key = entry.getKey();
            for (String value : entry.getValue()) {
                connection.addRequestProperty(key, value);
            }
        }

        // Write request body if present.
        final byte[] dataToSend = request.dataToSend();
        if (dataToSend != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(dataToSend.length);
            connection.getOutputStream().write(dataToSend);
        }

        final int responseCode = connection.getResponseCode();
        final String responseMessage = connection.getResponseMessage();

        if (responseCode == 429) {
            Logger.printDebug(() -> "ExtractorDownloader: Got 429 (reCaptcha) for " + request.url());
            connection.disconnect();
            throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
        }

        Logger.printDebug(() -> "ExtractorDownloader: Response " + responseCode + " for " + request.url());

        // Read response body.
        String responseBody = null;
        try {
            final InputStream inputStream = isErrorResponse(responseCode)
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            if (inputStream != null) {
                responseBody = readStream(inputStream);
            }
        } catch (Exception ignored) {
            // Some error responses may not have a body.
        }

        // Collect response headers.
        final Map<String, List<String>> responseHeaders = connection.getHeaderFields();

        connection.disconnect();

        return new Response(
                responseCode,
                responseMessage,
                responseHeaders,
                responseBody,
                connection.getURL().toString()
        );
    }

    private static boolean isErrorResponse(int responseCode) {
        return responseCode >= 400;
    }

    /**
     * Reads an InputStream into a byte array.
     */
    static byte[] readStreamBytes(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {
        return new String(readStreamBytes(inputStream), "UTF-8");
    }
}
