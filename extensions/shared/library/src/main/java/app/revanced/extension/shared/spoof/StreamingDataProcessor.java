package app.revanced.extension.shared.spoof;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

/**
 * Interface for post-processing streaming data after it's fetched from the spoofed client.
 * Implementations can modify the protobuf bytes (e.g., replace streaming URLs with
 * URLs from a different source like NewPipe Extractor).
 */
public interface StreamingDataProcessor {

    /**
     * Called when a new streaming data request is initiated for a video.
     * Implementations can use this to start fetching replacement data in parallel.
     *
     * @param videoId The YouTube video ID.
     */
    void onStreamingDataRequested(@NonNull String videoId);

    /**
     * Process the streaming data protobuf bytes before they are returned to the app.
     *
     * @param videoId       The YouTube video ID.
     * @param streamingData The raw protobuf bytes of the StreamingData.
     * @return Modified streaming data bytes, or the original if no modification is needed.
     */
    @NonNull
    ByteBuffer processStreamingData(@NonNull String videoId, @NonNull ByteBuffer streamingData);
}
