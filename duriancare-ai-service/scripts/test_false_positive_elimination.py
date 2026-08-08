"""
False positive elimination test suite.
Tests non-leaf images expect HTTP 422 "No durian leaf detected."
Tests valid leaf images expect HTTP 200 with a prediction.
"""
from __future__ import annotations

import hashlib
import json
import os
import sys
import time
from pathlib import Path

try:
    import requests
except ImportError:
    print("ERROR: requests not installed. Run: pip install requests")
    sys.exit(1)

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("ERROR: Pillow not installed. Run: pip install Pillow")
    sys.exit(1)

BASE_URL = os.getenv("AI_BASE_URL", "http://localhost:8000")
ENDPOINT = f"{BASE_URL}/api/v1/predict"
TIMEOUT = 120

DESKTOP = Path("C:/Users/minhd/OneDrive/Desktop")
TEST_DIR = DESKTOP / "Test"

# ──────────────────────────────────────────────────────────────────────────────
# NON-LEAF test images (expect 422)
# ──────────────────────────────────────────────────────────────────────────────

def _make_solid_image(path: Path, color: tuple[int, int, int], size: tuple[int, int] = (640, 480)) -> Path:
    img = Image.new("RGB", size, color)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "JPEG")
    return path


def _make_gradient_image(path: Path) -> Path:
    img = Image.new("RGB", (640, 480))
    draw = ImageDraw.Draw(img)
    for x in range(640):
        r = int(x / 640 * 255)
        b = int((1 - x / 640) * 200)
        draw.line([(x, 0), (x, 479)], fill=(r, 0, b))
    img.save(path, "JPEG")
    return path


def _make_human_skin_image(path: Path) -> Path:
    """Simulate a face/skin photo: warm reddish-tan, no green dominance."""
    img = Image.new("RGB", (640, 480), (195, 140, 110))
    draw = ImageDraw.Draw(img)
    draw.ellipse([200, 80, 440, 400], fill=(210, 155, 120), outline=(160, 110, 90), width=3)
    img.save(path, "JPEG")
    return path


def _make_grey_animal_image(path: Path, base: tuple[int, int, int] = (140, 130, 120)) -> Path:
    """Simulate a grey/brown cat or dog (fur tones, no green)."""
    img = Image.new("RGB", (640, 480), base)
    draw = ImageDraw.Draw(img)
    for i in range(30):
        x = (i * 43) % 640
        y = (i * 31) % 480
        shade = tuple(max(0, min(255, c + (i % 3 - 1) * 20)) for c in base)
        draw.ellipse([x, y, x + 40, y + 30], fill=shade)
    img.save(path, "JPEG")
    return path


def _make_dark_ui_image(path: Path) -> Path:
    """Simulate a laptop/keyboard/desktop screenshot (dark greys, blues)."""
    img = Image.new("RGB", (640, 480), (30, 32, 36))
    draw = ImageDraw.Draw(img)
    for row in range(6):
        for col in range(14):
            x, y = 20 + col * 43, 80 + row * 60
            draw.rounded_rectangle([x, y, x + 36, y + 48], radius=4, fill=(60, 63, 68), outline=(80, 85, 92))
    img.save(path, "JPEG")
    return path


def _make_car_image(path: Path) -> Path:
    """Simulate a car: metallic silver/grey with no significant green."""
    img = Image.new("RGB", (640, 480), (80, 80, 82))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle([80, 180, 560, 360], radius=20, fill=(150, 155, 165), outline=(100, 100, 110))
    draw.rounded_rectangle([150, 140, 490, 195], radius=10, fill=(60, 60, 65), outline=(90, 90, 95))
    draw.ellipse([120, 320, 220, 400], fill=(40, 40, 45), outline=(80, 80, 85))
    draw.ellipse([420, 320, 520, 400], fill=(40, 40, 45), outline=(80, 80, 85))
    img.save(path, "JPEG")
    return path


def _make_landscape_green_image(path: Path) -> Path:
    """Simulate a GREEN landscape (grass/forest) — hardest non-leaf case."""
    img = Image.new("RGB", (640, 480))
    draw = ImageDraw.Draw(img)
    for y in range(480):
        sky_ratio = max(0.0, 1.0 - y / 240.0)
        ground_ratio = min(1.0, (y - 200) / 280.0) if y > 200 else 0.0
        r = int(110 * sky_ratio + 50 * ground_ratio)
        g = int(160 * sky_ratio + 120 * ground_ratio)
        b = int(220 * sky_ratio + 40 * ground_ratio)
        draw.line([(0, y), (639, y)], fill=(r, g, b))
    img.save(path, "JPEG")
    return path


