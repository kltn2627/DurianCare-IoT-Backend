# Git Push and PR Readiness - 2026-10-16

Overall: **PARTIAL**. Tests completed; commit/push is **BLOCKED** in each repository.
No source code was modified in this execution. Only this new report was created.
No commit, staging, push, merge, branch switch, reset, clean, stash, deployment, seed or database write was performed.

## 1. Decision and Initial Inventory

All repositories are on `duy/cleanup-fixes`, tracking `origin/duy/cleanup-fixes`.
Origin is the matching `https://github.com/kltn2627/<repository>.git` in each case.
Fetch succeeded in all three. No unmerged index entries or staged files existed.
The working branches are not divergent from their tracking branches (ahead/behind 0/0).
Each HEAD is already contained in origin/dev, which is one merge commit ahead of HEAD.
`git log origin/dev..HEAD` and `git diff origin/dev...HEAD` are empty for all three.
Consequently no new working-branch-to-dev PR can presently carry the dirty changes.

| Repository | HEAD = verified remote working branch | origin/dev | origin/main | Tracked dirty files |
| --- | --- | --- | --- | --- |
| Backend | 8fbdfa8b8803813750e9194452d01013e0ba36fc | e1d4d00f959128ccc83ce3f3dbde9d43e9d80f5c | c7bf74a9c87f0e445057466cb214d4f74233de34 | 60 |
| Web | abaa3ca48c96c5c0d4f67a05ced85d7f652bbe45 | e2e83aad3d18d51a2584ff3683981330c90a5dc2 | ec6c978dc734ff380910fe083212ca792377b637 | 26 status entries; 25 with textual stat |
| Mobile | cde1765b8aab3c5ab9bd999ab069cc1f6bd56ce9 | 60cd5dcd5bce3b2728c78975340af478156f9ddd | 2edd3a7c468cefe2678b2e0d691b8078f49dd6de | 17 |

Initial tracked diff statistics: Backend +1920/-222; Web +1362/-700;
Mobile +1191/-433. These exclude untracked files. No dirty content was staged.
`git ls-remote origin refs/heads/duy/cleanup-fixes refs/heads/dev refs/heads/main`
independently confirmed all refs above after fetch. This is remote verification, NOT a push claim.

## 2. Scope Classification and Commit Gates

| Group | Classification and action |
| --- | --- |
| Unified export presentation helpers/tests | Known task; tested. Retain unstaged with dependent screens, not an orphan helper-only feature commit. |
| Web CropQrBuilder.tsx | Mixed accumulated +213/-73 lines relative to HEAD: mock removal, actual season/dispatch/summary API reads, self-inspection workflow, QR routing and stale snapshot protection, plus latest presentation. No reliable task-boundary patch baseline was available to separate these safely. Hold whole file. |
| Web cultivation client/types/labels/permissions and FarmerInspectionDialog | Related dependencies, including new states, new API calls and role behavior; cannot classify the entire group as presentation-only. Hold for explicit integrated scope review. |
| Mobile DurianTraceabilityScreen.tsx | Mixed eligibility, inspection workflow/actions, actual catalog data, request sequencing/errors and presentation. Depends on uncommitted types/labels/inspection modal. Hold rather than cherry-stage guessed hunks. |
| Mobile package.json/package-lock.json | Earlier Expo dependency alignment, not this presentation task. Preserve, do not silently include. |
| Backend cultivation/farm/auth/gateway/notification/search | Related earlier business/security work; tests pass but complete accumulated diff and ownership/dependency review is not complete. Hold rather than certify all 60 files and new source as a release. |
| Backend camera/UDP/AI/infrastructure and other old changes | Out of approved narrow presentation scope; retain, do not stage. No fixes attempted. |
| Backend artifacts/finalization, severity artifacts, hard-example datasets | Generated and possibly operational/private evidence; exclude pending content/privacy review. No blanket documentation/artifact add. |
| Mobile dist-farmer-export, dist-phase-g/h/i | Existing generated build outputs; retain on disk, exclude from commits. |
| Other new scripts/docs/tests | Retain until independently scoped; no assumption that every untracked file belongs to this release. |

