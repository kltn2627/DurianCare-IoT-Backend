# DurianCare AI Runtime Final Status

Date: 2026-07-11

This document records the final stabilization state for the AI image diagnosis workflow and Gateway upload path.

## Verified at runtime

- AI service starts successfully in Docker.
- AI health endpoint returns `UP`.
- Disease prediction endpoint returns HTTP `200`.
- Knowledge base recommendations are returned in the response.
- Conversation memory follow-up now keeps disease context.
- Conversation memory is isolated per user.
- Redis-backed RAG memory is active.
- API Gateway forwards multipart upload requests to the AI service.
- This verifies the backend upload path, not the browser-side FE flow.

## Verified evidence

- Gateway upload request to `http://localhost:8080/api/v1/predict` returned HTTP `200`.
- Response included:
  - `predicted_disease = HEALTHY_LEAF`
  - `confidence = 50.35%`
  - recommendation payload
- Follow-up chat query after `Anthracnose` stayed grounded in the same disease context.

## Not verified in this pass

- Browser-driven frontend upload flow
- Stress testing
- Failover drills
- Full observability review

## Final conclusion

The AI image upload workflow through Gateway is runtime-functional on the backend side. The browser-driven FE upload flow and the remaining stabilization checks were not executed in this verification pass.
