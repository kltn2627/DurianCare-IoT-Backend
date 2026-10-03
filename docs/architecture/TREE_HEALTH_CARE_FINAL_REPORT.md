# Tree Health Care — Final Architecture Report

**Date:** 2026-10-03  
**Branch:** duy/cleanup-fixes  
**Scope:** Full tree health care flow — Backend, Web Client, Mobile App

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Mobile App (Expo RN)         Web Client (Next.js)              │
│  ZoneTreesScreen              ZoneTreesWorkspace                 │
│  TreeDetailScreen             TreeDetailPanel                    │
│    ├── AIPanel                  ├── AIPanel                      │
│    ├── RecoveryPanel            ├── RecoveryPanel                │
│    └── CarePlanSection          └── CarePlanSection              │
└────────────────┬───────────────────────┬────────────────────────┘
                 │  HTTP (JWT Bearer)     │
                 ▼                        ▼
┌─────────────────────────────────────────────────────────────────┐
│  farm-service (Spring Boot, port 8082)                          │
│  ├── TreeController                                              │
│  ├── TreeService                                                 │
│  ├── TreeDiagnosisService                                        │
│  └── TreeCarePlanService ──► Kafka ──► notification-service     │
│                                         (port 8085)             │
│  MongoDB: duriancare_farm                                        │
│  ├── durian_trees                                                │
│  ├── tree_diagnosis_records                                      │
│  ├── tree_care_plans                                             │
│  └── notifications (via notification-service)                   │
└─────────────────────────────────────────────────────────────────┘
                 │  JPQL (JPA)
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│  auth-service (PostgreSQL)                                      │
│  └── knowledge_articles                                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Tree Health State Machine

```
HEALTHY ──(AI diagnoses disease)──► DISEASED
                                        │
                               farmer sets TREATING
                                        │
                                        ▼
DISEASED ◄──────────────────────── TREATING
                                        │
                           evaluateRecovery() shows
                           RECOVERED or IMPROVED
                                        │
                           farmer confirms with notes
                                        │
                                        ▼
                                   RECOVERED
                                        │
                            (new AI diagnosis resets)
                                        │
                                        ▼
                               HEALTHY / DISEASED
```

**Allowed manual transitions** (enforced in `TreeService.transitionHealthStatus()`):
- Any state → `TREATING`
- Any state → `RECOVERED`

All other status changes happen automatically via `TreeDiagnosisService.inferHealthStatus()`.

---

## 3. API Contract

### Diagnosis

| Method | URL | Description |
|--------|-----|-------------|
| `POST` | `/api/trees/{treeId}/diagnoses` | Save AI diagnosis result |
| `GET` | `/api/trees/{treeId}/diagnoses` | List diagnosis history (paginated) |
| `GET` | `/api/trees/{treeId}/diagnoses/latest` | Get most recent diagnosis |

**Save diagnosis body:**
```json
{
  "imageUrl": "string (required)",
  "diseaseCode": "string (required)",
  "diseaseName": "string (optional)",
  "confidence": 0.0-1.0 (stored as decimal, NOT percent),
  "boundingBox": { "left": %, "top": %, "width": %, "height": % },
  "source": "AI_MODEL | RECOVERY_VERIFICATION"
}
```

### Health Status

| Method | URL | Description |
|--------|-----|-------------|
| `PATCH` | `/api/trees/{treeId}/health-status` | Set TREATING or RECOVERED |
| `POST` | `/api/trees/{treeId}/evaluate-recovery` | Get recovery evaluation before confirming |

**Recovery evaluation response:**
```json
{
  "treeId": "string",
  "outcome": "RECOVERED | IMPROVED | STABLE | WORSENED | UNCERTAIN",
  "previousDiseaseCode": "string",
  "previousConfidence": 0.0-1.0,
  "currentDiseaseCode": "string",
  "currentConfidence": 0.0-1.0,
  "reason": "human-readable explanation",
  "evaluatedAt": "ISO-8601"
}
```

**Eligible for confirmation:** `outcome == RECOVERED || outcome == IMPROVED`

### Care Plans

| Method | URL | Description |
|--------|-----|-------------|
| `POST` | `/api/trees/{treeId}/care-plans` | Create care plan (triggers follow-up notification if followUpDate set) |
| `GET` | `/api/trees/{treeId}/care-plans` | List care plans for tree |
| `PATCH` | `/api/care-plans/{planId}/status` | Update plan status |

---

## 4. Database Schema (MongoDB)

### `durian_trees`
```
_id, treeCode, farmId, farmZoneId, healthStatus (enum),
positionX, positionY, variety, plantedDate, diagnosisCount,
latestDiagnosisAt, latestDiseaseCode, latestConfidence, status
```
Index: `treeCode` (unique)

### `tree_diagnosis_records`
```
_id, treeId, treeCode, imageUrl, diseaseCode, diseaseName,
confidence (0.0-1.0), boundingBox, source, impliedHealthStatus,
diagnosedAt, createdAt
```
Index: `{treeId: 1, diagnosedAt: -1}` compound

### `tree_care_plans`
```
_id, treeId, farmId, diagnosisId, diseaseCode, knowledgeArticleId,
treatment, startDate, followUpDate, status (enum), createdByUserId,
createdAt, updatedAt
```

### `notifications` (notification-service)
```
_id, receiverId, title, message, type (CARE), isRead,
metadata: {treeId, treeCode, diseaseCode, planId},
sourceEventId, createdAt
```

---

## 5. Web/Mobile Parity Table

