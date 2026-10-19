# Tree Disease Lifecycle E2E Runtime Verification

**Date:** 2026-10-07  
**Branch:** `duy/cleanup-fixes`  
**Scope:** Tree Map -> Tree Detail -> AI Diagnosis -> Diagnosis History -> Knowledge Base -> Care Plan -> Follow-up -> Notification -> Re-diagnosis -> Recovery Evaluation -> Recovery Gate -> Tree Map persistence.

## Final Verdict

**PARTIAL THESIS-READY SIGN-OFF.**

Backend, Gateway notification routing, and Web notification click-through are runtime verified with real auth and real services. Mobile source validation is verified, but Mobile runtime/device/camera cannot be marked PASS in this environment because no Android device/emulator tooling is available.

No commit, push, branch switch, DB reset, Docker volume deletion, auth bypass, JWT forging, AI model change, threshold change, class change, or retraining was performed.

## Runtime Stack

Runtime containers after targeted rebuilds:

| Service | Status |
| --- | --- |
| `duriancare-gateway` | Up on `8080`; rebuilt from current Gateway source |
| `duriancare-notification-service` | Up on `8085`; rebuilt from current notification source |
| `duriancare-auth-service` | Up |
| `duriancare-farm-service` | Up on `8082` |
| `duriancare-ai-service` | Up on `8000` |
| `duriancare-mongodb` | Up, healthy |
| `duriancare-postgres` | Up, healthy |
| `duriancare-kafka` | Up, healthy |

## Gateway Notification Alias

Impact analysis before editing `NotificationInboxController`:

```text
npx gitnexus impact NotificationInboxController --repo DurianCare-IoT-Backend
```

Result: LOW risk, 0 direct upstream dependents, 0 indexed processes affected.

Runtime issue found during verification: Gateway routed `/api/notifications/**`, but notification-service did not map the Web-shaped nested path `/api/notifications/notifications`, causing HTTP 405.

Fix:

- `duriancare-gateway/src/main/resources/application.yml` routes both `/api/v1/notification/**` and `/api/notifications/**`.
- `duriancare-notification-service/src/main/java/com/duriancare/notification/controller/NotificationInboxController.java` maps all three inbox bases:
  - `/api/v1/notification/notifications`
  - `/api/notifications`
  - `/api/notifications/notifications`

Targeted rebuild:

```text
docker compose up -d --no-deps --build duriancare-notification-service
```

Result: Docker Maven package BUILD SUCCESS; container recreated and started. Gateway had already been rebuilt and recreated successfully from current source.

Runtime verification used a throwaway FARMER account created through public register -> real Kafka OTP -> verify OTP -> login. No JWT was forged and no auth filter was bypassed.

| Check | Result |
| --- | --- |
| Register FARMER | HTTP 201 |
| Verify OTP | HTTP 200 |
| Login | HTTP 200 |
| Create farm | HTTP 201 |
| Create zone | HTTP 201 |
| Create tree | HTTP 201 |
| Save diagnosis | HTTP 201 |
| Mark tree `TREATING` | HTTP 200 |
| Create care plan with `diagnosisId` and omitted `diseaseCode` | HTTP 201 |
| `GET /api/v1/notification/notifications?size=20` | HTTP 200 |
| `GET /api/notifications/notifications?size=20` | HTTP 200 |
| `GET /api/notifications?size=20` | HTTP 200 |
| `PATCH /api/v1/notification/notifications/{id}/read` | HTTP 200 |
| `PATCH /api/notifications/notifications/{id}/read` | HTTP 200 |
| `PATCH /api/notifications/{id}/read` | HTTP 200 |
| Unauthenticated `GET /api/notifications/notifications?size=1` | HTTP 401 |
| Farm/tree/diagnosis regression reads | HTTP 200 |

Runtime object evidence:

