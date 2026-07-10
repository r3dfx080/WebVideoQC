package com.foxycorp.webvideoqc.model;

import java.time.Duration;

public record VideoMetadata(
        int width,
        int height,
        String codec,
        String pixelFormat,
        String colorRange,
        double fps,
        Duration duration,
        long bitRate
) {}