| Feature | Web | Mobile |
|---------|-----|--------|
| Tree Map with health colors | ✅ All 5 states | ✅ All 5 states (RECOVERED fixed) |
| Tree detail with health badge | ✅ | ✅ |
| AI Panel (camera → predict → save) | ✅ | ✅ |
| Confidence scale 0–1 | ✅ `/ 100` | ✅ `/ 100` |
| KB article search by disease code | ✅ | ✅ |
| Care Plan section (DISEASED/TREATING/SUSPECTED) | ✅ | ✅ |
| Follow-up date input on care plan | ✅ | ✅ |
| RecoveryPanel (TREATING only) | ✅ | ✅ |
| evaluateRecovery gate | ✅ (fixed) | ✅ (fixed) |
| "Chưa đủ điều kiện" block message | ✅ | ✅ |
| Diagnosis history timeline | ✅ | ✅ |
| Tree Map refresh from DB after save | ✅ | ✅ |
| Treatment panel (DISEASED/SUSPECTED → TREATING) | ✅ | ✅ |

---

## 6. Notification Flow

```
Farmer creates care plan with followUpDate
    │
    ▼
TreeCarePlanService.createCarePlan()
    │
    ├─ saves TreeCarePlan to MongoDB
    │
    └─ publishes FarmNotificationEvent {
           eventType: "CARE_FOLLOWUP",
           notificationType: "CARE",
           receiverId: farmerUserId,
           title: "Lịch tái khám cây {treeCode}",
           message: "Nhớ tái khám vào {followUpDate}",
           metadata: { treeId, treeCode, diseaseCode, planId }
       }
           │
           ▼ Kafka: duriancare.notification.events
           │
           ▼
notification-service NotificationEventListener
    │
    └─ persists Notification { type: CARE } to MongoDB
```

---

## 7. Changes Made This Session

| Commit | Repo | Description |
|--------|------|-------------|
| `cde1765` | Mobile | RECOVERED state on tree map + RecoveryPanel Phase 8 gate |
| `162f009` | Web | RecoveryPanel Phase 8 gate + RecoveryEvaluationResponse type |
| (earlier) | Backend | All care plan + recovery + notification features |
| (earlier) | Web | CarePlanSection + types + client methods |
| (earlier) | Mobile | CarePlanSection + needsRecovery fix |

---

## 8. Remaining Gaps and Manual Steps

### Gaps Not Addressed (Out of Scope)

1. **Mobile imageUrl** — `photo.uri` from expo-image-picker is a device-local URI. Backend stores it but it is not accessible remotely. Production requires S3/CDN upload before `saveDiagnosis()`.

2. **Care Schedule / Cultivation sync** — No link from `TreeCarePlan` to any scheduling or cultivation system. Would require new domain model.

3. **KB article tagging** — `KnowledgeArticleRepository.searchByStatus()` searches title/excerpt/tags. Disease codes (e.g. `Algal_Leaf_Spot`) must appear in article tags for search to return results.

### Phases Not Verified at Runtime (Phases 12–16)

Runtime verification requires live services (Docker Compose up, farmer JWT, real device).

**Manual steps to verify end-to-end:**

1. Start services: `docker compose up -d` in `DurianCare-IoT-Backend/infrastructure/`
2. Obtain farmer JWT for `minhdii1510@gmail.com` via `POST /api/v1/auth/login`
3. Find test trees in zone for `farmId: farm-khoa-luan-2026`
4. Pick a DISEASED tree:
   - Take photo in mobile app → AI panel → predict → save diagnosis
   - Verify confidence stored as 0–1 in MongoDB
   - Set health to TREATING via TreatmentPanel
   - Create care plan with follow-up date → verify `notifications` collection has new CARE notification
5. Pick a TREATING tree (or continue from step 4):
   - Save new "healthy" diagnosis (Healthy_Leaf)
   - Open RecoveryPanel → verify evaluateRecovery called → outcome shown
   - If RECOVERED/IMPROVED: enter notes → confirm → verify tree healthStatus = RECOVERED in DB
   - Verify tree map updates to cyan (#0891b2) on both web and mobile
6. Verify diagnosis history: all records preserved, no overwrites

---

## 9. Absolute Constraints Status

| Constraint | Status |
|------------|--------|
| Không reset database | ✅ Not done |
| Không xóa dữ liệu thật | ✅ Not done |
| Không tạo account mới | ✅ Not done |
| Không sửa password | ✅ Not done |
| Không hard-code email | ✅ None in business logic |
| Không hard-code tree ID | ✅ None |
| Không hard-code farm ID | ✅ None |
| Không tạo mock API | ✅ None |
| Không tạo fake frontend data | ✅ None |
| Không tạo demo-only route | ✅ None |
| Không bypass authorization | ✅ All endpoints require JWT |
| Không phá API hiện tại | ✅ Only additive changes |
| Web và Mobile cùng backend | ✅ Same `/api/*` endpoints |
| Nếu sửa API contract kiểm tra Web + Mobile | ✅ evaluateRecovery added to both |
| Không tuyên bố AI tốt hơn nếu chưa benchmark | ✅ Not claimed |
| Không tải dữ liệu internet để train | ✅ Not done |
| Không dùng confidence đơn độc để quyết định khỏi bệnh | ✅ evaluateRecovery compares two diagnoses |
| Health status và diagnosis history riêng biệt | ✅ Separate collection/field |
| Mọi thay đổi Tree Map phản ánh từ DB | ✅ listTrees() re-fetches from backend |
| KHÔNG train Pest Detection | ✅ Not done |
