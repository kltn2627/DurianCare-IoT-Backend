"""DurianCare IoT schematic — single source of truth.

Generates, from the component/pin/wire description below:
  durian_iot_schematic.drawio   editable in diagrams.net (draw.io desktop or app.diagrams.net)
  durian_iot_schematic.png      300-dpi export
  durian_iot_schematic.svg      vector export

Run:  python docs/hardware/generate_schematic.py
Edit the COMPONENTS / WIRES / NETS lists (or edit the .drawio directly and stop regenerating).

Line style: solid = verified in firmware (and, where noted, by runtime data); dashed = assumed / unverified.
"""
import os
import xml.etree.ElementTree as ET

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import Rectangle  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
W, H = 1700, 1080  # canvas units (px at 1:1 in draw.io)

# ── Components: box (x, y, w, h) and pins (name, side, offset from top, verified) ──────────────
COMPONENTS = [
    {"id": "U1", "title": "U1  ESP32 DevKit (ESP32-D0WD-V3)", "sub": "esp32-01 · firmware sketch_jul25a 1.3.0",
     "box": (560, 230, 220, 420),
     "pins": [("3V3", "L", 50), ("GND", "L", 110), ("GPIO18", "L", 190), ("GPIO34 / ADC1_CH6", "L", 300),
              ("VIN 5V", "R", 50)]},
    {"id": "S1", "title": "S1  DHT22 (AM2302)", "sub": "nhiệt độ + độ ẩm không khí",
     "box": (150, 230, 170, 150),
     "pins": [("VCC", "R", 40), ("DATA", "R", 80), ("GND", "R", 120)]},
    {"id": "R1", "title": "R1  10 kΩ", "sub": "kéo lên DATA (?)",
     "box": (400, 190, 36, 70), "pins": [], "dashed": True, "title_xy": (446, 214)},
    {"id": "S2", "title": "S2  Cảm biến độ ẩm đất", "sub": "đầu dò YL-69 + bo so sánh",
     "box": (150, 450, 170, 190),
     "pins": [("VCC", "R", 40), ("GND", "R", 80), ("AO", "R", 120), ("DO", "R", 160)]},
    {"id": "P1", "title": "P1  Nguồn USB 5 V", "sub": "cáp micro-USB (chưa xác minh)",
     "box": (840, 470, 150, 60), "pins": [], "dashed": True, "title_xy": (840, 556)},
    # Camera node — a different device and firmware
    {"id": "U2", "title": "U2  ESP32-CAM (AI-Thinker)", "sub": "ESP32-CAM-001 · OV2640 + PSRAM · firmware 2.1.0",
     "box": (1320, 230, 230, 360),
     "pins": [("5V", "L", 50), ("GND", "L", 100), ("U0R / GPIO3", "L", 170), ("U0T / GPIO1", "L", 220),
              ("IO0", "L", 290)]},
    {"id": "J1", "title": "J1  Bộ nạp USB-UART", "sub": "ESP32-CAM-MB / USB-TTL (?)",
     "box": (1070, 330, 140, 220), "pins": [], "dashed": True},
    {"id": "P2", "title": "P2  Nguồn 5 V ≥ 500 mA", "sub": "(chưa xác minh)",
     "box": (1070, 170, 140, 60), "pins": [], "dashed": True},
]

# Power/net symbols attached to a pin: (component, pin, net, verified)
NETS = [
    ("S1", "VCC", "3V3", False), ("S1", "GND", "GND", False),
    ("S2", "VCC", "3V3", False), ("S2", "GND", "GND", False),
    ("U1", "3V3", "3V3", True), ("U1", "GND", "GND", True), ("U2", "GND", "GND", False),
]

# Signal wires: (from comp, from pin, to comp, to pin, verified, waypoints)
WIRES = [
    ("S1", "DATA", "U1", "GPIO18", True, []),
    ("S2", "AO", "U1", "GPIO34 / ADC1_CH6", True, []),
    ("P1", None, "U1", "VIN 5V", False, [(935, 280)]),
    ("P2", None, "U2", "5V", False, []),
    ("J1", None, "U2", "U0R / GPIO3", False, []),
    ("J1", None, "U2", "U0T / GPIO1", False, []),
    ("J1", None, "U2", "IO0", False, []),
]

# Free-drawn dashed links (pull-up resistor) and not-connected markers
EXTRA_DASHED = [  # polylines in canvas coordinates
    [(418, 310), (418, 260)],                                # DATA junction → R1
    [(418, 190), (418, 172)],                                # R1 → 3V3
]
R1_SUPPLY = (418, 172)  # 3V3 symbol position for the pull-up
JUNCTIONS = [(418, 310)]
NOT_CONNECTED = [("S2", "DO")]
IO0_NOTE = (1070, 590, "IO0 nối GND khi nạp, để hở khi chạy")

