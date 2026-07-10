package com.foxycorp.webvideoqc.controller;

import com.foxycorp.webvideoqc.model.AnalyzeRequest;
import com.foxycorp.webvideoqc.model.VideoMetadata;
import com.foxycorp.webvideoqc.service.VideoAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/videos")
public class VideoAnalysisController {
    private final VideoAnalysisService videoAnalysisService;

    public VideoAnalysisController(VideoAnalysisService videoAnalysisService) {
        this.videoAnalysisService = videoAnalysisService;
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
}
