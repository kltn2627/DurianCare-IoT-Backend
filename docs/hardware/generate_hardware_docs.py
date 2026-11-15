"""DurianCare IoT hardware documentation generator.

One netlist (CONNECTIONS) is the source of truth for the wiring diagram, the electrical schematic, the
assembly-guide tables and the consistency check. Every drawing is built from simple primitives that are
rendered twice: with matplotlib (PDF / PNG 300 dpi / SVG, A4 landscape) and as an editable draw.io file.

Run:  python docs/hardware/generate_hardware_docs.py
Needs Python 3 with matplotlib. No firmware is read except for the read-only pin check.
"""
from __future__ import annotations

import csv
import html
import os
import re
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.backends.backend_pdf import PdfPages  # noqa: E402
from matplotlib.patches import Circle, FancyBboxPatch, Polygon, Rectangle  # noqa: E402

OUT = Path(__file__).resolve().parent
STEPS = OUT / "assembly"
FIRMWARE_SENSOR = Path(os.environ.get(
    "DURIAN_SENSOR_SKETCH", Path.home() / "OneDrive/Documents/Arduino/sketch_jul25a/sketch_jul25a.ino"))
FIRMWARE_CAMERA_PINS = Path(os.environ.get(
    "DURIAN_CAMERA_PINS", Path.home() / "OneDrive/Documents/Arduino/CameraWebServer/camera_pins.h"))
REVISION = "Rev B · 29/10/2026"
PAGE_W, PAGE_H = 297.0, 210.0  # A4 landscape, mm
FONT = "DejaVu Sans"

# ── Colours (meaning is explained in every legend) ────────────────────────────────────────────────
C = {
    "3V3": "#D32F2F", "5V": "#E65100", "GND": "#212121", "DATA": "#FBC02D", "AO": "#1E88E5",
    "TX": "#43A047", "RX": "#8E24AA", "BOOT": "#FB8C00", "PROBE": "#9E9E9E", "USB": "#607D8B",
    "pcb_esp": "#1F2A44", "pcb_sensor": "#1565C0", "pcb_cam": "#263238", "pcb_ttl": "#B71C1C",
    "pin": "#C9A227", "ink": "#1A1A1A", "muted": "#555555", "warn": "#E65100", "verify": "#E65100",
    "wifi": "#00897B", "paper": "#FFFFFF", "panel": "#F5F7FA",
}

# ── Netlist: the single source of truth ──────────────────────────────────────────────────────────
# status: VERIFIED_FW = declared in firmware (and, for DHT22, proven by real readings)
#         REQUIRED    = electrically required by the module (power/GND), wiring method not inspected
#         UNVERIFIED  = assumption to be confirmed on the real hardware
CONNECTIONS = [
    # id, device, from(ref, pin), to(ref, pin), net, kind, status, note
    dict(id="W1", dev="sensor", a=("U1", "3V3"), b=("J1", "chung"), net="+3V3", kind="Nguồn 3,3 V",
         status="REQUIRED", note="Một chân 3V3 cấp cho hai cảm biến; cách chia (breadboard, dây chữ Y, mối hàn) CẦN XÁC NHẬN"),
    dict(id="W2", dev="sensor", a=("J1", "chung"), b=("S1", "VCC (+)"), net="+3V3", kind="Nguồn 3,3 V",
         status="REQUIRED", note="DHT22 chạy 3,3–5,5 V; cấp 3V3 để DATA ở mức 3,3 V. Đang cấp 3V3 hay 5V: CẦN XÁC NHẬN"),
    dict(id="W3", dev="sensor", a=("J1", "chung"), b=("S2", "VCC"), net="+3V3", kind="Nguồn 3,3 V",
         status="REQUIRED", note="BẮT BUỘC 3V3: cấp 5V thì AO có thể > 3,3 V, hỏng GPIO34. Đang cấp gì: CẦN XÁC NHẬN"),
    dict(id="W4", dev="sensor", a=("U1", "GND (phải)"), b=("S1", "GND (−)"), net="GND", kind="Mass",
         status="REQUIRED", note="Dùng chân GND cạnh 3V3"),
    dict(id="W5", dev="sensor", a=("U1", "GND (trái)"), b=("S2", "GND"), net="GND", kind="Mass",
         status="REQUIRED", note="Dùng chân GND cạnh VIN"),
    dict(id="W6", dev="sensor", a=("U1", "D18 / GPIO18"), b=("S1", "DATA (OUT)"), net="DHT_DATA", kind="Tín hiệu số 1-wire",
         status="VERIFIED_FW", note="#define DHTPIN 18; có dữ liệu thật 22/10/2026"),
    dict(id="W7", dev="sensor", a=("U1", "D34 / GPIO34"), b=("S2", "AO"), net="SOIL_AO", kind="Tín hiệu analog",
         status="VERIFIED_FW", note="#define SOIL_PIN 34 (ADC1_CH6, chỉ vào). Giá trị đọc ≈ 0 → kiểm tra dây"),
    dict(id="W8", dev="sensor", a=("S2", "Đầu dò +/−"), b=("P1", "2 cực"), net="PROBE", kind="Cáp đầu dò (2 sợi)",
         status="UNVERIFIED", note="Cáp đi kèm module; không phân cực. Loại đầu dò (YL-69?) CẦN XÁC NHẬN"),
    dict(id="W9", dev="sensor", a=("PS1", "USB 5 V"), b=("U1", "micro-USB"), net="+5V_USB", kind="Cáp USB",
         status="UNVERIFIED", note="Nguồn sạc/PC cụ thể CẦN XÁC NHẬN"),
    # Camera – flashing with a USB-TTL adapter (option B)
    dict(id="C1", dev="camera_flash", a=("J2", "5V"), b=("U2", "5V"), net="+5V", kind="Nguồn 5 V",
         status="UNVERIFIED", note="Chỉ khi adapter cấp đủ dòng; nếu reset liên tục, dùng nguồn 5 V riêng (chung GND)"),
    dict(id="C2", dev="camera_flash", a=("J2", "GND"), b=("U2", "GND (cạnh 5V)"), net="GND", kind="Mass",
         status="REQUIRED", note="GND chung giữa adapter và ESP32-CAM là bắt buộc"),
    dict(id="C3", dev="camera_flash", a=("J2", "TXD"), b=("U2", "U0R / GPIO3"), net="UART_RX", kind="UART (chéo TX→RX)",
         status="REQUIRED", note="Mức logic 3,3 V — đặt jumper adapter ở 3V3 nếu có"),
    dict(id="C4", dev="camera_flash", a=("J2", "RXD"), b=("U2", "U0T / GPIO1"), net="UART_TX", kind="UART (chéo RX←TX)",
         status="REQUIRED", note="Nối chéo: RX adapter nhận từ U0T"),
    dict(id="C5", dev="camera_flash", a=("U2", "IO0 / GPIO0"), b=("U2", "GND (cạnh IO0)"), net="BOOT", kind="Jumper chế độ nạp",
         status="REQUIRED", note="CHỈ khi nạp. Tháo ra khi chạy (GPIO0 là XCLK của camera)"),
    # Camera – normal operation
    dict(id="C6", dev="camera_run", a=("PS2", "+5V"), b=("U2", "5V"), net="+5V", kind="Nguồn 5 V",
         status="UNVERIFIED", note="Khuyến nghị ≥ 1 A (Wi-Fi + camera + LED flash). Nguồn thực tế CẦN XÁC NHẬN"),
    dict(id="C7", dev="camera_run", a=("PS2", "GND"), b=("U2", "GND (cạnh 5V)"), net="GND", kind="Mass",
         status="REQUIRED", note=""),
]
CONN = {c["id"]: c for c in CONNECTIONS}
WIRE_STYLE = {"+3V3": "3V3", "GND": "GND", "DHT_DATA": "DATA", "SOIL_AO": "AO", "PROBE": "PROBE",
              "+5V_USB": "USB", "+5V": "5V", "UART_RX": "TX", "UART_TX": "RX", "BOOT": "BOOT"}

# Firmware facts this documentation claims (checked against the sketches when they are present).
FIRMWARE_CLAIMS = {"DHTPIN": 18, "SOIL_PIN": 34}
CAMERA_CLAIMS = {"PWDN_GPIO_NUM": 32, "RESET_GPIO_NUM": -1, "XCLK_GPIO_NUM": 0, "SIOD_GPIO_NUM": 26,
                 "SIOC_GPIO_NUM": 27, "Y9_GPIO_NUM": 35, "Y8_GPIO_NUM": 34, "Y7_GPIO_NUM": 39, "Y6_GPIO_NUM": 36,
                 "Y5_GPIO_NUM": 21, "Y4_GPIO_NUM": 19, "Y3_GPIO_NUM": 18, "Y2_GPIO_NUM": 5, "VSYNC_GPIO_NUM": 25,
                 "HREF_GPIO_NUM": 23, "PCLK_GPIO_NUM": 22, "LED_GPIO_NUM": 4}

# ESP32 DevKit V1 (DOIT, 30 pins) — silk labels top (antenna) to bottom (USB). Variant CẦN XÁC NHẬN.
DEVKIT_LEFT = ["EN", "VP", "VN", "D34", "D35", "D32", "D33", "D25", "D26", "D27", "D14", "D12", "D13", "GND", "VIN"]
DEVKIT_RIGHT = ["D23", "D22", "TX0", "RX0", "D21", "D19", "D18", "D5", "TX2", "RX2", "D4", "D2", "D15", "GND", "3V3"]
# AI-Thinker ESP32-CAM headers, listed from the 5V / 3V3 end.
CAM_LEFT = ["5V", "GND", "IO12", "IO13", "IO15", "IO14", "IO2", "IO4"]
CAM_RIGHT = ["3V3", "IO16", "IO0", "GND", "VCC", "U0R", "U0T", "GND"]