SCRATCHPAD = Path(os.getenv("TEMP", "/tmp")) / "claude_test_images"

NON_LEAF_CASES = [
    ("T1_lol_wallpaper", DESKTOP / "T1.png"),
    ("blank_white", _make_solid_image(SCRATCHPAD / "blank_white.jpg", (255, 255, 255))),
    ("pure_black", _make_solid_image(SCRATCHPAD / "pure_black.jpg", (0, 0, 0))),
    ("solid_red", _make_solid_image(SCRATCHPAD / "solid_red.jpg", (220, 30, 30))),
    ("solid_blue", _make_solid_image(SCRATCHPAD / "solid_blue.jpg", (30, 30, 220))),
    ("gradient_rb", _make_gradient_image(SCRATCHPAD / "gradient_rb.jpg")),
    ("human_face_skin", _make_human_skin_image(SCRATCHPAD / "human_face.jpg")),
    ("cat_grey_fur", _make_grey_animal_image(SCRATCHPAD / "cat_grey.jpg", (140, 130, 120))),
    ("dog_brown_fur", _make_grey_animal_image(SCRATCHPAD / "dog_brown.jpg", (150, 110, 80))),
    ("car_silver", _make_car_image(SCRATCHPAD / "car.jpg")),
    ("laptop_dark", _make_dark_ui_image(SCRATCHPAD / "laptop.jpg")),
    ("keyboard_dark", _make_dark_ui_image(SCRATCHPAD / "keyboard.jpg")),
    ("landscape_green", _make_landscape_green_image(SCRATCHPAD / "landscape_green.jpg")),
]

# ──────────────────────────────────────────────────────────────────────────────
# VALID LEAF test images (expect 200)
# ──────────────────────────────────────────────────────────────────────────────

VALID_LEAF_CASES: list[tuple[str, Path]] = []
if TEST_DIR.is_dir():
    for ext in ("*.jpg", "*.jpeg", "*.png", "*.webp"):
        for p in TEST_DIR.glob(ext):
            VALID_LEAF_CASES.append((f"leaf_{p.stem}", p))


def _post_image(label: str, image_path: Path) -> dict:
    if not image_path.is_file():
        return {"label": label, "path": str(image_path), "error": "FILE_NOT_FOUND", "skipped": True}
    try:
        with open(image_path, "rb") as f:
            content = f.read()
        files = {"image": (image_path.name, content, "image/jpeg")}
        t0 = time.perf_counter()
        resp = requests.post(ENDPOINT, files=files, timeout=TIMEOUT)
        elapsed = round((time.perf_counter() - t0) * 1000, 1)
        try:
            body = resp.json()
        except Exception:
            body = {"raw": resp.text[:500]}
        return {
            "label": label,
            "path": str(image_path),
            "status_code": resp.status_code,
            "body": body,
            "elapsed_ms": elapsed,
        }
    except requests.exceptions.ConnectionError as e:
        return {"label": label, "path": str(image_path), "error": f"CONNECTION_ERROR: {e}"}
    except Exception as e:
        return {"label": label, "path": str(image_path), "error": str(e)}


def _check_runtime_info() -> dict:
    try:
        resp = requests.get(f"{BASE_URL}/api/v1/runtime-info", timeout=10)
        return resp.json() if resp.status_code == 200 else {"error": resp.text}
    except Exception as e:
        return {"error": str(e)}


