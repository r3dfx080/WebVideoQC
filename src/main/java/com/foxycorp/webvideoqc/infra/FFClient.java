package com.foxycorp.webvideoqc.infra;

import com.foxycorp.webvideoqc.model.AudioStats;
import com.foxycorp.webvideoqc.model.VideoMetadata;
import com.foxycorp.webvideoqc.model.VideoStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

@Component
public class FFClient {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("[-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?");
    private final ObjectMapper objectMapper;
    private final String ffprobeBinary;
    private final String ffmpegBinary;
    private final String workDir = System.getProperty("user.dir");
    private final Environment env;

    public FFClient(
            ObjectMapper objectMapper,
            @Value("${webvideoqc.ffprobe.binary}") String ffprobeBinary,
            @Value("${webvideoqc.ffmpeg.binary}") String ffmpegBinary,
            Environment env
    ) {
        this.objectMapper = objectMapper;
        this.ffprobeBinary = ffprobeBinary;
        this.ffmpegBinary = ffmpegBinary;
        this.env = env;
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

            return mapToVideoMetadata(root);
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
     *
     * @param videoFile absolute path to video file
     * @return VideoStats object for passed video file
     */
    public VideoStats getVideoStats(Path videoFile, Boolean analyzeAudio, Boolean analyzeAudioExtended) {
        validateInput(videoFile);

        Path statsFile = Path.of(workDir + "\\temp-signalstats.json");
        try {
            Files.deleteIfExists(statsFile);
            Files.createFile(statsFile);
        } catch (IOException e) {
            throw new FFprobeException("Unable to allocate temp video stats file", e);
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
        command.add("frame=pts_time:frame_tags=lavfi.signalstats.YLOW,lavfi.signalstats.YMIN,lavfi.signalstats.YHIGH,lavfi.signalstats.YMAX,lavfi.signalstats.YAVG");
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

            JsonNode statsRoot = objectMapper.readTree(statsFile.toFile());

            List<VideoStats.FrameStats> frameStatsList = parseFrameStatsFromJson(statsRoot);

            VideoMetadata metadata = getMetadata(videoFile);

            Optional<AudioStats> audioStats = Optional.empty();
            if (analyzeAudio) {
                audioStats = Optional.of(getBasicAudioStats(videoFile));
                if (analyzeAudioExtended) {
                    audioStats.get().setIntegratedLoudness(getLoudness(videoFile));
                }
            }

            return new VideoStats(videoFile, frameStatsList, metadata, audioStats);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FFprobeException("Interrupted while running ffprobe", e);
        } catch (IOException e) {
            throw new FFprobeException("Unable to execute ffprobe", e);
        } finally {
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                try {
                    Files.deleteIfExists(statsFile);
                } catch (IOException e) {
                    throw new FFprobeException("Unable to delete temporary stats file", e);
                }
            }
        }
    }

