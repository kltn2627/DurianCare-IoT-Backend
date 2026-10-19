# ADMIN Email Repair: Operator-Controlled Plan

Status: BLOCKED pending authorized replacement address, approval reference, current-use assessment,
verified backup-admin login and a coordinated maintenance window. No account/config was repaired.

1. Confirm local/dev Docker context and the exact existing ADMIN UUID. Record role/status, current
   email and updated_at in an access-controlled operator record, not application logs or this report.
2. Run scripts/admin-email-repair-dry-run.sql with psql variables admin_id, replacement_email and
   approval_ref supplied locally. The script is READ ONLY and ends with ROLLBACK. It prints booleans
   and counts, never passwords, token/session content or the replacement email. Every check must pass;
   a supplied reference alone does not establish approval. Validate the new address through the existing
   Backend email validator and prove control of its mailbox separately.
3. Database custodian takes an encrypted/restorable backup through the established backup process.
   Do not export password hashes into source control. Securely retain the original bootstrap config.
4. Confirm a second ADMIN can log in using the official flow. Account owner explicitly approves
   revocation/re-login of this account's existing sessions; an old token may retain the old email claim.
5. During the approved window, custodian starts a transaction, SELECTs the target FOR UPDATE,
   rechecks exact UUID, old email, role/status and updated_at, checks email uniqueness, and updates
   ONLY email and updated_at. Require exactly one affected row; otherwise ROLLBACK. Password,
   UUID, role, status and ownership references must remain unchanged. This is a proposal, not an
   executable repair script and not permission to mutate the currently stored ADMIN.
6. Update only the approved bootstrap email configuration consistently. Existing-account bootstrap
   does not rename persisted users, so configuration-only repair is insufficient. Restart Auth only
   if configuration activation requires it; do not blindly recreate unrelated services.
7. Verify official login and authorized identity, backup-admin access and existing resource ownership.
   Never weaken email validation. Publish only sanitized statuses/IDs in acceptance evidence.
8. Rollback on failure: before COMMIT use ROLLBACK. After COMMIT, authorized custodian must lock
   and restore the old email only if UUID/current replacement/expected updated_at still match and
   the old address is free; restore paired bootstrap config. Revoke/reissue affected sessions through
   the supported process. Do not overwrite intervening account edits or restore an entire live database.

Next dry-run command, after local operator supplies the three non-secret variables:

```powershell
Get-Content -Raw scripts/admin-email-repair-dry-run.sql | docker exec -i -e APPROVED_ADMIN_ID -e APPROVED_ADMIN_EMAIL -e ADMIN_APPROVAL_REF duriancare-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v admin_id="$APPROVED_ADMIN_ID" -v replacement_email="$APPROVED_ADMIN_EMAIL" -v approval_ref="$ADMIN_APPROVAL_REF"'
```

The operator supplies these three approved values in the local environment first. The command forwards
only their names to docker exec; no password/token belongs in arguments or chat. A missing-variable
BLOCKED message may exit psql with code zero; exit code alone never means preflight or approval passed.
