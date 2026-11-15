# DurianCare-IoT Final Security Audit

Date: 2026-10-11

## Checked

- No secrets were printed or added to final reports.
- No database reset, volume deletion, or credential-changing operation was performed.
- Backend gateway and notification modules compile after route/event changes.
- Web authentication-dependent routes compile in production build.

## Residual Risks

- Runtime authorization behavior was not revalidated against a live gateway in this session.
- Dependency audit was not run for backend, web, or mobile packages.
- Expo doctor reports dependency patch-version drift in the mobile app.

## Recommendation

Before a public demo, run a live authenticated smoke test with real environment variables and verify only intended user roles can access farm, tree, notification, export, and AI routes.
