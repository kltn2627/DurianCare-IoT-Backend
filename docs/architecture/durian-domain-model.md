# DurianCare Domain Model

## Architecture Rules

- Each business service owns a separate MongoDB database.
- IoT telemetry owns the PostgreSQL `duriancare_iot` schema.
- References to another service are stored as string identifiers, not database foreign keys.
- Cross-service state is synchronized through Kafka events or queried through service APIs.
- MongoDB documents embed owned value objects and store cross-service references as IDs.

## 1. User And Auth Service

```text
User
  id: String
  email: String
  passwordHash: String
  role: UserRole
  status: UserStatus
  emailVerified: Boolean
  lastLoginAt: LocalDateTime?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

Profile
  id: UUID
  userId: UUID
  fullName: String
  phoneNumber: String?
  avatarUrl: String?
  address: String?
  province: String?
  agriculturalLicenseNumber: String?
  biography: String?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

UserPreference
  id: UUID
  userId: UUID
  language: String
  timezone: String
  pushNotificationEnabled: Boolean
  emailNotificationEnabled: Boolean
  diseaseAlertEnabled: Boolean
  treatmentReminderEnabled: Boolean
  updatedAt: LocalDateTime

UserConnection
  id: UUID
  requesterUserId: UUID
  recipientUserId: UUID
  type: ConnectionType
  status: ConnectionStatus
  requestedAt: LocalDateTime
  respondedAt: LocalDateTime?
```

Enums:

```text
UserRole = FARMER | ENGINEER | CUSTOMER
UserStatus = PENDING | ACTIVE | SUSPENDED | DEACTIVATED
ConnectionType = PROFESSIONAL | COMMUNITY
ConnectionStatus = PENDING | ACCEPTED | REJECTED | BLOCKED
```

Relationships: `User 1-1 Profile`, `User 1-1 UserPreference`; `UserConnection`
links two users inside the auth boundary.

## 2. Farm And Crop Service

```text
Farm
  id: UUID
  ownerUserId: UUID
  name: String
  address: String?
  province: String?
  district: String?
  latitude: BigDecimal?
  longitude: BigDecimal?
  areaHectares: BigDecimal?
  status: FarmStatus
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

FarmZone
  id: UUID
  farmId: UUID
  name: String
  code: String
  areaSquareMeters: BigDecimal?
  boundaryGeoJson: String?
  description: String?
  status: ZoneStatus
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

Species
  id: UUID
  commonName: String
  scientificName: String?
  cultivar: String
  expectedYieldKg: BigDecimal?
  growthDurationDays: Integer?
  description: String?

DurianTree
  id: UUID
  farmZoneId: UUID
  speciesId: UUID
  treeCode: String
  plantedDate: LocalDate?
  latitude: BigDecimal?
  longitude: BigDecimal?
  healthStatus: TreeHealthStatus
  status: TreeStatus
  notes: String?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

CropSeason
  id: UUID
  farmId: UUID
  name: String
  startDate: LocalDate
  expectedHarvestDate: LocalDate?
  actualHarvestDate: LocalDate?
  status: CropSeasonStatus
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

FarmAuthorization
  id: UUID
  farmId: UUID
  farmZoneId: UUID?
  ownerUserId: UUID
  engineerUserId: UUID
  initiatedByUserId: UUID
  invitationType: AuthorizationInvitationType
  status: AuthorizationStatus
  validFrom: LocalDateTime?
  validUntil: LocalDateTime?
  approvedAt: LocalDateTime?
  revokedAt: LocalDateTime?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

FarmPermission
  id: UUID
  authorizationId: UUID
  permission: FarmPermissionType
  granted: Boolean
```

Enums:

```text
FarmStatus = ACTIVE | INACTIVE | ARCHIVED
ZoneStatus = ACTIVE | INACTIVE | QUARANTINED | ARCHIVED
TreeStatus = ACTIVE | REMOVED | DEAD
TreeHealthStatus = HEALTHY | SUSPECTED | DISEASED | TREATING | RECOVERED
CropSeasonStatus = PLANNED | ACTIVE | HARVESTED | CLOSED
AuthorizationInvitationType = OWNER_INVITES_ENGINEER | ENGINEER_REQUESTS_ACCESS
AuthorizationStatus = PENDING | APPROVED | REJECTED | ACTIVE | REVOKED | EXPIRED
FarmPermissionType = READ_IOT | READ_DISEASE | CREATE_PROTOCOL | UPDATE_PROTOCOL | CONFIGURE_DEVICE
```

