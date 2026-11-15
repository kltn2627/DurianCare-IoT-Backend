# Web, Farmer Garden and Notification Completion

Date: 2026-10-15. Overall classification: **FIXED BUT NOT FULLY VERIFIED**.

This is a new execution window, not a replacement for prior acceptance evidence. Backend and Web
remain on `duy/cleanup-fixes`; their pre-existing dirty changes were preserved. No commit, push,
branch switch, reset, clean, database reset or volume deletion occurred. Camera was deferred:
no camera source edit, endpoint, stream, capture or hardware test was performed in this execution.

## Findings and Fixes

| Item | Result | Evidence |
| --- | --- | --- |
| Notification mark-all 403 | FIXED AND VERIFIED | Actual browser displayed the reported ownership message. Gateway probe with browser Origin returned 403; without Origin and without credentials returned the expected 401. Notification proxy forwarded Origin/Referer unlike the already-fixed shared proxy. Stripped those two transport headers; did not relax Gateway CORS/auth or ownership. |
| Authenticated mark-all | FIXED AND VERIFIED | Existing Farmer browser session: unread 8 -> 0. Mongo independently confirms own read=8, own unread=0, total notifications=23. No notification deleted. The original bulk URL/method were correct; this was not a single-notification-ID routing bug. |
| Bulk concurrency | FIXED AND VERIFIED (fixture) | Replaced read/mutate/saveAll with owner+isRead conditional Mongo update. Actual modified count returned. Two real-Mongo tests cover concurrent count total=2 for two own unread docs, other owner untouched, empty inbox and repeat=0. |
| Notification UX | FIXED AND VERIFIED in tested paths | Internal toast, no native mark-all/delete confirm, in-app delete confirmation, header preview/badge refresh event and 15-second count refresh, close/Escape controls, pagination. Rejected unsafe protocol-relative/backslash metadata targets. No live deletion test was performed. |
| Profile garden | FIXED AND VERIFIED | Legacy area endpoint errors were swallowed into empty arrays, yielding one farm but zero areas/trees. Canonical Farm/Zone + scoped Tree APIs now yield 1 farm / 4 zones / 100 active trees in the logged-in UI. Errors no longer display invented zero garden totals. |
| Zone counts/varieties | FIXED AND VERIFIED | List and detail use persisted active-tree responses and compare backend active count; each zone has 25 active trees. Actual varieties are deduplicated from tree data. Unknown age/stage remains unknown; no dates/ages were invented. |
| Map safety/navigation | FIXED AND VERIFIED in tested paths | Reject non-finite/out-of-range normalized positions, exclude removed trees, keyboard marker activation, reset old zone selection/data, reject farm/zone mismatch. Fixed back link to the existing farms page. |
| IoT registry gap | FIXED AND VERIFIED | Initially 4,578 telemetry rows but zero esp32-01 registry rows. Fail-closed scoped read correctly required a registry/authorized farm; single-farm UI fallback could not override it. Operator explicitly confirmed device ownership and registration into farm-khoa-luan-2026. |
| IoT official registration | FIXED AND VERIFIED | Added a small form using authenticated canonical farm catalog and existing POST /api/iot/devices via Web proxy/Gateway. Registered the real device, not a fake sensor. Verified registry ID 2634d1d5-e6a2-4930-aa39-73ada0ee217a, farm-khoa-luan-2026, zone=null, status=ACTIVE. No claimed zone location or online status. |
| IoT display | FIXED AND VERIFIED in tested paths | Browser now shows esp32-01, latest 30.6 C / air humidity 87.2% / soil 100%; measured 2026-10-14T16:52:17.067Z. last_seen_at is null, connectivity UNKNOWN, shown as Chua ro ket noi rather than online. Poll interval remains 8 seconds. Alert-load failure is distinct from no alerts. |
| Seasonal chart completeness | FIXED BUT NOT FULLY VERIFIED | UI explicitly labels loaded sample distribution and 1,000-row cap, not percentage of all elapsed season time. DB history currently starts 2026-08-12, not June. No June/July measurements or simulated history were inserted. |

Runtime notification success was observed after the Web fix and before Notification service redeployment.
The later atomic Backend change was verified with isolated Mongo fixtures and deployed with healthy
read endpoints; no new unread production notifications were manufactured just to retest a live bulk write.
Browser HTTP response codes were not separately captured for successful mutations; UI and persisted
database state are the runtime evidence. POST registration's 201 behavior is the source contract, not a
claimed separately captured browser network trace.

