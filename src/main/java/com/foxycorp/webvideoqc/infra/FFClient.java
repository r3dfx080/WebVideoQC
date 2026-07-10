package com.foxycorp.webvideoqc.infra;

import com.foxycorp.webvideoqc.model.VideoMetadata;
import com.foxycorp.webvideoqc.model.VideoStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Component
public class FFClient {
    private final ObjectMapper objectMapper;
    private final String ffprobeBinary;
    private final String ffmpegBinary;
    private final String workDir = System.getProperty("user.dir");

    public FFClient(
            ObjectMapper objectMapper,
            @Value("${webvideoqc.ffprobe.binary}") String ffprobeBinary,
            @Value("${webvideoqc.ffmpeg.binary}") String ffmpegBinary
    ) {
        this.objectMapper = objectMapper;
        this.ffprobeBinary = ffprobeBinary;
        this.ffmpegBinary = ffmpegBinary;
    }

    public VideoMetadata getMetadata(Path videoFile) {
        validateInput(videoFile);

        List<String> command = new ArrayList<>();
        command.add(ffprobeBinary);
        command.add("-v");
        command.add("error");
        command.add("-print_format");
        command.add("json");
        command.add("-show_format");
        command.add("-show_streams");
        command.add(videoFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);

        try {
            Process process = pb.start();

            JsonNode root;
            try (InputStream stdout = process.getInputStream()) {
                root = objectMapper.readTree(stdout);
            }

            String stderr;
            try (InputStream err = process.getErrorStream()) {
                stderr = new String(err.readAllBytes());
            }

            int exit = process.waitFor();
            if (exit != 0) {
                throw new FFprobeException("ffprobe failed (exit=" + exit + "): " + stderr);
            }

            return mapToMetadata(root);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FFprobeException("Interrupted while running ffprobe", e);
        } catch (IOException e) {
            throw new FFprobeException("Unable to execute ffprobe", e);
        }
    }

    /**
     * Gets VideoStats for a passed video file path.
     * FFmpeg's signalstats is used, resulting .txt is parsed and then deleted
     * @param videoFile absolute path to video file
     * @return VideoStats object for passed video file
     */
    public VideoStats getVideoStats(Path videoFile) {
        validateInput(videoFile);
        Path statsFile;
        try {
            statsFile = Files.createFile(Path.of(workDir + "/stats.txt"));
        } catch (IOException e) {
            throw new FFmpegException("Unable to allocate temp stats file", e);
        }

        String ffmpegStatsFilePath = escapeForFfmpegMetadataPath(statsFile);

        List<String> command = new ArrayList<>();
        command.add(ffmpegBinary);
        command.add("-i");
        command.add(videoFile.toAbsolutePath().toString());
        command.add("-vf");
        command.add("signalstats,metadata=print:file=" + ffmpegStatsFilePath);
        command.add("-f");
        command.add("null");
        command.add("-");

        ProcessBuilder pb = new ProcessBuilder(command);

        try {
            Process process = pb.start();

            String stderr;
            try (InputStream err = process.getErrorStream()) {
                stderr = new String(err.readAllBytes());
            }

            int exit = process.waitFor();
            if (exit != 0) {
                throw new FFmpegException("ffmpeg failed (exit=" + exit + "): " + stderr);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FFmpegException("Interrupted while running ffmpeg", e);
        } catch (IOException e) {
            throw new FFmpegException("Unable to execute ffmpeg", e);
        }

        VideoMetadata metadata = getMetadata(videoFile);
        try {
            return parseStats(statsFile, metadata);
        } catch (IOException e) {
            throw new FFmpegException("Unable to parse ffmpeg signalstats output", e);
        } finally {
            try {
                Files.deleteIfExists(statsFile);
            } catch (IOException e) {
                throw new FFmpegException("Unable to delete temporary stats file", e);
            }
        }
    }


    /**
     * Returns VideoStats object with embedded metadata & frame-by-frame statistics
     * @param statsFile .txt file with frame-by-frame statistics from ffmpeg.exe
     * @param metadata video metadata
     * @return VideoStats
     */
    public VideoStats parseStats(Path statsFile, VideoMetadata metadata) throws IOException {
        List<VideoStats.FrameStats> frames = new ArrayList<>();
        VideoStats.FrameStats current = null;

        for (String raw : Files.readAllLines(statsFile)) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("frame:")) {
                if (current != null) {
                    frames.add(current);
                }
                current = new VideoStats.FrameStats();
                continue;
            }

            if (current == null || line.indexOf('=') < 0) {
                continue;
            }

            double value = Double.parseDouble(line.substring(line.indexOf('=') + 1));
            if (line.startsWith("lavfi.signalstats.YLOW=")) {
                current.setYlow((int) value);
            } else if (line.startsWith("lavfi.signalstats.YHIGH=")) {
                current.setYhigh((int) value);
            } else if (line.startsWith("lavfi.signalstats.YMAX=")) {
                current.setYmax((int) value);
            } else if (line.startsWith("lavfi.signalstats.YAVG=")) {
                current.setYavg((float) value);
            }
        }

