# Final UI Operation and Git Report - 2026-10-16

Overall verdict: **PARTIAL**.
Local Web notice removal is verified. Two independent regression-test commits were pushed and
their remote SHAs verified. The accumulated operational UI implementation is NOT included in
those commits. No Backend commit/push was needed. This is not production-readiness sign-off.

## 1. Notice Removal and Commit Boundary

Exact literal found in Web src/components/dashboard/CropQrBuilder.tsx, previously line 355.
No exact literal or equivalent verified-shipment empty notice was found in Mobile operational
source. Backend has documentation about the empty-summary contract, not a UI renderer.

Changed only the Web JSX branch: when verifiedData is none, render null instead of the notice.
The error, loading and available branches remain. Summary fetching, source validation, null
quantities, provenance, synthetic exclusion, filters, release lists and snapshot sequencing remain.
No replacement banner/placeholder/empty state was added.

Important Git fact: git show HEAD:src/components/dashboard/CropQrBuilder.tsx before the new
commit contained neither the notice nor verifiedData. The renderer was part of the accumulated
unstaged implementation. Its removal cannot be represented as a deletion-only patch from HEAD.
That file also contains older API integration, self-inspection and dispatch changes, so it was
not swept into a fix commit. It remains dirty, including the local notice correction.
Mobile already had no notice; no Mobile screen source was changed in this execution.

New independent guards:
- Web src/__tests__/export-verification-notice.test.ts.
- Mobile tests/export-verification-notice.test.cjs.

Both are source-contract tests (not mounted UI tests). They assert the exact forbidden literal is
absent from the respective operations source. Existing tests separately cover null summary,
provenance, fixture write guards and selected-release/error handling. No assertion claims that a
literal-only source guard detects every possible paraphrase or dynamically generated UI notice.

The test-only commits preserve an already-absent notice in committed baseline. They do NOT ship
the accumulated unified-list UI. Reviewers must not describe these commits as deployment of that UI.

## 2. Actual Verification

All commands below ran during this execution; exit 0 unless explicitly stated.