## Canonical Farmer Data

Target identity: `mi***@gmail.com`, user ID `9ffc2a41-eaa6-4567-ad6d-d6741acafeb7`, FARMER/ACTIVE.
Identity/role were observed in authenticated Profile; UUID/role/status were independently resolved
read-only from Auth. No Farmer password or browser cookie/bearer/storage was read/exported. A new login
was not executed: the user's existing valid browser session was reused through ordinary UI controls.

Farm `farm-khoa-luan-2026` owns four embedded zones:

| Zone | Active trees | Boundary |
| --- | ---: | --- |
| zone-a-khu-bac | 25 | None |
| zone-b-khu-nam | 25 | None |
| zone-c-khu-dong | 25 | None |
| zone-d-khu-tay | 25 | None |

Farm/Zone is the canonical cultivation-area model; no new collection or migration was introduced.
Trees link by `farmId` + `farmZoneId`. Map positions are normalized `positionX/positionY` in [0,1],
not latitude/longitude, and use the existing SVG map. No polygon or geographic containment was invented.
Map endpoint is an unpaginated ACTIVE tree array. Read-only audit found no duplicate tree codes,
duplicate active positions, invalid positions or season scope/date issues for this garden. Five seasons
already exist, including the prior-season record. No extra farm/zone/tree/season seed was necessary.

UI evidence: Khu Bac detail shows 25 trees. Its map has 25 marker buttons. Clicking marker DC-T009
and clicking its table row both open DC-T009 / Ri6 / Cây Chín with its persisted diagnosis history.
Navigating through farm -> Khu Nam shows 25 markers, zero DC-T009 markers and no retained DC-T009
detail panel. No tree health/update/removal mutation was executed against these real trees.

`scripts/audit-farmer-garden.js` is intentionally read-only and repeatable, not a seed. Resolve the owner
UUID from Auth first and supply DURIANCARE_AUDIT_OWNER_ID to mongosh in the existing container.
Four pure tests cover repeatability, no input mutation, foreign-owner rejection, invalid references,
invalid positions and duplicate codes/positions. Existing init script 02_seed_tree_map_demo.js contains
deleteMany; legacy seed-cultivation-dev.js includes the invalid seed harvest lot. Neither was run.
Seed idempotency is NOT certified merely because this read-only audit is repeatable.

### Existing Care History Requires Reconciliation

Read-only target-farm inventory: 137 diagnosis records, 8 care plans. Five plans reference diagnosis IDs
that do not exist even in a global diagnosis lookup:

| Plan ID | Tree ID | Missing diagnosis ID | Persisted status |
| --- | --- | --- | --- |
| 6abfb1c49b3a2b4eca4ade4d | tree-008 | test | CANCELLED |
| 6abfb26519a9533b7e5adedf | tree-008 | 6abfac4b61b92b604c6855d7 | IN_PROGRESS |
| 6ac614ea1530625e82aab979 | tree-76 | 6ac615051530625e82aab97f | IN_PROGRESS |
| 6ac615721530625e82aab982 | tree-86 | 6ac615931530625e82aab988 | IN_PROGRESS |
| 6ac64bb8339f15c7ff5b1391 | tree-97 | 6ac64bb2339f15c7ff5b138f | PLANNED |

All referenced trees exist. The other three plans have matching tree/farm diagnosis references.
No orphan was deleted or reattached, no synthetic diagnosis was created. Owner/data custodian must
provide original diagnosis evidence or approve a separate corrective process. This is a real remaining
data-integrity blocker; the garden graph passing does not certify the full care-history graph.

## Automated Verification