        if (current != null) {
            frames.add(current);
        }
        return new VideoStats(frames, metadata);
    }

    /**
     * Saves VideoStats object into a compressed json
     * @param videoStats VideoStats object
     */
    public Path saveVideoStats(VideoStats videoStats) {
        if (videoStats == null) {
            throw new IllegalArgumentException("videoStats must not be null");
        }
        // TODO: make a proper path resolver
        Path output = Path.of(stripExtension(workDir) + "\\test.video-stats.json.gz");

        try (OutputStream out = Files.newOutputStream(output);
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            objectMapper.writeValue(gzip, videoStats);
        } catch (IOException e) {
            throw new FFmpegException("Unable to save compressed video stats JSON", e);
        }
        return output;
    }

    private void validateInput(Path videoFile) {
        if (videoFile == null) {
            throw new IllegalArgumentException("videoFile must not be null");
        }
        if (!Files.exists(videoFile) || !Files.isRegularFile(videoFile)) {
            throw new IllegalArgumentException("Video file not found: " + videoFile);
        }
    }

    private String escapeForFfmpegMetadataPath(Path path) {
        return path.toAbsolutePath().toString()
                .replace("\\", "/")
                .replace(":", "\\\\:");
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private VideoMetadata mapToMetadata(JsonNode root) {
        JsonNode format = root.path("format");
        JsonNode streams = root.path("streams");

        JsonNode videoStream = null;
        for (JsonNode stream : streams) {
            if ("video".equals(stream.path("codec_type").asString())) {
                videoStream = stream;
                break;
            }
        }

        if (videoStream == null) {
            throw new FFprobeException("No video stream found");
        }

        double durationSec = format.path("duration").asDouble(0.0);
        long bitRate = format.path("bit_rate").asLong(0L);

        int width = videoStream.path("width").asInt(0);
        int height = videoStream.path("height").asInt(0);
        String codec = videoStream.path("codec_name").asString("unknown");
        String pixFmt = videoStream.path("pix_fmt").asString("unknown");

        String colorRange = videoStream.path("color_range").asString("unknown");
        String avgFrameRate = videoStream.path("avg_frame_rate").asString("0/0");
        double fps = parseFps(avgFrameRate);

        return new VideoMetadata(
                width,
                height,
                codec,
                pixFmt,
                colorRange,
                fps,
                Duration.ofMillis((long) (durationSec * 1000)),
                bitRate
        );
    }

    private double parseFps(String ratio) {
        String[] parts = ratio.split("/");
        if (parts.length != 2) {
            return 0.0;
        }

        try {
            double num = Double.parseDouble(parts[0]);
            double den = Double.parseDouble(parts[1]);
            if (den == 0.0) {
                return 0.0;
            }
            return num / den;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
