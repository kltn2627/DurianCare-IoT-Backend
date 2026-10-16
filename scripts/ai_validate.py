#!/usr/bin/env python3
"""
DurianCare AI Validation Script — single entry point for all AI regression tests.

Usage:
    python scripts/ai_validate.py                  # full suite
    python scripts/ai_validate.py --phase safety   # safety only
    python scripts/ai_validate.py --phase diagnosis
    python scripts/ai_validate.py --integrity-check
    python scripts/ai_validate.py --verbose

Expected baseline (commit d77effc, 2026-09-16):
    Safety: 12/12  Diagnosis: 2/2 (+ 3 SKIP)  ESP32: 8/8  Integrity: 2/2
"""
import argparse
import csv
import hashlib
import io
import json
import random
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib
from datetime import datetime
from pathlib import Path

REPO_ROOT    = Path(__file__).resolve().parents[1]
AI_URL       = "http://localhost:8000/api/v1/predict"
HEALTH_URL   = "http://localhost:8000/actuator/health"
RUNTIME_URL  = "http://localhost:8000/api/v1/runtime-info"

COMMUNITY    = REPO_ROOT.parent / "DurianCare-IoT-Mobile-App" / "assets" / "images" / "community"

EXPECTED_HASHES = {
    "detector":   "ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b",
    "classifier": "fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe",
}
MODEL_PATHS = {
    "detector":   REPO_ROOT / "duriancare-ai-service" / "models" / "leaf_detector_best.pt",
    "classifier": REPO_ROOT / "duriancare-ai-service" / "models" / "mobilenetv2_classifier_high_acc.pth",
}

SCRATCH = Path.home() / "AppData" / "Local" / "Temp" / "duriancare_ai_validate"
SCRATCH.mkdir(parents=True, exist_ok=True)

# ── PNG generator ──────────────────────────────────────────────────────────

def _chunk(n, d):
    c = zlib.crc32(n + d) & 0xFFFFFFFF
    return struct.pack(">I", len(d)) + n + d + struct.pack(">I", c)

def solid_png(w, h, r, g, b):
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    row = b"\x00" + bytes([r, g, b]) * w
    return (b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", ihdr) +
            _chunk(b"IDAT", zlib.compress(row * h)) + _chunk(b"IEND", b""))

def noise_png(w, h, seed=42):
    rng = random.Random(seed)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    rows = b"".join(b"\x00" + bytes(rng.randint(0, 255) for _ in range(w * 3)) for _ in range(h))
    return (b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", ihdr) +
            _chunk(b"IDAT", zlib.compress(rows)) + _chunk(b"IEND", b""))

def green_noise_png(w, h, seed=7):
    rng = random.Random(seed)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    rows = b"".join(
        b"\x00" + b"".join(
            bytes([rng.randint(20, 60), rng.randint(80, 160), rng.randint(20, 60)])
            for _ in range(w)
        )
        for _ in range(h)
    )
    return (b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", ihdr) +
            _chunk(b"IDAT", zlib.compress(rows)) + _chunk(b"IEND", b""))

def _save(name, data):
    p = SCRATCH / name
    if not p.exists():
        p.write_bytes(data)
    return p

