package com.foxycorp.webvideoqc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// video stats from signalstats (ffmpeg) & metadata from ffprobe
@AllArgsConstructor
@Getter
@Setter
public class VideoStats {

    private Path videoPath;
    private List<FrameStats> frameStatsList;
    private VideoMetadata videoMetadata;
    private Optional<AudioStats> audioStats;

    public boolean isSD() {
        return videoMetadata.height() == 576 || videoMetadata.height() == 480 || videoMetadata.height() == 486;
    }

    public boolean isPAL() {
        return videoMetadata.height() == 576 && isPALFramerate();
    }

    public boolean isNTSC() {
        return (videoMetadata.height() == 480 || videoMetadata.height() == 486) && isNTSCFramerate();
    }

    public boolean isPALFramerate() {
        return Math.abs(videoMetadata.fps() - 25.0) < 0.1 || Math.abs(videoMetadata.fps() - 50.0) < 0.1;
    }

    public boolean isNTSCFramerate() {
        return Math.abs(videoMetadata.fps() - 30.0) < 0.1 || Math.abs(videoMetadata.fps() - 60.0) < 0.1;
    }

    public boolean isInterlaced() {
        return !videoMetadata.fieldOrder().equals("progressive");
    }

    public boolean isLimitedRange() {
        return !videoMetadata.colorRange().equals("pc");
    }

    @JsonProperty
    public String getFilename() {
        return videoPath.getFileName().toString();
    }

    @JsonProperty
    public boolean areAudioStatsPresent() {
        return audioStats.isPresent();
    }

    @Getter
    @Setter
    public static class FrameStats {
        private int ylow;
        private int ymin;
        private int yhigh;
        private int ymax;
        private float yavg;
    }
}


