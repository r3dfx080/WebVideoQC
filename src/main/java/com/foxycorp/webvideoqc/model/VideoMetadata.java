package com.foxycorp.webvideoqc.model;

import java.time.Duration;
import java.util.Objects;

// video metadata from ffprobe
public record VideoMetadata(
        int width,
        int height,
        String codec,
        String pixelFormat,
        String colorRange,
        double fps,
        Duration duration,
        long bitRate
) {
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        VideoMetadata that = (VideoMetadata) o;
        return width == that.width && height == that.height && Double.compare(fps, that.fps) == 0 && bitRate == that.bitRate && Objects.equals(codec, that.codec) && Objects.equals(colorRange, that.colorRange) && Objects.equals(duration, that.duration) && Objects.equals(pixelFormat, that.pixelFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, codec, pixelFormat, colorRange, fps, duration, bitRate);
    }
}
