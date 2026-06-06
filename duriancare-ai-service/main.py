import os
from contextlib import asynccontextmanager
from pathlib import Path
from tempfile import NamedTemporaryFile

from fastapi import FastAPI, File, HTTPException, UploadFile
from ultralytics import YOLO

MODEL_PATH = Path(os.getenv("YOLO_MODEL_PATH", "models/durian-disease.pt"))
model: YOLO | None = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global model
    if MODEL_PATH.exists():
        model = YOLO(str(MODEL_PATH))
    yield
    model = None


app = FastAPI(
    title="DurianCare AI Service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/actuator/health")
def health() -> dict[str, str | bool]:
    return {
        "status": "UP",
        "service": "duriancare-ai-service",
        "modelLoaded": model is not None,
    }


@app.post("/api/ai/diagnoses")
async def diagnose(image: UploadFile = File(...)) -> dict:
    if model is None:
        raise HTTPException(status_code=503, detail="YOLO model is not available")
    if not image.content_type or not image.content_type.startswith("image/"):
        raise HTTPException(status_code=415, detail="An image file is required")

    suffix = Path(image.filename or "upload.jpg").suffix
    with NamedTemporaryFile(suffix=suffix, delete=False) as temporary_file:
        temporary_path = Path(temporary_file.name)
        temporary_file.write(await image.read())

    try:
        result = model.predict(source=str(temporary_path), verbose=False)[0]
        detections = []
        for box in result.boxes:
            class_id = int(box.cls.item())
            detections.append(
                {
                    "classId": class_id,
                    "label": result.names[class_id],
                    "confidence": round(float(box.conf.item()), 6),
                    "boundingBox": [round(float(value), 2) for value in box.xyxy[0].tolist()],
                }
            )
        return {"detections": detections}
    finally:
        temporary_path.unlink(missing_ok=True)