Important: a passing dirty-workspace build does NOT prove an eventual selected-index commit builds.
There is deliberately no staged candidate and no commit SHA approved for push in this run.
The user-provided stop condition (mixed/unconfirmed old changes) applies to all three repositories.
No duplicate commit was created for the existing commits already merged to dev.

Reviewed actual diff excerpts include Web cultivation client/types/permissions/public traceability,
the accumulated CropQrBuilder diff, Mobile traceability/types/labels/package manifest/docs,
Backend CultivationCalendarService, TreeService, JWT properties, rate limits, notification bulk update
and search indexing authentication. This is NOT a claim of exhaustive review of every retained file.

GitNexus `detect_changes(scope=all)`: Backend CRITICAL (237 changed symbols, 83 affected,
60 tracked changed files); Mobile CRITICAL (81 changed symbols, 22 affected, 17 files).
These are accumulated-diff risk indicators, not proof of a particular exploitable bug.
Web has no registered GitNexus index. No symbol was edited and no risk warning was suppressed.

## 3. Secret and Artifact Screening

A read-only heuristic scan of changed/untracked text scanned 171 Backend, 48 Web and 20 Mobile
files, excluding artifacts, generated output, selected binary extensions and files over 2 MB.
No private-key / GitHub-token / AWS-key / JWT-shaped match was reported.
This is a LIMITED heuristic, not a complete secret clearance or history scan; gitleaks is unavailable.

A broader credential-assignment heuristic flagged Backend infrastructure/docker-compose.yml
lines 248, 277, 356, 391, 413, 437: all reference environment substitution; five contain fallbacks.
No values were printed. These configurations remain excluded, pending owner review of defaults.
Other potential assignments in Web proxy/auth helpers, a proxy test, a UDP test and the E2E harness
were not certified as secret-free; they remain unstaged. Pattern matches alone are not confirmed leaks.
Backend tracked-file lookup found no .env/private-key filename matching the lookup.
Ignored runtime .env contents, browser cookies and credentials were not read or exported.
No secret, raw log, database dump or generated bundle was added to the index.

## 4. Actual Test Runs

Commands ran against the existing dirty working trees on 2026-10-16, approximately 15:26-15:30 +07.
All test/build commands below exited 0.

| Repository / command | Result |
| --- | --- |
| Backend `mvn test -q` | Full parent reactor succeeded; fresh Surefire reports: 343 total, 336 PASS, 7 SKIP, 0 failures/errors. |
| Web `npm test -- --runInBand` | 104 PASS / 10 suites. |
| Web `npm run typecheck` | PASS. |
| Web `npm run lint` | PASS exit 0; 0 errors, 8 warnings outside presentation scope. |
| Web `npm run build` | PASS; 44 generated pages. |
| Mobile `node --test tests/*.test.cjs` | 3 PASS; only existing matching file is tests/export-presentation.test.cjs. |
| Mobile `npm run typecheck` | PASS. |
| Mobile `npx expo export --platform all --output-dir C:/Users/minhd/.codex/tmp/duriancare-pr-preflight-20261016` | PASS; iOS/Android/Web, 75 static routes, output outside repositories. |
| All repositories `git diff --check` | PASS exit 0; existing CRLF notices only. |
| All repositories `git ls-files --unmerged` | Empty. |
| All repositories `git fetch origin` and `git ls-remote ...` | PASS; refs verified above. |

Fresh Maven report breakdown:

| Module with Surefire reports | PASS | SKIP | FAIL / ERROR |
| --- | --- | --- | --- |
| auth | 79 | 1 | 0 / 0 |
| cultivation | 113 | 4 | 0 / 0 |
| farm | 88 | 0 | 0 / 0 |
| gateway | 12 | 0 | 0 / 0 |
| notification | 31 | 2 | 0 / 0 |
| search | 13 | 0 | 0 / 0 |

Other parent modules have no fresh Surefire test cases counted here; reactor success does not imply
a test suite exists in each module. Counts were parsed from reports modified after 15:26, not old runs.
The 1 auth debug test is disabled because it requires live PostgreSQL/Flyway context.
The 4 dispatch Mongo and 2 notification Mongo tests were SKIPPED because
DURIANCARE_FIXTURE_MONGO_URI is absent. No credential was supplied and no fixture DB was created.
These 6 skipped tests are NOT PASS. No database fingerprint equivalence is asserted for this run.

