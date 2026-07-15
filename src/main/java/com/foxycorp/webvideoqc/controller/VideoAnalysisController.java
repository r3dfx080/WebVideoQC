package com.foxycorp.webvideoqc.controller;

import com.foxycorp.webvideoqc.model.AnalyzeRequest;
import com.foxycorp.webvideoqc.model.VideoMetadata;
import com.foxycorp.webvideoqc.model.VideoStats;
import com.foxycorp.webvideoqc.service.VideoAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/videos")
public class VideoAnalysisController {
    private final VideoAnalysisService videoAnalysisService;

    private final ObjectMapper objectMapper;
    
    public VideoAnalysisController(VideoAnalysisService videoAnalysisService, ObjectMapper objectMapper) {
        this.videoAnalysisService = videoAnalysisService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/get-metadata-by-path")
    public ResponseEntity<VideoMetadata> getMetadataByPath(@Valid @RequestBody AnalyzeRequest request) {
        var metadata = videoAnalysisService.getMetadataByPath(request.getPath());
        return ResponseEntity.ok(metadata);
    }

    @PostMapping("/analyze-by-path")
    public ResponseEntity analyzeByPath(@Valid @RequestBody AnalyzeRequest request) {
        videoAnalysisService.analyzeByPath(request.getPath());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reports/latest")
    public ResponseEntity<VideoStats> latestReport() {
        Path reportPath = Path.of(System.getProperty("user.dir"), "latest.video-stats.json");

        if (!reportPath.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }

        VideoStats stats = objectMapper.readValue(reportPath.toFile(), VideoStats.class);
        return ResponseEntity.ok(stats);
    }

    @GetMapping(value = "/frame-preview", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> framePreview(
            @RequestParam(value = "path", required = false) String path,
            @RequestParam("frame") int frame,
            @RequestParam(value = "overlay", defaultValue = "false") boolean overlay
    ) {
        byte[] image = videoAnalysisService.getFramePreview(path, frame, overlay);
        return ResponseEntity.ok(image);
    }
}