# ══ Primitive canvas: rendered to matplotlib and to draw.io ══════════════════════════════════════
class Canvas:
    def __init__(self, name: str):
        self.name = name
        self.items: list[tuple] = []
        self.wires: dict[str, list] = {}

    def rect(self, x, y, w, h, fc="none", ec=C["ink"], lw=0.6, r=0.0, dash=False, alpha=1.0):
        self.items.append(("rect", x, y, w, h, dict(fc=fc, ec=ec, lw=lw, r=r, dash=dash, alpha=alpha)))

    def line(self, pts, color=C["ink"], lw=0.6, dash=False, alpha=1.0, outline=False, tag=None):
        self.items.append(("line", list(pts), dict(color=color, lw=lw, dash=dash, alpha=alpha, outline=outline, tag=tag)))

    def circle(self, x, y, r, fc=C["ink"], ec=C["ink"], lw=0.5, alpha=1.0):
        self.items.append(("circle", x, y, r, dict(fc=fc, ec=ec, lw=lw, alpha=alpha)))

    def poly(self, pts, fc="none", ec=C["ink"], lw=0.6, alpha=1.0):
        self.items.append(("poly", list(pts), dict(fc=fc, ec=ec, lw=lw, alpha=alpha)))

    def text(self, x, y, s, size=8.0, color=C["ink"], ha="left", va="center", weight="normal",
             box=None, rot=0, alpha=1.0, style="normal"):
        self.items.append(("text", x, y, s, dict(size=size, color=color, ha=ha, va=va, weight=weight,
                                                 box=box, rot=rot, alpha=alpha, style=style)))

    def wire(self, cid, pts, faded=False, label_at=None, dash=None):
        conn = CONN[cid]
        color = C[WIRE_STYLE[conn["net"]]]
        dashed = conn["status"] == "UNVERIFIED" if dash is None else dash
        alpha = 0.18 if faded else 1.0
        self.line(pts, color=color, lw=2.6, dash=dashed, alpha=alpha, outline=True, tag=cid)
        self.wires[cid] = pts
        lx, ly = label_at if label_at else midpoint(pts)
        self.text(lx, ly, cid, size=7.5, weight="bold", ha="center", alpha=alpha,
                  box=dict(fc="white", ec=color, lw=0.9, pad=0.6))


def midpoint(pts):
    best, length = None, -1.0
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        seg = abs(x2 - x1) + abs(y2 - y1)
        if seg > length:
            best, length = ((x1 + x2) / 2, (y1 + y2) / 2), seg
    return best


def crossings(wires: dict[str, list]) -> list[tuple]:
    """Proper crossings between orthogonal wire segments of different wires (shared end points allowed)."""
    segs = []
    for cid, pts in wires.items():
        for p, q in zip(pts, pts[1:]):
            segs.append((cid, p, q))
    found = []
    for i, (a, p1, p2) in enumerate(segs):
        for b, q1, q2 in segs[i + 1:]:
            if a == b:
                continue
            h1, h2 = p1[1] == p2[1], q1[1] == q2[1]
            if h1 == h2:
                continue
            (hp, hq), (vp, vq) = ((p1, p2), (q1, q2)) if h1 else ((q1, q2), (p1, p2))
            x, y = vp[0], hp[1]
            inside_h = min(hp[0], hq[0]) < x < max(hp[0], hq[0])
            inside_v = min(vp[1], vq[1]) < y < max(vp[1], vq[1])
            if inside_h and inside_v:
                found.append((a, b, (round(x, 1), round(y, 1))))
    return found


def render_mpl(cv: Canvas, title: str):
    fig = plt.figure(figsize=(PAGE_W / 25.4, PAGE_H / 25.4))
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, PAGE_W)
    ax.set_ylim(PAGE_H, 0)
    ax.axis("off")
    fig.patch.set_facecolor("white")
    for it in cv.items:
        kind = it[0]
        if kind == "rect":
            _, x, y, w, h, s = it
            ls = (0, (3, 2)) if s["dash"] else "solid"
            if s["r"]:
                p = FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0,rounding_size={s['r']}",
                                   fc=s["fc"], ec=s["ec"], lw=s["lw"], ls=ls, alpha=s["alpha"])
            else:
                p = Rectangle((x, y), w, h, fc=s["fc"], ec=s["ec"], lw=s["lw"], ls=ls, alpha=s["alpha"])
            ax.add_patch(p)
        elif kind == "line":
            _, pts, s = it
            xs, ys = zip(*pts)
            ls = (0, (4, 2.5)) if s["dash"] else "solid"
            if s["outline"]:
                ax.plot(xs, ys, color="#111111", lw=s["lw"] + 1.3, alpha=s["alpha"], solid_capstyle="round",
                        solid_joinstyle="round", ls=ls, dash_capstyle="round")
            ax.plot(xs, ys, color=s["color"], lw=s["lw"], alpha=s["alpha"], solid_capstyle="round",
                    solid_joinstyle="round", ls=ls, dash_capstyle="round")
        elif kind == "circle":
            _, x, y, r, s = it
            ax.add_patch(Circle((x, y), r, fc=s["fc"], ec=s["ec"], lw=s["lw"], alpha=s["alpha"]))
        elif kind == "poly":
            _, pts, s = it
            ax.add_patch(Polygon(pts, closed=True, fc=s["fc"], ec=s["ec"], lw=s["lw"], alpha=s["alpha"]))
        elif kind == "text":
            _, x, y, txt, s = it
            bbox = None
            if s["box"]:
                b = s["box"]
                bbox = dict(boxstyle=f"round,pad={b.get('pad', 0.3)}", fc=b.get("fc", "white"),
                            ec=b.get("ec", "none"), lw=b.get("lw", 0.6), alpha=s["alpha"])
            ax.text(x, y, txt, fontsize=s["size"], color=s["color"], ha=s["ha"], va=s["va"],
                    fontweight=s["weight"], fontfamily=FONT, bbox=bbox, rotation=s["rot"], alpha=s["alpha"],
                    fontstyle=s["style"], linespacing=1.25)
    fig.text(0.5, 0.995, "", fontsize=1)  # keep full page bounds
    return fig


# draw.io export (1 mm = 4 px) ────────────────────────────────────────────────────────────────────
PX = 4.0


def drawio_page(cv: Canvas, page_id: str, page_name: str) -> str:
    cells = ['<mxCell id="0"/>', '<mxCell id="1" parent="0"/>']
    n = 2

    def nid():
        nonlocal n
        n += 1
        return f"{page_id}-{n}"

    for it in cv.items:
        kind = it[0]
        if kind == "rect":
            _, x, y, w, h, s = it
            fill = "none" if s["fc"] == "none" else s["fc"]
            style = (f"rounded={1 if s['r'] else 0};arcSize={int(min(50, s['r'] * 100 / max(min(w, h), 1)))};"
                     f"absoluteArcSize=0;whiteSpace=wrap;html=1;fillColor={fill};strokeColor={s['ec']};"
                     f"strokeWidth={s['lw'] * 1.4:.1f};dashed={1 if s['dash'] else 0};opacity={int(s['alpha'] * 100)};")
            cells.append(f'<mxCell id="{nid()}" value="" style="{style}" vertex="1" parent="1">'
                         f'<mxGeometry x="{x * PX:.1f}" y="{y * PX:.1f}" width="{w * PX:.1f}" height="{h * PX:.1f}" as="geometry"/></mxCell>')
        elif kind == "circle":
            _, x, y, r, s = it
            style = (f"ellipse;whiteSpace=wrap;html=1;fillColor={s['fc']};strokeColor={s['ec']};"
                     f"opacity={int(s['alpha'] * 100)};")
            cells.append(f'<mxCell id="{nid()}" value="" style="{style}" vertex="1" parent="1">'
                         f'<mxGeometry x="{(x - r) * PX:.1f}" y="{(y - r) * PX:.1f}" width="{2 * r * PX:.1f}" '
                         f'height="{2 * r * PX:.1f}" as="geometry"/></mxCell>')
        elif kind in ("line", "poly"):
            _, pts, s = it
            if kind == "poly":
                pts = pts + [pts[0]]
                color, lw, dash, alpha = s["ec"], s["lw"], False, s["alpha"]
            else:
                color, lw, dash, alpha = s["color"], s["lw"], s["dash"], s["alpha"]
            tooltip = f"tooltip={html.escape(s.get('tag') or '')};" if kind == "line" and s.get("tag") else ""
            style = (f"endArrow=none;html=1;rounded=0;strokeColor={color};strokeWidth={lw * 1.4:.1f};"
                     f"dashed={1 if dash else 0};opacity={int(alpha * 100)};{tooltip}")
            (sx, sy), (tx, ty) = pts[0], pts[-1]
            mids = "".join(f'<mxPoint x="{px * PX:.1f}" y="{py * PX:.1f}"/>' for px, py in pts[1:-1])
            arr = f'<Array as="points">{mids}</Array>' if mids else ""
            cells.append(f'<mxCell id="{nid()}" value="" style="{style}" edge="1" parent="1">'
                         f'<mxGeometry relative="1" as="geometry"><mxPoint x="{sx * PX:.1f}" y="{sy * PX:.1f}" as="sourcePoint"/>'
                         f'<mxPoint x="{tx * PX:.1f}" y="{ty * PX:.1f}" as="targetPoint"/>{arr}</mxGeometry></mxCell>')
        elif kind == "text":
            _, x, y, txt, s = it
            lines = txt.split("\n")
            fs = s["size"] * 1.41
            w = max(len(l) for l in lines) * fs * 0.62 + 8
            h = len(lines) * fs * 1.3 + 4
            px, py = x * PX, y * PX
            px -= {"left": 0, "center": w / 2, "right": w}[s["ha"]]
            py -= {"top": 0, "center": h / 2, "bottom": h, "baseline": h, "center_baseline": h / 2}[s["va"]]
            box = s["box"] or {}
            style = (f"text;html=1;whiteSpace=nowrap;align={s['ha']};verticalAlign=middle;fontSize={fs:.1f};"
                     f"fontColor={s['color']};fontStyle={1 if s['weight'] == 'bold' else 0};"
                     f"fillColor={box.get('fc', 'none') if box else 'none'};strokeColor={box.get('ec', 'none') if box else 'none'};"
                     f"rotation={-s['rot']};opacity={int(s['alpha'] * 100)};")
            value = "<br>".join(html.escape(l) for l in lines)
            cells.append(f'<mxCell id="{nid()}" value="{html.escape(value)}" style="{style}" vertex="1" parent="1">'
                         f'<mxGeometry x="{px:.1f}" y="{py:.1f}" width="{w:.1f}" height="{h:.1f}" as="geometry"/></mxCell>')
    body = "".join(cells)
    return (f'<diagram id="{page_id}" name="{html.escape(page_name)}"><mxGraphModel dx="1200" dy="850" grid="1" '
            f'gridSize="10" guides="1" page="1" pageScale="1" pageWidth="{PAGE_W * PX:.0f}" pageHeight="{PAGE_H * PX:.0f}" '
            f'math="0" shadow="0"><root>{body}</root></mxGraphModel></diagram>')