Relationships: `Farm 1-N FarmZone`, `FarmZone 1-N DurianTree`,
`Species 1-N DurianTree`, `Farm 1-N CropSeason`,
`FarmAuthorization 1-N FarmPermission`.

## 3. AI Disease Analytics Service

```text
DiseaseCatalog
  id: UUID
  code: String
  vietnameseName: String
  scientificName: String?
  description: String?
  recommendedAction: String?
  severity: DiseaseSeverity
  active: Boolean

DiseaseAnalysis
  id: UUID
  requestedByUserId: UUID
  farmId: UUID
  farmZoneId: UUID
  durianTreeId: UUID?
  imageUrl: String
  modelName: String
  modelVersion: String
  status: AnalysisStatus
  requestedAt: LocalDateTime
  completedAt: LocalDateTime?
  failureReason: String?

DiseaseDetection
  id: UUID
  analysisId: UUID
  diseaseCatalogId: UUID
  confidence: BigDecimal
  boundingBoxX1: BigDecimal?
  boundingBoxY1: BigDecimal?
  boundingBoxX2: BigDecimal?
  boundingBoxY2: BigDecimal?
  severity: DiseaseSeverity

DiseaseRecord
  id: UUID
  analysisId: UUID
  farmId: UUID
  farmZoneId: UUID
  durianTreeId: UUID?
  detectedDiseaseCode: String
  imageUrl: String
  confidence: BigDecimal
  severity: DiseaseSeverity
  detectedAt: LocalDateTime
  reviewedByEngineerId: UUID?
  reviewStatus: ReviewStatus
  engineerNote: String?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
```

Enums:

```text
AnalysisStatus = PENDING | PROCESSING | COMPLETED | FAILED
DiseaseSeverity = LOW | MEDIUM | HIGH | CRITICAL
ReviewStatus = UNREVIEWED | CONFIRMED | CORRECTED | REJECTED
```

Relationships: `DiseaseAnalysis 1-N DiseaseDetection`; one completed analysis may
produce one canonical `DiseaseRecord`. Publish `DiseaseDetected` after recording.

## 4. Treatment Protocol And Traceability Service

```text
TreatmentProtocol
  id: UUID
  farmId: UUID
  farmZoneId: UUID
  cropSeasonId: UUID
  diseaseRecordId: UUID?
  createdByEngineerId: UUID
  approvedByOwnerId: UUID?
  name: String
  objective: String
  status: ProtocolStatus
  startDate: LocalDate
  endDate: LocalDate?
  approvedAt: LocalDateTime?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

TreatmentStep
  id: UUID
  protocolId: UUID
  dayNumber: Integer
  scheduledDate: LocalDate
  actionType: TreatmentActionType
  title: String
  instructions: String
  productName: String?
  dosage: String?
  safetyIntervalDays: Integer?
  sortOrder: Integer

TreatmentExecution
  id: UUID
  treatmentStepId: UUID
  performedByUserId: UUID
  status: ExecutionStatus
  performedAt: LocalDateTime?
  actualDosage: String?
  evidenceImageUrl: String?
  notes: String?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

TraceabilityProfile
  id: UUID
  farmId: UUID
  cropSeasonId: UUID
  publicSlug: String
  title: String
  status: TraceabilityStatus
  publishedAt: LocalDateTime?
  lastAggregatedAt: LocalDateTime?
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

TraceabilitySnapshot
  id: UUID
  traceabilityProfileId: UUID
  version: Integer
  farmSnapshot: Json
  environmentSummary: Json
  diseaseHistory: Json
  treatmentHistory: Json
  harvestSummary: Json?
  generatedAt: LocalDateTime

QRTraceability
  id: UUID
  traceabilityProfileId: UUID
  snapshotId: UUID
  publicUrl: String
  qrImageUrl: String
  tokenHash: String
  expiresAt: LocalDateTime?
  active: Boolean
  generatedAt: LocalDateTime
```

Enums:

```text
ProtocolStatus = DRAFT | PENDING_APPROVAL | APPROVED | ACTIVE | COMPLETED | CANCELLED
TreatmentActionType = SPRAY | FERTILIZE | WATER | PRUNE | QUARANTINE | INSPECT | OTHER
ExecutionStatus = PENDING | COMPLETED | SKIPPED | FAILED
TraceabilityStatus = DRAFT | PUBLISHED | ARCHIVED
```

Relationships: `TreatmentProtocol 1-N TreatmentStep`,
`TreatmentStep 1-N TreatmentExecution`, `TraceabilityProfile 1-N Snapshot`,
and each QR points to an immutable published snapshot.

## 5. IoT Gateway And Telemetry Service

