from __future__ import annotations

from argparse import ArgumentParser
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse
import csv
from datetime import datetime, timezone
import json
import mimetypes
import threading


CANDIDATE_FIELDS = [
    "image_id",
    "path",
    "disease_code",
    "severity_label",
    "severity_score",
    "annotation_status",
    "review_status",
    "annotator",
    "notes",
    "source_dataset",
    "source_split",
    "sha256",
    "tree_group_id",
    "session_id",
]

ANNOTATION_FIELDS = [
    "image_id",
    "image_path",
    "disease",
    "annotation_type",
    "annotation_source",
    "annotator",
    "review_status",
    "severity_label",
    "severity_score",
    "affected_area_ratio",
    "source_dataset",
    "tree_group_id",
    "session_id",
    "notes",
    "updated_at_utc",
]

ALLOWED_LABELS = {"NONE", "MILD", "MODERATE", "SEVERE", "UNCERTAIN", ""}
ALLOWED_REVIEW = {"PENDING", "NEEDS_REVIEW", "NEEDS_EXPERT_REVIEW", "UNCERTAIN", "REJECTED", "APPROVED"}


HTML = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>DurianCare Severity Annotation</title>
<style>
body{margin:0;font-family:Arial,sans-serif;background:#f6f7f9;color:#17202a}
header{height:52px;display:flex;align-items:center;gap:12px;padding:0 16px;background:#1f4f3a;color:white}
select,input,textarea,button{font:inherit}
button{border:1px solid #b8c1cc;background:white;border-radius:6px;padding:8px 10px;cursor:pointer}
button.primary{background:#1f7a4d;color:white;border-color:#1f7a4d}
button.danger{background:#a33;color:white;border-color:#a33}
main{display:grid;grid-template-columns:minmax(320px,1fr) 390px;gap:16px;padding:16px;min-height:calc(100vh - 84px)}
.viewer{display:flex;align-items:center;justify-content:center;background:#111;border-radius:8px;overflow:hidden;min-height:540px}
.viewer img{max-width:100%;max-height:calc(100vh - 120px);object-fit:contain}
.panel{background:white;border:1px solid #d7dde5;border-radius:8px;padding:14px;display:flex;flex-direction:column;gap:12px}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.meta{font-size:13px;color:#52606d;line-height:1.45;word-break:break-word}
.status{padding:8px;background:#eef4f0;border-radius:6px}
textarea{min-height:80px;resize:vertical}
footer{padding:8px 16px;color:#52606d;font-size:12px}
@media(max-width:900px){main{grid-template-columns:1fr}.viewer{min-height:360px}}
</style>
</head>
<body>
<header>
  <strong>DurianCare Severity Annotation</strong>
  <span id="progress"></span>
</header>
<main>
  <section class="viewer"><img id="image" alt="candidate"></section>
  <aside class="panel">
    <div class="row">
      <label>Disease <select id="disease"></select></label>
      <label>Source <select id="source"></select></label>
      <label>Status <select id="status">
        <option value="">All</option><option>PENDING</option><option>NEEDS_REVIEW</option>
        <option>NEEDS_EXPERT_REVIEW</option><option>UNCERTAIN</option><option>REJECTED</option><option>APPROVED</option>
      </select></label>
      <label>Search <input id="search" placeholder="filename or id"></label>
      <button id="apply">Apply</button>
    </div>
    <div class="status" id="summary"></div>
    <div class="meta" id="meta"></div>
    <div class="grid">
      <button data-label="NONE">1 None</button>
      <button data-label="MILD">2 Mild</button>
      <button data-label="MODERATE">3 Moderate</button>
      <button data-label="SEVERE">4 Severe</button>
      <button data-label="UNCERTAIN">U Uncertain</button>
      <button class="danger" data-reject="1">R Reject</button>
      <button id="approve">A Approve</button>
    </div>
    <label>Severity score 0..1 <input id="score" type="number" min="0" max="1" step="0.01"></label>
    <label>Affected area ratio 0..1 <input id="ratio" type="number" min="0" max="1" step="0.01"></label>
    <label>Annotator <input id="annotator" placeholder="name or reviewer id"></label>
    <label>Notes <textarea id="notes"></textarea></label>
    <div class="row">
      <button id="prev">Previous</button>
      <button class="primary" id="save">Save</button>
      <button id="next">Next</button>
    </div>
    <div class="meta">
      Shortcuts: 1/2/3/4 label, U uncertain, R reject, S save, N next, P previous.
      APPROVED is disabled unless server starts with --enable-approve.
    </div>
  </aside>
</main>
<footer>Provisional labels are not training ground truth until expert-approved.</footer>
<script>
let index=0,state=null,label="",reject=false;
async function load(){
  const disease=document.getElementById('disease').value;
  const source=document.getElementById('source').value;
  const status=document.getElementById('status').value;
  const search=document.getElementById('search').value;
  const res=await fetch(`/api/state?index=${index}&disease=${encodeURIComponent(disease)}&source=${encodeURIComponent(source)}&status=${encodeURIComponent(status)}&search=${encodeURIComponent(search)}`);
  state=await res.json(); index=state.index;
  const diseaseSelect=document.getElementById('disease');
  if(!diseaseSelect.dataset.ready){
    diseaseSelect.innerHTML='<option value="">All</option>'+state.diseases.map(d=>`<option>${d}</option>`).join('');
    diseaseSelect.dataset.ready='1';
  }
  const sourceSelect=document.getElementById('source');
  if(!sourceSelect.dataset.ready){
    sourceSelect.innerHTML='<option value="">All</option>'+state.sources.map(s=>`<option>${s}</option>`).join('');
    sourceSelect.dataset.ready='1';
  }
  document.getElementById('progress').textContent=`${state.total?index+1:0}/${state.total}`;
  document.getElementById('summary').textContent=`${state.progress.reviewed}/${state.progress.total} reviewed (${state.progress.percent}%). Remaining: ${state.progress.remaining}. Approved: ${state.progress.approved}. Uncertain: ${state.progress.uncertain}. Rejected: ${state.progress.rejected}.`;
  if(!state.candidate){ document.getElementById('meta').textContent='No candidate for current filter.'; document.getElementById('image').src=''; return; }
  const c=state.candidate;
  document.getElementById('image').src=`/image?path=${encodeURIComponent(c.path)}`;
  const duplicate = c.near_duplicate_group ? `<br><b>Near-duplicate group:</b> ${c.near_duplicate_group}` : '';
  document.getElementById('meta').innerHTML=`<b>${c.image_id}</b><br>${c.disease_code}<br>${c.source_dataset||''} ${c.source_split||''}<br>${c.path}<br>Status: ${c.review_status||c.annotation_status}${duplicate}`;
  label=c.severity_label||""; reject=false;
  document.getElementById('score').value=c.severity_score||'';
  document.getElementById('ratio').value=c.affected_area_ratio||'';
  document.getElementById('annotator').value=c.annotator||localStorage.annotator||'';
  document.getElementById('notes').value=c.notes||'';
}
async function save(){
  if(!state||!state.candidate)return;
  const annotator=document.getElementById('annotator').value.trim();
  localStorage.annotator=annotator;
  const payload={image_id:state.candidate.image_id,severity_label:label,severity_score:document.getElementById('score').value,
    affected_area_ratio:document.getElementById('ratio').value,annotator,notes:document.getElementById('notes').value,
    review_status:reject?'REJECTED':(label==='UNCERTAIN'?'UNCERTAIN':'NEEDS_REVIEW')};
  if(payload.review_status==='NEEDS_REVIEW' && payload.severity_label==='') payload.review_status='PENDING';
  const res=await fetch('/api/annotate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
  if(!res.ok){ alert(await res.text()); return; }
  await load();
}
document.querySelectorAll('[data-label]').forEach(b=>b.onclick=()=>{label=b.dataset.label;reject=false;save();});
document.querySelector('[data-reject]').onclick=()=>{label='';reject=true;save();};
document.getElementById('approve').onclick=async()=>{ if(!state||!state.candidate)return; const payload={image_id:state.candidate.image_id,severity_label:label,severity_score:document.getElementById('score').value,affected_area_ratio:document.getElementById('ratio').value,annotator:document.getElementById('annotator').value.trim(),notes:document.getElementById('notes').value,review_status:'APPROVED'}; const res=await fetch('/api/annotate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}); if(!res.ok){ alert(await res.text()); return; } await load(); };
document.getElementById('save').onclick=save;
document.getElementById('next').onclick=()=>{index++;load();};
document.getElementById('prev').onclick=()=>{index=Math.max(0,index-1);load();};
document.getElementById('apply').onclick=()=>{index=0;load();};
document.addEventListener('keydown',e=>{if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA')return;
 if(['1','2','3','4'].includes(e.key)){label=['NONE','MILD','MODERATE','SEVERE'][Number(e.key)-1];reject=false;save();}
 else if(e.key.toLowerCase()==='u'){label='UNCERTAIN';reject=false;save();}
 else if(e.key.toLowerCase()==='r'){label='';reject=true;save();}
 else if(e.key.toLowerCase()==='s')save(); else if(e.key.toLowerCase()==='a'){document.getElementById('approve').click();}
 else if(e.key.toLowerCase()==='n'){index++;load();}
 else if(e.key.toLowerCase()==='p'){index=Math.max(0,index-1);load();}
});
load();
</script>
</body>
</html>
"""


class AnnotationStore:
    def __init__(self, dataset_root: Path, candidates: Path, annotations: Path, duplicate_groups: Path, enable_approve: bool):
        self.dataset_root = dataset_root
        self.candidates_path = candidates
        self.annotations_path = annotations
        self.duplicate_groups_path = duplicate_groups
        self.enable_approve = enable_approve
        self.lock = threading.Lock()
        self.candidates = self._read_candidates()
        self.duplicates = self._read_duplicate_groups()

    def state(self, index: int, disease: str, source: str, status: str, search: str) -> dict[str, object]:
        merged = self._merged()
        needle = search.lower().strip()
        filtered = [
            row
            for row in merged
            if (not disease or row["disease_code"] == disease)
            and (not source or row.get("source_dataset") == source)
            and (not status or (row.get("review_status") or row.get("annotation_status")) == status)
            and (not needle or needle in row.get("image_id", "").lower() or needle in Path(row.get("path", "")).name.lower())
        ]
        if not filtered:
            index = 0
            candidate = None
        else:
            index = max(0, min(index, len(filtered) - 1))
            candidate = filtered[index]
        return {
            "index": index,
            "total": len(filtered),
            "candidate": candidate,
            "diseases": sorted({row["disease_code"] for row in self.candidates}),
            "sources": sorted({row.get("source_dataset") or "UNKNOWN" for row in self.candidates}),
            "progress": self._progress(merged),
        }

    def annotate(self, payload: dict[str, object]) -> None:
        image_id = str(payload.get("image_id") or "")
        if not image_id:
            raise ValueError("image_id is required")
        candidate = next((row for row in self.candidates if row["image_id"] == image_id), None)
        if candidate is None:
            raise ValueError(f"unknown image_id: {image_id}")
        review_status = str(payload.get("review_status") or "NEEDS_REVIEW").upper()
        severity_label = str(payload.get("severity_label") or "").upper()
        if review_status == "APPROVED" and not self.enable_approve:
            raise ValueError("APPROVED writes require --enable-approve")
        if review_status not in ALLOWED_REVIEW:
            raise ValueError(f"invalid review_status: {review_status}")
        if severity_label not in ALLOWED_LABELS:
            raise ValueError(f"invalid severity_label: {severity_label}")
        if review_status == "APPROVED" and severity_label not in {"NONE", "MILD", "MODERATE", "SEVERE"}:
            raise ValueError("APPROVED rows require a concrete severity label")
        image_path = Path(candidate["path"])
        if not image_path.is_absolute():
            image_path = self.dataset_root / image_path
        if review_status == "APPROVED" and not image_path.exists():
            raise ValueError(f"APPROVED row image is missing: {candidate['path']}")
        score = _optional_ratio(payload.get("severity_score"), "severity_score")
        ratio = _optional_ratio(payload.get("affected_area_ratio"), "affected_area_ratio")
        rows = self._read_annotations()
        existing = next((row for row in rows if row["image_id"] == image_id), None)
        if existing and existing.get("review_status") == "APPROVED" and not self.enable_approve:
            raise ValueError("existing APPROVED row cannot be overwritten without --enable-approve")
        new_row = {
            "image_id": image_id,
            "image_path": candidate["path"],
            "disease": candidate["disease_code"],
            "annotation_type": "HUMAN_IMAGE_LEVEL_SEVERITY",
            "annotation_source": "LOCAL_WEB_UI",
            "annotator": str(payload.get("annotator") or ""),
            "review_status": review_status,
            "severity_label": severity_label,
            "severity_score": score,
            "affected_area_ratio": ratio,
            "source_dataset": candidate.get("source_dataset") or "UNKNOWN",
            "tree_group_id": "",
            "session_id": candidate.get("sha256") or image_id,
            "notes": str(payload.get("notes") or ""),
            "updated_at_utc": datetime.now(timezone.utc).isoformat(),
        }
        with self.lock:
            rows = [row for row in rows if row["image_id"] != image_id]
            rows.append(new_row)
            self.annotations_path.parent.mkdir(parents=True, exist_ok=True)
            with self.annotations_path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(handle, fieldnames=ANNOTATION_FIELDS)
                writer.writeheader()
                writer.writerows(rows)

    def _merged(self) -> list[dict[str, str]]:
        annotations = {row["image_id"]: row for row in self._read_annotations()}
        merged = []
        for candidate in self.candidates:
            row = dict(candidate)
            row.update(self.duplicates.get(row["image_id"], {}))
            annotation = annotations.get(row["image_id"])
            if annotation:
                row.update(
                    {
                        "severity_label": annotation.get("severity_label", ""),
                        "severity_score": annotation.get("severity_score", ""),
                        "affected_area_ratio": annotation.get("affected_area_ratio", ""),
                        "review_status": annotation.get("review_status", ""),
                        "annotator": annotation.get("annotator", ""),
                        "notes": annotation.get("notes", ""),
                        "updated_at_utc": annotation.get("updated_at_utc", ""),
                    }
                )
            merged.append(row)
        return merged

    def _progress(self, rows: list[dict[str, str]]) -> dict[str, object]:
        total = len(rows)
        reviewed = sum(1 for row in rows if row.get("review_status") and row.get("review_status") != "PENDING")
        approved = sum(1 for row in rows if row.get("review_status") == "APPROVED")
        rejected = sum(1 for row in rows if row.get("review_status") == "REJECTED")
        uncertain = sum(1 for row in rows if row.get("review_status") == "UNCERTAIN")
        return {
            "total": total,
            "reviewed": reviewed,
            "remaining": total - reviewed,
            "approved": approved,
            "uncertain": uncertain,
            "rejected": rejected,
            "percent": round((reviewed / total * 100) if total else 0, 2),
        }

    def _read_candidates(self) -> list[dict[str, str]]:
        with self.candidates_path.open("r", encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))
        return [_normalize_candidate(row) for row in rows]

    def _read_annotations(self) -> list[dict[str, str]]:
        if not self.annotations_path.exists():
            return []
        with self.annotations_path.open("r", encoding="utf-8", newline="") as handle:
            return list(csv.DictReader(handle))

    def _read_duplicate_groups(self) -> dict[str, dict[str, str]]:
        if not self.duplicate_groups_path.exists():
            return {}
        with self.duplicate_groups_path.open("r", encoding="utf-8", newline="") as handle:
            return {row["image_id"]: row for row in csv.DictReader(handle) if row.get("image_id")}


def main() -> int:
    parser = ArgumentParser(description="Run a local web UI for DurianCare severity annotation.")
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, default=None)
    parser.add_argument("--annotations", type=Path, default=None)
    parser.add_argument("--duplicate-groups", type=Path, default=None)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--enable-approve", action="store_true")
    args = parser.parse_args()

    candidates = args.candidates or args.dataset_root / "metadata" / "severity_annotation_candidates.csv"
    annotations = args.annotations or args.dataset_root / "metadata" / "annotations.csv"
    duplicate_groups = args.duplicate_groups or args.dataset_root / "reports" / "duplicate_groups.csv"
    store = AnnotationStore(args.dataset_root, candidates, annotations, duplicate_groups, args.enable_approve)

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/":
                self._send(HTTPStatus.OK, HTML.encode("utf-8"), "text/html; charset=utf-8")
            elif parsed.path == "/api/state":
                query = parse_qs(parsed.query)
                index = int(query.get("index", ["0"])[0] or 0)
                disease = query.get("disease", [""])[0]
                source = query.get("source", [""])[0]
                status = query.get("status", [""])[0]
                search = query.get("search", [""])[0]
                self._json(store.state(index, disease, source, status, search))
            elif parsed.path == "/image":
                query = parse_qs(parsed.query)
                image_path = Path(unquote(query.get("path", [""])[0]))
                if not image_path.is_absolute():
                    image_path = store.dataset_root / image_path
                self._file(image_path)
            else:
                self._send(HTTPStatus.NOT_FOUND, b"not found", "text/plain")

        def do_POST(self) -> None:
            if urlparse(self.path).path != "/api/annotate":
                self._send(HTTPStatus.NOT_FOUND, b"not found", "text/plain")
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                store.annotate(payload)
                self._json({"ok": True})
            except Exception as exc:
                self._send(HTTPStatus.BAD_REQUEST, str(exc).encode("utf-8"), "text/plain; charset=utf-8")

        def log_message(self, format: str, *args: object) -> None:
            return

        def _json(self, payload: object) -> None:
            self._send(HTTPStatus.OK, json.dumps(payload, ensure_ascii=False).encode("utf-8"), "application/json")

        def _file(self, path: Path) -> None:
            if not path.exists() or not path.is_file():
                self._send(HTTPStatus.NOT_FOUND, b"missing image", "text/plain")
                return
            content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            self._send(HTTPStatus.OK, path.read_bytes(), content_type)

        def _send(self, status: HTTPStatus, body: bytes, content_type: str) -> None:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Annotation UI: http://{args.host}:{args.port}")
    print("APPROVED writes:", "enabled" if args.enable_approve else "disabled")
    server.serve_forever()
    return 0


def _normalize_candidate(row: dict[str, str]) -> dict[str, str]:
    if "path" not in row and "image_path" in row:
        row["path"] = row["image_path"]
    if "disease_code" not in row and "disease" in row:
        row["disease_code"] = row["disease"]
    for field in CANDIDATE_FIELDS:
        row.setdefault(field, "")
    return row


def _optional_ratio(value: object, name: str) -> str:
    text = str(value or "").strip()
    if not text:
        return ""
    number = float(text)
    if not 0 <= number <= 1:
        raise ValueError(f"{name} must be between 0 and 1")
    return text


if __name__ == "__main__":
    raise SystemExit(main())