def write_drawio(path: Path, pages: list[tuple[Canvas, str, str]]):
    diagrams = "".join(drawio_page(cv, pid, name) for cv, pid, name in pages)
    path.write_text(f'<mxfile host="generate_hardware_docs.py" type="device">{diagrams}</mxfile>', encoding="utf-8")


# ══ Shared drawing helpers ═══════════════════════════════════════════════════════════════════════
def frame(cv: Canvas, title: str, subtitle: str, sheet: str, show_sub: bool = True):
    cv.rect(5, 5, PAGE_W - 10, PAGE_H - 10, ec=C["ink"], lw=0.8)
    cv.rect(5, 5, PAGE_W - 10, 13, fc="#EEF2F7", ec=C["ink"], lw=0.8)
    cv.text(9, 11.5, title, size=13, weight="bold")
    cv.text(PAGE_W - 9, 9.5, f"DurianCare IoT · {sheet}", size=8, ha="right", color=C["muted"])
    cv.text(PAGE_W - 9, 14.5, REVISION, size=8, ha="right", color=C["muted"])
    if show_sub:
        cv.text(9, 21.5, subtitle, size=8.5, color=C["muted"], va="center", style="italic")


def verify_tag(cv: Canvas, x, y, text="CẦN XÁC NHẬN", ha="left", size=7.5):
    cv.text(x, y, "⚠ " + text, size=size, color="white", weight="bold", ha=ha,
            box=dict(fc=C["verify"], ec=C["verify"], pad=0.35))


def pin_pad(cv: Canvas, x, y, used=True, faded=False):
    a = 0.35 if faded else 1.0
    cv.rect(x - 1.5, y - 1.5, 3, 3, fc="#2B2B2B", ec="#000000", lw=0.4, alpha=a)
    cv.circle(x, y, 0.9, fc=C["pin"] if used else "#8A7A3A", ec="#5A4A00", lw=0.3, alpha=a)


def wifi_link(cv: Canvas, x, y, label, to_x, to_y):
    for r in (2.0, 3.6, 5.2):
        cv.line([(x - r * 0.75, y - r * 0.55), (x, y - r * 1.0), (x + r * 0.75, y - r * 0.55)], color=C["wifi"], lw=1.1)
    cv.circle(x, y + 0.4, 0.8, fc=C["wifi"], ec=C["wifi"])
    if to_x is not None:
        cv.line([(x + 6, y - 2), (to_x, to_y)], color=C["wifi"], lw=1.0, dash=True)
    cv.text(x, y + 4.2, "Wi-Fi", size=7.5, color=C["wifi"], ha="center", weight="bold")


def cloud(cv: Canvas, x, y, w, h, title, lines):
    cv.rect(x, y, w, h, fc="#E0F2F1", ec=C["wifi"], lw=0.9, r=4)
    cv.text(x + 3, y + 4.5, title, size=8.5, weight="bold", color="#00695C")
    cv.text(x + 3, y + 9, "\n".join(lines), size=7.5, color=C["ink"], va="top")


