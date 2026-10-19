# Notification Inbox

Authenticated inbox APIs are owned by `duriancare-notification-service`.

## Routes

The service accepts these compatible bases:

- `/api/v1/notification/notifications`
- `/api/notifications`
- `/api/notifications/notifications`

The Web route `src/app/api/notifications/[[...path]]/route.ts` normalizes both `/api/notifications/...` and the older `/api/notifications/notifications/...` shape to the canonical Gateway path `/api/v1/notification/notifications/...`.

## Ownership

The client never sends a recipient ID for inbox mutations. The current user is resolved from the authenticated session/JWT and forwarded as `X-Auth-User-Id` by the trusted Web/Gateway boundary.

- `PATCH /{id}/read` updates only when the notification belongs to the current user.
- `PATCH /read-all` reads unread notifications by current `receiverId` and updates only those records.
- Missing identity returns `401`.
- A notification not owned by the current user is not returned by the scoped repository query.

This preserves the ownership rule while keeping unread counts synchronized by refreshing inbox data after successful mutations.
# Master Acceptance Addendum - 2026-10-15

Auth-event malformed input logs no longer include raw event payloads or provider exception details.
OTP delivery failures propagate a sanitized exception to Kafka error handling instead of being swallowed.
Notification module: 31 tests passed; log-redaction and failure-propagation regressions reproduced the
old behavior before correction. This does not establish exactly-once delivery or live SMTP failover.
The deployed notification container returned health 200/UP after the final rebuild. Gateway OTP
generate/validate aliases now use the existing resend/verification rate-limit policies.

