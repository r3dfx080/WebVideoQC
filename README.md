# WebVideoQC

Local technical video QC tool focused on **luma analysis**, with optional audio checks and rule evaluation.

WebVideoQC is a Spring Boot app with a browser UI. Point it at a work folder, pick a file, run analysis via *
*ffmpeg/ffprobe**, then inspect per-frame luma stats, metadata, audio metrics, QC issues, and frame previews.

**Default UI:** [http://localhost:8081](http://localhost:8081)

---

## Features

- List media files from a configured work directory (newest first)
- Read container/stream metadata with `ffprobe`
- Extract per-frame luma signal stats via lavfi `signalstats` (`YLOW`, `YMIN`, `YHIGH`, `YMAX`, `YAVG`)
- Optional audio QC:
    - Basic: true peak, DC offset, RMS (`astats`)
    - Extended: integrated loudness / LUFS (`loudnorm`)
- The latest report is saved as:
    - `latest.video-stats.json` (humanly-readable version)
    - `latest.video-stats.json.gz`
- Runtime QC evaluation (`WARNING` / `CAUTION`) injected when serving the latest report (not stored in the JSON file)
- Chart.js luma chart with horizontal wheel zoom
- Click a chart point to preview that frame (optional broadcast-range overlay)

---

## Using the UI

1. **Workdir files** — search/select a file from `webvideoqc.user.workdir`
2. **Analyze / Load**
    - **Analyze audio** — basic peak / DC / RMS
    - **Calculate loudness** — integrated LUFS (enabled only when audio analysis is on)
3. After analysis (or on page load), the app loads **latest report**
4. Inspect:
    - **QC issues**
    - **Metadata**
    - **Audio stats** (when present)
    - **Luma chart** (`YAVG`, `YMAX`, `YMIN`, `YHIGH`)
5. Click a chart point for a **frame preview**; toggle broadcast-range overlay as needed

Chart Y-axis scaling uses `videoMetadata.bitDepth` when available.

---

## Requirements

| Dependency               | Notes                                      |
|--------------------------|--------------------------------------------|
| **Java 21+**             | Build and runtime                          |
| **ffmpeg** & **ffprobe** | On `PATH`, or set absolute paths in config |

---

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property                             | Default    | Purpose                                                               |
|--------------------------------------|------------|-----------------------------------------------------------------------|
| `server.port`                        | `8081`     | HTTP port                                                             |
| `spring.profiles.active`             | `prod`     | `prod` deletes temp analysis files; non-prod keeps them for debugging |
| `webvideoqc.user.workdir`            | *(set me)* | Absolute path to working directory listed in the UI                   |
| `webvideoqc.ffprobe.binary`          | `ffprobe`  | ffprobe executable name or absolute path                              |
| `webvideoqc.ffmpeg.binary`           | `ffmpeg`   | ffmpeg executable name or absolute path                               |
| `webvideoqc.ffprobe.timeout-seconds` | `1800`     | Analysis timeout (seconds)                                            |

---

## Runtime QC rules

Evaluated by `QcEvaluationService` when serving `/api/videos/reports/latest` (warnings conceptually first):

| Severity | Condition (summary)                                                                                                    |
|----------|------------------------------------------------------------------------------------------------------------------------|
| WARNING  | Limited-range content with substantial luma outside broadcast range (soft, bit-depth-aware scoring; >30% “bad” frames) |
| WARNING  | Interlaced + h-family codec                                                                                            |
| WARNING  | Interlaced + HEVC (H.265)                                                                                              |
| WARNING  | Interlaced NTSC without 4:1:1 chroma subsampling                                                                       |
| CAUTION  | SD content with bt.709 color space                                                                                     |
| WARNING  | Audio clipping (TruePeak >= 0 dBFS)                                                                                    |
| CAUTION  | DC offset present (>5%)                                                                                                |
| CAUTION  | Integrated loudness outside EBU R128 −23 ±1 LU (too high or too low)                                                   |

---

## Tech stack

- Java 21
- Spring Boot 4.1 (Web, Validation, Actuator)
- Gradle (Kotlin DSL)
- Lombok
- Chart.js (CDN) in the static UI
- ffmpeg / ffprobe