    public AudioStats getBasicAudioStats(Path videoFile) {
        Path statsFile = Path.of(workDir + "\\temp-basic-audiostats.json");
        try {
            Files.deleteIfExists(statsFile);
            Files.createFile(statsFile);
        } catch (IOException e) {
            throw new FFprobeException("Unable to allocate temp audio stats file", e);
        }

        //ffmpeg -v info -hide_banner -nostats -i "X:\path\to\input.mov" -af "astats=metadata=0:reset=0" -f null NUL

        List<String> command = new ArrayList<>();

        command.add(ffmpegBinary);
        command.add("-v");
        command.add("info");
        command.add("-hide_banner");
        command.add("-nostats");
        command.add("-i");

        command.add(videoFile.toAbsolutePath().toString());

        command.add("-af");
        command.add("astats=metadata=0:reset=0");
        command.add("-f");
        command.add("null");
        command.add("NUL");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

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
            JsonNode parsedRoot = parseAstatsToJson(stderr);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statsFile.toFile(), parsedRoot);
            return mapToAudioStats(parsedRoot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FFmpegException("Interrupted while running ffmpeg", e);
        } catch (IOException e) {
            throw new FFmpegException("Unable to execute ffmpeg", e);
        } finally {
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                try {
                    Files.deleteIfExists(statsFile);
                } catch (IOException e) {
                    throw new FFmpegException("Unable to delete temporary audio stats file", e);
                }
            }
        }
    }

    public float getLoudness(Path videoFile) {
        validateInput(videoFile);

        Path loudnessStatsFile = Path.of(workDir + "\\temp-loudness-stats.log");
        try {
            Files.deleteIfExists(loudnessStatsFile);
            Files.createFile(loudnessStatsFile);
        } catch (IOException e) {
            throw new FFprobeException("Unable to allocate temp video stats file", e);
        }

        //ffmpeg -hide_banner -nostats -i I:\bars.mov -af loudnorm=print_format=json -f null -

        List<String> command = new ArrayList<>();

        command.add(ffmpegBinary);
        command.add("-hide_banner");
        command.add("-nostats");
        command.add("-i");
        command.add(videoFile.toAbsolutePath().toString());
        command.add("-af");
        command.add("loudnorm=print_format=json");
        command.add("-f");
        command.add("null");
        command.add("-");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(loudnessStatsFile.toFile());
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = pb.start();

            int exit = process.waitFor();
            String stderr = Files.readString(loudnessStatsFile);
            if (exit != 0) {
                throw new FFmpegException("ffmpeg failed (exit=" + exit + "): " + stderr);
            }
            int jsonStart = stderr.indexOf('{');
            int jsonEnd = stderr.lastIndexOf('}');
            if (jsonStart < 0 || jsonEnd < 0 || jsonEnd <= jsonStart) {
                throw new FFmpegException("Unable to parse loudnorm output JSON from ffmpeg stderr");
            }

            String jsonPayload = stderr.substring(jsonStart, jsonEnd + 1);

            JsonNode statsRoot = objectMapper.readTree(jsonPayload);

            return extractLoudnessData(statsRoot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FFmpegException("Interrupted while running ffmpeg", e);
        } catch (IOException e) {
            throw new FFmpegException("Unable to execute ffmpeg", e);
        } finally {
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                try {
                    Files.deleteIfExists(loudnessStatsFile);
                } catch (IOException e) {
                    throw new FFmpegException("Unable to delete temporary audio stats file", e);
                }
            }
        }
    }

    private float extractLoudnessData(JsonNode root) {
        return Float.parseFloat(root.get("output_i").asString());
    }

    private AudioStats mapToAudioStats(JsonNode root) {
        JsonNode overall = root.path("overall");
        float truePeak = parseFloatOrNegativeInfinity(overall.path("peak_level_db").asString());
        float dcOffset = parseFloatOrNegativeInfinity(overall.path("dc_offset").asString());
        float rms = parseFloatOrNegativeInfinity(overall.path("rms_level_db").asString());
        return new AudioStats(truePeak, dcOffset, rms);
    }

    private JsonNode parseAstatsToJson(String stderr) {
        var root = objectMapper.createObjectNode();
        var overallNode = objectMapper.createObjectNode();
        root.set("overall", overallNode);
        boolean inOverallSection = false;

        for (String rawLine : stderr.split("\\R")) {
            String line = stripFfmpegPrefix(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }


            if ("Overall".equalsIgnoreCase(line)) {
                inOverallSection = true;
                continue;
            }
            if (!inOverallSection) {
                continue;
            }

            int separator = line.indexOf(':');
            if (separator <= 0 || separator == line.length() - 1) {
                continue;
            }

            String metricName = line.substring(0, separator).trim();
            String metricValue = line.substring(separator + 1).trim();
            String metricKey = normalizeMetricKey(metricName);
            overallNode.put(metricKey, metricValue);
        }

        return root;
    }

    private String stripFfmpegPrefix(String line) {
        int marker = line.lastIndexOf("] ");
        if (marker >= 0 && marker + 2 < line.length()) {
            return line.substring(marker + 2);
        }
        return line;
    }

    private String normalizeMetricKey(String metricName) {
        return metricName
                .toLowerCase()
                .replace("(", "")
                .replace(")", "")
                .replace(".", "")
                .replace("/", "_")
                .replace(" ", "_");
    }

    private float parseFloatOrNegativeInfinity(String value) {
        String normalized = value.trim().toLowerCase();
        Matcher matcher = NUMERIC_PATTERN.matcher(value);

        if ("-inf".equals(normalized) || !matcher.find()) {
            return Float.NEGATIVE_INFINITY;
        }
        try {
            return Float.parseFloat(matcher.group());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    /**
     * Saves VideoStats object into a compressed json
     *
     * @param videoStats VideoStats object
     */
    public Path saveVideoStats(VideoStats videoStats) {
        if (videoStats == null) {
            throw new IllegalArgumentException("videoStats must not be null");
        }
        // TODO: make a proper path resolver
        Path gzipOutput = Path.of(workDir + "\\latest.video-stats.json.gz");
        Path jsonOutput = Path.of(workDir + "\\latest.video-stats.json");
        objectMapper.writeValue(jsonOutput.toFile(), videoStats);

        try (OutputStream out = Files.newOutputStream(gzipOutput);
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            objectMapper.writeValue(gzip, videoStats);
        } catch (IOException e) {
            throw new FFmpegException("Unable to save compressed video stats JSON", e);
        }
        return gzipOutput;
    }

    public byte[] renderFrame(Path videoFile, int frameNumber, boolean overlay) {
        validateInput(videoFile);

        String selectFilter = "select='eq(n\\," + frameNumber + ")'";
        String filter = overlay
                ? selectFilter + ",signalstats=out=brng:color=red,format=yuv420p"
                : selectFilter + ",format=yuv420p";
        Path previewOutput = Path.of(workDir, "temp-preview.jpg");

        List<String> command = new ArrayList<>();
        command.add(ffmpegBinary);
        command.add("-y");
        command.add("-v");
        command.add("error");
        command.add("-i");
        command.add(videoFile.toAbsolutePath().toString());
        command.add("-vf");
        command.add(filter);
        command.add("-frames:v");
        command.add("1");
        command.add("-pix_fmt");
        command.add("yuvj420p");
        command.add(previewOutput.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);

        try {
            Process process = pb.start();
            String stderr;
            try (InputStream err = process.getErrorStream()) {
                stderr = new String(err.readAllBytes());
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new FFmpegException("ffmpeg preview failed (exit=" + exit + "): " + stderr);
            }
            byte[] imageBytes = Files.readAllBytes(previewOutput);
            if (imageBytes.length == 0) {
                throw new FFmpegException("ffmpeg preview returned empty image");
            }
            return imageBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FFmpegException("Interrupted while rendering frame preview", e);
        } catch (IOException e) {
            throw new FFmpegException("Unable to execute ffmpeg for frame preview", e);
        }
    }


    /**
     * Validate that video file exist and is a regular file
     */
    private void validateInput(Path videoFile) {
        if (videoFile == null) {
            throw new IllegalArgumentException("videoFile must not be null");
        }
        if (!Files.exists(videoFile) || !Files.isRegularFile(videoFile)) {
            throw new IllegalArgumentException("Video file not found: " + videoFile);
        }
    }

    private List<VideoStats.FrameStats> parseFrameStatsFromJson(JsonNode root) {
        List<VideoStats.FrameStats> frames = new ArrayList<>();

        for (JsonNode frame : root.path("frames")) {
            JsonNode tags = frame.path("tags");
            if (tags.isMissingNode() || tags.isNull()) continue;

            VideoStats.FrameStats fs = new VideoStats.FrameStats();
            fs.setYlow(Integer.parseInt(tags.path("lavfi.signalstats.YLOW").asString()));
            fs.setYmin(Integer.parseInt(tags.path("lavfi.signalstats.YMIN").asString()));
            fs.setYhigh(Integer.parseInt(tags.path("lavfi.signalstats.YHIGH").asString()));
            fs.setYmax(Integer.parseInt(tags.path("lavfi.signalstats.YMAX").asString()));
            fs.setYavg(Float.parseFloat(tags.path("lavfi.signalstats.YAVG").asString()));

            frames.add(fs);
        }

        return frames;
    }

    private VideoMetadata mapToVideoMetadata(JsonNode root) {
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
        String fieldOrder = videoStream.path("field_order").asString("unknown");
        String colorRange = videoStream.path("color_range").asString("unknown");
        String avgFrameRate = videoStream.path("avg_frame_rate").asString("0/0");
        double fps = parseFps(avgFrameRate);
        int bitDepth = videoStream.path("bits_per_raw_sample").asInt(0);

        return new VideoMetadata(
                width,
                height,
                codec,
                pixFmt,
                colorRange,
                fieldOrder,
                fps,
                Duration.ofMillis((long) (durationSec * 1000)),
                bitRate,
                bitDepth
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
