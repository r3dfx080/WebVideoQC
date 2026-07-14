package com.foxycorp.webvideoqc.service;

import com.foxycorp.webvideoqc.infra.FFClient;
import com.foxycorp.webvideoqc.model.VideoMetadata;
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
}