SECTIONS = [
    ((40, 70, 990, 640), "THIẾT BỊ 1 — TRẠM CẢM BIẾN MÔI TRƯỜNG (esp32-01)"),
    ((1050, 70, 610, 640), "THIẾT BỊ 2 — CAMERA GIÁM SÁT (ESP32-CAM-001)"),
]

NOTES = [
    "Nét liền: chân đã khai báo trong firmware. DHT22→GPIO18 đã có dữ liệu thật (33 °C / 81 %, 22/10/2026).",
    "Nét đứt / dấu (?): nguồn cấp, điện trở kéo lên, bộ nạp — chưa có bằng chứng, cần xác nhận trên mạch thật.",
    "S2 phải cấp 3,3 V: nếu cấp 5 V, AO có thể vượt 3,3 V của ADC ESP32. Log ghi raw = 0 gần như mọi lần đọc → kiểm tra dây AO/VCC.",
    "GPIO34 thuộc ADC1, chỉ nhập, không phải chân strapping; dùng được khi Wi-Fi bật (ADC2 thì không).",
    "U2 không nối cảm biến ngoài. Các chân camera OV2640 (DVP) là cố định trên board AI-Thinker, xem bảng pin trong HARDWARE.md.",
    "Báo cáo B ghi DHT22 ở GPIO23 và cảm biến mưa ở GPIO35: firmware hiện tại không dùng hai chân này.",
]


def pin_point(comp, pin_name):
    c = next(c for c in COMPONENTS if c["id"] == comp)
    x, y, w, h = c["box"]
    if pin_name is None:  # right/left edge middle for adapters/power boxes
        return (x + w, y + h / 2) if comp in ("P1",) else (x + w, y + h / 2)
    for name, side, off, *_ in c["pins"]:
        if name == pin_name:
            return (x, y + off) if side == "L" else (x + w, y + off)
    raise KeyError(f"{comp}.{pin_name}")


def wire_path(frm, fpin, to, tpin, waypoints):
    a = pin_point(frm, fpin)
    b = pin_point(to, tpin)
    if frm == "P1":
        px, py, pw, ph = next(c for c in COMPONENTS if c["id"] == "P1")["box"]
        return [(px + pw / 2, py), (px + pw / 2, b[1]), (b[0] + 14, b[1])]
    if frm == "J1":
        jx, jy, jw, jh = next(c for c in COMPONENTS if c["id"] == "J1")["box"]
        return [(jx + jw, b[1]), (b[0] - 14, b[1])]
    if frm == "P2":
        px, py, pw, ph = next(c for c in COMPONENTS if c["id"] == "P2")["box"]
        return [(px + pw, py + 30), (b[0] - 50, py + 30), (b[0] - 50, b[1]), (b[0] - 14, b[1])]
    mid = (a[0] + b[0]) / 2
    return [a, (mid, a[1]), (mid, b[1]), b] + []


