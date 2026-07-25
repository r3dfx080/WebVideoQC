package com.foxycorp.webvideoqc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AudioStats {

    private float truePeak;
    private float integratedLoudness = Float.NaN; // defaults to NaN in case of basic audio analysis
    private float dcOffset;
    private float rms;

    public AudioStats(float truePeak, float dcOffset, float rms) {
        this.truePeak = truePeak;
        this.dcOffset = dcOffset;
        this.rms = rms;
    }

    public boolean isDCOffsetPresent() {
        return dcOffset > 1.0;
    }

    @JsonProperty
    public boolean hasClipping() {
        return truePeak >= 0.0;
    }

    @JsonProperty
    public boolean containsLoudnessData() {
        return !Float.isNaN(integratedLoudness);
    }
}
