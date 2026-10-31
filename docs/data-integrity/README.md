# Data integrity migration

`audit_integrity.js` is read-only and reports each Farm, Zone, Season, Plan,
Activity, HarvestBatch, LabSample, LabResidueResult, ExportRelease and legacy
`cultivation_schedules` record as `VALID`, `ORPHAN`, `SCOPE_MISMATCH` or
`LEGACY_UNVERIFIED`.

Run `infrastructure/mongodb/migrations/run_integrity_migration.ps1` with
`-Mode DRY_RUN` first. `-Mode APPLY` creates verified BSON backups of the Farm
and Cultivation databases before any write and records their SHA-256 hashes.
The Mongo script also rejects APPLY unless the runner confirms the backup.

The only automatic parent repair is `season-minh-phat-2026` in
`farm-minh-phat-2026` / `plot-khu-a-truong-thanh`. This exact scope is declared
in the seed and corroborated by its Plan and HarvestBatch. Every unresolved
record receives an entry in `legacy_integrity_markers`. Business IDs and
business statuses remain unchanged. A marker stays in place until a separate,
evidence-backed review explicitly clears it; canonical APIs hide marked
records even if someone later adds a missing parent.

The `demo-*` Seasons are deliberately not created. The historical
`demo-export-release-ri6-eu-2026-001` retains its `RELEASED` status for audit
history but is not a verified release. The two Zone B activities are marked
for review rather than being reparented to an unproven historical Plan.
