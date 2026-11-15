# DurianCare-IoT Final Thesis Claims

Date: 2026-10-11

## Supported Claims

- The backend compiles and its automated test suite passes.
- The web client compiles, builds for production, passes lint with warnings only, and passes its Jest suite.
- The mobile app typechecks and can export a web bundle.
- The AI service source compiles.
- Frozen disease AI model artifacts are present and hash-verified.
- The project includes a guarded severity-data pipeline that prevents invalid training before annotation requirements are met.
- Tree disease lifecycle and care-planning documentation is available for thesis review.

## Claims To Avoid

- Do not claim the severity model is trained.
- Do not claim severity accuracy, precision, recall, F1, or production readiness.
- Do not claim a fresh live E2E passed on 2026-10-11.
- Do not claim physical IoT hardware was verified in this final session.
- Do not claim dependency hygiene is perfect while Expo doctor still reports patch-version drift.

## Recommended Final Wording

> The system is implemented and source-verified for thesis demonstration. The disease lifecycle, treatment follow-up, web/mobile surfaces, AI freeze evidence, and guarded severity dataset pipeline are ready for demo rehearsal. Live infrastructure E2E and hardware validation remain required before a public final demo.
