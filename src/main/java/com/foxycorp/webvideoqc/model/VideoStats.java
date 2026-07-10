package com.foxycorp.webvideoqc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

// video stats from signalstats (ffmpeg) & metadata from ffprobe
@AllArgsConstructor
@Getter
@Setter
public class VideoStats {
    // TODO: implement md5(?) for fast checking

    private List<FrameStats> frameStatsList;
    private VideoMetadata videoMetadata;

    @Data
    public static class FrameStats {

        private int ylow;
        private int yhigh;
        private int ymax;
        private float yavg;
    }

}