# ── Rendering (matplotlib) ──────────────────────────────────────────────────────────────────
def render(path_png, path_svg):
    fig = plt.figure(figsize=(W / 100, H / 100), dpi=100)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_xlim(0, W)
    ax.set_ylim(H, 0)
    ax.axis("off")
    red, blue, green, grey = "#8B1A1A", "#1F3A93", "#1E7B34", "#555555"
    ax.add_patch(Rectangle((15, 15), W - 30, H - 30, fill=False, ec=red, lw=1.5))
    for (x, y, w, h), label in SECTIONS:
        ax.add_patch(Rectangle((x, y), w, h, fill=False, ec=grey, lw=1, ls=(0, (2, 3))))
        ax.text(x + 12, y + 22, label, fontsize=11, weight="bold", color=grey)
    for c in COMPONENTS:
        x, y, w, h = c["box"]
        ax.add_patch(Rectangle((x, y), w, h, fill=True, fc="#FFF8F0", ec=red, lw=1.6, ls="--" if c.get("dashed") else "-"))
        tx0, ty0 = c.get("title_xy", (x, y - 22))
        ax.text(tx0, ty0, c["title"], fontsize=10.5, weight="bold", color=blue)
        ax.text(tx0, ty0 + 14, c["sub"], fontsize=8.5, color=grey)
        for name, side, off in c["pins"]:
            px = x if side == "L" else x + w
            ax.plot([px - 14 if side == "L" else px, px if side == "L" else px + 14], [y + off] * 2, color=red, lw=1.4)
            ax.text(px + 8 if side == "L" else px - 8, y + off + 4, name, fontsize=9, color=blue,
                    ha="left" if side == "L" else "right")
    for frm, fpin, to, tpin, verified, wps in WIRES:
        pts = wire_path(frm, fpin, to, tpin, wps)
        ax.plot([p[0] for p in pts], [p[1] for p in pts], color=green, lw=1.8, ls="-" if verified else "--")
    for poly in EXTRA_DASHED:
        ax.plot([p[0] for p in poly], [p[1] for p in poly], color=green, lw=1.6, ls="--")
    rx, ry = R1_SUPPLY
    ax.plot([rx - 8, rx, rx + 8], [ry + 8, ry, ry + 8], color=red, lw=1.4)
    ax.text(rx, ry - 6, "3V3", fontsize=9, weight="bold", color=red, ha="center")
    for jx, jy in JUNCTIONS:
        ax.plot([jx], [jy], marker="o", markersize=5, color=green)
    for comp, pin, net, verified in NETS:
        px, py = pin_point(comp, pin)
        c = next(cc for cc in COMPONENTS if cc["id"] == comp)
        side = next(s for n, s, *_ in c["pins"] if n == pin)
        sx = px + 14 if side == "R" else px - 14
        ex = sx + 40 if side == "R" else sx - 40
        ax.plot([sx, ex], [py, py], color=green, lw=1.6, ls="-" if verified else "--")
        if net == "GND":
            for i, half in enumerate((10, 6, 2)):
                ax.plot([ex - half, ex + half], [py + 6 + i * 4] * 2, color=red, lw=1.4)
            ax.plot([ex, ex], [py, py + 6], color=red, lw=1.4)
        else:
            ax.plot([ex - 8, ex, ex + 8], [py + 8, py, py + 8], color=red, lw=1.4)
            ax.text(ex, py - 6, net, fontsize=9, weight="bold", color=red, ha="center")
    for comp, pin in NOT_CONNECTED:
        px, py = pin_point(comp, pin)
        ax.plot([px + 10, px + 22], [py - 6, py + 6], color=red, lw=2)
        ax.plot([px + 10, px + 22], [py + 6, py - 6], color=red, lw=2)
        ax.text(px + 28, py + 4, "không dùng", fontsize=8, color=grey)
    nx, ny, note = IO0_NOTE
    ax.text(nx, ny, note, fontsize=8.5, color=grey)
    jx, jy, jw, jh = next(c for c in COMPONENTS if c["id"] == "J1")["box"]
    for label, pin in (("TX → U0R", "U0R / GPIO3"), ("RX ← U0T", "U0T / GPIO1"), ("GND (jumper)", "IO0")):
        ax.text(jx + jw + 6, pin_point("U2", pin)[1] - 6, label, fontsize=8, color=grey)
    # Notes + title block
    ax.text(40, 740, "GHI CHÚ", fontsize=11, weight="bold", color=red)
    for i, note in enumerate(NOTES):
        ax.text(40, 765 + i * 24, f"{i + 1}. {note}", fontsize=9, color="#222222")
    tx, ty = 1180, 920
    ax.add_patch(Rectangle((tx, ty), 490, 140, fill=False, ec=red, lw=1.3))
    ax.text(tx + 10, ty + 28, "TITLE:  DurianCare IoT — Sơ đồ nguyên lý", fontsize=11, weight="bold", color=blue)
    ax.text(tx + 10, ty + 58, "REV: 1.0 (bản xác minh theo firmware)", fontsize=9.5, color="#222222")
    ax.text(tx + 10, ty + 84, "Ngày: 28/10/2026 · Sheet 1/1", fontsize=9.5, color="#222222")
    ax.text(tx + 10, ty + 110, "Nguồn: docs/hardware/generate_schematic.py", fontsize=9.5, color="#222222")
    ax.text(tx + 10, ty + 132, "Liền = theo firmware · Đứt = chưa xác minh", fontsize=9.5, color="#222222")
    fig.savefig(path_png, dpi=300)
    fig.savefig(path_svg)
    plt.close(fig)