def _make_esp32_sims(src: Path, label: str) -> list[tuple[str, Path]]:
    sims = []
    try:
        from PIL import Image, ImageFilter
        img = Image.open(src).convert("RGB")
        # Low-res 320x240
        p = SCRATCH / f"esp32_lowres_{label}.jpg"
        if not p.exists():
            img.resize((320, 240), Image.BILINEAR).save(str(p), "JPEG", quality=60)
        sims.append(("lowres_320x240", p))
        # JPEG q20
        import io as _io
        buf = _io.BytesIO()
        img.resize((640, 480), Image.BILINEAR).save(buf, "JPEG", quality=20)
        p = SCRATCH / f"esp32_jpeg20_{label}.jpg"
        if not p.exists():
            p.write_bytes(buf.getvalue())
        sims.append(("jpeg_q20", p))
        # Dark
        import numpy as np
        dark = Image.fromarray((np.array(img, dtype=np.float32) * 0.35).clip(0, 255).astype("uint8"))
        p = SCRATCH / f"esp32_dark_{label}.jpg"
        if not p.exists():
            dark.resize((320, 240), Image.BILINEAR).save(str(p), "JPEG", quality=70)
        sims.append(("dark_0.35x", p))
        # Blur
        blurred = img.filter(ImageFilter.GaussianBlur(radius=3))
        p = SCRATCH / f"esp32_blur_{label}.jpg"
        if not p.exists():
            blurred.resize((320, 240), Image.BILINEAR).save(str(p), "JPEG", quality=70)
        sims.append(("motion_blur", p))
    except ImportError:
        pass
    return sims

# ── HTTP ───────────────────────────────────────────────────────────────────

def predict(path: Path, timeout=90):
    bnd = b"----DurianValidate"
    data = path.read_bytes()
    body = (b"--" + bnd + b"\r\n"
            b'Content-Disposition: form-data; name="image"; filename="' + path.name.encode() + b'"\r\n'
            b"Content-Type: image/jpeg\r\n\r\n" + data +
            b"\r\n--" + bnd + b"--\r\n")
    req = urllib.request.Request(AI_URL, data=body,
                                  headers={"Content-Type": f"multipart/form-data; boundary={bnd.decode()}"},
                                  method="POST")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            ms = (time.perf_counter() - t0) * 1000
            return r.status, json.loads(r.read()), round(ms, 1)
    except urllib.error.HTTPError as e:
        ms = (time.perf_counter() - t0) * 1000
        try: b = json.loads(e.read())
        except: b = {}
        return e.code, b, round(ms, 1)
    except Exception as ex:
        return -1, {"error": str(ex)}, -1

def get_disease(body):
    d = body.get("data", {}) if isinstance(body, dict) else {}
    return (body.get("predictedDisease") or d.get("predictedDisease") or
            body.get("disease") or d.get("disease"))

def get_confidence(body):
    d = body.get("data", {}) if isinstance(body, dict) else {}
    return body.get("confidence") or d.get("confidence") or ""

# ── Integrity check ────────────────────────────────────────────────────────

def run_integrity_check(verbose=False):
    print("\n[Model Integrity Check]")
    results = []
    for name, path in MODEL_PATHS.items():
        if not path.exists():
            print(f"  FAIL  {name}: FILE NOT FOUND at {path}")
            results.append({"name": name, "passed": False, "note": "file not found"})
            continue
        h = hashlib.sha256(path.read_bytes()).hexdigest()
        expected = EXPECTED_HASHES[name]
        passed = (h == expected)
        status = "PASS" if passed else "FAIL"
        if verbose or not passed:
            print(f"  {status}  {name}: {h[:16]}... ({'match' if passed else 'MISMATCH — expected ' + expected[:16] + '...'})")
        else:
            print(f"  {status}  {name}")
        results.append({"name": name, "passed": passed, "sha256": h, "expected": expected})

    # Also verify runtime SHA256 via API
    try:
        ri = json.loads(urllib.request.urlopen(RUNTIME_URL, timeout=5).read())
        runtime_sha = ri.get("detector_sha256", "")
        expected_det = EXPECTED_HASHES["detector"]
        rt_passed = (runtime_sha == expected_det)
        print(f"  {'PASS' if rt_passed else 'FAIL'}  runtime_detector_sha256 ({'match' if rt_passed else 'MISMATCH'})")
        results.append({"name": "runtime_detector", "passed": rt_passed, "sha256": runtime_sha, "expected": expected_det})
    except Exception as e:
        print(f"  SKIP  runtime_sha256 (API unavailable: {e})")
    return results

# ── Safety tests ───────────────────────────────────────────────────────────