Web warnings remain in DiseaseDiagnosisWorkspace, CameraSection and ExportCompliancePanel.
Mobile has no lint or unit-test package script; the existing Node helper/source-contract test was
run explicitly. No new dependency was installed or upgraded.

NOT TESTED here: authenticated write E2E, native device UI, physical QR scan, hardware,
separate staged-only checkout, actual remote CI for proposed changes, IoT/AI standalone suites.
No feature was changed to make tests pass. Previous read-only UI evidence remains historical,
not a new live E2E result.

## 5. Remote CI and Release Constraints

Backend tracked .github/workflows/ci.yml triggers only main push/PR; it compiles Java test sources
with tests skipped and validates Compose. It does not automatically provide full dev-PR regression
or authenticated E2E evidence. Web/Mobile have no tracked .github workflow in this checkout.
Branch protection and remote check-run results were NOT verified; gh is unavailable.
Do not infer merge permission or green CI from fetch/ls-remote.

Current dev-to-main ranges are large and predate this dirty work. Local test evidence is for dirty
HEAD, NOT clean origin/dev or origin/main, and must not be attached as release validation for them.

## 6. Six Copy-Ready PR Templates

These are text templates only. No GitHub PR was opened or merged. Go to each repository's
Pull requests > New pull request and select the exact base/compare below, then copy the relevant
template. Do not submit A while GitHub reports no changes; first resolve scope and produce a
reviewed, tested commit. B stays PENDING VALIDATION until clean dev and CI are checked.
Reviewer: repository maintainers/domain owners; no specific person is asserted.

### Backend PR A - Working Branch to dev

**Title:** fix(backend): finalize reviewed farmer operational safeguards
**Base:** `dev`
**Compare:** `duy/cleanup-fixes`
**Status:** BLOCKED - current committed compare diff is empty; no new commit or push in this run.

**Summary:** Proposed review of farm health/recovery gates, cultivation/export ownership and dispatch provenance, auth/gateway/notification/search regression.
These changes are present in the working tree, not yet in the remote PR diff.

**Changes by group:** presentation/provenance and snapshot behavior; required domain/API dependencies
only after separate approval. Do not include unreviewed camera/UDP/AI, operational artifacts or old
dependency changes by association. Preserve fixture provenance and backend verified-total rules.

**Test evidence:** mvn test -q: exit 0, 336 PASS / 7 SKIP / 0 FAIL; six real-Mongo fixture tests not executed. Evidence applies to the dirty workspace, not an assembled commit.
**Risks:** mixed accumulated diff; selected-index dependency closure unverified; no live write E2E.
**Not tested:** native/hardware, clean PR-head CI and production behavior.

**Review checklist:**
- [ ] Approve exact file/hunk manifest and ownership of earlier changes.
- [ ] Read complete staged diff, run secret scan, GitNexus and staged diff check.
- [ ] Run regression on the assembled commit, not only this working tree.
- [ ] Verify source markers/NO_VERIFIED_DATA/null and authenticated ownership gates.
- [ ] Non-force push only if tracking branch has no unseen remote commits; verify remote SHA.
- [ ] Confirm actual PR contains the intended changes; reviewers approve, operator merges.

### Backend PR B - dev to main

**Title:** release(backend): promote validated dev baseline
**Base:** `main`
**Compare:** `dev`
**Status:** PENDING VALIDATION - do not treat as ready to release.

**Release summary:** Review the actual committed development baseline below; current uncommitted
operational/presentation changes are NOT included. Range statistics:
`1616 files changed, 152886 insertions(+), 1113 deletions(-)`.

**Actual commits currently in origin/dev but not origin/main (at fetched refs):**

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

