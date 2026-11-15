# DurianCare-IoT Final Test Report

Date: 2026-10-11

## Backend

| Command | Result |
| --- | --- |
| `mvn -pl duriancare-farm-service "-Dtest=TreeDiagnosisServiceTest,TreeServiceRecoveryGateTest,TreeCarePlanServiceTest" test` | PASS, 33 tests |
| `mvn -pl duriancare-gateway -DskipTests compile` | PASS |
| `mvn -pl duriancare-notification-service -DskipTests compile` | PASS |
| `mvn -DskipTests compile` | PASS |
| `mvn test` | PASS |

Observed backend full-test counts: gateway 9, auth 69 run / 1 skipped, farm 70, cultivation 45, notification 23, search 10. Reactor build ended `BUILD SUCCESS`.

## Web

| Command | Result |
| --- | --- |
| `npm run lint` | PASS with 10 warnings |
| `npm run typecheck` | PASS |
| `npm run build` | PASS, 44 static pages generated |
| `npm test -- --runInBand` | PASS, 23 tests |

## Mobile

| Command | Result |
| --- | --- |
| `npm run typecheck` | PASS |
| `npm run doctor` | FAIL: Expo SDK patch-version mismatches only |
| `npx expo export --platform web` | PASS, 75 routes exported |

## AI And Severity Pipeline

| Command | Result |
| --- | --- |
| Python compile of `duriancare-ai-service/**/*.py` | PASS |
| `python scripts\severity_training\test_pipeline_guards.py` | PASS, 4 tests |
| `python scripts\severity_training\validate_dataset.py ...` | PASS, 5 images, 0 approved annotations, 0 annotation errors |

## Phase H Current Evidence - 2026-10-14

Verdict: `PARTIALLY_COMPLETE`

| Area | Command | Result |
| --- | --- | --- |
| Backend focused farm/care | `mvn -pl duriancare-farm-service "-Dtest=TreeServiceRecoveryGateTest,TreeCarePlanServiceTest" test` | PASS: 24 tests |
| Backend farm module | `mvn -pl duriancare-farm-service test` | PASS: 81 tests |
| Backend full reactor | `mvn test` | PASS: `BUILD SUCCESS`, 10 modules |
| Web typecheck | `npm run typecheck` | PASS |
| Web lint | `npm run lint` | PASS with 8 warnings |
| Web Jest | `npm run test -- --runInBand` | PASS: 1 suite, 23 tests |
| Web build | `npm run build` | PASS |
| Mobile typecheck | `npm run typecheck` | PASS |
| Mobile validation | `npm run validate` | PASS: Expo Doctor 21/21 |
| Mobile export | `npx expo export --platform web --output-dir dist-phase-h` | PASS: 75 routes exported |
| Python compile | `python -m compileall -q duriancare-ai-service scripts\severity_training scripts\demo_e2e_harness.py` | PASS |
| AI unittest | `python -m unittest discover -s tests` from AI service | PASS: 25 tests |
| AI pytest | `python -m pytest tests` from AI service | BLOCKED: `pytest` unavailable in active Python |
| AI integrity/safety | `python scripts\ai_validate.py --integrity-check --phase safety` | PASS: 15/15 |
| Severity validation | `python scripts\severity_training\validate_dataset.py ...` | PASS: 5 images, 0 approved annotations |
| Severity gate | `python scripts\severity_training\training_gate.py ...` | Expected `DO_NOT_TRAIN` |
| Severity guard tests | `python scripts\severity_training\test_pipeline_guards.py` | PASS: 4 tests |

Notes:

- The full Maven reactor emitted framework-generated development security logs during tests; those values are not copied here.
- Web lint warnings remain in existing AI/camera/export files and are not Phase H blockers.
- Mobile `dist-phase-h/` is generated export evidence, not source.
| `python scripts\severity_training\training_gate.py ...` | EXPECTED FAIL: `DO_NOT_TRAIN` |

Severity gate reason codes: `PROTOCOL_NOT_FINALIZED`, `APPROVED_COUNT_TOO_LOW`, `CLASS_MINIMUM_TOO_LOW`.

## Runtime Services

`docker compose -f infrastructure\docker-compose.yml ps` showed no running services. Live cross-service runtime E2E was therefore not rerun in this session.

## Phase I Test Evidence - 2026-10-14

Verdict: `PARTIALLY_COMPLETE`

| Area | Command | Result |
| --- | --- | --- |
| Backend focused farm/care | `mvn -pl duriancare-farm-service "-Dtest=TreeServiceRecoveryGateTest,TreeCarePlanServiceTest,TreeDiagnosisServiceTest" test` | PASS: 46 tests |
| Backend full reactor | `mvn test` | PASS: 251 tests, 0 failures, 0 errors, 1 skipped; `BUILD SUCCESS` |
| Web typecheck | `npm run typecheck` | PASS |
| Web lint | `npm run lint` | PASS with 8 warnings |
| Web Jest | `npm test -- --runInBand` | PASS: 1 suite, 23 tests |
| Web build | `npm run build` | PASS |
| Mobile validation | `npm run validate` | PASS: typecheck plus Expo Doctor 21/21 |
| Mobile export | `npx expo export --platform web --output-dir dist-phase-i` | PASS: 75 routes exported |
| AI compile | `python -m compileall -q .` from AI service | PASS |
| AI unittest | `python -m unittest discover -s tests` from AI service | PASS: 25 tests |
| AI integrity/safety | `python scripts\ai_validate.py --integrity-check --phase safety` | PASS: 15/15 |
| Severity validation | `python scripts\severity_training\validate_dataset.py --dataset-root ... --output artifacts\severity_training\phase_i_validation.json` | PASS: 5 images, 0 approved annotations, 0 annotation errors |
| Severity gate | `python scripts\severity_training\training_gate.py --dataset-root ... --output artifacts\severity_training\phase_i_training_gate.json` | Expected safety block: `DO_NOT_TRAIN`, exit 1 |
| Severity guard tests | `python -m unittest test_pipeline_guards.py` from `scripts\severity_training` | PASS: 4 tests |
| IoT service | `npm run check; npm test` from `duriancare-iot-service` | PASS: syntax check plus 37 tests |

Notes:

- `pytest` remains unavailable in the active Python, so AI pytest was not rerun; AI unittest and safety validation passed.
- Maven/Spring test logs may emit generated development security values; no such values are copied into final reports.
- Web lint warnings remain 8 warnings in existing AI/camera/export files.
- Mobile `dist-phase-i/` is generated export evidence, not source.
