package com.foxycorp.webvideoqc.infra;

import com.foxycorp.webvideoqc.model.VideoMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class FFprobeClient {

    private final ObjectMapper objectMapper;
    private final String ffprobeBinary;

    public FFprobeClient(
            ObjectMapper objectMapper,
            @Value("${webvideoqc.ffprobe.binary}") String ffprobeBinary
    ) {
        this.objectMapper = objectMapper;
        this.ffprobeBinary = ffprobeBinary;
    }

    public VideoMetadata probe(Path videoFile) {
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

    private void validateInput(Path videoFile) {
        if (videoFile == null) {
            throw new IllegalArgumentException("videoFile must not be null");
        }
        if (!Files.exists(videoFile) || !Files.isRegularFile(videoFile)) {
            throw new IllegalArgumentException("Video file not found: " + videoFile);
        }
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
        if (parts.length != 2) return 0.0;

        try {
            double num = Double.parseDouble(parts[0]);
            double den = Double.parseDouble(parts[1]);
            if (den == 0.0) return 0.0;
            return num / den;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