def run_safety(verbose=False):
    print("\n[Safety Regression — non-leaf must return HTTP 422]")
    tests = [
        ("solid_white",   _save("v_white.png",   solid_png(320, 240, 255, 255, 255))),
        ("solid_black",   _save("v_black.png",   solid_png(320, 240, 0, 0, 0))),
        ("solid_red",     _save("v_red.png",     solid_png(320, 240, 220, 40, 40))),
        ("solid_blue",    _save("v_blue.png",    solid_png(320, 240, 40, 40, 220))),
        ("solid_yellow",  _save("v_yellow.png",  solid_png(320, 240, 220, 220, 40))),
        ("solid_gray",    _save("v_gray.png",    solid_png(320, 240, 128, 128, 128))),
        ("random_noise",  _save("v_noise.png",   noise_png(320, 240))),
        ("green_noise",   _save("v_gnoise.png",  green_noise_png(320, 240))),
        ("solid_green",   _save("v_green.png",   solid_png(320, 240, 30, 140, 30))),
        ("solid_orange",  _save("v_orange.png",  solid_png(320, 240, 220, 120, 20))),
        ("dark_green",    _save("v_dark.png",    solid_png(320, 240, 10, 25, 10))),
        ("bright_white",  _save("v_bright.png",  solid_png(320, 240, 245, 248, 240))),
    ]
    results = []
    for label, path in tests:
        s, b, ms = predict(path, timeout=60)
        disease = get_disease(b)
        passed = (s == 422) and (disease is None or disease == "")
        marker = "PASS" if passed else "FAIL"
        if verbose or not passed:
            print(f"  {marker}  {label:<20}  HTTP {s}  [{ms:.0f}ms]  disease={disease}")
        else:
            print(f"  {marker}  {label}")
        results.append({"phase": "safety", "label": label, "status": s, "disease": disease,
                        "passed": passed, "ms": ms})
    return results

# ── Diagnosis tests ────────────────────────────────────────────────────────

def run_diagnosis(verbose=False):
    print("\n[Diagnosis Regression — leaf images must return HTTP 200 + correct disease]")
    leaf_tests = [
        ("algal_leaf_spot", COMMUNITY / "algal-leaf-spot.jpg",   "ALGAL_LEAF_SPOT"),
        ("leaf_blight",     COMMUNITY / "leaf-blight.jpg",        "LEAF_BLIGHT"),
    ]
    missing = [
        ("allocaridara_attack", "ALLOCARIDARA_ATTACK"),
        ("healthy_leaf",        "HEALTHY_LEAF"),
        ("phomopsis_leaf_spot", "PHOMOPSIS_LEAF_SPOT"),
    ]
    results = []
    for label, path, expected_disease in leaf_tests:
        if not path.exists():
            print(f"  SKIP  {label} — image not found: {path}")
            results.append({"phase": "diagnosis", "label": label, "passed": False,
                            "skipped": True, "note": "image not found"})
            continue
        s, b, ms = predict(path, timeout=90)
        disease = get_disease(b)
        conf = get_confidence(b)
        passed = (s == 200) and (disease == expected_disease)
        marker = "PASS" if passed else "FAIL"
        print(f"  {marker}  {label:<25}  HTTP {s}  [{ms:.0f}ms]  {disease} ({conf})")
        results.append({"phase": "diagnosis", "label": label, "expected": expected_disease,
                        "disease": disease, "confidence": conf, "status": s,
                        "passed": passed, "skipped": False, "ms": ms})

    for label, expected_disease in missing:
        print(f"  SKIP  {label:<25}  NOT TESTED — NO VALID IMAGE AVAILABLE")
        results.append({"phase": "diagnosis", "label": label, "expected": expected_disease,
                        "disease": None, "status": -1, "passed": False,
                        "skipped": True, "note": "NOT TESTED — NO VALID IMAGE AVAILABLE"})
    return results

# ── ESP32 tests ────────────────────────────────────────────────────────────

