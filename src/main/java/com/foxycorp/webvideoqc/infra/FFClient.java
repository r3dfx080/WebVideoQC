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
     * ffprobe's lavfi + signalstats is used, resulting .json is parsed and then deleted
     * @param videoFile absolute path to video file
     * @return VideoStats object for passed video file
     */
    public VideoStats getVideoStats(Path videoFile) {
        validateInput(videoFile);
        Path statsFile;
        try {
            statsFile = Files.createTempFile("videoqc-signalstats-", ".json");
        } catch (IOException e) {
            throw new FFprobeException("Unable to allocate temp stats file", e);
        }

        List<String> command = new ArrayList<>();

        command.add(ffprobeBinary);
        command.add("-v");
        command.add("error");

        String lavfiPath = videoFile.toAbsolutePath().toString()
                .replace("\\", "/")
                .replace(":", "\\:")
                .replace("'", "\\'");

        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("movie=filename='" + lavfiPath + "',signalstats");

        command.add("-show_frames");
        command.add("-show_entries");
        command.add("frame=pts_time:frame_tags=lavfi.signalstats.YLOW,lavfi.signalstats.YHIGH,lavfi.signalstats.YMAX,lavfi.signalstats.YAVG");
        command.add("-of");
        command.add("json=compact=1");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(statsFile.toFile());

        try {
            Process process = pb.start();

            String stderr;
            try (InputStream err = process.getErrorStream()) {
                stderr = new String(err.readAllBytes());
            }

            int exit = process.waitFor();
            if (exit != 0) {
                throw new FFprobeException("ffmpeg failed (exit=" + exit + "): " + stderr);
            }
            VideoMetadata metadata = getMetadata(videoFile);
            JsonNode statsRoot = objectMapper.readTree(statsFile.toFile());
            return parseStatsFromJson(statsRoot, metadata);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FFprobeException("Interrupted while running ffprobe", e);
        } catch (IOException e) {
            throw new FFprobeException("Unable to execute ffprobe", e);
        } finally {
//            try {
//                Files.deleteIfExists(statsFile);
//            } catch (IOException e) {
//                throw new FFprobeException("Unable to delete temporary stats file", e);
//            }
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
        Path gzipOutput = Path.of(workDir + "\\test.video-stats.json.gz");
        Path jsonOutput = Path.of(workDir + "\\test.video-stats.json");

        objectMapper.writeValue(jsonOutput.toFile(), videoStats);

        try (OutputStream out = Files.newOutputStream(gzipOutput);
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            objectMapper.writeValue(gzip, videoStats);
        } catch (IOException e) {
            throw new FFmpegException("Unable to save compressed video stats JSON", e);
        }
        return gzipOutput;
    }

    private void validateInput(Path videoFile) {
        if (videoFile == null) {
            throw new IllegalArgumentException("videoFile must not be null");
        }
        if (!Files.exists(videoFile) || !Files.isRegularFile(videoFile)) {
            throw new IllegalArgumentException("Video file not found: " + videoFile);
        }
    }


    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private VideoStats parseStatsFromJson(JsonNode root, VideoMetadata metadata) {
        List<VideoStats.FrameStats> frames = new ArrayList<>();

        for (JsonNode frame : root.path("frames")) {
            JsonNode tags = frame.path("tags");
            if (tags.isMissingNode() || tags.isNull()) continue;

            VideoStats.FrameStats fs = new VideoStats.FrameStats();
            fs.setYlow(parseInt(tags, "lavfi.signalstats.YLOW"));
            fs.setYhigh(parseInt(tags, "lavfi.signalstats.YHIGH"));
            fs.setYmax(parseInt(tags, "lavfi.signalstats.YMAX"));
            fs.setYavg(parseFloat(tags, "lavfi.signalstats.YAVG"));

            frames.add(fs);
        }

        return new VideoStats(frames, metadata);
    }

    private int parseInt(JsonNode tags, String key) {
        String v = tags.path(key).asString(null);
        if (v == null || v.isBlank()) return 0;
        return (int) Double.parseDouble(v);
    }

    private float parseFloat(JsonNode tags, String key) {
        String v = tags.path(key).asString(null);
        if (v == null || v.isBlank()) return 0f;
        return Float.parseFloat(v);
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
