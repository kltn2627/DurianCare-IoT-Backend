# DurianCare AI — Performance Report

**Date:** 2026-09-15  
**Branch:** duy/cleanup-fixes  
**Environment:** CPU only (no GPU), Docker container, localhost:8000  
**Benchmark:** Full validation run — 22 executed tests + 3 skipped (no images)

---

## Latency Summary

| Metric | Value | Notes |
|--------|-------|-------|
| Average | 2,181 ms | Across all 22 executed tests |
| Median | 1,187 ms | More representative of typical warm requests |
| P95 | 6,924 ms | Driven by solid_green (passes color gate, YOLO runs exhaustively) |
| Max | 18,086 ms | Solid yellow — YOLO runs all variants, no detection; includes partial warmup |

---

## Latency by Path

### Path 1: Pre-YOLO fast rejection (green gate fails)
Images rejected before YOLO runs. Fastest path.

| Image | Latency (ms) |
|-------|-------------|
| solid_red | 5 |
| solid_blue | 5 |
| solid_orange | 5 |
| random_noise | 7 |
| solid_black | 17 |
| solid_white | 20 |
| solid_gray | 30 |

**Typical: 5–30 ms**

### Path 2: YOLO runs, no leaf found (422 returned)
Images pass the color gate but YOLO finds no leaf bbox. Multi-attempt search runs.

| Image | Latency (ms) | Notes |
|-------|-------------|-------|
| dark_green | 902 | Dark solid — limited YOLO variants |
| bright_white | 873 | Bright solid — limited YOLO variants |
| green_noise | 2,648 | Greenish noise — more YOLO attempts |
| solid_green | 6,924 | Solid green — full multi-attempt search |
| solid_yellow | 18,086 | Yellow passes gate; full YOLO search + warmup overhead |

**Typical: 900–7,000 ms (warm); up to 18 seconds with cold YOLO start**

### Path 3: Leaf detected, classified successfully (200 returned)
Full pipeline: YOLO detection → crop validation → MobileNetV2 → response.

| Image | Latency (ms) | Disease | Confidence |
|-------|-------------|---------|-----------|
| algal_leaf_spot (1080×810, original) | 3,604 | ALGAL_LEAF_SPOT | 48.59% |
| leaf_blight (1080×810, original) | 3,910 | LEAF_BLIGHT | 78.07% |
| algal_lowres_320x240 (ESP32 sim) | 1,175 | ALGAL_LEAF_SPOT | 63.47% |
| algal_jpeg_q20_640x480 (ESP32 sim) | 1,944 | ALGAL_LEAF_SPOT | 53.95% |
| algal_dark_underexposed (ESP32 sim) | 1,137 | ALGAL_LEAF_SPOT | 77.57% |
| algal_motion_blur (ESP32 sim) | 1,206 | ALGAL_LEAF_SPOT | 66.66% |
| blight_lowres_320x240 (ESP32 sim) | 1,277 | LEAF_BLIGHT | 55.21% |
| blight_jpeg_q20_640x480 (ESP32 sim) | 1,771 | LEAF_BLIGHT | 64.40% |
| blight_dark_underexposed (ESP32 sim) | 1,187 | LEAF_BLIGHT | 46.78% |
| blight_motion_blur (ESP32 sim) | 1,248 | LEAF_BLIGHT | 61.86% |

**Typical (warm, real leaf): 1,100–4,000 ms**

---

## Phase Averages

| Phase | Tests Run | Avg Latency (ms) |
|-------|-----------|-----------------|
| safety_regression | 12 | 2,460 |
| diagnosis_regression | 2 | 3,757 |
| esp32_simulation | 8 | 1,368 |

---

## Observations

1. **Pre-YOLO fast-reject is very fast (5–30 ms).** Images failing the green excess gate are handled efficiently with no YOLO overhead. This is the majority of non-leaf requests in production.

2. **Full YOLO path (warm) averages 1.1–4.0 seconds.** For leaf images that reach classification, latency is driven almost entirely by YOLO inference (MobileNetV2 adds negligible overhead on CPU).

3. **ESP32-simulated images are faster than full-resolution (1,137–1,944 ms vs 3,604–3,910 ms).** The lower resolution (320×240) reduces YOLO processing time. Classification accuracy is maintained.

4. **YOLO warmup adds ~14–16 seconds to the first call.** After warmup (model cached in memory), all subsequent requests drop to typical latency. In the benchmark, warmup hit solid_yellow (test 5), explaining the 18-second spike.

5. **CPU latency is unsuitable for high-concurrency production.** At 1–4 seconds per warm request, a single CPU handles at most 15–60 requests per minute. For the thesis/demo context, this is acceptable. GPU deployment would reduce to ~200 ms.

---

## Recommendations

| Issue | Recommendation | Impact |
|-------|---------------|--------|
| CPU only | Deploy with GPU | 25× latency reduction |
| Yellow/green solids reach YOLO | Add texture/variance fast-check before YOLO | Avoid 1–18s for obvious non-leaves |
| YOLO warmup spike | Pre-warm YOLO on service start with a dummy inference | Eliminate first-call spike |
| Low confidence on algal-leaf-spot (48.59%) | Tune temperature scaling > 1.0 | Better-calibrated confidence |

---

*All measurements: single-threaded, CPU, Docker container on Windows 11 host, Python 3.x.*