| Executed command | Exit | Actual result |
| --- | ---: | --- |
| mvn -pl duriancare-notification-service -am test '-Dstyle.color=never' | 0 | 33 cases: 31 pass, 2 opt-in Mongo skip |
| mvn -q -pl duriancare-notification-service '-Dtest=NotificationBulkMongoIntegrationTest' test, isolated URI in process memory | 0 | 2 real Mongo tests pass; exact fixture documents deleted and zero remaining asserted |
| mvn test '-Dstyle.color=never' | 0 | All reactor modules BUILD SUCCESS; 325 cases, 318 pass, 7 skip, 0 failure/error |
| npm test -- --runInBand (Web) | 0 | 6 suites, 55 tests pass |
| npm run typecheck (Web) | 0 | Pass |
| npm run lint (Web) | 0 | 0 errors, 8 pre-existing warnings in AI/camera/export files; no camera source edited |
| npm run build (Web) | 0 | Production build and 44 page generation pass |
| node --test scripts/audit-farmer-garden.test.js scripts/audit-harvest-data.test.js scripts/database-fingerprint.test.js | 0 | 13 tests pass |
| node --test test/read-api.test.js (IoT service) | 0 | 24 tests pass: identity, unmapped/cross-owner denial, configure permission, duplicate registration, scoped telemetry and alerts; no camera tests |
| python -m unittest discover -s scripts -p test_dispatch_e2e_harness.py | 0 | 3 tests pass |
| python scripts/demo_e2e_harness.py | 1 | BLOCKED_AUTH_CONTEXT; steps=[], writeEnabled=false; no host credentials or write flag present |
| scripts/admin-email-repair-dry-run.sql through psql, no variables | 0 | BLOCKED_ADMIN_ID_REQUIRED, no write; psql zero exit is not approval |
| Same dry-run with explicit FIXTURE_ONLY_NOT_APPROVAL inputs | 0 | READ ONLY BEGIN / boolean-count checks / ROLLBACK; no account edit |
| git diff --check, Backend/Web | 0 | No whitespace errors |

Intermediate failures were corrected and rerun: a PowerShell-unquoted Maven property, a missing Mockito
times import in the new assertion, optional farm-name/test fixture typing, and an effect-state lint error.
No failed intermediate run is presented as a pass. The two real Mongo tests use fresh UUID databases,
only four known fixture notification IDs, no dropDatabase/collection deletion, and verified exact cleanup.
They are not an authenticated Gateway fixture/E2E. The full reactor's seven skips include these two,
four opt-in dispatch Mongo tests and the existing unsafe live OTP debug helper.

Mobile, AI training/model checks and physical hardware were NOT rerun or modified in this task.
Frozen models/severity gate were not changed. Search/full-project cross-role UI workflows and real-tree
CRUD/recovery were not exhaustively executed; earlier evidence is historical, not a new runtime pass.

## Deployment and Integrity Windows

Verified Docker context desktop-linux, Compose project duriancare and the repository's existing
infrastructure/docker-compose.yml. Built and recreated ONLY Notification service:

```powershell
docker compose -f infrastructure/docker-compose.yml --env-file infrastructure/.env build duriancare-notification-service
docker compose -f infrastructure/docker-compose.yml --env-file infrastructure/.env up -d --no-deps --no-build duriancare-notification-service
```

Both exited 0. Final running image:
`sha256:698b78cfad997c309814c84021182963cbdc7005842d06036d59ca8dddbf110b`.
Rollback image retained as `duriancare/notification-service:before-master-web-fix-20261015`
(original sha256:3a67c4913cf9bb7cd01cf18c1126094e0901dfee05a899fc9c7d8d7df10fb148).
Other containers retained their IDs; no other service was restarted. Notification health was 200/UP.
Web uses the existing local dev server with hot reload, plus a verified production build; no production
Web deployment is claimed.

- Mongo evidence: master-web-farmer-mongo-integrity.json covers farm/cultivation only, UTC
  05:28:17 -> 05:30:32 on Oct 15. All canonical document hashes match. This window starts AFTER
  mark-all and covers notification-only deployment, not the entire day or all databases.
- PostgreSQL evidence: master-web-farmer-sql-integrity.json captures 42 tables around the explicitly
  approved real-device registration. ONLY iot_devices changed, count 0 -> 1; other 41 table hashes
  match, including users and telemetry. Existing telemetry count remains 4,578. This is not a claim
  that no business records changed: exactly one device registry was intentionally created.
- Notification mutation: exactly the target's eight unread notifications became read. No before/after
  notification-wide fingerprint was taken before that action, so other-recipient runtime equality is
  not claimed; other-recipient preservation is separately covered in real Mongo fixtures.

## ADMIN and Business Evidence Gates

Malformed ADMIN UUID remains `33c193a5-9bf0-456a-9bcb-842d76b769bb`, ADMIN/ACTIVE with two @ signs;
one other ACTIVE ADMIN exists, but its usable login is not established. Current configured bootstrap
email matches the running Auth environment and still contains two @ signs. Bootstrap properties only
reject blanks and initializer normalizes then persists; official LoginRequest uses Jakarta @Email.
This explains configuration -> malformed persisted identity -> login validation rejection. Existing
account bootstrap does not rename a persisted account. No current-use inference from account name.

