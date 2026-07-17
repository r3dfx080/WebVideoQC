package com.foxycorp.webvideoqc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.util.List;

// video stats from signalstats (ffmpeg) & metadata from ffprobe
@AllArgsConstructor
@Getter
@Setter
public class VideoStats {

    private Path videoPath;
    private List<FrameStats> frameStatsList;
    private VideoMetadata videoMetadata;

    @Data
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

    public String getFilename() {
        return videoPath.getFileName().toString();
    }
}


