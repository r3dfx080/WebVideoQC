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

    /**
     * @return warnings first, then cautions
     */
    public List<QcIssue> evaluate(VideoStats stats) {
        if (stats == null) {
            return List.of();
        }

        List<QcIssue> issues = new ArrayList<>();

        VideoMetadata metadata = stats.getVideoMetadata();
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
        int maxCode = (1 << bitDepth) - 1;

        if (!stats.isLimitedRange()) {
            for (VideoStats.FrameStats frame : frames) {
                if (frame.getYmin() < 0 || frame.getYmax() > maxCode) {
                    return true;
                }
            }
            return false;
        }

        int black = limitedRangeBlack(bitDepth);
        int white = limitedRangeWhite(bitDepth);
        for (VideoStats.FrameStats frame : frames) {
            if (frame.getYmin() < black || frame.getYmax() > white) {
                return true;
            }
        }
        return false;
    }

    private static int limitedRangeBlack(int bitDepth) {
        return 16 << (bitDepth - 8);
    }

    private static int limitedRangeWhite(int bitDepth) {
        return 235 << (bitDepth - 8);
    }
}