def legend(cv: Canvas, x, y, entries, title="Chú giải màu dây (chỉ để minh họa — dây thật có thể khác màu)"):
    cv.text(x, y, title, size=8, weight="bold")
    col_w = 70
    for i, (key, label, dashed) in enumerate(entries):
        cx = x + (i % 2) * col_w
        cy = y + 5.5 + (i // 2) * 5.2
        if key == "WIFI":
            cv.line([(cx, cy), (cx + 10, cy)], color=C["wifi"], lw=1.0, dash=True)
        else:
            cv.line([(cx, cy), (cx + 10, cy)], color=C[key], lw=2.6, outline=True, dash=dashed)
        cv.text(cx + 12.5, cy, label, size=7.5)


def wire_table(cv: Canvas, x, y, ids, title="Danh sách dây (chi tiết: bảng nối dây trong hướng dẫn)"):
    cv.text(x, y, title, size=8, weight="bold")
    rows = (len(ids) + 1) // 2
    for i, cid in enumerate(ids):
        c = CONN[cid]
        cx = x + (i // rows) * 68
        cy = y + 5.2 + (i % rows) * 4.6
        cv.text(cx, cy, cid, size=7, weight="bold", color=C[WIRE_STYLE[c["net"]]] if c["net"] not in ("GND",) else C["ink"])
        txt = f"{c['a'][0]} {c['a'][1].split(' / ')[0]} → {c['b'][0]} {c['b'][1].split(' / ')[0]}"
        cv.text(cx + 6, cy, txt, size=6.8)


# ══ Product A — wiring diagram ═══════════════════════════════════════════════════════════════════
K = 2.2  # drawing scale for physical parts (≈ 2.2 × real size)
CAM_SCALE = 0.78  # ESP32-CAM drawn a little smaller so three panels fit on one page
PITCH = 2.54 * K


def devkit(cv: Canvas, x0, y0, faded=False):
    """ESP32 DevKit V1 30-pin seen from the component side, antenna up, USB down. Returns pin coordinates."""
    w, h = 28 * K, 51.5 * K
    a = 0.35 if faded else 1.0
    cv.rect(x0, y0, w, h, fc=C["pcb_esp"], ec="#000000", lw=0.8, r=2.0, alpha=a)
    cv.rect(x0 + 15, y0 + 2, w - 30, 30, fc="#B0BEC5", ec="#455A64", lw=0.6, r=1.0, alpha=a)  # WROOM can
    cv.text(x0 + w / 2, y0 + 13, "ESP32\nWROOM-32", size=7.5, ha="center", color="#263238", weight="bold", alpha=a)
    cv.rect(x0 + w / 2 - 6, y0 + h - 7, 12, 8, fc="#B0BEC5", ec="#455A64", lw=0.6, alpha=a)  # micro USB
    cv.text(x0 + w / 2, y0 + h - 10.5, "micro-USB", size=6.5, ha="center", color="white", alpha=a)
    cv.text(x0 + w / 2, y0 + 40, "U1\nESP32 DevKit\n(30 chân)", size=8, ha="center", color="white", weight="bold", alpha=a)
    xl, xr = x0 + 5.65, x0 + w - 5.65
    first = y0 + (h - 14 * PITCH) / 2 + 4
    pins = {}
    for i, (l, r) in enumerate(zip(DEVKIT_LEFT, DEVKIT_RIGHT)):
        y = first + i * PITCH
        for side, name, px in (("L", l, xl), ("R", r, xr)):
            key = name if name != "GND" else f"GND{side}"
            pins[key] = (px, y)
            pin_pad(cv, px, y, faded=faded)
            if side == "L":
                cv.text(px + 2.6, y, name, size=7, color="white", alpha=a)
            else:
                cv.text(px - 2.6, y, name, size=7, color="white", ha="right", alpha=a)
    return pins, (x0, y0, w, h)


def dht22_module(cv: Canvas, x, pins_y, faded=False):
    """3-pin DHT22 breakout with pins on its left edge at the given heights (top→bottom: OUT, −, +)."""
    a = 0.35 if faded else 1.0
    top, bottom = pins_y[0] - 7, pins_y[-1] + 7
    cv.rect(x, top, 13, bottom - top, fc=C["pcb_sensor"], ec="#0D47A1", lw=0.7, r=1.0, alpha=a)
    cv.rect(x + 13, top - 18, 34, bottom - top + 36, fc="#FAFAFA", ec="#9E9E9E", lw=0.8, r=1.5, alpha=a)
    for i in range(9):
        yy = top - 14 + i * ((bottom - top + 28) / 8.5)
        cv.line([(x + 17, yy), (x + 43, yy)], color="#BDBDBD", lw=0.8, alpha=a)
    labels = ["DATA (OUT)", "GND (−)", "VCC (+)"]
    pts = {}
    for name, y in zip(labels, pins_y):
        pin_pad(cv, x + 2.5, y, faded=faded)
        cv.text(x + 4.6, y, name.split(" ")[0], size=6.5, color="white", alpha=a)
        pts[name] = (x + 1, y)
    cv.text(x + 30, top - 22, "S1 · DHT22 / AM2302", size=8.5, weight="bold", ha="center", alpha=a)
    cv.text(x + 30, bottom + 21, "module 3 chân; thứ tự chân\ntùy hãng — đọc chữ in", size=6.5,
            ha="center", va="top", color=C["muted"], alpha=a)
    return pts


def soil_module(cv: Canvas, x_right, pins_y, faded=False):
    """LM393 comparator board, 4-pin header on its right edge (top→bottom: AO, DO, GND, VCC)."""
    a = 0.35 if faded else 1.0
    top, bottom = pins_y[0] - 8, pins_y[-1] + 8
    x0 = x_right - 34
    cv.rect(x0, top, 34, bottom - top, fc=C["pcb_sensor"], ec="#0D47A1", lw=0.7, r=1.0, alpha=a)
    cv.rect(x0 + 9, top + 5, 9, 9, fc="#263238", ec="#000", lw=0.4, alpha=a)  # LM393
    cv.text(x0 + 13.5, top + 16.5, "LM393", size=6, ha="center", color="white", alpha=a)
    cv.circle(x0 + 13.5, bottom - 9, 3.2, fc="#1E88E5", ec="#0D47A1", alpha=a)  # trimmer
    cv.text(x0 + 13.5, bottom - 3.5, "biến trở\n(chỉ cho DO)", size=5.5, ha="center", color="white", alpha=a, va="center")
    pts = {}
    for name, y in zip(["AO", "DO", "GND", "VCC"], pins_y):
        pin_pad(cv, x_right - 2.5, y, used=name != "DO", faded=faded)
        cv.text(x_right - 5, y, name, size=7, ha="right", color="white", alpha=a)
        pts[name] = (x_right - 1, y)
    probe_pins = {"P+": (x0 + 2.5, pins_y[0] + 3), "P−": (x0 + 2.5, pins_y[0] + 3 + PITCH)}
    for name, (px, py) in probe_pins.items():
        pin_pad(cv, px, py, faded=faded)
    cv.text(x0, top - 7, "S2 · Module ẩm đất\n(so sánh LM393)", size=8.5, weight="bold", alpha=a)
    cv.text(x0 + 17, bottom + 3.5, "DO: không nối", size=6.8, ha="center", color=C["muted"], alpha=a)
    return pts, probe_pins


def probe(cv: Canvas, x, y, faded=False):
    a = 0.35 if faded else 1.0
    cv.rect(x, y, 22, 9, fc=C["pcb_sensor"], ec="#0D47A1", lw=0.6, alpha=a)
    for dx in (3, 14):
        cv.rect(x + dx, y + 9, 5, 34, fc="#C0C0C0", ec="#757575", lw=0.5, alpha=a)
        cv.poly([(x + dx, y + 43), (x + dx + 5, y + 43), (x + dx + 2.5, y + 47)], fc="#C0C0C0", ec="#757575", alpha=a)
    pins = {"+": (x + 6, y + 3.5), "−": (x + 16, y + 3.5)}
    for (px, py) in pins.values():
        pin_pad(cv, px, py, faded=faded)
    cv.text(x - 6, y - 20, "P1 · Đầu dò đất\n(2 cực, không phân cực)", size=8, weight="bold", alpha=a)
    return pins


def page_wiring_sensor(highlight: set[str] | None = None, title_suffix="", step_note=None) -> Canvas:
    cv = Canvas("wiring_sensor")
    frame(cv, "SƠ ĐỒ LẮP ĐẶT · Thiết bị 1 — Trạm cảm biến môi trường (esp32-01)" + title_suffix,
          "Nối theo TÊN CHÂN in trên board. Pinout vẽ theo ESP32 DevKit V1 30 chân (DOIT) — loại board thật CẦN XÁC NHẬN.",
          "Wiring Diagram · Trang 1/2", show_sub=step_note is None)
    fade = (lambda cid: highlight is not None and cid not in highlight)
    pins, (bx, by, bw, bh) = devkit(cv, 118, 30)
    verify_tag(cv, bx + bw / 2, by - 3.5, "Loại board CẦN XÁC NHẬN", ha="center")

    # DHT22 on the right: OUT one pitch above the GND row, − on GND row, + on 3V3 row.
    yg, y3 = pins["GNDR"][1], pins["3V3"][1]
    dht_pins = dht22_module(cv, 213, [yg - PITCH, yg, y3])
    # Soil module on the left, half a pitch below the board's GND row.
    ygl = pins["GNDL"][1]
    soil_y = [ygl - 1.5 * PITCH, ygl - 0.5 * PITCH, ygl + 0.5 * PITCH, ygl + 1.5 * PITCH]
    soil_pins, probe_pins = soil_module(cv, 93, soil_y)
    pr = probe(cv, 18, 92)
    # soil illustration
    cv.rect(10, 128, 46, 22, fc="#D7CCC8", ec="#8D6E63", lw=0.6, r=1.5)
    for i in range(6):
        cv.line([(13 + i * 7, 147), (17 + i * 7, 131)], color="#A1887F", lw=0.5)
    cv.text(33, 153.5, "cắm vào đất ở vùng rễ", size=7, ha="center", color=C["muted"])

    # USB supply under the board
    ux = bx + bw / 2
    cv.rect(ux - 21, by + bh + 8, 42, 10, fc="#ECEFF1", ec="#607D8B", lw=0.7, r=1.5)
    cv.text(ux, by + bh + 13, "PS1 · Nguồn USB 5 V", size=7.5, ha="center", weight="bold")
    cv.wire("W9", [(ux, by + bh + 1), (ux, by + bh + 8)], faded=fade("W9"), label_at=(ux + 6, by + bh + 4.5))

    # Wires — orthogonal, no crossings (checked in build())
    J = (196, y3)
    cv.wire("W6", [pins["D18"], (203, pins["D18"][1]), (203, dht_pins["DATA (OUT)"][1]), dht_pins["DATA (OUT)"]],
            faded=fade("W6"), label_at=(203, (pins["D18"][1] + dht_pins["DATA (OUT)"][1]) / 2))
    cv.wire("W4", [pins["GNDR"], dht_pins["GND (−)"]], faded=fade("W4"), label_at=(205, yg))
    cv.wire("W1", [pins["3V3"], J], faded=fade("W1"), label_at=(185, y3 + 4.5))
    cv.wire("W2", [J, dht_pins["VCC (+)"]], faded=fade("W2"), label_at=(205.5, y3 + 4.5))
    low = by + bh + 25
    cv.wire("W3", [J, (J[0], low), (101, low), (101, soil_pins["VCC"][1]), soil_pins["VCC"]],
            faded=fade("W3"), label_at=(150, low))
    cv.wire("W5", [pins["GNDL"], (106, ygl), (106, soil_pins["GND"][1]), soil_pins["GND"]],
            faded=fade("W5"), label_at=(113, ygl - 3.2))
    cv.wire("W7", [pins["D34"], (110, pins["D34"][1]), (110, soil_pins["AO"][1]), soil_pins["AO"]],
            faded=fade("W7"), label_at=(110, (pins["D34"][1] + soil_pins["AO"][1]) / 2))
    cv.wire("W8", [probe_pins["P+"], (54, probe_pins["P+"][1]), (54, 80), (pr["+"][0], 80), pr["+"]],
            faded=fade("W8"), label_at=(54, 94))
    cv.line([probe_pins["P−"], (47, probe_pins["P−"][1]), (47, 87), (pr["−"][0], 87), pr["−"]],
            color=C["PROBE"], lw=2.6, dash=True, alpha=0.18 if fade("W8") else 1.0, outline=True, tag="W8")
    cv.circle(J[0], J[1], 1.6, fc=C["3V3"], ec="#000")
    cv.circle(J[0], J[1], 3.2, fc="none", ec=C["verify"], lw=1.0)
    cv.text(J[0] - 3, 162.5, "J1 · điểm chia 3V3 cho S1 và S2 (breadboard / dây chữ Y / mối hàn?)", size=6.6,
            ha="right", color=C["verify"], weight="bold")

    # Notes and warnings
    cv.text(10, 30, "⚠ Cảm biến đất phải cấp 3V3, KHÔNG cấp 5V:\n   AO có thể vượt 3,3 V và làm hỏng GPIO34.",
            size=7.5, color=C["warn"], weight="bold", va="top")
    cv.text(10, 41, "GPIO34 chỉ là ngõ vào analog (ADC1_CH6),\ndùng được khi Wi-Fi đang bật.", size=7.5, va="top")
    cv.text(10, 160, "Giá trị đọc hiện ≈ 0 gần như mọi lần → đo AO\nbằng đồng hồ trước khi tin số liệu (mục 5 hướng dẫn).",
            size=7, va="top", color=C["warn"])
    cv.text(238, 30, "R1 10 kΩ (DATA → VCC): chỉ cần khi\ndùng cảm biến 4 chân trần. Module\n3 chân thường đã có sẵn.",
            size=7.2, va="top")
    verify_tag(cv, 238, 47, "Module 3 chân hay 4 chân?")
    cloud(cv, 226, 53, 64, 26, "Mạng (không phải dây điện)",
          ["Wi-Fi 2,4 GHz → router →", "Backend DurianCare: HTTP POST", "/api/v1/sensors/data, UDP 4210"])
    wifi_link(cv, 200, 50, "", 226, 62)

    legend(cv, 10, 176, [("3V3", "3V3 — nguồn 3,3 V", False), ("GND", "GND — mass", False),
                         ("DATA", "DATA DHT22 (GPIO18)", False), ("AO", "AO ẩm đất (GPIO34)", False),
                         ("PROBE", "Cáp đầu dò / chưa xác nhận (nét đứt)", True), ("USB", "Cáp USB 5 V", True),
                         ("WIFI", "Kết nối Wi-Fi (không có dây)", False)])
    wire_table(cv, 150, 176, ["W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8"])
    if step_note:
        cv.text(PAGE_W / 2, 21.5, step_note, size=9, ha="center", weight="bold",
                box=dict(fc="#FFF8E1", ec=C["warn"], pad=0.5))
    return cv


def esp32cam(cv: Canvas, x0, y_bottom, label="U2 · ESP32-CAM\n(AI-Thinker)", faded=False):
    """Board seen from the camera side, 5V/3V3 end at the bottom. Returns pin coordinates keyed by 'L5V' etc."""
    a = 0.35 if faded else 1.0
    sc = CAM_SCALE
    w, h = 27 * K * sc, 40.5 * K * sc
    p = 2.54 * K * sc
    y0 = y_bottom - h
    cv.rect(x0, y0, w, h, fc=C["pcb_cam"], ec="#000", lw=0.8, r=1.5, alpha=a)
    cv.rect(x0 + 6, y0 + 3, w - 12, 13, fc="#90A4AE", ec="#455A64", lw=0.5, r=1.0, alpha=a)  # shield
    cv.text(x0 + w / 2, y0 + 9.5, label, size=6.8, ha="center", color="#102027", weight="bold", alpha=a)
    cv.circle(x0 + w / 2, y0 + 24, 5.5, fc="#212121", ec="#9E9E9E", lw=0.8, alpha=a)  # OV2640 lens
    cv.circle(x0 + w / 2, y0 + 24, 2.8, fc="#37474F", ec="#000", lw=0.5, alpha=a)
    cv.text(x0 + w / 2, y0 + 32, "OV2640", size=6.2, ha="center", color="white", alpha=a)
    cv.rect(x0 + w / 2 - 2.5, y0 + 35, 5, 3, fc="#FFF59D", ec="#F9A825", lw=0.5, alpha=a)
    cv.text(x0 + w / 2, y0 + 41, "LED\nIO4", size=5.8, ha="center", color="#FFF59D", alpha=a)
    xl, xr = x0 + 4.2 * sc, x0 + w - 4.2 * sc
    pins = {}
    for i, (l, r) in enumerate(zip(CAM_LEFT, CAM_RIGHT)):
        y = y_bottom - 3.5 - i * p
        pins["L" + l + ("" if l != "GND" else "")] = (xl, y)
        pins["R" + r + (str(i) if r == "GND" else "")] = (xr, y)
        pin_pad(cv, xl, y, faded=faded)
        pin_pad(cv, xr, y, faded=faded)
        cv.text(xl + 2.4, y, l, size=6.5, color="white", alpha=a)
        cv.text(xr - 2.4, y, r, size=6.5, color="white", ha="right", alpha=a)
    return pins, (x0, y0, w, h)


def page_wiring_camera(highlight: set[str] | None = None, title_suffix="", step_note=None, panels=("A", "B", "C")) -> Canvas:
    cv = Canvas("wiring_camera")
    frame(cv, "SƠ ĐỒ LẮP ĐẶT · Thiết bị 2 — Camera giám sát (ESP32-CAM-001)" + title_suffix,
          "Camera không nối dây với trạm cảm biến; hai thiết bị chỉ liên lạc với backend qua Wi-Fi. "
          "Sơ đồ không chứng minh camera đã hoạt động.", "Wiring Diagram · Trang 2/2", show_sub=step_note is None)
    fade = (lambda cid: highlight is not None and cid not in highlight)
    # Panel A — ESP32-CAM-MB
    if "A" in panels:
        cv.rect(9, 27, 74, 140, fc=C["panel"], ec="#B0BEC5", lw=0.6, r=2)
        cv.text(13, 32, "A · NẠP FIRMWARE — đế ESP32-CAM-MB", size=8.5, weight="bold")
        verify_tag(cv, 13, 38, "Có dùng đế MB không?")
        pins, (x0, y0, w, h) = esp32cam(cv, 46 - 27 * K * CAM_SCALE / 2, 136, label="U2 cắm\nvào đế MB")
        cv.rect(16, 137, 60, 16, fc="#4E342E", ec="#000", lw=0.7, r=1.5)
        cv.text(46, 141, "Đế ESP32-CAM-MB (CH340)", size=6.8, ha="center", color="white", weight="bold")
        for bxp, lab in ((24, "IO0"), (68, "RST")):
            cv.circle(bxp, 146.5, 2.4, fc="#E0E0E0", ec="#000", lw=0.5)
            cv.text(bxp, 151, lab, size=6.2, ha="center", color="white")
        cv.rect(41, 150, 10, 5, fc="#B0BEC5", ec="#455A64", lw=0.5)
        cv.line([(46, 155), (46, 160)], color=C["USB"], lw=2.6, dash=True, outline=True)
        cv.text(46, 163, "USB → máy tính", size=7, ha="center")
        cv.text(13, 44, "Không cần dây rời. Nạp:\n1. Giữ IO0, nhấn-thả RST\n2. Thả IO0, bấm Upload\n"
                         "3. Xong: nhấn RST để chạy\n(một số đế tự vào chế độ nạp)", size=7, va="top")
    # Panel B — USB-TTL
    if "B" in panels:
        cv.rect(87, 27, 112, 140, fc=C["panel"], ec="#B0BEC5", lw=0.6, r=2)
        cv.text(91, 32, "B · NẠP FIRMWARE — adapter USB-TTL 3,3 V", size=8.5, weight="bold")
        verify_tag(cv, 91, 38, "Loại adapter đang dùng?")
        pins, (x0, y0, w, h) = esp32cam(cv, 122, 131)
        lc, rc = pins["L5V"][0], pins["R3V3"][0]
        # adapter
        ax_ = lc - 17
        cv.rect(ax_ - 6, 147, 62, 17, fc=C["pcb_ttl"], ec="#000", lw=0.7, r=1.2)
        cv.text(ax_ + 25, 157.5, "J2 · adapter USB-TTL", size=7, ha="center", color="white", weight="bold")
        cv.text(ax_ + 25, 161.2, "jumper mức logic ở 3,3 V (nếu có)", size=6, ha="center", color="white")
        names = ["GND", "5V", "TXD", "RXD", "3V3"]
        ap = {}
        for i, nme in enumerate(names):
            px = ax_ + i * 5.08 * 1.6
            pin_pad(cv, px, 150)
            cv.text(px, 153.3, nme, size=5.8, ha="center", color="white")
            ap[nme] = (px, 150)
        cv.text(ap["3V3"][0] + 3, 145, "3V3: không dùng", size=6, color=C["muted"])
        g_lane, v_lane = lc - 11, lc - 6
        cv.wire("C2", [ap["GND"], (ap["GND"][0], 141), (g_lane, 141), (g_lane, pins["LGND"][1]), pins["LGND"]],
                faded=fade("C2"), label_at=(g_lane, 132), dash=False)
        cv.wire("C1", [ap["5V"], (ap["5V"][0], 144), (v_lane, 144), (v_lane, pins["L5V"][1]), pins["L5V"]],
                faded=fade("C1"), label_at=(v_lane + 0.5, 137.5))
        tx_lane, rx_lane = rc + 12, rc + 18
        cv.wire("C3", [ap["TXD"], (ap["TXD"][0], 136), (tx_lane, 136), (tx_lane, pins["RU0R"][1]), pins["RU0R"]],
                faded=fade("C3"), label_at=(tx_lane, 110))
        cv.wire("C4", [ap["RXD"], (ap["RXD"][0], 140), (rx_lane, 140), (rx_lane, pins["RU0T"][1]), pins["RU0T"]],
                faded=fade("C4"), label_at=(rx_lane, 98))
        jx = rc + 5
        cv.wire("C5", [pins["RIO0"], (jx, pins["RIO0"][1]), (jx, pins["RGND3"][1]), pins["RGND3"]],
                faded=fade("C5"), label_at=(jx + 0.8,(pins["RIO0"][1] + pins["RGND3"][1]) / 2), dash=True)
        cv.text(rc + 21, pins["RIO0"][1] + 1, "C5: chỉ\nkhi nạp", size=6.5, color=C["BOOT"], weight="bold", va="center")
        cv.text(91, 44, "TX adapter → U0R, RX adapter ← U0T (nối chéo).\nIO0 nối GND rồi nhấn RST → chế độ nạp.",
                size=7, va="top")
        cv.text(91, 53.5, "VCC trên ESP32-CAM là ngõ RA — không cấp nguồn vào.", size=7, va="top", color=C["warn"],
                weight="bold")
    # Panel C — operation
    if "C" in panels:
        cv.rect(203, 27, 85, 140, fc=C["panel"], ec="#B0BEC5", lw=0.6, r=2)
        cv.text(207, 32, "C · VẬN HÀNH", size=8.5, weight="bold")
        pins, (x0, y0, w, h) = esp32cam(cv, 240, 131)
        lc = pins["L5V"][0]
        cv.rect(214, 147, 44, 12, fc="#ECEFF1", ec="#607D8B", lw=0.7, r=1.5)
        cv.text(236, 153, "PS2 · Nguồn 5 V ≥ 1 A", size=7, ha="center", weight="bold")
        g_lane, v_lane = lc - 11, lc - 6
        cv.wire("C7", [(222, 147), (222, 141), (g_lane, 141), (g_lane, pins["LGND"][1]), pins["LGND"]],
                faded=fade("C7"), label_at=(g_lane, 132))
        cv.wire("C6", [(229, 147), (229, 144), (v_lane, 144), (v_lane, pins["L5V"][1]), pins["L5V"]],
                faded=fade("C6"), label_at=(v_lane + 0.5, 137.5))
        verify_tag(cv, 207, 163, "Nguồn thực tế?")
        cv.text(207, 38, "1. Tháo dây C5 (IO0–GND).\n2. Tháo TX/RX nếu không cần log.\n3. Cấp 5 V, nhấn RST.\n"
                         "IO0 phải để hở: khi chạy GPIO0\nlà xung XCLK cấp cho camera.", size=7, va="top")
        wifi_link(cv, 216, 82, "", None, None)
        cv.text(207, 92, "Backend qua\nWi-Fi: đăng ký,\nheartbeat,\nảnh chụp", size=6.5, va="top", color="#00695C")
    legend(cv, 10, 176, [("5V", "5V — nguồn 5 V", False), ("GND", "GND — mass", False),
                         ("TX", "TXD adapter → U0R", False), ("RX", "RXD adapter ← U0T", False),
                         ("BOOT", "IO0–GND chỉ khi nạp (nét đứt)", True), ("USB", "Cáp USB", True),
                         ("WIFI", "Kết nối Wi-Fi (không có dây)", False)])
    wire_table(cv, 150, 176, ["C1", "C2", "C3", "C4", "C5", "C6", "C7"])
    if step_note:
        cv.text(PAGE_W / 2, 21.5, step_note, size=9, ha="center", weight="bold",
                box=dict(fc="#FFF8E1", ec=C["warn"], pad=0.5))
    return cv


# ══ Product B — electrical schematic ═════════════════════════════════════════════════════════════
def sym_gnd(cv: Canvas, x, y):
    cv.line([(x, y), (x, y + 3)], lw=0.9)
    for i, half in enumerate((3.2, 2.0, 0.8)):
        cv.line([(x - half, y + 3 + i * 1.2), (x + half, y + 3 + i * 1.2)], lw=0.9)


def sym_power(cv: Canvas, x, y, name):
    cv.line([(x, y), (x, y - 4)], lw=0.9)
    cv.line([(x - 3, y - 4), (x + 3, y - 4)], lw=1.2)
    cv.text(x, y - 7, name, size=7.5, ha="center", weight="bold", color=C["3V3"] if "3V3" in name else C["5V"])


def sym_resistor(cv: Canvas, x, y1, y2, name, value, dashed=False):
    """Vertical resistor between y1 (top) and y2 (bottom)."""
    mid = (y1 + y2) / 2
    zig = [(x, y1), (x, mid - 6)]
    for i in range(6):
        zig.append((x + (2 if i % 2 == 0 else -2), mid - 6 + (i + 0.5) * 2))
    zig += [(x, mid + 6), (x, y2)]
    cv.line(zig, lw=0.9, dash=dashed)
    cv.text(x + 4, mid - 2, name, size=7.5, weight="bold")
    cv.text(x + 4, mid + 2.5, value, size=7)


def sym_junction(cv: Canvas, x, y):
    cv.circle(x, y, 0.9, fc=C["ink"], ec=C["ink"])


def sym_nc(cv: Canvas, x, y):
    cv.line([(x - 1.8, y - 1.8), (x + 1.8, y + 1.8)], lw=0.9)
    cv.line([(x - 1.8, y + 1.8), (x + 1.8, y - 1.8)], lw=0.9)


def net_label(cv: Canvas, x, y, name, ha="left"):
    cv.text(x, y - 1.8, name, size=7, ha=ha, va="bottom", color="#0D47A1", weight="bold")


def ic_box(cv: Canvas, x, y, w, h, ref, name, pins_left=(), pins_right=(), pitch=7.0, top=8.0):
    cv.rect(x, y, w, h, fc="#FFFDE7", ec=C["ink"], lw=1.0)
    cv.text(x + w / 2, y - 4, ref, size=8.5, weight="bold", ha="center")
    cv.text(x + w / 2, y + h + 4, name, size=7, ha="center", color=C["muted"])
    pts = {}
    for i, (num, nm) in enumerate(pins_left):
        py = y + top + i * pitch
        cv.line([(x - 6, py), (x, py)], lw=0.9)
        cv.text(x + 1.5, py, nm, size=7)
        cv.text(x - 3, py - 1.6, num, size=5.8, ha="center", va="bottom", color=C["muted"])
        pts[nm] = (x - 6, py)
    for i, (num, nm) in enumerate(pins_right):
        py = y + top + i * pitch
        cv.line([(x + w, py), (x + w + 6, py)], lw=0.9)
        cv.text(x + w - 1.5, py, nm, size=7, ha="right")
        cv.text(x + w + 3, py - 1.6, num, size=5.8, ha="center", va="bottom", color=C["muted"])
        pts[nm] = (x + w + 6, py)
    return pts


def title_block(cv: Canvas, sheet, title):
    x, y = PAGE_W - 105, PAGE_H - 27
    cv.rect(x, y, 100, 22, fc="white", ec=C["ink"], lw=0.8)
    cv.line([(x, y + 8), (x + 100, y + 8)], lw=0.5)
    cv.line([(x, y + 15), (x + 100, y + 15)], lw=0.5)
    cv.text(x + 2, y + 4, f"DurianCare IoT — {title}", size=7.5, weight="bold")
    cv.text(x + 2, y + 11.5, f"Tờ {sheet}  ·  {REVISION}  ·  nguồn: generate_hardware_docs.py", size=6.5)
    cv.text(x + 2, y + 18.5, "Bản vẽ tài liệu, không phải bản vẽ sản xuất PCB", size=6.5, color=C["muted"])


def page_schematic_sensor() -> Canvas:
    cv = Canvas("schematic_sensor")
    frame(cv, "SƠ ĐỒ NGUYÊN LÝ · Tờ 1 — Trạm cảm biến môi trường", "Chỉ vẽ các chân được dùng. "
          "Nét đứt / ⚠ = chưa xác minh trên phần cứng thật.", "Electrical Schematic · Tờ 1/2")
    u1 = ic_box(cv, 90, 50, 46, 64, "U1", "ESP32 DevKit (ESP32-D0WD-V3), loại board CẦN XÁC NHẬN",
                pins_left=[("USB", "USB 5V"), ("VIN", "VIN"), ("GND", "GND")],
                pins_right=[("3V3", "3V3"), ("D18", "GPIO18"), ("D34", "GPIO34/ADC1_6"), ("GND", "GND ")],
                pitch=12, top=10)
    cv.text(113, 92, "LDO 3,3 V trên board\n(AMS1117, giả định)", size=6.5, ha="center", color=C["muted"], va="top")
    # USB supply
    cv.rect(30, 52, 26, 14, fc="white", ec=C["ink"], lw=0.9, dash=True)
    cv.text(43, 56.5, "PS1", size=7.5, weight="bold", ha="center")
    cv.text(43, 61.5, "USB 5 V", size=6.5, ha="center")
    cv.line([(56, u1["USB 5V"][1]), u1["USB 5V"]], lw=0.9, dash=True)
    net_label(cv, 60, u1["USB 5V"][1], "+5V_USB")
    sym_nc(cv, *u1["VIN"])
    cv.text(u1["VIN"][0] - 3, u1["VIN"][1] + 3.5, "VIN không dùng (cấp qua USB)", size=6, ha="right", color=C["muted"])
    sym_gnd(cv, u1["GND"][0] - 4, u1["GND"][1])
    cv.line([u1["GND"], (u1["GND"][0] - 4, u1["GND"][1])], lw=0.9)

    # +3V3 rail
    x3 = u1["3V3"][0]
    rail_y = u1["3V3"][1]
    cv.line([u1["3V3"], (214, rail_y)], lw=1.1, color=C["3V3"])
    sym_power(cv, 152, rail_y, "+3V3")
    sym_junction(cv, 152, rail_y)

    # DHT22 S1
    s1 = ic_box(cv, 225, 76, 36, 30, "S1", "DHT22 / AM2302 (module 3 chân?)",
                pins_left=[("1", "VCC"), ("2", "DATA"), ("4", "GND")], pitch=8, top=7)
    cv.line([(214, rail_y), (214, s1["VCC"][1]), s1["VCC"]], lw=1.1, color=C["3V3"])
    # DATA net with R1 pull-up
    data_y = s1["DATA"][1]
    cv.line([u1["GPIO18"], (142 + 18, u1["GPIO18"][1]), (160, data_y), s1["DATA"]], lw=1.1)
    net_label(cv, 163, data_y, "DHT_DATA")
    sym_junction(cv, 196, data_y)
    sym_resistor(cv, 196, rail_y, data_y, "", "", dashed=True)
    sym_junction(cv, 196, rail_y)
    cv.text(192, (rail_y + data_y) / 2 - 3, "R1 10 kΩ", size=7.5, weight="bold", ha="right")
    cv.text(192, (rail_y + data_y) / 2 + 2, "(datasheet: 5,1 kΩ)\nchỉ lắp nếu S1 là\ncảm biến 4 chân trần", size=6,
            ha="right", va="top", color=C["verify"])
    sym_gnd(cv, s1["GND"][0] - 3, s1["GND"][1] + 3)
    cv.line([s1["GND"], (s1["GND"][0] - 3, s1["GND"][1]), (s1["GND"][0] - 3, s1["GND"][1] + 3)], lw=0.9)

    # Soil S2
    s2 = ic_box(cv, 225, 128, 36, 38, "S2", "Module ẩm đất LM393 + đầu dò P1 (YL-69?)",
                pins_left=[("1", "VCC"), ("2", "GND"), ("3", "DO"), ("4", "AO")], pitch=8, top=7)
    cv.line([s2["VCC"], (s2["VCC"][0] - 8, s2["VCC"][1])], lw=1.1, color=C["3V3"])
    sym_power(cv, s2["VCC"][0] - 8, s2["VCC"][1], "+3V3")
    gx = s2["GND"][0] - 6
    cv.line([s2["GND"], (gx, s2["GND"][1])], lw=0.9)
    sym_gnd(cv, gx, s2["GND"][1])
    sym_nc(cv, *s2["DO"])
    cv.line([u1["GPIO34/ADC1_6"], (150, u1["GPIO34/ADC1_6"][1]), (150, s2["AO"][1]), s2["AO"]], lw=1.1)
    net_label(cv, 155, s2["AO"][1], "SOIL_AO (0–3,3 V)")
    sym_gnd(cv, u1["GND "][0] + 4, u1["GND "][1])
    cv.line([u1["GND "], (u1["GND "][0] + 4, u1["GND "][1])], lw=0.9)
    # probe
    cv.rect(270, 140, 18, 18, fc="white", ec=C["ink"], lw=0.9, dash=True)
    cv.text(279, 145, "P1", size=7.5, weight="bold", ha="center")
    cv.text(279, 150, "đầu dò\n2 cực", size=6, ha="center", va="top")
    cv.line([(261, 146), (270, 146)], lw=0.9, dash=True)
    cv.line([(261, 152), (270, 152)], lw=0.9, dash=True)

    # Notes
    notes = [
        "Ghi chú kỹ thuật",
        "1. Mức logic ESP32 = 3,3 V; các chân GPIO không chịu 5 V.",
        "2. GPIO34: ngõ vào, không có điện trở kéo nội; ADC1 dùng được khi Wi-Fi bật.",
        "   Arduino-ESP32 mặc định suy hao 11 dB: đọc tới ~3,1 V; tuyến tính tốt ~0,15–2,45 V.",
        "3. AO tỉ lệ với VCC của S2 → S2 phải cấp 3V3 để AO ≤ 3,3 V.",
        "4. Firmware: map(raw, 3200 → 0 %, 1200 → 100 %), hiệu chuẩn thực nghiệm.",
        "5. DHT22: 3,3–5,5 V, chu kỳ đọc ≥ 2 s; datasheet khuyên kéo lên 5,1 kΩ (thường dùng 10 kΩ).",
        "6. Chưa xác minh: loại DevKit, cách chia 3V3 (J1 trên wiring), S1 3 hay 4 chân, nguồn S1/S2, loại P1.",
    ]
    for i, n in enumerate(notes):
        cv.text(12, 128 + i * 5.2, n, size=7 if i else 8, weight="bold" if i == 0 else "normal")
    title_block(cv, "1/2", "Trạm cảm biến")
    return cv


def page_schematic_camera() -> Canvas:
    cv = Canvas("schematic_camera")
    frame(cv, "SƠ ĐỒ NGUYÊN LÝ · Tờ 2 — Camera giám sát ESP32-CAM", "Khối nét đứt bên trong U2 là linh kiện có sẵn "
          "trên board AI-Thinker (không đi dây). JP1 đóng khi nạp, mở khi chạy.", "Electrical Schematic · Tờ 2/2")
    u2 = ic_box(cv, 120, 45, 60, 86, "U2", "AI-Thinker ESP32-CAM (ESP32-S + PSRAM)",
                pins_left=[("5V", "5V"), ("GND", "GND"), ("3V3", "3V3"), ("VCC", "VCC (ra)")],
                pins_right=[("U0R", "U0R / GPIO3"), ("U0T", "U0T / GPIO1"), ("IO0", "IO0 / GPIO0"), ("GND", "GND ")],
                pitch=12, top=12)
    # internal blocks
    cv.rect(126, 96, 48, 30, fc="none", ec=C["muted"], lw=0.7, dash=True)
    cv.text(150, 100, "Trên board (cố định)", size=6.5, ha="center", color=C["muted"], weight="bold")
    cv.text(128, 104.5, "OV2640 (DVP) Y2–Y9:\n5, 18, 19, 21, 36, 39, 34, 35\nXCLK 0 · PCLK 22 · VSYNC 25\n"
                        "HREF 23 · SDA 26 · SCL 27\nPWDN 32 · RESET −1 · LED GPIO4", size=5.8, va="top")
    # power in
    cv.rect(40, 50, 30, 16, fc="white", ec=C["ink"], lw=0.9, dash=True)
    cv.text(55, 55, "PS2 (chạy)", size=7, weight="bold", ha="center")
    cv.text(55, 60.5, "5 V ≥ 1 A ⚠", size=6.5, ha="center", color=C["verify"])
    cv.line([(70, u2["5V"][1]), u2["5V"]], lw=1.1, color=C["5V"])
    sym_junction(cv, 88, u2["5V"][1])
    sym_power(cv, 88, u2["5V"][1], "+5V")
    cv.line([(70, 63), (78, 63), (78, 75)], lw=0.9)
    sym_gnd(cv, 78, 75)
    cv.line([u2["GND"], (104, u2["GND"][1])], lw=0.9)
    sym_gnd(cv, 104, u2["GND"][1])
    sym_nc(cv, *u2["3V3"])
    sym_nc(cv, *u2["VCC (ra)"])
    cv.text(u2["VCC (ra)"][0] - 3, u2["VCC (ra)"][1] + 4, "VCC là ngõ ra 3,3 V (hoặc 5 V tùy jumper) —\nkhông cấp nguồn vào",
            size=6, ha="right", color=C["warn"], va="top")
    # UART header J2 (flash only)
    j2x = 236
    cv.rect(j2x, 50, 34, 44, fc="white", ec=C["ink"], lw=0.9)
    cv.text(j2x + 17, 46, "J2 · USB-TTL 3,3 V (chỉ khi nạp)", size=7.5, weight="bold", ha="center")
    jp = {}
    for i, nm in enumerate(["TXD", "RXD", "5V", "GND"]):
        py = [u2["U0R / GPIO3"][1], u2["U0T / GPIO1"][1], 84, 90][i]
        cv.line([(j2x - 6, py), (j2x, py)], lw=0.9)
        cv.text(j2x + 1.5, py, nm, size=7)
        jp[nm] = (j2x - 6, py)
    cv.line([u2["U0R / GPIO3"], jp["TXD"]], lw=1.1, color=C["TX"])
    net_label(cv, 196, u2["U0R / GPIO3"][1], "UART_RX ← TXD")
    cv.line([u2["U0T / GPIO1"], jp["RXD"]], lw=1.1, color=C["RX"])
    net_label(cv, 196, u2["U0T / GPIO1"][1], "UART_TX → RXD")
    cv.line([jp["GND"], (226, 90)], lw=0.9)
    sym_gnd(cv, 226, 90)
    cv.line([jp["5V"], (226, 84)], lw=0.9, dash=True)
    sym_power(cv, 226, 84, "+5V")
    cv.text(j2x + 17, 98, "+5V từ adapter (dây C1) chỉ dùng khi\nadapter cấp đủ dòng — nếu không, dùng PS2", size=6,
            va="top", ha="center", color=C["muted"])
    # JP1
    io0 = u2["IO0 / GPIO0"]
    cv.line([io0, (200, io0[1])], lw=1.1, color=C["BOOT"])
    cv.circle(202, io0[1], 1.2, fc="white", ec=C["ink"])
    cv.circle(210, io0[1], 1.2, fc="white", ec=C["ink"])
    cv.line([(203.2, io0[1] - 0.8), (209, io0[1] - 4)], lw=0.9, dash=True)
    cv.line([(211.2, io0[1]), (216, io0[1]), (216, io0[1] + 4)], lw=0.9)
    sym_gnd(cv, 216, io0[1] + 4)
    cv.text(204, io0[1] + 5, "JP1 · IO0–GND", size=7, weight="bold", ha="center")
    cv.text(204, io0[1] + 9, "đóng: nạp · mở: chạy", size=6.2, ha="center", color=C["BOOT"], weight="bold")
    cv.line([u2["GND "], (194, u2["GND "][1])], lw=0.9)
    sym_gnd(cv, 194, u2["GND "][1])
    notes = [
        "Ghi chú kỹ thuật",
        "1. Nguồn vào chân 5V qua LDO trên board; Wi-Fi + camera có đỉnh dòng vài trăm mA,",
        "   LED flash GPIO4 tăng thêm → nguồn yếu gây brownout / reset liên tục.",
        "2. UART0 dùng để nạp (115200 baud log). TX/RX nối chéo, mức 3,3 V.",
        "3. GPIO0 vừa là chân chọn chế độ khởi động vừa là XCLK camera → JP1 phải mở khi chạy.",
        "4. GPIO12 (MTDI) là chân strapping điện áp flash — không kéo lên khi khởi động.",
        "5. Firmware CameraWebServer: CAMERA_MODEL_AI_THINKER, PSRAM, không dùng thẻ SD.",
        "6. Chưa xác minh: loại adapter/đế nạp, nguồn PS2, cảm biến OV2640 (chưa đọc PID).",
    ]
    for i, n in enumerate(notes):
        cv.text(12, 140 + i * 5.0, n, size=7 if i else 8, weight="bold" if i == 0 else "normal")
    title_block(cv, "2/2", "Camera")
    return cv


# ══ Tables, checks and build ═════════════════════════════════════════════════════════════════════
def firmware_check():
    results = []
    if FIRMWARE_SENSOR.exists():
        src = FIRMWARE_SENSOR.read_text(encoding="utf-8", errors="replace")
        for name, want in FIRMWARE_CLAIMS.items():
            m = re.search(rf"#define\s+{name}\s+(\d+)", src)
            got = int(m.group(1)) if m else None
            results.append(("sketch_jul25a.ino", name, want, got, got == want))
    else:
        results.append(("sketch_jul25a.ino", "(không tìm thấy file)", None, None, None))
    if FIRMWARE_CAMERA_PINS.exists():
        src = FIRMWARE_CAMERA_PINS.read_text(encoding="utf-8", errors="replace")
        block = src.split("defined(CAMERA_MODEL_AI_THINKER)", 1)[-1].split("#elif", 1)[0]
        for name, want in CAMERA_CLAIMS.items():
            m = re.search(rf"#define\s+{name}\s+(-?\d+)", block)
            got = int(m.group(1)) if m else None
            results.append(("camera_pins.h (AI_THINKER)", name, want, got, got == want))
    else:
        results.append(("camera_pins.h", "(không tìm thấy file)", None, None, None))
    return results


def write_tables():
    with open(OUT / "DurianCare_Pin_Mapping.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["Dây", "Thiết bị", "Linh kiện nguồn", "Chân nguồn", "Linh kiện đích", "Chân đích", "Net",
                    "Loại kết nối", "Trạng thái", "Lưu ý"])
        for c in CONNECTIONS:
            w.writerow([c["id"], c["dev"], c["a"][0], c["a"][1], c["b"][0], c["b"][1], c["net"], c["kind"],
                        c["status"], c["note"]])
    bom = [
        ("U1", "Board phát triển ESP32 (chip ESP32-D0WD-V3), dự kiến DevKit V1 30 chân", 1, "Chip đã xác minh (esptool); loại board CẦN XÁC NHẬN"),
        ("S1", "Cảm biến nhiệt độ/độ ẩm DHT22 / AM2302", 1, "Đã xác minh (firmware + dữ liệu thật); dạng module CẦN XÁC NHẬN"),
        ("R1", "Điện trở 10 kΩ 1/4 W (kéo lên DATA)", "0–1", "Chỉ khi S1 là cảm biến 4 chân trần"),
        ("S2", "Module so sánh LM393 cho cảm biến ẩm đất (AO/DO)", 1, "Chân AO đã khai báo trong firmware; dây CẦN XÁC NHẬN"),
        ("P1", "Đầu dò ẩm đất 2 cực (YL-69 theo báo cáo B)", 1, "CẦN XÁC NHẬN"),
        ("J1", "Cách chia 3V3 cho S1 và S2 (breadboard / dây chữ Y / mối hàn)", 1, "CẦN XÁC NHẬN — không thêm vào BOM khi chưa rõ"),
        ("PS1", "Nguồn USB 5 V + cáp micro-USB cho U1", 1, "CẦN XÁC NHẬN"),
        ("W1–W7", "Dây jumper (cái–cái nếu cắm thẳng header)", 7, "Loại đầu cắm CẦN XÁC NHẬN"),
        ("U2", "AI-Thinker ESP32-CAM (OV2640, PSRAM, LED flash)", 1, "Xác minh gián tiếp (backend nhận ảnh 19/10/2026)"),
        ("J2", "Adapter USB-TTL 3,3 V hoặc đế ESP32-CAM-MB", 1, "CẦN XÁC NHẬN loại"),
        ("JP1", "Dây jumper IO0–GND (chỉ khi nạp, nếu dùng USB-TTL)", 1, "Không cần nếu dùng đế MB"),
        ("PS2", "Nguồn 5 V ≥ 1 A cho ESP32-CAM khi vận hành", 1, "CẦN XÁC NHẬN"),
        ("C1–C4, C6–C7", "Dây jumper cái–cái", 6, "Theo phương án nạp/nguồn"),
    ]
    with open(OUT / "DurianCare_BOM.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["Mã", "Linh kiện", "Số lượng", "Trạng thái / bằng chứng"])
        w.writerows(bom)
    return bom


# How each connection must appear on the schematic: (sheet, labels that must be present as text).
SCHEMATIC_EXPECT = {
    "W1": (0, ["U1", "3V3", "+3V3"]), "W2": (0, ["S1", "VCC", "+3V3"]), "W3": (0, ["S2", "VCC", "+3V3"]),
    "W4": (0, ["S1", "GND"]), "W5": (0, ["S2", "GND"]), "W6": (0, ["GPIO18", "DATA", "DHT_DATA"]),
    "W7": (0, ["GPIO34/ADC1_6", "AO", "SOIL_AO (0–3,3 V)"]), "W8": (0, ["P1"]), "W9": (0, ["PS1", "+5V_USB"]),
    "C1": (1, ["5V", "+5V"]), "C2": (1, ["GND"]), "C3": (1, ["TXD", "U0R / GPIO3", "UART_RX ← TXD"]),
    "C4": (1, ["RXD", "U0T / GPIO1", "UART_TX → RXD"]), "C5": (1, ["JP1 · IO0–GND", "IO0 / GPIO0"]),
    "C6": (1, ["PS2 (chạy)", "+5V"]), "C7": (1, ["GND"]),
}


def consistency_report(wiring: list[Canvas], schem: list[Canvas], fw):
    drawn = {cid for cv in wiring for cid in cv.wires}
    texts = [{it[3] for it in cv.items if it[0] == "text"} for cv in schem]
    rows = []
    for c in CONNECTIONS:
        sheet, labels = SCHEMATIC_EXPECT[c["id"]]
        missing = [l for l in labels if l not in texts[sheet]]
        rows.append((c, c["id"] in drawn, not missing, missing))
    out = ["# Đối chiếu Wiring ↔ Schematic ↔ Firmware", "",
           "> Tệp này do `generate_hardware_docs.py` sinh ra mỗi lần chạy. Wiring và schematic vẽ từ cùng một "
           "netlist (`CONNECTIONS`); bước kiểm tra bên dưới xác nhận mỗi dây có mặt trên wiring, các nhãn chân/net "
           "tương ứng có mặt trên schematic, và GPIO trong tài liệu khớp với `#define` trong firmware.", "",
           "## 1. Từng dây", "", "| Dây | Từ | Đến | Net | Wiring | Schematic | Trạng thái phần cứng |",
           "|---|---|---|---|---|---|---|"]
    for c, on_wiring, on_schem, missing in rows:
        out.append(f"| {c['id']} | {c['a'][0]} {c['a'][1]} | {c['b'][0]} {c['b'][1]} | `{c['net']}` | "
                   f"{'có' if on_wiring else '**THIẾU**'} | {'có' if on_schem else '**THIẾU** ' + ', '.join(missing)} | "
                   f"{STATUS_VI[c['status']]} |")
    out += ["", "## 2. GPIO so với firmware", "", "| Nguồn | Hằng số | Tài liệu | Firmware | Kết quả |", "|---|---|---|---|---|"]
    for src, name, want, got, ok in fw:
        res = "khớp" if ok else ("**KHÔNG KHỚP**" if ok is False else "bỏ qua (không có file)")
        out.append(f"| {src} | `{name}` | {want} | {got} | {res} |")
    out += ["", "## 3. Bố cục", "", f"- Dây giao nhau trên wiring diagram: {'không có' if not any(crossings(cv.wires) for cv in wiring) else 'CÓ'} "
            "(kiểm tra tự động trên các đoạn dây vuông góc).", ""]
    (OUT / "DurianCare_Consistency_Check.md").write_text("\n".join(out), encoding="utf-8")
    return [(c["id"], w and sc) for c, w, sc, _ in rows]


STATUS_VI = {"VERIFIED_FW": "đã xác minh (firmware)", "REQUIRED": "bắt buộc về điện, cách nối chưa kiểm tra",
             "UNVERIFIED": "CẦN XÁC NHẬN"}


def build():
    STEPS.mkdir(exist_ok=True)
    wiring = [page_wiring_sensor(), page_wiring_camera()]
    schem = [page_schematic_sensor(), page_schematic_camera()]

    problems = []
    for cv in wiring:
        x = crossings(cv.wires)
        if x:
            problems.append(f"{cv.name}: dây giao nhau {x}")
    if problems:
        raise SystemExit("\n".join(problems))

    for stem, pages, names in (("DurianCare_Wiring_Diagram", wiring, ["Tram cam bien", "Camera"]),
                               ("DurianCare_Electrical_Schematic", schem, ["To 1 - Tram cam bien", "To 2 - Camera"])):
        with PdfPages(OUT / f"{stem}.pdf") as pdf:
            for i, cv in enumerate(pages, 1):
                fig = render_mpl(cv, stem)
                pdf.savefig(fig)
                fig.savefig(OUT / f"{stem}_p{i}.png", dpi=300)
                fig.savefig(OUT / f"{stem}_p{i}.svg")
                plt.close(fig)
        write_drawio(OUT / f"{stem}.drawio", [(cv, f"p{i}", n) for i, (cv, n) in enumerate(zip(pages, names), 1)])

    steps = [
        ("step1_gnd", page_wiring_sensor({"W4", "W5"}, " — Bước 3a",
                                         "BƯỚC 3a · Nối GND trước: W4 (GND phải → S1 −), W5 (GND trái → S2 GND)")),
        ("step2_3v3", page_wiring_sensor({"W1", "W2", "W3"}, " — Bước 3b",
                                         "BƯỚC 3b · Nối 3V3: W1 → điểm chia J1 → W2 (S1 +), W3 (S2 VCC). KHÔNG dùng VIN/5V")),
        ("step3_signals", page_wiring_sensor({"W6", "W7", "W8"}, " — Bước 4",
                                             "BƯỚC 4 · Tín hiệu: W6 D18 → DATA, W7 D34 → AO, W8 cáp đầu dò")),
        ("step4_cam_flash", page_wiring_camera({"C1", "C2", "C3", "C4", "C5"}, " — Bước 6",
                                               "BƯỚC 6 · Chế độ NẠP: C1–C4 tới adapter, C5 nối IO0–GND", panels=("A", "B"))),
        ("step5_cam_run", page_wiring_camera({"C6", "C7"}, " — Bước 7",
                                             "BƯỚC 7 · VẬN HÀNH: tháo C5 (và C3/C4), cấp 5 V qua C6/C7, nhấn RST",
                                             panels=("C",))),
    ]
    for name, cv, *_ in [(s[0], s[1]) for s in steps]:
        fig = render_mpl(cv, name)
        fig.savefig(STEPS / f"{name}.png", dpi=170)
        plt.close(fig)

    bom = write_tables()
    fw = firmware_check()
    drawn = consistency_report(wiring, schem, fw)
    return fw, drawn, bom


if __name__ == "__main__":
    fw, drawn, _ = build()
    print("Wiring crossings: none")
    print("Wiring + schematic:", ", ".join(f"{cid}{'' if ok else ' (THIẾU)'}" for cid, ok in drawn))
    for src, name, want, got, ok in fw:
        flag = "OK" if ok else ("KHÔNG KHỚP" if ok is False else "BỎ QUA")
        print(f"[{flag}] {src}: {name} tài liệu={want} firmware={got}")
