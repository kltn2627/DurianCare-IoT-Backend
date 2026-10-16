# Cultivation Calendar Domain Model

The cultivation calendar module lives in `duriancare-cultivation-service` and owns MongoDB documents in the
`duriancare_cultivation` database. Farm, plot, tree, cultivation season and user data are referenced by external IDs.

## Core Planning

- `CultivationPlanTemplate`: reusable template with code, growth stage and planned template activities.
- `CultivationPlan`: season-specific plan linked by `farmId`, `plotId` and `cultivationSeasonId`.
- `CultivationActivity`: scheduled work item with activity type, schedule, assignees, approval status and optimistic version.
- `ActivityExecution`: immutable actual execution record. Planned activity data is not used as a substitute for actual data.

## Inputs And Residue Control

- `AgriculturalInput`: master catalog for fertilizers, biological controls, pesticides and other inputs.
- `ActivityInputUsage`: actual input usage attached to an execution. It stores product and active-ingredient snapshots.
- `ResidueStandard`: MRL standard by market, commodity and active ingredient. MRL values are data, not source-code constants.
- `LabSample` and `LabResidueResult`: residue testing workflow and measured values.

## Harvest And Export

- `HarvestBatch`: harvest lot with quantity, destination market, safe harvest date and chemical risk.
- `ComplianceAssessment`: saved risk assessment with blocking reasons and warnings.
- `ExportRelease`: export review/release record with immutable traceability and compliance snapshots.
- `AuditLog`: append-only operational audit record for approvals, overrides, lab results, harvest and export release changes.

## Existing Compatibility

The existing `CultivationSchedule` API and collection remain unchanged for backward compatibility.