| Command | Actual result |
| --- | --- |
| Backend mvn test -q | 343 total: 336 PASS, 7 SKIP, 0 failures/errors. |
| Web npm test -- --runInBand | 105 PASS, 11 suites. |
| Web npm run typecheck | PASS. |
| Web npm run lint | 0 errors, 8 existing warnings outside this notice change. |
| Web npm run build | PASS; 44 generated pages. |
| Mobile node --test tests/*.test.cjs | 4 PASS; helper/source-contract tests only. |
| Mobile npm run typecheck | PASS. |
| Mobile npx expo export --platform all --output-dir C:/Users/minhd/.codex/tmp/duriancare-ui-notice-20261016 | PASS Android/iOS/Web; 75 static routes. Output outside repositories. |
| Isolated staged Web: npm test -- --runInBand --runTestsByPath src/__tests__/export-verification-notice.test.ts | 1 PASS. |
| Isolated staged Mobile: node --test tests/export-verification-notice.test.cjs | 1 PASS. |
| git diff --check in all 3 repositories | Exit 0; existing CRLF notices only. |
| git diff --cached --check before both commits | Exit 0; each index contained exactly one new test. |
| git fetch origin and git ls-remote | Exit 0 in each repository. |
| Gateway GET http://localhost:8080/actuator/health without credentials | HTTP 200, body not logged; readiness only. |
| Gateway GET http://localhost:8080/api/users/me without credentials | HTTP 401, body not logged; no authentication bypass. |

Fresh Surefire totals after the 15:35 +07 start:
auth 79 PASS/1 SKIP; cultivation 113/4; farm 88/0; gateway 12/0;
notification 31/2; search 13/0. Other reactor modules do not contribute fresh test cases.
Skips: auth live PostgreSQL debug helper (1); dispatch Mongo fixture tests (4) and notification
Mongo fixture tests (2) because DURIANCARE_FIXTURE_MONGO_URI is absent. These are NOT passes.
No real-Mongo fixture was enabled and no database unchanged/fingerprint assertion is made.

Isolated candidates were generated with git write-tree and git archive of the required source/test
paths, expanded into fresh external temporary directories, without changing branches or working
files. Web reused installed node_modules via a junction; no npm install was run. The candidates
contain committed baseline screen source plus the staged test, not the dirty screen.
These were focused test runs, not claims of full staged-only build or native E2E.

Mobile has no configured lint script. No dependency, lockfile, camera/UDP, auth policy or backend
code was edited to make tests pass.

## 3. Read-Only Runtime Evidence

Existing authenticated Web session at localhost:3000/dashboard/client/crops was reloaded through
normal UI. No cookie store, token, password, header or browser secret was read or exported.
After loading, the forbidden notice was absent. The shared list still showed:
- Ri6, actual Khu Tay season name, 3.500 kg, persisted Da xuat xuong.
- Monthong, actual Khu Dong season name, 5.000 kg, persisted Da giao hang.

No business write action was used. This is authenticated read-only UI smoke, not a complete
Gateway identity/ownership/write E2E. QR switch/error guards were inspected and exercised by
existing automated contracts; no fresh live QR request-failure injection or physical scan occurred.

## 4. Operational Review Findings

| Area | Evidence and disposition |
| --- | --- |
| Web notice | Fixed locally in the none-only JSX branch; useful error/loading/available messages retained. |
| Farm/tree | TreeService source uses HEALTHY for create/generate and ACTIVE-only map queries; existing farm tests pass. No farm changes in commits. |
| Export/dispatch | HarvestDispatchService.summary checks view scope, confirmation, relationships, chronology and batch/release synthetic markers; zero eligible records return NO_VERIFIED_DATA and null quantities/months. Existing tests pass. No formula changed. |
| Principal/ownership | Dispatch controller constructs actor from forwarded authentication headers; service checks farm/zone permissions. Gateway production trust boundary and direct service network exposure were not exhaustively re-audited. No ownership relaxation made. |
| Export safety | Existing validation/self-inspection/PHI checks and separate season production state left intact. Real safety evidence remains required; fixtures not reclassified. |
| QR | Web clears snapshot before request, checks request sequence and releaseId. Mobile openReleaseDetail clears traceability, ignores obsolete responses and rejects wrong releaseId. Private route authentication not changed. |
| Telemetry | read-api uses case-insensitive device comparisons and bounded timestamps, with farm-access checks; Web ranges uses +07 boundaries and Asia/Ho_Chi_Minh labels. No generated/interpolated readings introduced. |
| Gateway contracts | application.yml routes users/auth to auth; harvest/export/seasons to cultivation; IoT/sensors to IoT; notification routes to notification service. Source contract evidence only, not every route exercised live. |
| Auth/search/notification/profile | Regression suites/typechecks/builds pass; earlier changes preserved. No new bug fix claimed beyond the notice. Full accumulated-diff security review remains incomplete. |

The older reports retain historical results unchanged. This review is targeted and does not certify
every accumulated line as audited or safe to commit.

## 5. GitNexus CRITICAL Investigation

The installed GitNexus implementation at
C:/Users/minhd/AppData/Roaming/npm/node_modules/gitnexus/dist/mcp/local/local-backend.js
computes detect_changes risk from affected execution-process count:
0 -> low; 1-5 -> medium; 6-15 -> high; above 15 -> critical (around lines 1379-1386).
This explains the observed broad-diff verdict; it is not a runtime failure or vulnerability detector.
MCP-reported counts match this installed rule, but no claim is made that every index reference is complete.

| Scope | Actual result | Relevance |
| --- | --- | --- |
| Backend accumulated all | CRITICAL: 237 changed symbols, 83 affected processes, 60 files | Unresolved broad integration review; no Backend code committed. |
| Mobile accumulated all | CRITICAL: 81 changed symbols, 22 affected processes, 17 files | Unresolved broad integration review; old source/lockfile excluded from new commit. |
| Mobile staged test only, before commit | LOW: 1 file, 0 mapped changed symbols/processes | New test is not indexed as a production call path. Manual full diff review also performed; zero graph symbols is not proof of exhaustive test analysis. |
| Web impact(CropQrBuilder) and staged detect_changes | Repository not registered | UNKNOWN graph coverage; manually inspected JSX and exact staged test. No indexing or broad source change performed. |

Specific affected examples, from the MCP result:
- Backend TreeCarePlanService.createCarePlan -> requireTree -> farm not-found/access-denied
  and createCarePlan -> publishFollowUpNotification -> FarmNotificationEventPublisher.publish.
- Backend TreeController.updateCarePlanStatus -> TreeCarePlanService.updateStatus -> requireTree;
  TreeDiagnosisService.saveDiagnosis -> ownership exceptions/status.
- Backend CultivationCalendarService canViewLabSample / seasonScopes appears in CreateLabResult
  authorization flows. These pre-existing changes are not part of the staged test-only group.
- Mobile NotificationDetailScreen -> resolveNotificationTarget -> metadata/fallback helpers;
  TreeDetailContent/load -> authorizedRequest; CarePlanSection -> authorizedRequest.
- Mobile DurianTraceabilityScreen -> foundation catalog builders, openReleaseDetail,
  runReleaseAction and submitRelease (context); indexed process includes ShortFoundationId.

The production dependency paths remain outside the test-only commits. The broad warning is NOT
marked resolved or suppressed. No broad risk cleanup/refactor was attempted.

## 6. Commits, Pushes and Remote Verification

All repositories stayed on duy/cleanup-fixes, with their existing origin tracking branch.
Origin URLs verified as https://github.com/kltn2627/<repository>.git.
Initial staged indexes were empty; tracking divergence before new commits was 0/0.
After each commit, fetch succeeded and ahead/behind was 1/0 before a normal push.
No protected dev/main push, force push, merge or rebase was performed.

| Repository | Commit and exact file | Push / independently verified remote SHA |
| --- | --- | --- |
| Backend | No new commit; HEAD 8fbdfa8b8803813750e9194452d01013e0ba36fc | No push; existing remote matches. |
| Web | ef36627c0331bd484668cccc073f514fb3aecaaa; src/__tests__/export-verification-notice.test.ts only | git push origin HEAD:refs/heads/duy/cleanup-fixes exited 0; ls-remote returned the same SHA. |
| Mobile | e1be509c7c24fa7812da4dae63f6409220690f84; tests/export-verification-notice.test.cjs only | git push origin HEAD:refs/heads/duy/cleanup-fixes exited 0; ls-remote returned the same SHA. |

Commit messages: test(web): prevent export verification notice;
test(mobile): prevent export verification notice.
Both staged diffs were read in full, literal-pattern secret checked and diff-checked.
There are no credentials, config defaults, generated files or unreviewed source hunks in either commit.
A literal-pattern scan is not a comprehensive repository/history security audit.

## 7. Six PR Templates

Templates only; no PR opened or merged. On GitHub choose Pull requests > New pull request,
set the exact base/compare below and copy the relevant content. Do not describe dirty code as
part of the remote PR diff. No specific reviewer identity is invented.

### Backend A - duy/cleanup-fixes to dev

Title: chore(backend): review remaining operational integration changes
Base: dev
Compare: duy/cleanup-fixes
Status: BLOCKED / NO NEW DIFF. Current HEAD already merged into dev; do not open duplicate PR.
Summary: No new Backend change was made for UI notice removal.
Actual changes: none in origin/dev..HEAD.
Test evidence: dirty-workspace Maven 336 PASS/7 SKIP; not validation of an absent candidate commit.
Limitations: accumulated Backend CRITICAL risk and unreviewed dirty source remain excluded.
Not tested: authenticated write E2E, real-Mongo tests without approved fixture URI, hardware.
Reviewer checklist:
- [ ] Approve a separate integrated file/hunk manifest.
- [ ] Review full staged source, secrets, dependency closure and GitNexus.
- [ ] Test a real candidate before push; verify remote SHA.
- [ ] Open PR only when a new reviewed diff exists.

### Web A - duy/cleanup-fixes to dev

Title: test(web): prevent export verification notice
Base: dev
Compare: duy/cleanup-fixes
Summary: Add an independent source-contract guard against the unwanted operational export notice.
Actual changes: ef36627, src/__tests__/export-verification-notice.test.ts only.
Test evidence: isolated index candidate 1 PASS; dirty-workspace Jest 105 PASS, typecheck/lint/build
exit 0, lint retains 8 old warnings. The full workspace results are not a full PR-head build.
Known limitations: local JSX correction and older unified-list/QR integration remain uncommitted.
This PR does not deploy those UI changes or verify shipments.
Not tested: mounted UI via this guard, clean full PR build, live write E2E/native/hardware.
Reviewer checklist:
- [ ] Confirm compare contains only the new test and no fixture/business/config changes.
- [ ] Run Jest against PR head.
- [ ] Keep local operational source integration as a separate reviewed change.
- [ ] Operator merges after review; no automatic merge.

### Mobile A - duy/cleanup-fixes to dev

Title: test(mobile): prevent export verification notice
Base: dev
Compare: duy/cleanup-fixes
Summary: Preserve absence of the unwanted notice in the traceability screen with a Node source guard.
Actual changes: e1be509, tests/export-verification-notice.test.cjs only.
Test evidence: isolated index test 1 PASS; dirty-workspace Node 4 PASS, typecheck/export exit 0.
Known limitations: no Mobile source change was needed; accumulated screen/DTO/dependency changes
are not in this PR. Source-contract tests do not simulate or mount native UI.
Not tested: physical/emulated native UI, camera, authenticated write E2E.
Reviewer checklist:
- [ ] Confirm PR only adds the Node test.
- [ ] Run node --test tests/export-verification-notice.test.cjs at PR head.
- [ ] Review production Mobile integration separately.
- [ ] Operator merges only after normal review.

### Backend B - dev to main

Title: release(backend): promote validated dev baseline
Base: main
Compare: dev
Status: PENDING VALIDATION.
Summary: Promote only the committed dev baseline after clean-dev validation and CI review.
Actual changes currently in origin/dev but not origin/main (fetched refs):

```text
e1d4d00 feat(farm-service): complete tree health care flow — care plans, recovery evaluation, backend gate (#20)
8fbdfa8 fix(backend): enforce evaluateRecovery gate on PATCH /health-status → RECOVERED
1ce36c0 docs: add Web/Mobile parity matrix + runtime verification section in final report
417b20f docs(architecture): add E2E audit and final report for tree health care flow
4894a9e feat(backend): tree care plan + recovery evaluation + care followup notification
dc68796 fix(auth-service): add tag search to KB article queries
25ed321 feat(farm-service): add PATCH /api/trees/{id}/health-status for manual state transitions
1885402 fix(services): repair 4 runtime regressions blocking core API endpoints
2b73661 docs(ai): Phase N — Allocaridara pest detection feasibility report
77624ec test(farm-service): Phase M — TreeDiagnosisService unit tests + fix stale FarmZone test constructors
b0499bc fix(farm): add RECOVERED_BY_FARMER to inferHealthStatus healthy set
b70ce73 feat(farm-service): Phase B — farm/zone creation with auto tree generation
3514fc5 merge(duy/cleanup-fixes): merge origin/dev — preserve all features both sides
5d206ef chore(duy/cleanup-fixes): commit local changes before dev sync
1d1eaa7 feat (mobile): sync mobile (#19)
4386f3f feat (mobile): sync mobile
a4a0a06 docs(ai): freeze graduation baseline and handover
d8a19cc feat(ai): Phase 27 final hardening — baseline, reproducibility, demo, traceability
d77effc feat(ai): Phase 26 final validation artifacts and benchmark results
2ee7a64 docs(ai): add Phase 14 final report for AI diagnosis module
11b860a feat(ai,iot,gateway): graceful detector failure, health fields, audit & infra fixes
b05c5a9 fix(gateway,auth): add IoT public routes and auth community migration
9aaea83 feat(chat, ai, knowledge, community): complete core workflows and integration (#18)
7906bd6 feat(chat): improve engineer and farmer chat workflows
b78dde3 feat(community, knowledge, chat): preserve current feature changes
0615f9a feat(ai/backend): enhance detector pipeline, update agent guidelines & runtime artifacts (#17)
67e497e feat(ai/backend): enhance detector pipeline, update agent guidelines & runtime artifacts
5f3e073 fix(ai): eliminate false positives for non-leaf images
505610f feat(ai): upgrade adaptive inference pipeline
27de679 fix(ai-service): soften esp32 preprocessing and preserve low-confiden… (#16)
d94a01c fix(ai-service): soften esp32 preprocessing and preserve low-confidence top predictions
074c37f feat(chat, knowledge): improve chat and knowledge flows (#15)
71a5728 feat(chat, knowledge): improve chat and knowledge flows
6fb4189 merge(dev): synchronize latest development changes (#14)
2dc5656 merge(dev): synchronize latest development changes
8677462 fix(auth): resolve login HTTP 500 by fixing root cause
7fe1bef restore stashed backend changes (#13)
8ffe78b restore stashed backend changes
c8410fb fix(runtime): resolve merge regressions
d846f61 Merge remote-tracking branch 'origin/dev' into duy/cleanup-fixes
e5e0672 feat(cultivation, chat, farm-zone): implement cultivation CRUD, engineer chat, and farm zone management" (#12)
316375a feat(cultivation, chat, farm-zone): implement cultivation CRUD, engineer chat, and farm zone management
1550faf fix(runtime): align mongo defaults for IntelliJ
5d8feaf merge(dev): synchronize latest development changes (#11)
78301a6 merge(dev): synchronize latest development changes
353b68a feat(chat) Update chat workspace for experts and farmers (#10)
5ed8ee2 Merge branch 'dev' of https://github.com/kltn2627/DurianCare-IoT-Backend into 22635201_NguyenThiHaNhu
3de50df fix : mail fix send (#9)
a1fc588 fix : mail fix send
1c7be5e feat: update expert and farmer chat workspace
5fb62e0 fix(runtime): align IntelliJ and Docker configuration for local devel… (#8)
79679ca fix(runtime): align IntelliJ and Docker configuration for local development
9977656 feat: implement user profile module and backend enhancements (#7)
ff418e6 feat: implement profile module and integrate auth, search, notification
48b74b4 feat(auth): Complete Authentication & Async OTP Verification Flow (#6)
9a4ba77 feat(auth): implement dynamic JWT authentication, OTP verification flow and Kafka event integration
```

New test-only commits from this execution are NOT yet in dev; update this list after PR A is merged.
Test evidence: no clean-dev release test or remote CI result was verified in this run.
Do not reuse dirty-workspace results as proof of clean-dev readiness.
Known limitations: historical release range includes changes beyond this notice task; all must be
reviewed. Backend tracked CI currently compiles tests with execution skipped on main events;
no tracked Web/Mobile GitHub workflow was found in the earlier inventory. Remote protection rules
and check results were not verified here.
Not tested: full clean-dev regression, authenticated write E2E, native-device/hardware acceptance.
Reviewer checklist:
- [ ] Inspect current dev/main compare and actual commit set again.
- [ ] Validate clean dev and required CI/security checks.
- [ ] Confirm service/client compatibility and deployment configuration without exposing secrets.
- [ ] Record known limitations and separately approve rollout/rollback.
- [ ] Operator opens and merges PR; do not automatically reset/revert or force push on rollback.

### Web-Client B - dev to main

Title: release(web-client): promote validated dev baseline
Base: main
Compare: dev
Status: PENDING VALIDATION.
Summary: Promote only the committed dev baseline after clean-dev validation and CI review.
Actual changes currently in origin/dev but not origin/main (fetched refs):

```text
e2e83aa Merge pull request #14 from kltn2627/duy/cleanup-fixes
abaa3ca chore(web): update next-env.d.ts routes path (build artifact)
162f009 fix(web): gate RecoveryPanel behind evaluateRecovery + add RecoveryEvaluationResponse type
24ac3c0 feat(web): add CarePlanSection + care plan types and API client methods
8a225b0 fix(web): wire TREATING/RECOVERED health-status transitions + fix KB search
caf0fc1 feat(web): add RECOVERED health status display across tree map and detail views
26a1305 feat(web): wire CultivationCalendar to real API, remove MockDataBanner
d056aff fix(web): Phase Q — remove silent mock fallback, add visible mock-data banners
fc08afb fix(web): Phase P — tree map syncs after recovery confirmation
6596e6d test(web): Phase M — getDiseaseCategory/getDiseaseAlertMessage unit tests (23 cases)
aab7290 feat(web): Phase L — active season banner + KB-rule safe harvest date (parity with mobile)
7745dce feat(web): Phase I — KB article links in AI diagnosis result
1c3462c feat(web): Phase F+G — standardize AI categories, RECOVERED_BY_FARMER
5cfd7d3 feat(web): Phase B/C — farm/zone creation + auto tree generation UI
d323c67 merge(duy/cleanup-fixes): merge origin/dev — Web Client
bac92d9 chore(duy/cleanup-fixes): commit local changes before dev sync
e08c79a Merge pull request #13 from kltn2627/22635201_NguyenThiHaNhu
1924ada feat(mobile): sync mobile
d9b5498 chore: gitignore .claude tool directory
ff88827 feat(camera,sensors,ai): add camera ESP32 page, enhanced sensor charts & AI diagnosis
01ec46a feat(traceability): QR export modal, batch lifecycle UI & public trace page
71ea5bf Merge pull request #12 from kltn2627/22635201_NguyenThiHaNhu
16cdd4c feat(chat): improve engineer and farmer chat workflows
cd49c3c Merge pull request #11 from kltn2627/duy/cleanup-fixes
8527eec fix : FE
6fbc14f Merge pull request #10 from kltn2627/duy/cleanup-fixes
e1c437e fix(runtime): resolve merge regressions after dev sync
70c0fe6 Merge pull request #9 from kltn2627/22635201_NguyenThiHaNhu
f10c8c0 feat(chat, ai): stabilize chat UI and AI diagnosis flow
ddae8ee Merge pull request #8 from kltn2627/duy/cleanup-fixes
049a9be fix(runtime): stabilize diagnosis upload and locale fonts
835cf6c Merge branch 'dev' of https://github.com/kltn2627/DurianCare-IoT-Web-Client into duy/cleanup-fixes
c57c490 Merge pull request #7 from kltn2627/22635201_NguyenThiHaNhu
995ecda restore stashed frontend changes
efd5474 update
bf9292c merge(dev): synchronize latest web changes
26a0cda Merge pull request #6 from kltn2627/22635201_NguyenThiHaNhu
1e2fb49 feat(cultivation, chat, farm-zone): implement cultivation CRUD, engineer chat, and farm zone management
c12a3aa Merge pull request #5 from kltn2627/duy/cleanup-fixes
983c694 fix(runtime): resolve merge regressions after syncing dev
409e4c2 Merge pull request #4 from kltn2627/22635201_NguyenThiHaNhu
81964e2 feat: update expert and farmer chat workspace
6ba6f08 Merge pull request #3 from kltn2627/duy/cleanup-fixes
a9f54b0 feat(web): integrate authentication, search, notification and profile modules
521370b Merge pull request #2 from kltn2627/duy/cleanup-fixes
dbe2803 feat(web): sync with dev, implement farmer workspaces, article reader and admin dispatch console
19ea09e Merge pull request #1 from kltn2627/22635201_NguyenThiHaNhu
bbe3e85 feat (CultivationCalendar): add CultivationCalendar
```

New test-only commits from this execution are NOT yet in dev; update this list after PR A is merged.
Test evidence: no clean-dev release test or remote CI result was verified in this run.
Do not reuse dirty-workspace results as proof of clean-dev readiness.
Known limitations: historical release range includes changes beyond this notice task; all must be
reviewed. Backend tracked CI currently compiles tests with execution skipped on main events;
no tracked Web/Mobile GitHub workflow was found in the earlier inventory. Remote protection rules
and check results were not verified here.
Not tested: full clean-dev regression, authenticated write E2E, native-device/hardware acceptance.
Reviewer checklist:
- [ ] Inspect current dev/main compare and actual commit set again.
- [ ] Validate clean dev and required CI/security checks.
- [ ] Confirm service/client compatibility and deployment configuration without exposing secrets.
- [ ] Record known limitations and separately approve rollout/rollback.
- [ ] Operator opens and merges PR; do not automatically reset/revert or force push on rollback.

### Mobile-App B - dev to main

Title: release(mobile-app): promote validated dev baseline
Base: main
Compare: dev
Status: PENDING VALIDATION.
Summary: Promote only the committed dev baseline after clean-dev validation and CI review.
Actual changes currently in origin/dev but not origin/main (fetched refs):

```text
60cd5dc Merge pull request #8 from kltn2627/duy/cleanup-fixes
cde1765 fix(mobile): add RECOVERED state to tree map + gate RecoveryPanel behind evaluateRecovery
170839d feat(mobile): add CarePlanSection and fix needsRecovery scope
33bce11 fix(mobile): use disease.code (not name) for KB article search
e0c215c feat(mobile): wire health status transitions — TREATING + RECOVERED states
b58aa87 docs(architecture): add full project recovery report (19-phase audit)
6a4efd0 docs(architecture): add regression recovery report and API regression matrix
5bbbcf0 docs(demo): add runtime verification, demo scripts, and DB migration scripts
56e355a feat(mobile): Phase S — season edit UI wired to PATCH /api/v1/cultivation-seasons/{id}
3ad2246 feat(mobile): Phase S — wire updateCultivationSeason to existing backend PATCH
f9f8010 fix(mobile): remove stale predictedDisease param from askDurianAssistant
b809cf3 fix(mobile): correct .env.example config gaps from physical-device audit
c6d85aa fix(mobile): Phase R — remove broken expert-chat media upload contract
da7023a fix(mobile): Phase P — tree map syncs after recovery confirmation
8b52fbd feat(mobile): Phase K — KB-rule-based safe harvest date in season banner
018e76f feat(mobile): Phase J — active cultivation season banner in ZoneTreesScreen
f412c6e feat(mobile): Phase I — KB article links in AI diagnosis result
17a1b3c feat(mobile): Phase F+G — standardize AI categories, RECOVERED_BY_FARMER
831a706 feat(mobile): Phase B/C — farm/zone creation + auto tree generation
40ed996 docs(baseline): full multi-repo dev sync baseline report
7168615 docs(mobile): add dev sync baseline report (PHASE 0-7)
aceb7d1 fix(mobile): resolve TypeScript errors after dev merge
8ca575f chore(mobile): merge origin/dev into duy/cleanup-fixes
db22ead chore(duy/cleanup-fixes): commit local changes before dev sync
9a6f10f Merge pull request #7 from kltn2627/22635201_NguyenThiHaNhu
8d722f1 feat (mobile): sync mobile
393d8dc feat(iot): enhance live telemetry hook with reconnection and sensor type filtering
cd815d9 feat(traceability): batch lifecycle management, QR modal & QR scanner
b6ef870 update
551bc80 fix(runtime): resolve merge regressions
5a2968b Merge pull request #6 from kltn2627/22635201_NguyenThiHaNhu
b932df7 feat(cultivation, chat, farm-zone): implement cultivation CRUD, engineer chat, and farm zone management
66bd184 Merge pull request #5 from kltn2627/duy/cleanup-fixes
73b19c6 merge(dev): synchronize latest development changes
62e56b5 Merge pull request #4 from kltn2627/22635201_NguyenThiHaNhu
d9bc21c feat: update expert realtime chat channel
e184754 Merge pull request #3 from kltn2627/duy/cleanup-fixes
f5a55fc feat(mobile): integrate authentication, search, notification and profile modules
00554dc Merge pull request #2 from kltn2627/duy/cleanup-fixes
d1e7273 feat(mobile): sync with dev, build hybrid chat tabs and mobile knowledge base screen
04bfe02 Merge pull request #1 from kltn2627/22635201_NguyenThiHaNhu
06abeba feat (CultivationCalendar): add CultivationCalendar
```

New test-only commits from this execution are NOT yet in dev; update this list after PR A is merged.
Test evidence: no clean-dev release test or remote CI result was verified in this run.
Do not reuse dirty-workspace results as proof of clean-dev readiness.
Known limitations: historical release range includes changes beyond this notice task; all must be
reviewed. Backend tracked CI currently compiles tests with execution skipped on main events;
no tracked Web/Mobile GitHub workflow was found in the earlier inventory. Remote protection rules
and check results were not verified here.
Not tested: full clean-dev regression, authenticated write E2E, native-device/hardware acceptance.
Reviewer checklist:
- [ ] Inspect current dev/main compare and actual commit set again.
- [ ] Validate clean dev and required CI/security checks.
- [ ] Confirm service/client compatibility and deployment configuration without exposing secrets.
- [ ] Record known limitations and separately approve rollout/rollback.
- [ ] Operator opens and merges PR; do not automatically reset/revert or force push on rollback.

## 8. Preserved Dirty Work and Remaining Blockers

The complete retained file manifest is below. Each listed production/config/docs file remains
unstaged for the same ownership/integration/artifact reasons recorded in the previous readiness
report. In particular, CropQrBuilder.tsx includes the local notice correction but is not in ef36627.
Existing camera/UDP/AI changes, datasets, old artifacts, Mobile lockfile/dependency edits and prior
dist output directories were neither modified nor committed in this execution.
This new report is also left untracked, not silently included in a source commit.

### DurianCare-IoT-Backend retained status after pushes

```text
## duy/cleanup-fixes...origin/duy/cleanup-fixes
 M config-server/src/main/resources/config/duriancare-auth-service.yml
 M docs/IOT_READ_API.md
 M docs/cultivation-calendar/api.md
 M docs/cultivation-calendar/export-traceability.md
 M duriancare-ai-service/app/services/s3_storage.py
 M duriancare-ai-service/main.py
 M duriancare-ai-service/tests/test_s3_storage.py
 M duriancare-auth-service/pom.xml
 M duriancare-auth-service/src/main/java/com/duriancare/auth/config/JwtProperties.java
 M duriancare-auth-service/src/main/resources/application.yml
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/api/CultivationCalendarController.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/api/CultivationCalendarDtos.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/config/MongoConfig.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/CultivationSeason.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/ExportRelease.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/ExportReleaseStatus.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/HarvestBatch.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/CultivationCalendarService.java
 M duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/CultivationSeasonCatalogService.java
 M duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/CultivationCalendarAuthorizationServiceTest.java
 M duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/CultivationSeasonCatalogServiceTest.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/controller/FarmApiExceptionHandler.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/controller/TreeController.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/domain/TreeCarePlan.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/dto/CreateTreeCarePlanRequest.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/dto/TreeCarePlanResponse.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/event/publisher/FarmNotificationEvent.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/event/publisher/FarmNotificationEventPublisher.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/repository/TreeCarePlanRepository.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/service/TreeCarePlanService.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/service/TreeDiagnosisService.java
 M duriancare-farm-service/src/main/java/com/duriancare/farm/service/TreeService.java
 M duriancare-farm-service/src/test/java/com/duriancare/farm/service/TreeDiagnosisServiceTest.java
 M duriancare-farm-service/src/test/java/com/duriancare/farm/service/TreeServiceRecoveryGateTest.java
 M duriancare-gateway/src/main/java/com/duriancare/gateway/filter/AuthRateLimitFilter.java
 M duriancare-gateway/src/main/java/com/duriancare/gateway/security/JwtProperties.java
 M duriancare-gateway/src/main/resources/application.yml
 M duriancare-gateway/src/test/java/com/duriancare/gateway/filter/AuthRateLimitFilterTest.java
 M duriancare-iot-service/package.json
 M duriancare-iot-service/src/camera/cameraRoutes.js
 M duriancare-iot-service/src/camera/cameraService.js
 M duriancare-iot-service/src/camera/deviceService.js
 M duriancare-iot-service/src/main.js
 M duriancare-iot-service/src/read-api.js
 M duriancare-iot-service/test/read-api.test.js
 M duriancare-iot-service/tests/camera-registration.test.js
 M duriancare-notification-service/src/main/java/com/duriancare/notification/controller/NotificationInboxController.java
 M duriancare-notification-service/src/main/java/com/duriancare/notification/event/listener/AuthEventListener.java
 M duriancare-notification-service/src/main/java/com/duriancare/notification/event/listener/NotificationEventListener.java
 M duriancare-notification-service/src/main/java/com/duriancare/notification/event/listener/NotificationEventPayload.java
 M duriancare-notification-service/src/main/java/com/duriancare/notification/repository/NotificationRepository.java
 M duriancare-notification-service/src/main/java/com/duriancare/notification/service/impl/NotificationServiceImpl.java
 M duriancare-notification-service/src/test/java/com/duriancare/notification/controller/NotificationInboxControllerTest.java
 M duriancare-notification-service/src/test/java/com/duriancare/notification/event/listener/AuthEventListenerTest.java
 M duriancare-notification-service/src/test/java/com/duriancare/notification/event/listener/NotificationEventListenerTest.java
 M duriancare-notification-service/src/test/java/com/duriancare/notification/service/impl/NotificationServiceImplTest.java
 M duriancare-search-service/src/main/java/com/duriancare/search/controller/SearchApiExceptionHandler.java
 M duriancare-search-service/src/main/java/com/duriancare/search/controller/SearchController.java
 M duriancare-search-service/src/test/java/com/duriancare/search/controller/SearchControllerTest.java
 M infrastructure/docker-compose.yml
?? artifacts/finalization/
?? artifacts/severity_training/
?? docs/FINAL_TEAM_PUSH_READINESS_2026_10_16.md
?? docs/IOT_UDP_DISCOVERY.md
?? docs/TREE_AI_CARE_PLANNING.md
?? docs/admin-email-repair-runbook.md
?? docs/architecture/CODEX_TAKEOVER_AUDIT.md
?? docs/architecture/TREE_DISEASE_LIFECYCLE.md
?? docs/architecture/TREE_DISEASE_LIFECYCLE_E2E.md
?? docs/care-plan-reference-reconciliation.md
?? docs/cultivation-calendar/operational-data-audit.md
?? docs/notifications.md
?? docs/release/
?? duriancare-ai-service/app/api/recommendations.py
?? duriancare-ai-service/datasets/hard_examples/2026-10-14_10-00-55/
?? duriancare-ai-service/datasets/hard_examples/2026-10-14_10-01-06/
?? duriancare-ai-service/datasets/hard_examples/2026-10-14_11-26-45/
?? duriancare-ai-service/datasets/hard_examples/2026-10-14_11-27-01/
?? duriancare-auth-service/src/test/java/com/duriancare/auth/bootstrap/
?? duriancare-auth-service/src/test/java/com/duriancare/auth/config/
?? duriancare-auth-service/src/test/java/com/duriancare/auth/controller/LoginContractTest.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/api/HarvestDispatchController.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/api/SeasonProductionController.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/CultivationSeasonStatus.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/HarvestDispatch.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/SeasonProductionStatus.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/repository/HarvestDispatchRepository.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/HarvestDispatchService.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/SeasonProductionService.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/api/
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/config/
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/CultivationSeasonWiringTest.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/FarmerExportWorkflowTest.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/service/HarvestDispatchServiceTest.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/domain/RecommendedCareTaskDraft.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/domain/TreeCareRecommendation.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/domain/TreeCareRecommendationStatus.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/domain/TreeCareTask.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/domain/TreeCareTaskStatus.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/dto/AcceptCareRecommendationRequest.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/dto/CareRecommendationRequest.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/dto/RecommendedCareTaskRequest.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/dto/TreeCareRecommendationResponse.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/dto/TreeCareTaskResponse.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/dto/UpdateCareRecommendationRequest.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/repository/TreeCareRecommendationRepository.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/repository/TreeCareTaskRepository.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/service/AiCareRecommendationClient.java
?? duriancare-farm-service/src/main/java/com/duriancare/farm/service/impl/RestAiCareRecommendationClient.java
?? duriancare-farm-service/src/test/java/com/duriancare/farm/controller/TreeMutationContractTest.java
?? duriancare-farm-service/src/test/java/com/duriancare/farm/service/TreeCarePlanServiceTest.java
?? duriancare-gateway/src/test/java/com/duriancare/gateway/security/
?? duriancare-iot-service/scripts/seed-iot-history.js
?? duriancare-iot-service/src/discovery/
?? duriancare-iot-service/test-support/
?? duriancare-iot-service/test/camera-capture.test.js
?? duriancare-iot-service/test/seed-history.test.js
?? duriancare-iot-service/test/udp-camera-cache.test.js
?? duriancare-iot-service/test/udp-discovery-pg.test.js
?? duriancare-iot-service/test/udp-discovery.test.js
?? duriancare-iot-service/test/udp-lifecycle.test.js
?? duriancare-notification-service/src/test/java/com/duriancare/notification/service/impl/NotificationBulkMongoIntegrationTest.java
?? infrastructure/docker-compose.udp-discovery.yml
?? scripts/admin-email-repair-dry-run.sql
?? scripts/audit-care-plan-references.js
?? scripts/audit-care-plan-references.test.js
?? scripts/audit-farmer-garden.js
?? scripts/audit-farmer-garden.test.js
?? scripts/audit-harvest-data.js
?? scripts/audit-harvest-data.test.js
?? scripts/database-fingerprint.js
?? scripts/database-fingerprint.sql
?? scripts/database-fingerprint.test.js
?? scripts/demo_e2e_harness.py
?? scripts/dispatch_e2e_harness.py
?? scripts/master_backend_probe.py
?? scripts/seed-export-dispatches.js
?? scripts/seed-export-dispatches.test.js
?? scripts/severity_training/
?? scripts/test_dispatch_e2e_harness.py
?? scripts/verify-deployed-source.ps1
```

### DurianCare-IoT-Web-Client retained status after pushes

```text
## duy/cleanup-fixes...origin/duy/cleanup-fixes
 M next.config.ts
 M src/app/api/backend/[...path]/route.ts
 M src/app/api/notifications/[[...path]]/route.ts
 M src/app/traceability/[cropId]/page.tsx
 M src/components/camera/CameraSection.tsx
 M src/components/charts/SensorCharts.tsx
 M src/components/cultivation-zones/CultivationZoneDetail.tsx
 M src/components/cultivation-zones/CultivationZonesWorkspace.tsx
 M src/components/dashboard/CropQrBuilder.tsx
 M src/components/dashboard/DashboardShell.tsx
 M src/components/dashboard/ProfileView.tsx
 M src/components/export/ExportCompliancePanel.tsx
 M src/components/notifications/NotificationInbox.tsx
 M src/components/trees/TreeDetailPanel.tsx
 M src/components/trees/TreeMapCanvas.tsx
 M src/components/trees/ZoneTreesWorkspace.tsx
 M src/features/cultivation-calendar/labels.ts
 M src/features/cultivation-calendar/permissions.ts
 M src/lib/cultivation/client.ts
 M src/lib/cultivation/types.ts
 M src/lib/iot/client.ts
 M src/lib/iot/types.ts
 M src/lib/notification/client.ts
 M src/lib/trees/client.ts
 M src/lib/trees/types.ts
 M tsconfig.json
?? src/__tests__/export-presentation.test.ts
?? src/__tests__/farmer-export.test.ts
?? src/__tests__/garden.test.ts
?? src/__tests__/iot-dashboard.test.ts
?? src/__tests__/iot-history.test.ts
?? src/__tests__/iot-registration.test.ts
?? src/__tests__/notification-client.test.ts
?? src/__tests__/notification-proxy.test.ts
?? src/__tests__/tree-errors.test.ts
?? src/app/api/users/
?? src/components/dashboard/FarmerInspectionDialog.tsx
?? src/components/feedback/
?? src/components/iot/
?? src/components/notifications/NotificationBell.tsx
?? src/lib/auth/gatewayProxy.ts
?? src/lib/cultivation/garden.ts
?? src/lib/cultivation/presentation.ts
?? src/lib/cultivation/releaseSelection.ts
?? src/lib/iot/history.ts
?? src/lib/iot/ranges.ts
?? src/lib/notification/target.ts
?? src/lib/trees/errors.ts
?? src/lib/trees/map.ts
```

### DurianCare-IoT-Mobile-App retained status after pushes

```text
## duy/cleanup-fixes...origin/duy/cleanup-fixes
 M app/(main)/_layout.tsx
 M docs/mobile/08_TRACEABILITY.md
 M package-lock.json
 M package.json
 M src/features/cultivation/api/cultivationLabels.ts
 M src/features/cultivation/api/cultivationTypes.ts
 M src/features/dashboard/DurianOperationsScreen.tsx
 M src/features/more/DurianMoreScreen.tsx
 M src/features/notification/DurianNotificationInboxScreen.tsx
 M src/features/notification/NotificationDetailScreen.tsx
 M src/features/notification/notificationApi.ts
 M src/features/notification/notificationTargetResolver.ts
 M src/features/profile/DurianTraceabilityScreen.tsx
 M src/features/trees/TreeDetailScreen.tsx
 M src/features/trees/ZoneTreesScreen.tsx
 M src/features/trees/treeApi.ts
 M src/features/trees/treeTypes.ts
?? dist-farmer-export/
?? dist-phase-g/
?? dist-phase-h/
?? dist-phase-i/
?? src/features/profile/FarmerInspectionModal.tsx
?? src/features/profile/exportPresentation.ts
?? tests/export-presentation.test.cjs
```

Blockers / next actions:
1. Operational source integration remains uncommitted. Owners must approve complete dependency
   groups or provide an authoritative historical hunk baseline; no unrelated source was swept in.
2. Broad GitNexus CRITICAL warnings remain for retained Backend/Mobile dirty source, not the
   two independent test-only commits.
3. Six real-Mongo fixture tests require an approved isolated URI; no shared-data write was enabled.
4. Remote CI/protection and clean-dev release validation remain unverified. All dev-to-main
   templates are PENDING VALIDATION.
5. Authenticated write E2E and native/hardware acceptance are NOT TESTED. No production-ready
   claim, shipment evidence claim or replacement of null totals was made.

Only the exact Web notice renderer and two new regression tests were edited/added in source.
No seed, database/volume operation, deployment, restart, authentication bypass, camera/UDP fix,
dependency edit, branch change, stash, reset, clean or data deletion was performed.
Conclusion: **PARTIAL**. Two safe test commits pushed and verified; local UI corrected; broad
operational source changes deliberately remain outside those remote commits.

