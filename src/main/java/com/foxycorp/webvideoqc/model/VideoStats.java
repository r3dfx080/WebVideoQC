package com.foxycorp.webvideoqc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
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

    @Getter
    @Setter
    public static class FrameStats {
        private int ylow;
        private int ymin;
        private int yhigh;
        private int ymax;
        private float yavg;
    }

    public boolean isSD() {
        return videoMetadata.height() == 576 || videoMetadata.height() == 480 || videoMetadata.height() == 486;
    }

    public boolean isPAL() {
        return videoMetadata.height() == 576;
    }

    public boolean isInterlaced() {
        return !videoMetadata.fieldOrder().equals("progressive");
    }

    public boolean isLimitedRange() {return videoMetadata.colorRange().equals("tv");}

    @JsonProperty
    public String getFilename() {
        return videoPath.getFileName().toString();
    }

    @JsonProperty
    public boolean areAudioStatsPresent() {
        return audioStats.isPresent();
    }
}