**CI/test evidence:** No clean-dev release validation or remote CI result verified in this run.
Dirty-workspace results (mvn test -q: exit 0, 336 PASS / 7 SKIP / 0 FAIL; six real-Mongo fixture tests not executed) must not be relabeled as clean-dev evidence.
**Known limitations:** large historical range; no authenticated shipment write E2E, physical QR or native-device acceptance here.
**Rollback:** retain previous main SHA from section 1. No automatic rollback, reset or force push.
For any deployed state/schema change, separately approve forward-compatible rollback/data handling;
a Git revert alone is not evidence of database compatibility.

**Release checklist:**
- [ ] Inspect full dev/main compare and all included commits, including historical artifacts.
- [ ] Complete PR A and re-fetch if new changes are intended for this release.
- [ ] Validate clean dev, required CI/security checks and branch protections.
- [ ] Confirm dependency/config and backend-client compatibility.
- [ ] Record acceptance evidence and known limitations; approve deployment/rollback separately.
- [ ] Operator creates/releases/merges PR manually; no automatic merge.

### Web-Client PR A - Working Branch to dev

**Title:** fix(web): unify export presentation and guard QR snapshots
**Base:** `dev`
**Compare:** `duy/cleanup-fixes`
**Status:** BLOCKED - current committed compare diff is empty; no new commit or push in this run.

**Summary:** Proposed review of unified export lists, correct private QR snapshot selection, immutable provenance and null verified totals.
These changes are present in the working tree, not yet in the remote PR diff.

**Changes by group:** presentation/provenance and snapshot behavior; required domain/API dependencies
only after separate approval. Do not include unreviewed camera/UDP/AI, operational artifacts or old
dependency changes by association. Preserve fixture provenance and backend verified-total rules.

**Test evidence:** Jest 104 PASS; typecheck/lint/build exit 0; 8 pre-existing lint warnings. Evidence applies to the dirty workspace, not an assembled commit.
**Risks:** mixed accumulated diff; selected-index dependency closure unverified; no live write E2E.
**Not tested:** native/hardware, clean PR-head CI and production behavior.

**Review checklist:**
- [ ] Approve exact file/hunk manifest and ownership of earlier changes.
- [ ] Read complete staged diff, run secret scan, GitNexus and staged diff check.
- [ ] Run regression on the assembled commit, not only this working tree.
- [ ] Verify source markers/NO_VERIFIED_DATA/null and authenticated ownership gates.
- [ ] Non-force push only if tracking branch has no unseen remote commits; verify remote SHA.
- [ ] Confirm actual PR contains the intended changes; reviewers approve, operator merges.

### Web-Client PR B - dev to main

**Title:** release(web-client): promote validated dev baseline
**Base:** `main`
**Compare:** `dev`
**Status:** PENDING VALIDATION - do not treat as ready to release.

**Release summary:** Review the actual committed development baseline below; current uncommitted
operational/presentation changes are NOT included. Range statistics:
`167 files changed, 37740 insertions(+), 2955 deletions(-)`.

**Actual commits currently in origin/dev but not origin/main (at fetched refs):**

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

**CI/test evidence:** No clean-dev release validation or remote CI result verified in this run.
Dirty-workspace results (Jest 104 PASS; typecheck/lint/build exit 0; 8 pre-existing lint warnings) must not be relabeled as clean-dev evidence.
**Known limitations:** large historical range; no authenticated shipment write E2E, physical QR or native-device acceptance here.
**Rollback:** retain previous main SHA from section 1. No automatic rollback, reset or force push.
For any deployed state/schema change, separately approve forward-compatible rollback/data handling;
a Git revert alone is not evidence of database compatibility.

**Release checklist:**
- [ ] Inspect full dev/main compare and all included commits, including historical artifacts.
- [ ] Complete PR A and re-fetch if new changes are intended for this release.
- [ ] Validate clean dev, required CI/security checks and branch protections.
- [ ] Confirm dependency/config and backend-client compatibility.
- [ ] Record acceptance evidence and known limitations; approve deployment/rollback separately.
- [ ] Operator creates/releases/merges PR manually; no automatic merge.

### Mobile-App PR A - Working Branch to dev

**Title:** fix(mobile): unify traceability presentation and guard snapshots
**Base:** `dev`
**Compare:** `duy/cleanup-fixes`
**Status:** BLOCKED - current committed compare diff is empty; no new commit or push in this run.

