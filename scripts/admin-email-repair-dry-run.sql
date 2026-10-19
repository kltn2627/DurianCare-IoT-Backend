-- Read-only preflight. No UPDATE, backup export, password or session contents.
-- Run only with operator-supplied admin_id, replacement_email and approval_ref.
\set ON_ERROR_STOP on
\if :{?admin_id}
\else
  \echo BLOCKED_ADMIN_ID_REQUIRED
  \quit
\endif
\if :{?replacement_email}
\else
  \echo BLOCKED_APPROVED_REPLACEMENT_EMAIL_REQUIRED
  \quit
\endif
\if :{?approval_ref}
\else
  \echo BLOCKED_APPROVAL_REFERENCE_REQUIRED
  \quit
\endif
BEGIN TRANSACTION READ ONLY;
SELECT count(*) = 1 AS existing_admin_found,
       coalesce(bool_and(role = 'ADMIN' AND status = 'ACTIVE'), false) AS active_admin,
       coalesce(bool_and(length(email) - length(replace(email, '@', '')) = 2), false) AS malformed_email_confirmed
FROM duriancare_auth.users WHERE id = :'admin_id'::uuid;
SELECT (length(:'replacement_email') - length(replace(:'replacement_email', '@', '')) = 1
        AND :'replacement_email' = lower(trim(:'replacement_email'))
        AND :'replacement_email' ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$') AS replacement_format_candidate,
       length(trim(:'approval_ref')) > 0 AS approval_reference_supplied,
       NOT EXISTS (SELECT 1 FROM duriancare_auth.users
                   WHERE lower(email) = lower(:'replacement_email')) AS no_email_conflict,
       (SELECT count(*) FROM duriancare_auth.users WHERE role = 'ADMIN' AND status = 'ACTIVE'
        AND id <> :'admin_id'::uuid) AS other_active_admins;
ROLLBACK;
-- Boolean checks are not proof of approval, replacement-address ownership or backup-admin usability.
-- Backend Jakarta email validation remains authoritative; this preflight never performs a repair.
