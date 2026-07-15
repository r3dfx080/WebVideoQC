package com.foxycorp.webvideoqc.service;

import com.foxycorp.webvideoqc.infra.FFClient;
import com.foxycorp.webvideoqc.model.VideoMetadata;
import com.foxycorp.webvideoqc.model.VideoStats;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Service
public class VideoAnalysisService {
    private final FFClient ffClient;
    private final ObjectMapper objectMapper;

    public VideoAnalysisService(FFClient ffClient, ObjectMapper objectMapper) {
        this.ffClient = ffClient;
        this.objectMapper = objectMapper;
    }

    private Path resolvePreviewPath(String rawPath) {
        if (rawPath != null && !rawPath.isBlank()) {
            return getPath(rawPath);
        }

        Path reportPath = Path.of(System.getProperty("user.dir"), "latest.video-stats.json");
        if (!Files.exists(reportPath) || !Files.isRegularFile(reportPath)) {
            throw new IllegalArgumentException("Path is required when latest report is unavailable");
        }

        VideoStats stats = objectMapper.readValue(reportPath.toFile(), VideoStats.class);
        if (stats == null || stats.getVideoPath() == null) {
            throw new IllegalArgumentException("Latest report does not contain videoPath");
        }

        Path statsVideoPath = stats.getVideoPath().normalize();
        if (!Files.exists(statsVideoPath) || !Files.isRegularFile(statsVideoPath)) {
            throw new VideoFileNotFoundException(statsVideoPath.toString());
        }
        return statsVideoPath;
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
        return ffClient.renderFrame(resolvePreviewPath(rawPath), frame, overlay);
    }

    private static @NonNull Path getPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }
        Path path;
        try {
            path = Path.of(rawPath).normalize();
        } catch (InvalidPathException e) {
            if (rawPath.startsWith("file:")) {
                try {
                    path = Path.of(URI.create(rawPath)).normalize();
                } catch (RuntimeException ignored) {
                    throw new IllegalArgumentException("Path is invalid: " + rawPath);
                }
            } else {
                throw new IllegalArgumentException("Path is invalid: " + rawPath);
            }
        }
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
