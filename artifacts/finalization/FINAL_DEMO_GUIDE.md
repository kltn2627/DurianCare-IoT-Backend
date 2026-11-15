# DurianCare-IoT Final Demo Guide

Date: 2026-10-11

## Demo-Safe Narrative

Use this positioning:

> DurianCare-IoT supports farm/tree disease monitoring, diagnosis review, treatment planning, notifications, and AI-assisted disease analysis. Severity training is prepared with a guarded annotation pipeline and is intentionally blocked until enough verified labels exist.

## Pre-Demo Checklist

1. Start infrastructure and application services without resetting volumes.
2. Verify gateway health and authentication.
3. Open the web client and log in with a valid demo account.
4. Show farm, zone, tree detail, diagnosis history, treatment plan, notification, and export flows.
5. Run the mobile app or Expo web export against the same backend.
6. Demonstrate AI disease analysis with the frozen model only.
7. For severity AI, show the annotation guide, validation summary, and `DO_NOT_TRAIN` gate as responsible ML governance.

## Useful Commands

```powershell
docker compose -f infrastructure\docker-compose.yml ps
mvn test
```

```powershell
cd ..\DurianCare-IoT-Web-Client
npm run lint
npm run typecheck
npm run build
npm test -- --runInBand
```

```powershell
cd ..\DurianCare-IoT-Mobile-App
npm run typecheck
npx expo export --platform web
```

```powershell
cd ..\DurianCare-IoT-Backend
python scripts\severity_training\training_gate.py --dataset-root "..\DataSet\Durian_Severity_Training" --artifacts-root ".\artifacts\severity_training"
```