**Summary:** Proposed review of unified farm/zone/season-scoped traceability lists, obsolete-response guards and immutable provenance.
These changes are present in the working tree, not yet in the remote PR diff.

**Changes by group:** presentation/provenance and snapshot behavior; required domain/API dependencies
only after separate approval. Do not include unreviewed camera/UDP/AI, operational artifacts or old
dependency changes by association. Preserve fixture provenance and backend verified-total rules.

**Test evidence:** Node helper/source-contract 3 PASS; typecheck and all-platform Expo export exit 0; native UI NOT TESTED. Evidence applies to the dirty workspace, not an assembled commit.
**Risks:** mixed accumulated diff; selected-index dependency closure unverified; no live write E2E.
**Not tested:** native/hardware, clean PR-head CI and production behavior.

**Review checklist:**
- [ ] Approve exact file/hunk manifest and ownership of earlier changes.
- [ ] Read complete staged diff, run secret scan, GitNexus and staged diff check.
- [ ] Run regression on the assembled commit, not only this working tree.
- [ ] Verify source markers/NO_VERIFIED_DATA/null and authenticated ownership gates.
- [ ] Non-force push only if tracking branch has no unseen remote commits; verify remote SHA.
- [ ] Confirm actual PR contains the intended changes; reviewers approve, operator merges.

### Mobile-App PR B - dev to main

**Title:** release(mobile-app): promote validated dev baseline
**Base:** `main`
**Compare:** `dev`
**Status:** PENDING VALIDATION - do not treat as ready to release.

**Release summary:** Review the actual committed development baseline below; current uncommitted
operational/presentation changes are NOT included. Range statistics:
`299 files changed, 92993 insertions(+), 4562 deletions(-)`.

**Actual commits currently in origin/dev but not origin/main (at fetched refs):**

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

**CI/test evidence:** No clean-dev release validation or remote CI result verified in this run.
Dirty-workspace results (Node helper/source-contract 3 PASS; typecheck and all-platform Expo export exit 0; native UI NOT TESTED) must not be relabeled as clean-dev evidence.
**Known limitations:** large historical range; no authenticated shipment write E2E, physical QR or native-device acceptance here.
**Rollback:** retain previous main SHA from section 1. No automatic rollback, reset or force push.
For any deployed state/schema change, separately approve forward-compatible rollback/data handling;
a Git revert alone is not evidence of database compatibility.

**Release checklist:**
- [ ] Inspect full dev/main compare and all included commits, including historical artifacts.
- [ ] Complete PR A and re-fetch if new changes are intended for this release.
- [ ] Validate clean dev, required CI/security checks and branch protections.
- [ ] Confirm dependency/config and backend-client compatibility.
- [ ] Record acceptance evidence and known limitations; approve deployment/rollback separately.
- [ ] Operator creates/releases/merges PR manually; no automatic merge.

## 7. Retained File Manifest

All paths below were intentionally retained UNSTAGED. Source/test/docs entries are HOLD pending
integrated scope/ownership review, not automatically approved for inclusion. Generated directories
are EXCLUDE. Files under artifacts/ and dataset hard_examples are EXCLUDE pending privacy and
artifact-policy review. No retained file was deleted to make status clean.
This new report itself is also untracked and not included in a commit.

### DurianCare-IoT-Backend

