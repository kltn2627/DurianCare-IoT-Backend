# DurianCare Notification Service

OTP values are hashed with BCrypt and stored in Redis under
`duriancare:notification:otp:<email>`. The Redis TTL, OTP length, retry limit
and Brevo SMTP connection are supplied exclusively from the repository root
`.env` through Docker Compose.
