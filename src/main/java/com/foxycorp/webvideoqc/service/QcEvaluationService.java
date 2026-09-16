package com.foxycorp.webvideoqc.service;

import com.foxycorp.webvideoqc.model.AudioStats;
import com.foxycorp.webvideoqc.model.QcIssue;
import com.foxycorp.webvideoqc.model.VideoMetadata;
import com.foxycorp.webvideoqc.model.VideoStats;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives caution/warning findings from analyzed {@link VideoStats}.
 * Results are runtime-only and are not persisted in the report JSON.
 */
@Service
public class QcEvaluationService {

    private static final double EBU_R128_TARGET_LUFS = -23.0;
    private static final double EBU_R128_TOLERANCE_LU = 1.0;

    // 8-bit codes forgiven past 16/235; scaled with bit depth
    int LUMA_SOFT_CODES_8BIT = 2;
    double LUMA_EXCURSION_POWER = 2.0;
    // frame counts as "bad" only if soft weight exceeds this threshold
    double LUMA_BAD_FRAME_TAU = 0.05;
    double LUMA_BAD_FRAME_SHARE = 0.30;

    /**
     * @return warnings first, then cautions
     */
    public List<QcIssue> evaluate(VideoStats stats) {
        if (stats == null) {
            return List.of();
        }

        List<QcIssue> issues = new ArrayList<>();

        VideoMetadata metadata = stats.getVideoMetadata();

        if (stats.isLimitedRange() && hasLumaOutsideBroadcastRange(stats)) {
            issues.add(new QcIssue(
                    QcIssue.Severity.WARNING,
                    "More than 30% of frames have luma (Y) values outside of " +
                            "broadcast range! Adjust the range tag accordingly or " +
                            "use limiter"
            ));
        }

        if (stats.isInterlaced() && metadata.codec().startsWith("h")) {
            issues.add(new QcIssue(
                    QcIssue.Severity.WARNING,
                    "An interlaced source was compressed with h-family codec!" +
                            "A color information may be lost on one of the fields! " +
                            "Check the footage manually or use x-family codec with proper interlaced tag"
            ));
        }
        if (stats.isInterlaced() && metadata.codec().contains("265")) {
            issues.add(new QcIssue(
                    QcIssue.Severity.WARNING,
                    "An interlaced source was compressed with HEVC (H.265) codec!" +
                            "HEVC does NOT support interlaced encoding natively." +
                            "Use H.264 or deinterlace the footage"
            ));
        }
        if (stats.isInterlaced() && stats.isNTSC() && !metadata.pixelFormat().contains("411")) {
            issues.add(new QcIssue(
                    QcIssue.Severity.WARNING,
                    "An NTSC source may have been interpreted incorrectly! " +
                            "A 4:1:1 chroma subsampling is required, but " + metadata.pixelFormat() +
                            " pixel format is used. You may be losing vertical color resolution! " +
                            "Re-encode the footage accordingly"
            ));
        }
        if (stats.isSD() && metadata.colorSpace().contains("709")) {
            issues.add(new QcIssue(
                    QcIssue.Severity.CAUTION,
                    "Your SD footage uses incorrect color space! " +
                            metadata.colorSpace() + " was detected. " +
                            "Re-encode the footage with appropriate color space (bt.601, etc.) " +
                            "or ignore this message"
            ));
        }

        if (stats.areAudioStatsPresent()) {
            AudioStats audioStats = stats.getAudioStats().get();
            if (audioStats.hasClipping()) {
                issues.add(new QcIssue(
                        QcIssue.Severity.WARNING,
                        "Audio is clipping (true peak ≥ 0 dBFS)"
                ));
            }
            if (audioStats.isDCOffsetPresent()) {
                issues.add(new QcIssue(
                        QcIssue.Severity.CAUTION,
                        "A DC offset was detected"
                ));
            }
            if (audioStats.containsLoudnessData()) {
                float loudness = audioStats.getIntegratedLoudness();
                float delta = loudness - (float) EBU_R128_TARGET_LUFS;
                if ((delta > 0) && (delta > EBU_R128_TOLERANCE_LU)) {
                    issues.add(new QcIssue(
                            QcIssue.Severity.CAUTION,
                            String.format(
                                    "Integrated loudness %.2f LUFS outside EBU R128 target (−23 ±1 LU)",
                                    loudness
                            )
                    ));
                    // integrated loudness is lower than -25 LUFS
                } else if (delta < -1) {
                    issues.add(new QcIssue(
                            QcIssue.Severity.CAUTION,
                            String.format(
                                    "The loudness [%.2f LUFS] is too low",
                                    loudness
                            )
                    ));
                }
            }
        }

        return issues;
    }


    private boolean hasLumaOutsideBroadcastRange(VideoStats stats) {
        List<VideoStats.FrameStats> frames = stats.getFrameStatsList();
        VideoMetadata metadata = stats.getVideoMetadata();
        if (frames == null || frames.isEmpty() || metadata == null) {
            return false;
        }

        int bitDepth = metadata.bitDepth();
        int black = limitedRangeBlack(bitDepth);
        int white = limitedRangeWhite(bitDepth);
        int maxCode = (1 << bitDepth) - 1;
        int softCodes = LUMA_SOFT_CODES_8BIT << (bitDepth - 8);

        int badFrames = 0;
        for (VideoStats.FrameStats frame : frames) {
            double w = frameLumaSoftWeight(
                    frame.getYmin(), frame.getYmax(),
                    black, white, maxCode, softCodes, LUMA_EXCURSION_POWER);
            if (w > LUMA_BAD_FRAME_TAU) {
                badFrames++;
            }
        }

        return badFrames > frames.size() * LUMA_BAD_FRAME_SHARE;
    }


    /**
     * @return per-frame weight in [0, 1]: max of soft low / soft high excursion
     */
    private static double frameLumaSoftWeight(
            int ymin, int ymax,
            int black, int white, int maxCode,
            int softCodes, double p) {
        int lowOver = Math.max(0, black - ymin);
        int highOver = Math.max(0, ymax - white);
        double wLow = softExcursion(lowOver, black, softCodes, p);
        double wHigh = softExcursion(highOver, maxCode - white, softCodes, p);
        return Math.max(wLow, wHigh);
    }


    private static double softExcursion(int overshootCodes, int fullIllegalSpan, int softCodes, double p) {
        int dead = Math.min(softCodes, fullIllegalSpan - 1); // keep at least 1 code of span
        int punishable = fullIllegalSpan - dead;
        double t = (overshootCodes - dead) / (double) punishable;
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return Math.pow(t, p);
    }


    private static int limitedRangeBlack(int bitDepth) {
        return 16 << (bitDepth - 8);
    }

    private static int limitedRangeWhite(int bitDepth) {
        return 235 << (bitDepth - 8);
    }
}