```text
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
?? duriancare-ai-service/app/api/recommendations.py
?? duriancare-auth-service/src/test/java/com/duriancare/auth/bootstrap/AdminBootstrapSafetyTest.java
?? duriancare-auth-service/src/test/java/com/duriancare/auth/config/AuthDependencyHealthTest.java
?? duriancare-auth-service/src/test/java/com/duriancare/auth/config/JwtConfigurationValidationTest.java
?? duriancare-auth-service/src/test/java/com/duriancare/auth/controller/LoginContractTest.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/api/HarvestDispatchController.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/api/SeasonProductionController.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/CultivationSeasonStatus.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/HarvestDispatch.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/domain/SeasonProductionStatus.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/repository/HarvestDispatchRepository.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/HarvestDispatchService.java
?? duriancare-cultivation-service/src/main/java/com/duriancare/cultivation/service/SeasonProductionService.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/api/FarmerDispatchLifecycleTest.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/api/HarvestDispatchControllerTest.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/api/HarvestDispatchMongoIntegrationTest.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/api/HarvestDispatchWorkflowIntegrationTest.java
?? duriancare-cultivation-service/src/test/java/com/duriancare/cultivation/config/HarvestBatchStatusConverterTest.java
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
?? duriancare-gateway/src/test/java/com/duriancare/gateway/security/JwtConfigurationValidationTest.java
?? duriancare-iot-service/scripts/seed-iot-history.js
?? duriancare-iot-service/src/discovery/udpDiscoveryService.js
?? duriancare-iot-service/test-support/camera-schema.cjs
?? duriancare-iot-service/test-support/main-lifecycle.cjs
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
?? scripts/severity_training/annotation_cli.py
?? scripts/severity_training/annotation_web.py
?? scripts/severity_training/audit_raw_datasets.py
?? scripts/severity_training/build_annotation_candidates.py
?? scripts/severity_training/collect_data.py
?? scripts/severity_training/common.py
?? scripts/severity_training/convert_annotations.py
?? scripts/severity_training/dataset_report.py
?? scripts/severity_training/detect_duplicates.py
?? scripts/severity_training/download_public_datasets.py
?? scripts/severity_training/evaluate.py
?? scripts/severity_training/export_model.py
?? scripts/severity_training/infer.py
?? scripts/severity_training/prepare_annotations.py
?? scripts/severity_training/progression.py
?? scripts/severity_training/split_dataset.py
?? scripts/severity_training/stage_mobiusi_seed.py
?? scripts/severity_training/test_pipeline_guards.py
?? scripts/severity_training/train.py
?? scripts/severity_training/training_gate.py
?? scripts/severity_training/validate_dataset.py
?? scripts/test_dispatch_e2e_harness.py
?? scripts/verify-deployed-source.ps1
```

Generated/evidence paths additionally retained and excluded (87 file entries), grouped:

- `artifacts/finalization/`
- `artifacts/severity_training/`
- `duriancare-ai-service/datasets/hard_examples/`

### DurianCare-IoT-Web-Client

```text
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
?? src/app/api/users/[...path]/route.ts
?? src/components/dashboard/FarmerInspectionDialog.tsx
?? src/components/feedback/Toast.tsx
?? src/components/iot/IotDeviceRegistration.tsx
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

### DurianCare-IoT-Mobile-App

```text
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
?? src/features/profile/FarmerInspectionModal.tsx
?? src/features/profile/exportPresentation.ts
?? tests/export-presentation.test.cjs
```

Generated/evidence paths additionally retained and excluded (353 file entries), grouped:

- `dist-farmer-export/`
- `dist-phase-g/`
- `dist-phase-h/`
- `dist-phase-i/`

## 8. Operator Decisions and Next Safe Actions

1. Domain owners approve a file/hunk manifest for Backend cultivation/dispatch and farm/auth/
   notification/search separately; explicitly exclude camera/UDP/AI and artifacts unless separately reviewed.
2. Web/Mobile owners decide whether the complete cultivation dependency group (screen, DTOs,
   client methods, labels/permissions and inspection dialog) belongs in one integration change.
   Presentation-only staging cannot safely guess the historical hunk boundaries.
3. Review environment-default findings and new scripts/docs for secrets or operational identifiers
   using an approved secret scanner. No credential values should be pasted into chat.
4. Once scoped, stage explicit paths (never git add . / -A), inspect git diff --cached and its
   --check result, run GitNexus detect_changes(scope=staged), and test the actual candidate.
5. Re-fetch and verify HEAD/tracking relation immediately before a normal push. Do not merge,
   rebase or change branches automatically if remote changes.
6. Create PR A only after a new approved commit exists; PR B remains PENDING VALIDATION.

No commit SHA/file set was produced, no branch was pushed, and no remote PR was opened.
Existing matching remote SHAs were verified, but that is not completion of the requested push.
Overall **PARTIAL**: safe inventory, regression and six PR templates completed; commit/push blocked
by mixed/unapproved accumulated scope and incomplete candidate/security/release validation.

