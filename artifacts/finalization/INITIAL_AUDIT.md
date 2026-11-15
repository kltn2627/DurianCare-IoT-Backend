# DurianCare-IoT Final Initial Audit

Date: 2026-10-11
Backend repo: `C:\Users\minhd\OneDrive\Desktop\KhoaLuanTotNghiep_26-27\DurianCare-IoT-Backend`

## Scope

Checked the current backend, web, mobile, AI, and severity-training workspace state without resetting git history, database volumes, or generated data.

## Repositories

| Area | Path | Branch | HEAD |
| --- | --- | --- | --- |
| Backend | `DurianCare-IoT-Backend` | `duy/cleanup-fixes` | `8fbdfa8` |
| Web | `DurianCare-IoT-Web-Client` | `duy/cleanup-fixes` | `abaa3ca` |
| Mobile | `DurianCare-IoT-Mobile-App` | `duy/cleanup-fixes` | `cde1765` |

## Findings

| ID | Severity | Status | Finding |
| --- | --- | --- | --- |
| FIN-001 | Medium | Fixed | Web lint had blocking errors in camera/export/tree UI. |
| FIN-002 | Medium | Open | Expo doctor reports SDK patch-version mismatches. Typecheck/export still pass. |
| FIN-003 | Medium | Open | Docker services were not running, so live DB/API/browser/mobile runtime E2E was not rerun in this session. |
| FIN-004 | Expected blocker | Open | Severity model training must remain blocked until human annotation reaches the documented gate. |
| FIN-005 | Low | Open | Web lint still has non-blocking warnings. |
| FIN-006 | Low | Fixed | Stale `.next` type cache caused an initial web typecheck failure; cache was cleared and typecheck passed. |

## No-Destructive-Action Note

No commit, push, reset, checkout, database reset, or Docker volume deletion was performed.