def run_esp32(verbose=False):
    print("\n[ESP32 Simulation — degraded inputs must return correct disease]")
    print("  NOTE: SIMULATED — not real ESP32-CAM hardware")
    results = []
    sources = [
        (COMMUNITY / "algal-leaf-spot.jpg", "algal", "ALGAL_LEAF_SPOT"),
        (COMMUNITY / "leaf-blight.jpg",     "blight", "LEAF_BLIGHT"),
    ]
    for src, label, expected_disease in sources:
        if not src.exists():
            print(f"  SKIP  {label} — source image not found")
            continue
        sims = _make_esp32_sims(src, label)
        if not sims:
            print(f"  SKIP  {label} — PIL/numpy not available for simulation")
            continue
        for sim_label, path in sims:
            s, b, ms = predict(path, timeout=60)
            disease = get_disease(b)
            conf = get_confidence(b)
            passed = (s == 200) and (disease == expected_disease)
            marker = "PASS" if passed else "FAIL"
            full_label = f"{label}_{sim_label}"
            print(f"  {marker}  {full_label:<35}  HTTP {s}  [{ms:.0f}ms]  {disease} ({conf})")
            results.append({"phase": "esp32", "label": full_label, "expected": expected_disease,
                            "disease": disease, "confidence": conf, "status": s,
                            "passed": passed, "ms": ms})
    return results

# ── Main ───────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="DurianCare AI Validation")
    parser.add_argument("--phase", choices=["safety", "diagnosis", "esp32", "all"], default="all")
    parser.add_argument("--integrity-check", action="store_true")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()

    ts = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    print("=" * 65)
    print(f"DurianCare AI Validation — {ts}")
    print("=" * 65)

    # Health check
    try:
        h = json.loads(urllib.request.urlopen(HEALTH_URL, timeout=10).read())
        det = h.get("detectorReady"); diag = h.get("diagnosisReady")
        print(f"\nHealth: {h.get('status')} | detectorReady={det} | diagnosisReady={diag} | device={h.get('device')}")
        if not det or not diag:
            print("ERROR: AI service not fully ready."); sys.exit(1)
    except Exception as e:
        print(f"ERROR: Cannot reach AI service: {e}"); sys.exit(1)

    all_results = []

    if args.integrity_check or args.phase == "all":
        all_results += run_integrity_check(args.verbose)

    if args.phase in ("safety", "all"):
        all_results += run_safety(args.verbose)

    if args.phase in ("diagnosis", "all"):
        all_results += run_diagnosis(args.verbose)

    if args.phase in ("esp32", "all"):
        all_results += run_esp32(args.verbose)

    run   = [r for r in all_results if not r.get("skipped")]
    skip  = [r for r in all_results if r.get("skipped")]
    passed = [r for r in run if r.get("passed")]
    failed = [r for r in run if not r.get("passed")]
    ms_vals = sorted(r["ms"] for r in run if r.get("ms", -1) > 0)

    print("\n" + "=" * 65)
    print("SUMMARY")
    print("=" * 65)
    print(f"  Run: {len(run)}   PASS: {len(passed)}   FAIL: {len(failed)}   SKIP: {len(skip)}")
    if ms_vals:
        avg = sum(ms_vals) / len(ms_vals)
        med = ms_vals[len(ms_vals) // 2]
        p95 = ms_vals[min(int(len(ms_vals) * 0.95), len(ms_vals)-1)]
        print(f"  Latency avg: {avg:.0f}ms  median: {med:.0f}ms  P95: {p95:.0f}ms  max: {max(ms_vals):.0f}ms")

    if failed:
        print("\nFAILURES:")
        for r in failed:
            print(f"  FAIL  {r.get('label')}  HTTP {r.get('status')}  disease={r.get('disease')}")
        print(f"\n  exit 1 — {len(failed)} test(s) failed")
        sys.exit(1)
    else:
        print("\n  ALL TESTS PASS")
        sys.exit(0)

if __name__ == "__main__":
    main()
