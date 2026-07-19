package com.foxycorp.webvideoqc.model;

public record AudioStats(
        float truePeak,
        float integratedLoudness,
        float dcOffset,
        float rms
) {

    public boolean isDCOffsetPresent() {
        return dcOffset > 1.0;
    }

    public boolean hasClipping() {
        return truePeak >= 0.0;
    }

    public boolean containsLoudnessData() {return !Float.isNaN(integratedLoudness);}
}
