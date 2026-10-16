# DurianCare AI — License Audit

**Date:** 2026-09-16  
**Branch:** duy/cleanup-fixes  
**Scope:** AI service only (detector, classifier, inference framework, training)

> **Disclaimer:** This document summarizes the license terms as understood at the time of writing. It is not legal advice. Verify against the official license texts before any commercial use.

---

## 1. Leaf Detector Model

| Field | Value |
|-------|-------|
| Model | YOLO11x leaf detector |
| Source | HuggingFace `pedromiguelsanchez/yolo-plant-leaf-detection` |
| Model license | **MIT** |
| SHA256 | `ac2bd7f82f9fd1e054f496a90f21ce77eb9697b27a71a4ecdf6d7c47ac71408b` |

**MIT license:** Permits use, modification, and distribution for any purpose (including commercial). No source disclosure requirement.

**Implication:** Model weights themselves carry no restriction on thesis or commercial use.

---

## 2. Inference Framework — Ultralytics YOLO

| Field | Value |
|-------|-------|
| Package | `ultralytics` 8.3.102 |
| License | **AGPL-3.0** (open-source tier) / Commercial Enterprise license (paid tier) |
| Source | https://github.com/ultralytics/ultralytics |

### AGPL-3.0 Terms (current use)

AGPL-3.0 requires:

> "If you modify the Program and distribute it, you must release the source code of your modifications under the same license (AGPL-3.0), including over network use."

**Key implications for DurianCare:**

| Scenario | Requirement |
|----------|-------------|
| Thesis demonstration (private, non-commercial) | No distribution → AGPL does not apply. Safe. |
| Running as a private internal service with no distribution | No distribution → Safe. |
| Distributing a closed-source product that calls this service | The service **runs** Ultralytics; if the server binary is distributed, AGPL applies. The backend is NOT currently distributed — it is self-hosted. Safe. |
| Releasing DurianCare as a commercial closed-source product to others | AGPL requires source disclosure OR Ultralytics Enterprise license. **Review required.** |
| Publishing only a mobile app that calls a self-hosted backend | The mobile app does not contain Ultralytics. The backend is self-hosted and not distributed. Likely safe, but verify. |

**Current use:** Thesis/academic non-commercial — AGPL does not impose distribution obligations because the code is not distributed.

**Before commercial deployment:** Either (a) ensure all code is AGPL-compatible open source, or (b) obtain an Ultralytics Enterprise license (commercial/closed-source tier). License inquiry: https://www.ultralytics.com/license

---

## 3. Training Framework — PyTorch / torchvision

| Package | License | Notes |
|---------|---------|-------|
| `torch` 2.6.0+cpu | BSD-3-Clause | Permissive; no restriction on commercial use |
| `torchvision` 0.21.0+cpu | BSD-3-Clause | Same |

BSD-3-Clause: Free for any use including commercial. Only requires attribution in derived works.

---

## 4. Disease Classifier Model (MobileNetV2)

| Field | Value |
|-------|-------|
| Architecture | MobileNetV2 from `torchvision.models.mobilenet_v2` |
| Weights origin | Project-trained from scratch (no pretrained backbone loaded, `weights=None`) |
| Training data | Internal project dataset — not public |
| License | Follows the project license (no external training data restriction identified) |
| SHA256 | `fc3dc5c92e43a9dbb196029fa56f9722ed91613d11ae7ccdadda1093fd1e5afe` |

MobileNetV2 architecture paper (Howard et al. 2018, Google) is published research. The `torchvision` implementation is BSD-3-Clause. The trained weights are original project output — license is at the project's discretion.

**Note:** If pretrained weights were loaded at any point during training (e.g. ImageNet initialization), the original pretrained weights were from `torchvision` (BSD-3-Clause). The fine-tuned weights would inherit the same permissive license.

---

## 5. Other Runtime Dependencies

| Package | License | Notes |
|---------|---------|-------|
| `fastapi` 0.115.12 | MIT | Permissive |
| `uvicorn` 0.34.0 | BSD-3-Clause | Permissive |
| `pillow` 11.1.0 | HPND (similar to MIT) | Permissive |
| Python 3.12 | PSF-2.0 | Permissive |

---

## 6. Training Dataset

The MobileNetV2 classifier was trained on an internal disease image dataset. No external public dataset license was identified in this audit. If any public dataset images were included in training, their licenses should be verified before commercial redistribution of the model weights.

---

## 7. Summary Table

| Component | License | Thesis/Non-commercial | Commercial Closed-source |
|-----------|---------|----------------------|-------------------------|
| YOLO11x detector weights | MIT | ✅ Safe | ✅ Safe |
| Ultralytics framework | AGPL-3.0 | ✅ Safe (non-distributed) | ⚠️ Review required |
| MobileNetV2 classifier | Project (BSD-compatible) | ✅ Safe | ✅ Likely safe |
| PyTorch / torchvision | BSD-3-Clause | ✅ Safe | ✅ Safe |
| FastAPI / uvicorn / Pillow | MIT / BSD | ✅ Safe | ✅ Safe |

---

## 8. Action Items for Commercial Deployment

1. **Ultralytics AGPL-3.0:** Decide between (a) open-source the entire backend under AGPL-3.0, or (b) purchase Ultralytics Enterprise license.
2. **Training dataset provenance:** Document the origin of every image used to train the MobileNetV2 classifier.
3. **Legal review:** Have a qualified IP attorney review the Ultralytics license interpretation for the specific distribution model.

For thesis graduation purposes, none of these are blockers. The current use is private, self-hosted, and non-commercial.