| Object | Id |
| --- | --- |
| Farm | `6ac552ea168511e77e8e0155` |
| Zone | `95757a9f-0e87-4172-a949-04d3a27dbb1a` |
| Tree | `6ac552ea168511e77e8e0156` |
| Tree code | `WEB-TREE-20261007025821` |
| Diagnosis | `6ac552ea168511e77e8e0157` |
| Care plan | `6ac552ea168511e77e8e0158` |
| CARE notification | `6ac552ea575d23d8bf18e72b` |
| Target URL | `/dashboard/client/farms/6ac552ea168511e77e8e0155/zones/95757a9f-0e87-4172-a949-04d3a27dbb1a?treeId=6ac552ea168511e77e8e0156` |

Metadata matched `treeId`, `farmId`, `zoneId`, and `carePlanId`.

## Backend Lifecycle

Earlier full backend runtime E2E through Gateway remains verified:

- Normal FARMER register -> OTP verify -> login.
- Farm, zone, and tree created through Gateway.
- AI prediction through Gateway `/api/v1/predict` with real `DLD_Dataset` fixtures:
  - `PHOMOPSIS_LEAF_SPOT`, 96.20%.
  - Healthy re-diagnosis fixture, 34.10%.
- Disease diagnosis saved to tree.
- Tree marked `TREATING`.
- Knowledge base lookup returned a matching article.
- Conflicting `diagnosisId` + `diseaseCode` care plan rejected with HTTP 409.
- Valid care plan accepted with disease derived from referenced diagnosis.
- CARE notification persisted with tree/farm/zone/care-plan metadata and `targetUrl`.
- Healthy re-diagnosis saved.
- Recovery evaluation returned `RECOVERED`.
- `PATCH /api/trees/{treeId}/health-status` to `RECOVERED` succeeded.
- Negative recovery gate rejected insufficient evidence with HTTP 409.

Existing `tree-008` consistency was audited and corrected earlier:

| Field | Value |
| --- | --- |
| Diagnosis id | `6abfac4b61b92b604c6855d7` |
| Diagnosis disease | `Algal_Leaf_Spot` |
| Care plan id | `6abfb26519a9533b7e5adedf` |
| Care plan disease | `Algal_Leaf_Spot` |
| Plan diagnosis id | `6abfac4b61b92b604c6855d7` |
| Plan tree id | `tree-008` |
| Plan farm id | `farm-khoa-luan-2026` |

## Web Runtime

Web browser E2E was run through the real Next.js app at `http://localhost:3000`.

Flow:

1. Started `npm run dev` outside the sandbox after Turbopack hit a Windows path canonicalization access error in the sandbox.
2. Opened `/login`.
3. Logged in through the real login form with the runtime FARMER account.
4. Opened `/notifications`.
5. Switched from unread to all notifications because the route verification had already marked the notification read.
6. Clicked the CARE notification `Mở` action.
7. Browser navigated to the target URL and opened the tree detail panel from `?treeId=...`.

Observed final URL:

```text
http://localhost:3000/dashboard/client/farms/6ac552ea168511e77e8e0155/zones/95757a9f-0e87-4172-a949-04d3a27dbb1a?treeId=6ac552ea168511e77e8e0156
```

Observed UI evidence:

- Page title: `Bản đồ cây | DurianCare`.
- Zone heading: `Runtime Zone`.
- Tree map button: `Cây WEB-TREE-20261007025821`.
- Detail panel heading: `WEB-TREE-20261007025821`.
- Health status: `Đang điều trị`.
- Care plan panel: `KẾ HOẠCH CHĂM SÓC PHOMOPSIS_LEAF_SPOT`.
- Diagnosis history: one WEB diagnosis at 96%.

Screenshot proof saved locally:

```text
C:\Users\minhd\.codex\visualizations\2026\10\06\01a10fa0-7d4f-7302-94f8-faf287e1332e\web-notification-clickthrough.png
```

Web status: **RUNTIME VERIFIED**.

## Mobile

Static validation:

```text
npm run validate
```

Result:

- TypeScript: PASS.
- Expo Doctor: FAIL, 20/21 checks passed.

Expo Doctor patch mismatches:

