package app.revanced.extension.youtube.patches.spoof;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.revanced.extension.shared.Logger;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

/**
 * Fetches streaming URLs from YouTube using NewPipe Extractor with the WEB client.
 * Provides itag-to-URL mappings that can be used to replace URLs in the protobuf StreamingData.
 */
public final class NewPipeStreamFetcher {

    private static volatile boolean initialized = false;

    /**
     * Initializes NewPipe Extractor with our Downloader and PoToken provider.
     * Must be called before any stream fetching.
     */
    public static synchronized void initialize() {
        if (initialized) return;
        try {
            NewPipe.init(ExtractorDownloader.getInstance());
            YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl.INSTANCE);
            initialized = true;
            Logger.printInfo(() -> "NewPipe Extractor initialized");
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to initialize NewPipe Extractor", ex);
        }
    }

    /**
     * Result of a NewPipe stream fetch containing itag-to-URL mappings.
     */
    public static final class StreamResult {
        /** Map of itag number to streaming URL. */
        @NonNull
        public final Map<Integer, String> itagUrlMap;

        StreamResult(@NonNull Map<Integer, String> itagUrlMap) {
            this.itagUrlMap = itagUrlMap;
        }

        @Nullable
        public String getUrlForItag(int itag) {
            return itagUrlMap.get(itag);
        }

        public boolean isEmpty() {
            return itagUrlMap.isEmpty();
        }
    }

    /**
     * Fetches streams for a video using NewPipe Extractor and returns itag-to-URL mappings.
     * This uses the WEB client with PoToken authentication.
     *
     * @param videoId The YouTube video ID.
     * @return StreamResult with itag-to-URL mappings, or null on failure.
     */
    @Nullable
    public static StreamResult fetchStreams(@NonNull String videoId) {
        if (!initialized) {
            initialize();
        }

        if (!initialized) {
            Logger.printDebug(() -> "NewPipe not initialized, cannot fetch streams");
            return null;
        }

        try {
            final long startTime = System.currentTimeMillis();
            Logger.printDebug(() -> "NewPipe: Fetching streams for videoId=" + videoId);

            StreamInfo info = StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=" + videoId
            );

            Map<Integer, String> itagUrlMap = new HashMap<>();

            // Collect video streams (combined audio+video).
            List<VideoStream> videoStreams = info.getVideoStreams();
            if (videoStreams != null) {
                for (VideoStream stream : videoStreams) {
                    addStreamToMap(itagUrlMap, stream.getItag(), stream.getContent(), stream.isUrl());
                }
            }

            // Collect video-only streams (adaptive).
            List<VideoStream> videoOnlyStreams = info.getVideoOnlyStreams();
            if (videoOnlyStreams != null) {
                for (VideoStream stream : videoOnlyStreams) {
                    addStreamToMap(itagUrlMap, stream.getItag(), stream.getContent(), stream.isUrl());
                }
            }

            // Collect audio streams.
            List<AudioStream> audioStreams = info.getAudioStreams();
            if (audioStreams != null) {
                for (AudioStream stream : audioStreams) {
                    addStreamToMap(itagUrlMap, stream.getItag(), stream.getContent(), stream.isUrl());
                }
            }

            Logger.printDebug(() -> "NewPipe: Got " + itagUrlMap.size() + " streams for videoId=" + videoId
                    + " in " + (System.currentTimeMillis() - startTime) + "ms");

            return new StreamResult(Collections.unmodifiableMap(itagUrlMap));

        } catch (Exception ex) {
            Logger.printException(() -> "NewPipe: Failed to fetch streams for videoId=" + videoId, ex);
            return null;
        }
    }

    private static void addStreamToMap(Map<Integer, String> map, int itag, String content, boolean isUrl) {
        if (itag > 0 && isUrl && content != null && !content.isEmpty()) {
            map.put(itag, content);
        }
    }
}