```text
IoTDevice
Telemetry
  id: UUID
  deviceId: String
  temperature: BigDecimal?
  humidity: BigDecimal?
  light: BigDecimal?
  timestamp: LocalDateTime
  receivedAt: LocalDateTime
```

Enums:

```text
Telemetry fields are nullable individually, but each record must contain at
least one measurement.
```

Telemetry is indexed by `deviceId` and `timestamp`.

## 6. Chat And Notification Service

```text
Conversation
  id: UUID
  farmId: UUID?
  farmZoneId: UUID?
  type: ConversationType
  title: String?
  createdByUserId: UUID
  active: Boolean
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

ConversationMember
  id: UUID
  conversationId: UUID
  userId: UUID
  role: ConversationMemberRole
  joinedAt: LocalDateTime
  leftAt: LocalDateTime?
  muted: Boolean

Message
  id: UUID
  conversationId: UUID
  senderUserId: UUID?
  type: MessageType
  content: String
  metadata: Json?
  replyToMessageId: UUID?
  sentAt: LocalDateTime
  editedAt: LocalDateTime?
  deletedAt: LocalDateTime?

PinnedMessage
  id: UUID
  conversationId: UUID
  messageId: UUID
  pinnedByUserId: UUID
  pinnedAt: LocalDateTime

Notification
  id: UUID
  recipientUserId: UUID
  type: NotificationType
  title: String
  body: String
  referenceType: String?
  referenceId: UUID?
  channel: NotificationChannel
  status: NotificationStatus
  scheduledAt: LocalDateTime?
  sentAt: LocalDateTime?
  readAt: LocalDateTime?
  createdAt: LocalDateTime

ReminderJob
  id: UUID
  treatmentStepId: UUID
  recipientUserId: UUID
  scheduledFor: LocalDateTime
  status: ReminderStatus
  sentAt: LocalDateTime?
```

Enums:

```text
ConversationType = DIRECT | FARM_SUPPORT | ZONE_SUPPORT
ConversationMemberRole = OWNER | ENGINEER | MEMBER | BOT
MessageType = TEXT | IMAGE | FILE | SYSTEM | DISEASE_ALERT | TREATMENT_REMINDER
NotificationType = DISEASE_ALERT | TREATMENT_REMINDER | AUTHORIZATION | CHAT
NotificationChannel = IN_APP | PUSH | EMAIL
NotificationStatus = PENDING | SENT | DELIVERED | READ | FAILED
ReminderStatus = SCHEDULED | SENT | CANCELLED | FAILED
```

Relationships: `Conversation 1-N ConversationMember`, `Conversation 1-N Message`,
`Conversation 1-N PinnedMessage`. The service consumes `DiseaseDetected` and
`TreatmentStepScheduled` events to create bot messages and notifications.

## 7. Community Service

```text
Post
  id: UUID
  authorUserId: UUID
  farmId: UUID?
  diseaseRecordId: UUID?
  title: String
  content: String
  visibility: PostVisibility
  status: ContentStatus
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

PostAttachment
  id: UUID
  postId: UUID
  type: AttachmentType
  url: String
  thumbnailUrl: String?
  fileName: String?
  sortOrder: Integer

Comment
  id: UUID
  postId: UUID
  authorUserId: UUID
  parentCommentId: UUID?
  content: String
  status: ContentStatus
  createdAt: LocalDateTime
  updatedAt: LocalDateTime

CommunityGroup
  id: UUID
  name: String
  description: String?
  ownerUserId: UUID
  visibility: GroupVisibility
  createdAt: LocalDateTime

GroupSettings
  id: UUID
  groupId: UUID
  allowMemberPosts: Boolean
  requirePostApproval: Boolean
  allowComments: Boolean
  updatedAt: LocalDateTime
```

Enums:

```text
PostVisibility = PUBLIC | COMMUNITY | GROUP
ContentStatus = ACTIVE | HIDDEN | DELETED
AttachmentType = IMAGE | VIDEO | DOCUMENT
GroupVisibility = PUBLIC | PRIVATE
```

Relationships: `Post 1-N PostAttachment`, `Post 1-N Comment`,
`Comment 1-N child Comment`, `CommunityGroup 1-1 GroupSettings`.

## Integration Events

```text
UserRegistered
FarmAuthorizationActivated
FarmAuthorizationRevoked
DeviceTelemetryReceived
DiseaseDetected
DiseaseRecordReviewed
TreatmentProtocolApproved
TreatmentStepScheduled
TreatmentStepCompleted
TraceabilitySnapshotPublished
```

Every event should contain `eventId`, `eventType`, `aggregateId`,
`occurredAt`, `schemaVersion`, `correlationId`, and its domain payload.