| Package | Expected | Found |
| --- | --- | --- |
| `expo` | `~57.0.27` | `57.0.25` |
| `expo-camera` | `~57.0.6` | `57.0.5` |
| `expo-constants` | `~57.0.21` | `57.0.19` |
| `expo-document-picker` | `~57.0.3` | `57.0.2` |
| `expo-linking` | `~57.0.12` | `57.0.11` |
| `expo-router` | `~57.0.25` | `57.0.23` |

No package upgrade was performed because the brief explicitly says not to upgrade Expo packages just to silence Expo Doctor.

Device/emulator availability:

- `adb devices` failed because `adb` is not installed/on PATH.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` were not available.
- The usual local Android SDK platform-tools path did not exist.

Mobile runtime status: **NOT TESTABLE in this environment**. Source behavior is code-verified and TypeScript-verified, but device/emulator notification click-through, tree lifecycle runtime, and camera runtime are not PASS.

## Camera

Hardware camera / ESP32 camera runtime was not available in this environment.

Camera status: **NOT TESTABLE**.

Software AI upload through the real Gateway and AI service was verified earlier with real dataset image fixtures, but that does not replace hardware camera sign-off.

## Tests And Validation

Backend focused tests:

```text
mvn -pl duriancare-farm-service "-Dtest=TreeDiagnosisServiceTest,TreeServiceRecoveryGateTest,TreeCarePlanServiceTest" test
```

Result: 30 tests, 0 failures, 0 errors.

Gateway compile:

```text
mvn -pl duriancare-gateway -DskipTests compile
```

Result: BUILD SUCCESS.

Notification-service compile:

```text
mvn -pl duriancare-notification-service -DskipTests compile
```

Result: BUILD SUCCESS.

Web typecheck:

```text
npm run typecheck
```

Result: PASS.

Mobile validation:

```text
npm run validate
```

Result: TypeScript PASS; Expo Doctor failed only on SDK patch-version mismatches listed above.

## Final Matrix

| Area | Status |
| --- | --- |
| Auth through Gateway | RUNTIME VERIFIED |
| Farm/zone/tree creation | RUNTIME VERIFIED |
| Tree map API | RUNTIME VERIFIED |
| Tree detail API | RUNTIME VERIFIED |
| AI prediction via Gateway | RUNTIME VERIFIED |
| Diagnosis save/history | RUNTIME VERIFIED |
| Knowledge lookup | RUNTIME VERIFIED |
| Care plan diagnosis-derived disease | RUNTIME VERIFIED |
| Conflicting care-plan disease rejection | RUNTIME VERIFIED |
| CARE notification persistence and metadata | RUNTIME VERIFIED |
| Gateway `/api/v1/notification/notifications` | RUNTIME VERIFIED |
| Gateway `/api/notifications/notifications` | RUNTIME VERIFIED |
| Gateway `/api/notifications` | RUNTIME VERIFIED |
| Notification mark-read legacy/direct/nested aliases | RUNTIME VERIFIED |
| Unauthenticated notification alias access | RUNTIME VERIFIED, HTTP 401 |
| Recovery evaluation | RUNTIME VERIFIED |
| `RECOVERED` gate allowed path | RUNTIME VERIFIED |
| `RECOVERED` gate blocked path | RUNTIME VERIFIED |
| Existing `tree-008` consistency | DATA VERIFIED |
| Web typecheck | PASS |
| Web notification click-through | RUNTIME VERIFIED |
| Web tree detail panel from notification `targetUrl` | RUNTIME VERIFIED |
| Mobile TypeScript | PASS |
| Mobile Expo Doctor | PARTIAL, patch mismatches only |
| Mobile runtime notification route | NOT TESTABLE, no emulator/device tooling |
| Mobile tree lifecycle runtime | NOT TESTABLE, no emulator/device tooling |
| Camera hardware/runtime | NOT TESTABLE |

## Remaining Blockers

1. Run Mobile E2E on a real Android/iOS device or emulator with the current app build.
2. Run the camera/ESP32 camera path on available hardware.
3. Decide whether to accept the Expo SDK patch-version mismatches for thesis demo or upgrade them in a controlled dependency update.
