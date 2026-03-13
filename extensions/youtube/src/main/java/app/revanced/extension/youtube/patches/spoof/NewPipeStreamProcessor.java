package app.revanced.extension.youtube.patches.spoof;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.spoof.StreamingDataProcessor;

/**
 * Processes streaming data by replacing URLs with NewPipe Extractor WEB client URLs.
 * <p>
 * When a streaming data request is made, this processor kicks off a parallel NewPipe
 * stream fetch. When the streaming data is ready to be returned, the URLs in the
 * protobuf are replaced with the NewPipe URLs (matched by itag).
 */
public final class NewPipeStreamProcessor implements StreamingDataProcessor {

    private static final int FETCH_TIMEOUT_SECONDS = 15;

    /** Cache of pending/completed NewPipe stream fetches by videoId. */
    private final ConcurrentHashMap<String, Future<NewPipeStreamFetcher.StreamResult>> pendingFetches =
            new ConcurrentHashMap<>();

    private static volatile NewPipeStreamProcessor instance;

    public static NewPipeStreamProcessor getInstance() {
        if (instance == null) {
            synchronized (NewPipeStreamProcessor.class) {
                if (instance == null) {
                    instance = new NewPipeStreamProcessor();
                }
            }
        }
        return instance;
    }

    private NewPipeStreamProcessor() {
    }

    @Override
    public void onStreamingDataRequested(@NonNull String videoId) {
        // Start NewPipe fetch in parallel.
        Future<NewPipeStreamFetcher.StreamResult> future = Utils.submitOnBackgroundThread(
                () -> NewPipeStreamFetcher.fetchStreams(videoId)
        );
        pendingFetches.put(videoId, future);
        Logger.printDebug(() -> "NewPipeStreamProcessor: Started fetch for " + videoId);
    }

    @NonNull
    @Override
    public ByteBuffer processStreamingData(@NonNull String videoId, @NonNull ByteBuffer streamingData) {
        Future<NewPipeStreamFetcher.StreamResult> future = pendingFetches.get(videoId);
        if (future == null) {
            Logger.printDebug(() -> "NewPipeStreamProcessor: No pending fetch for " + videoId);
            return streamingData;
        }

        try {
            NewPipeStreamFetcher.StreamResult result = future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result == null || result.isEmpty()) {
                Logger.printDebug(() -> "NewPipeStreamProcessor: Empty result for " + videoId);
                return streamingData;
            }

            Logger.printDebug(() -> "NewPipeStreamProcessor: Replacing URLs for " + videoId
                    + " with " + result.itagUrlMap.size() + " streams");

            ByteBuffer replaced = ProtobufUrlReplacer.replaceStreamingDataUrls(streamingData, result.itagUrlMap);
            if (replaced != null) {
                return replaced;
            }
        } catch (TimeoutException ex) {
            Logger.printInfo(() -> "NewPipeStreamProcessor: Fetch timed out for " + videoId);
        } catch (InterruptedException ex) {
            Logger.printException(() -> "NewPipeStreamProcessor: Fetch interrupted for " + videoId, ex);
            Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
            Logger.printException(() -> "NewPipeStreamProcessor: Fetch failed for " + videoId, ex);
        } finally {
            // Clean up after processing.
            pendingFetches.remove(videoId);
        }

        return streamingData;
    }
}
