package com.foxycorp.webvideoqc.service;

import com.foxycorp.webvideoqc.infra.FFprobeClient;
import com.foxycorp.webvideoqc.model.VideoMetadata;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class VideoAnalysisService {
    private final FFprobeClient ffprobeClient;

    public VideoAnalysisService(FFprobeClient ffprobeClient) {
        this.ffprobeClient = ffprobeClient;
    }

    public VideoMetadata analyzeByPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }

        Path path = Path.of(rawPath).normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new VideoFileNotFoundException(path.toString());
        }

        return ffprobeClient.probe(path);
    }
}
