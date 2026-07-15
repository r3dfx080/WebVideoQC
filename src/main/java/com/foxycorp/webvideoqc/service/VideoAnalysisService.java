package com.foxycorp.webvideoqc.service;

import com.foxycorp.webvideoqc.infra.FFClient;
import com.foxycorp.webvideoqc.model.VideoMetadata;
import com.foxycorp.webvideoqc.model.VideoStats;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class VideoAnalysisService {
    private final FFClient ffClient;

    public VideoAnalysisService(FFClient ffClient) {
        this.ffClient = ffClient;
    }

    public VideoMetadata getMetadataByPath(String rawPath) {
        return ffClient.getMetadata(getPath(rawPath));
    }

    public Path analyzeByPath(String rawPath){
        var path = getPath(rawPath);
        var videoStats = ffClient.getVideoStats(path);
        return ffClient.saveVideoStats(videoStats);
    }

    public byte[] getFramePreview(String rawPath, int frame, boolean overlay) {
        return ffClient.renderFrame(getPath(rawPath), frame, overlay);
    }

    private static @NonNull Path getPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }

        Path path = Path.of(rawPath).normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new VideoFileNotFoundException(path.toString());
        }
        return path;
    }

    public boolean videoExists(Path path) {
        return Files.exists(path) && !Files.isRegularFile(path);
    }

    /**
     * @param currentPath path of video for comparison
     * @param currentMetadata metadata of video for comparison
     * @param existingStats existing VideoStats instance
     */
    public boolean isEqual(Path currentPath, VideoMetadata currentMetadata, VideoStats existingStats){
        return currentPath.equals(existingStats.getVideoPath()) && currentMetadata.equals(existingStats.getVideoMetadata());
    }
}