Added scripts/admin-email-repair-dry-run.sql and docs/admin-email-repair-runbook.md: read-only checks,
secured backup requirement, approved transaction preconditions, paired config/account changes, session
impact and guarded rollback. FIXTURE_ONLY_NOT_APPROVAL is test input, NOT evidence of permission or
a proposed replacement email. BLOCKED until administrator supplies approved replacement/address
control, documented approval, current-use assessment, tested backup access and maintenance window.

Export/dispatch unchanged: 1,850 KG remains unverified; invalid 1,200 KG seed remains excluded.
No release, dispatch, customer, shipment date, grading/packing weight or evidence was created.
NO_VERIFIED_DATA/null contract remains; existing reactor tests cover those rules, not actual shipping.
Original source documents/review evidence and an approved isolated dispatch fixture/second identity
remain prerequisites for separately authorized live write E2E.

## Files Changed in This Execution

Backend production: NotificationRepository.java; NotificationServiceImpl.java.
Backend tests: NotificationServiceImplTest.java; new NotificationBulkMongoIntegrationTest.java.
Tools/docs: new audit-farmer-garden.js/.test.js; admin-email-repair-dry-run.sql; admin-email-repair-runbook.md;
this report, two integrity JSON artifacts and updates to the existing acceptance/operational audit reports.

Web: notification proxy route; notification/client.ts; DashboardShell.tsx; NotificationInbox.tsx;
new NotificationBell.tsx and feedback/Toast.tsx; ProfileView.tsx; CultivationZonesWorkspace.tsx;
CultivationZoneDetail.tsx; ZoneTreesWorkspace.tsx; TreeMapCanvas.tsx; new cultivation/garden.ts and
trees/map.ts; notification/target.ts; iot/client.ts; SensorCharts.tsx; new iot/IotDeviceRegistration.tsx; four new regression
files garden.test.ts, notification-client.test.ts, notification-proxy.test.ts, iot-registration.test.ts.
Other dirty paths predate this execution and were not reverted or attributed to these fixes.

Saved UI evidence: [desktop sensors](master-sensors-desktop.jpg), [mobile sensors](master-sensors-mobile.jpg),
[mobile notification preview](master-notification-mobile.jpg). Tested viewport overrides were reset.
Final Git status inventory: Backend 110 entries, Web 36 entries (including pre-existing work, not a count
of this task's edits); neither branch changed and no commit/push was performed.

GitNexus upstream impact was requested for Notification service/repository/test changes. It returned
UNKNOWN with interface-dispatch/incomplete caller coverage, not a zero-risk finding. Controller ->
service -> repository consumers were reviewed manually and tested. Web is not a registered GitNexus
repo; its affected imports, proxy contracts and UI flows were inspected directly instead.

## Next Safe Actions

1. ADMIN owner/DB custodian supplies the runbook's actual approvals and backup-access evidence.
   Run the dry-run with local approved variables; no automated account repair is enabled.
2. Data custodian reconciles the five missing diagnosis references using real source evidence.
3. Farmer supplies fresh device telemetry through its existing authorized ingest when available;
   last_seen/connectivity must be observed, not fabricated from registration success.
4. For CLI authenticated E2E, operator provides credentials securely in its local environment and runs
   python scripts/demo_e2e_harness.py with write flag absent. Browser session is not exported.
5. Supply actual dispatch/release documents or an approved isolated manifest before business-write E2E.

Final classification: **FIXED BUT NOT FULLY VERIFIED**. The observed notification failure, Profile
counts, map scope and approved real-device linkage are resolved; a project-wide COMPLETE claim
would hide the remaining care-history, ADMIN, full-history and business-evidence limitations.

## UDP Discovery Follow-up - 2026-10-15

See [MASTER_UDP_DISCOVERY_2026_10_15.md](MASTER_UDP_DISCOVERY_2026_10_15.md). The Backend-only follow-up adds tested discovery and origin-aware snapshot caching (64 fixture-enabled IoT tests pass); no Web files were changed. New source is not deployed and bare unsigned CAM packets cannot authorize updates. Five missing diagnosis references, ADMIN approval and dispatch evidence remain blocked. Existing Web results above are historical, not freshly rerun by this follow-up.
