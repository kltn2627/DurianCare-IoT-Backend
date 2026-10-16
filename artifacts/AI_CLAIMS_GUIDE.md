# DurianCare AI — Claims Guide

**Purpose:** Precise language for thesis documentation, demo presentations, and reports.  
**Date:** 2026-09-16  

This guide exists because imprecise AI claims are a common source of thesis committee questions. Use the safe claims verbatim or as a model for your own wording.

---

## SAFE CLAIMS

### Detector
- "The system uses a YOLO11x-based generic plant-leaf detector."
- "The leaf detector was sourced from HuggingFace (`pedromiguelsanchez/yolo-plant-leaf-detection`, MIT license)."
- "The detector identifies leaf bounding boxes; it was not fine-tuned on durian leaves specifically."
- "Historical end-to-end leaf detection rate: 94.12% (48/51 valid leaf images in a prior benchmark)."
- "The original durian-specific detector used during initial development was not preserved in version control and is irrecoverable."

### Classifier
- "The disease classifier is a MobileNetV2 model trained for 150 epochs on an internal dataset."
- "The classifier achieved 97.43% accuracy on a held-out test set of 1,088 samples (classifier-isolation evaluation)."
- "Per-class precision and recall ranged from 0.96 to 1.00 across all five disease classes."
- "The classifier covers five durian leaf conditions: algal leaf spot, allocaridara attack, healthy leaf, leaf blight, and phomopsis leaf spot."

### End-to-End Pipeline
- "End-to-end pipeline validation (client → gateway → detector → classifier) was completed for 2 of 5 disease classes due to limited test image availability."
- "ALGAL_LEAF_SPOT and LEAF_BLIGHT were verified end-to-end with 100% accuracy on the available test images."
- "ALLOCARIDARA_ATTACK, HEALTHY_LEAF, and PHOMOPSIS_LEAF_SPOT were not tested end-to-end; only classifier-isolation metrics are available for these classes."

### Safety
- "The safety regression rejected 100% of non-leaf test inputs (0% false positive rate, n=21 across two sessions)."
- "The system uses a multi-gate safety pipeline: green channel gate, YOLO bounding-box requirement, crop validation, and then classification."
- "No non-leaf image ever produced a disease diagnosis in any test run."
- "The API returns HTTP 422 for any input that does not contain a detectable leaf."

### Performance
- "Average inference latency on CPU is approximately 2,200 ms per request."
- "Pre-YOLO fast-rejection (non-leaf images failing the color gate) takes 5–30 ms."
- "The system runs on CPU; GPU deployment would reduce latency by approximately 25×."

### ESP32
- "ESP32-CAM robustness was evaluated through programmatic simulation of low-resolution (320×240), heavily compressed (JPEG q=20), dark (0.35× brightness), and motion-blurred variants."
- "All 8 simulated degraded-image variants were correctly classified."
- "Real ESP32-CAM hardware validation has not been performed; these results are simulated."

### Reproducibility
- "The full validation suite can be reproduced with a single command: `python scripts/ai_validate.py`."
- "Model integrity is verified by comparing SHA256 checksums of local files, Docker container files, and the runtime API response."

### Quality Gate
- "The current validation gate is READY WITH KNOWN LIMITATIONS."
- "Known limitations include: generic (non-durian-specific) leaf detector, end-to-end coverage of 2 of 5 disease classes, CPU-only runtime, and simulated ESP32 evaluation."

---

## CLAIMS TO AVOID

### Inflated accuracy claims
- ❌ "97.43% overall AI diagnosis accuracy." — *This is classifier-isolation accuracy. End-to-end accuracy is lower (detector FNR ~6% reduces it). Say "97.43% on the n=1088 classifier benchmark."*
- ❌ "100% disease detection accuracy." — *Only 2/5 classes tested end-to-end. Classifier is not the whole pipeline.*
- ❌ "The AI correctly diagnoses all 5 durian diseases." — *Only 2 verified end-to-end. Say "2 of 5 classes verified."*

### False detector claims
- ❌ "The detector is trained specifically for durian leaves." — *The YOLO11x detector is a generic plant-leaf detector. The durian-specific original is irrecoverable.*
- ❌ "Our custom YOLO model detects durian leaves." — *It is an off-the-shelf model from HuggingFace, not trained by this project.*

### False validation claims
- ❌ "The system is fully validated on all 5 diseases." — *3 classes lack end-to-end test coverage.*
- ❌ "ESP32-CAM was physically validated." — *Only simulated.*
- ❌ "The system was tested with real device images." — *No real ESP32 images were available.*

### False license claims
- ❌ "Production/commercial license is fully cleared." — *Ultralytics AGPL-3.0 requires review for commercial closed-source use.*
- ❌ "The system is production-ready." — *It is ready for graduation demonstration with known limitations.*

### Overstated readiness
- ❌ "READY FOR DEMO" without qualification. — *The correct gate is READY WITH KNOWN LIMITATIONS.*
- ❌ "The AI service handles production-level traffic." — *Single-threaded CPU, ~2 s/request. Not designed for concurrent production load.*

---

## Precision Rewrites

| Imprecise | Precise replacement |
|-----------|---------------------|
| "AI accuracy is 97.43%" | "MobileNetV2 classifier accuracy is 97.43% on n=1088 (classifier isolation)" |
| "Fully tested" | "2 of 5 disease classes verified end-to-end" |
| "Custom durian detector" | "Generic plant-leaf detector (YOLO11x, not durian-specific)" |
| "ESP32 validated" | "ESP32 robustness evaluated through simulation (not real device)" |
| "Production ready" | "Ready for graduation demonstration with known limitations" |
| "100% safe" | "FPR = 0% across all tested inputs (n=21+ non-leaf tests)" |