def run() -> None:
    print("=" * 70)
    print("FALSE POSITIVE ELIMINATION TEST SUITE")
    print(f"Endpoint: {ENDPOINT}")
    print("=" * 70)

    runtime = _check_runtime_info()
    print("\n[RUNTIME INFO]")
    print(json.dumps(runtime, indent=2))

    total = 0
    passed = 0
    failed = 0
    results = []

    # ── NON-LEAF TESTS ────────────────────────────────────────────────────────
    print("\n" + "─" * 70)
    print("NON-LEAF TESTS  (expect HTTP 422 for ALL)")
    print("─" * 70)

    for label, path in NON_LEAF_CASES:
        r = _post_image(label, path)
        if r.get("skipped"):
            print(f"  SKIP  {label} — {r['error']}")
            continue
        if r.get("error"):
            print(f"  ERR   {label} — {r['error']}")
            results.append({**r, "test_type": "non_leaf", "passed": False})
            failed += 1
            total += 1
            continue
        sc = r["status_code"]
        body = r["body"]
        detail = body.get("detail", "") if isinstance(body, dict) else ""
        is_pass = sc == 422 and "No durian leaf detected" in str(detail)
        status_str = "PASS" if is_pass else "FAIL"
        print(f"  {status_str}  [{sc}] {label}  ({r['elapsed_ms']}ms)  detail={detail!r}")
        if not is_pass and sc != 422:
            prediction = body.get("data", {})
            print(f"        → FALSE POSITIVE: disease={prediction.get('predictedDisease')} "
                  f"conf={prediction.get('confidence')}")
        results.append({**r, "test_type": "non_leaf", "expected_status": 422, "passed": is_pass})
        total += 1
        if is_pass:
            passed += 1
        else:
            failed += 1

    # ── VALID LEAF TESTS ──────────────────────────────────────────────────────
    print("\n" + "─" * 70)
    print(f"VALID LEAF TESTS  (expect HTTP 200 for ALL — {len(VALID_LEAF_CASES)} images)")
    print("─" * 70)

    for label, path in VALID_LEAF_CASES:
        r = _post_image(label, path)
        if r.get("skipped"):
            print(f"  SKIP  {label} — {r['error']}")
            continue
        if r.get("error"):
            print(f"  ERR   {label} — {r['error']}")
            results.append({**r, "test_type": "valid_leaf", "passed": False})
            failed += 1
            total += 1
            continue
        sc = r["status_code"]
        body = r["body"]
        is_pass = sc == 200 and isinstance(body, dict) and body.get("status") == "success"
        status_str = "PASS" if is_pass else "FAIL"
        prediction = (body.get("data") or {}) if isinstance(body, dict) else {}
        print(f"  {status_str}  [{sc}] {label}  ({r['elapsed_ms']}ms)  "
              f"disease={prediction.get('predictedDisease','?')}  "
              f"conf={prediction.get('confidence','?')}")
        results.append({**r, "test_type": "valid_leaf", "expected_status": 200, "passed": is_pass})
        total += 1
        if is_pass:
            passed += 1
        else:
            failed += 1

    # ── SUMMARY ───────────────────────────────────────────────────────────────
    print("\n" + "=" * 70)
    non_leaf_results = [r for r in results if r.get("test_type") == "non_leaf"]
    leaf_results = [r for r in results if r.get("test_type") == "valid_leaf"]
    non_leaf_pass = sum(1 for r in non_leaf_results if r.get("passed"))
    leaf_pass = sum(1 for r in leaf_results if r.get("passed"))
    fp_count = sum(1 for r in non_leaf_results if not r.get("passed") and not r.get("skipped"))
    fn_count = sum(1 for r in leaf_results if not r.get("passed") and not r.get("skipped"))

    print(f"NON-LEAF: {non_leaf_pass}/{len(non_leaf_results)} passed  |  FALSE POSITIVES: {fp_count}")
    print(f"LEAF:     {leaf_pass}/{len(leaf_results)} passed    |  FALSE NEGATIVES: {fn_count}")
    print(f"TOTAL:    {passed}/{total} passed")
    fpr = (fp_count / max(1, len(non_leaf_results))) * 100
    fnr = (fn_count / max(1, len(leaf_results))) * 100
    print(f"FALSE POSITIVE RATE: {fpr:.1f}%  (target: 0.0%)")
    print(f"FALSE NEGATIVE RATE: {fnr:.1f}%  (target: 0.0%)")

    success = fpr == 0.0 and fnr == 0.0
    print()
    if success:
        print("✓ SUCCESS CRITERIA MET: FPR=0% and FNR=0%")
    else:
        print("✗ CRITERIA NOT MET — pipeline still has issues")

    out_path = Path("artifacts/false_positive_test_results.json")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps({
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "endpoint": ENDPOINT,
        "runtime_info": runtime,
        "summary": {
            "total": total,
            "passed": passed,
            "failed": failed,
            "non_leaf_total": len(non_leaf_results),
            "non_leaf_passed": non_leaf_pass,
            "false_positives": fp_count,
            "false_positive_rate_pct": round(fpr, 2),
            "leaf_total": len(leaf_results),
            "leaf_passed": leaf_pass,
            "false_negatives": fn_count,
            "false_negative_rate_pct": round(fnr, 2),
            "criteria_met": success,
        },
        "results": results,
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nResults saved to {out_path}")
    print("=" * 70)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    run()
