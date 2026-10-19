# Historical Care-Plan Reference Reconciliation

Status: BLOCKED_SOURCE_EVIDENCE. Existing data is preserved.

scripts/audit-care-plan-references.js is a read-only dry-run. Supply DURIANCARE_AUDIT_FARM_ID
and DURIANCARE_AUDIT_OWNER_ID explicitly to mongosh; the farm must match that owner. It queries
diagnoses by actual referenced IDs globally, not merely by a matching farm filter. No inference by
disease code, similar dates or tree name is accepted as a substitute for the referenced diagnosis.

Current target-farm result: 8 plans, 5 MISSING_DIAGNOSIS, 3 matching references, no automatic action.
Exact IDs remain in MASTER_WEB_FARMER_COMPLETION_2026_10_15.md. Current diagnosis service has
no delete flow found; the legacy tree-map init script contains a farm-scoped diagnosis delete/reinsert.
That script's configured demo farm differs from this farm, so its presence is NOT proof it caused
these five historical gaps. No executed import/deletion log or original diagnosis backup was supplied.
Current validation/tests do not prove how old missing references originated.

Required custodian input: original record/backup or authenticated audit event for each exact diagnosis
ID; tree/farm/zone and actor provenance; schema/import version and timestamps; signed per-plan repair
approval; secured restorable backup; proposed exact corrective fields. Do not fabricate a diagnosis,
reattach to the latest diagnosis, delete a plan or change its historical status to silence the audit.

Only after those inputs exist may a separate repair be proposed. Dry-run must show original and
evidence-supported target references and reject owner/tree/farm/zone mismatch or concurrent changes.
Use a transaction when supported, or exact compare-and-set of original ID/reference/version and an
approved compensation plan. Require an exact affected-record count; abort on any mismatch. Re-run
read-only reference checks and plan/task/notification ownership tests. No executable repair script is
provided without evidence, because there is not yet a legitimate target value to encode.

Rollback proposal: pre-commit abort; after an approved commit restore only the approved original
fields from protected backup IF current values/version still equal that repair's output. Preserve plan,
diagnosis, task and notification history; never restore an entire live database or overwrite intervening
user changes. Three pure tests cover missing/valid/mismatched references without changing inputs.
