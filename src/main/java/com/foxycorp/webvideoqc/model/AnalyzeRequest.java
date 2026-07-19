package com.foxycorp.webvideoqc.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AnalyzeRequest {
    @NotBlank
    private String path;
    private Boolean analyzeAudio;
    private Boolean analyzeAudioExtended;
}