# ── draw.io export ──────────────────────────────────────────────────────────────────────────
def to_drawio(path):
    mxfile = ET.Element("mxfile", host="DurianCare generator", version="1")
    diagram = ET.SubElement(mxfile, "diagram", id="durian-iot", name="DurianCare IoT schematic")
    model = ET.SubElement(diagram, "mxGraphModel", dx="1700", dy="1080", grid="1", gridSize="10", guides="1",
                          pageWidth=str(W), pageHeight=str(H), page="1")
    root = ET.SubElement(model, "root")
    ET.SubElement(root, "mxCell", id="0")
    ET.SubElement(root, "mxCell", id="1", parent="0")
    counter = [0]

    def nid():
        counter[0] += 1
        return f"c{counter[0]}"

    def vertex(value, x, y, w, h, style):
        cell = ET.SubElement(root, "mxCell", id=nid(), value=value, style=style, vertex="1", parent="1")
        ET.SubElement(cell, "mxGeometry", x=str(x), y=str(y), width=str(w), height=str(h), **{"as": "geometry"})

    def polyline(points, dashed, color="#1E7B34"):
        style = f"endArrow=none;html=1;strokeColor={color};strokeWidth=2;" + ("dashed=1;" if dashed else "")
        cell = ET.SubElement(root, "mxCell", id=nid(), value="", style=style, edge="1", parent="1")
        geo = ET.SubElement(cell, "mxGeometry", relative="1", **{"as": "geometry"})
        ET.SubElement(geo, "mxPoint", x=str(points[0][0]), y=str(points[0][1]), **{"as": "sourcePoint"})
        ET.SubElement(geo, "mxPoint", x=str(points[-1][0]), y=str(points[-1][1]), **{"as": "targetPoint"})
        if len(points) > 2:
            arr = ET.SubElement(geo, "Array", **{"as": "points"})
            for px, py in points[1:-1]:
                ET.SubElement(arr, "mxPoint", x=str(px), y=str(py))

    text = "text;html=1;align=left;verticalAlign=middle;fontColor=#1F3A93;fontSize=12;"
    for (x, y, w, h), label in SECTIONS:
        vertex(label, x, y, w, h, "rounded=0;whiteSpace=wrap;html=1;fillColor=none;dashed=1;strokeColor=#555555;"
               "verticalAlign=top;align=left;spacingLeft=10;fontStyle=1;fontColor=#555555;")
    for c in COMPONENTS:
        x, y, w, h = c["box"]
        vertex("", x, y, w, h, "rounded=0;whiteSpace=wrap;html=1;fillColor=#FFF8F0;strokeColor=#8B1A1A;strokeWidth=2;"
               + ("dashed=1;" if c.get("dashed") else ""))
        tx0, ty0 = c.get("title_xy", (x, y - 22))
        vertex(f"<b>{c['title']}</b><br><font color='#555555' style='font-size:10px'>{c['sub']}</font>",
               tx0, ty0 - 18, max(w, 320), 36, text)
        for name, side, off in c["pins"]:
            px = x if side == "L" else x + w
            polyline([(px - 14 if side == "L" else px, y + off), (px if side == "L" else px + 14, y + off)], False, "#8B1A1A")
            vertex(name, px + 6 if side == "L" else px - 136, y + off - 10, 130, 20,
                   text + ("align=left;" if side == "L" else "align=right;"))
    for frm, fpin, to, tpin, verified, wps in WIRES:
        polyline(wire_path(frm, fpin, to, tpin, wps), not verified)
    for poly in EXTRA_DASHED:
        polyline(poly, True)
    for comp, pin, net, verified in NETS:
        px, py = pin_point(comp, pin)
        c = next(cc for cc in COMPONENTS if cc["id"] == comp)
        side = next(s for n, s, *_ in c["pins"] if n == pin)
        sx = px + 14 if side == "R" else px - 14
        ex = sx + 40 if side == "R" else sx - 40
        polyline([(sx, py), (ex, py)], not verified)
        vertex(net, ex - 20, py - 26 if net != "GND" else py + 4, 40, 20,
               "text;html=1;align=center;fontStyle=1;fontColor=#8B1A1A;fontSize=11;")
    vertex("3V3", R1_SUPPLY[0] - 20, R1_SUPPLY[1] - 26, 40, 20, "text;html=1;align=center;fontStyle=1;fontColor=#8B1A1A;fontSize=11;")
    vertex(IO0_NOTE[2], IO0_NOTE[0], IO0_NOTE[1] - 10, 360, 20, "text;html=1;align=left;fontSize=10;fontColor=#555555;")
    for i, note in enumerate(NOTES):
        vertex(f"{i + 1}. {note}", 40, 755 + i * 24, 1120, 22, "text;html=1;align=left;fontSize=11;whiteSpace=wrap;")
    vertex("<b>TITLE: DurianCare IoT — Sơ đồ nguyên lý</b><br>REV 1.0 · 28/10/2026 · Sheet 1/1<br>"
           "Liền = theo firmware · Đứt = chưa xác minh", 1180, 920, 490, 140,
           "rounded=0;whiteSpace=wrap;html=1;strokeColor=#8B1A1A;fillColor=none;align=left;spacingLeft=10;fontSize=12;")
    ET.indent(mxfile)
    ET.ElementTree(mxfile).write(path, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    render(os.path.join(HERE, "durian_iot_schematic.png"), os.path.join(HERE, "durian_iot_schematic.svg"))
    to_drawio(os.path.join(HERE, "durian_iot_schematic.drawio"))
    print("generated:", ", ".join(f for f in sorted(os.listdir(HERE)) if f.startswith("durian_iot_schematic")))
