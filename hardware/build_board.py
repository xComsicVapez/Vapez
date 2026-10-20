#!/usr/bin/env python3
"""
Generate, autoroute, check and export the DIY phone mainboard for JLCPCB.

    python3 hardware/build_board.py            # full build (needs KiCad 9 + Java)
    python3 hardware/build_board.py --no-route # placement only, skip autorouter

Outputs land in hardware/output/:
    diy-phone.kicad_pcb / .kicad_pro   KiCad project (open it to inspect/edit)
    jlcpcb/gerbers.zip                 upload this on jlcpcb.com
    jlcpcb/bom.csv, jlcpcb/cpl.csv     upload these for PCB assembly
    drc.json                           KiCad design-rule-check report
    render-front.png / render-back.png 3D renders
"""
import argparse
import csv
import json
import os
import shutil
import subprocess
import sys
import zipfile
from collections import defaultdict

import pcbnew

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import phone_design as D  # noqa: E402

OUT = os.path.join(HERE, "output")
FAB = os.path.join(OUT, "jlcpcb")
NAME = "diy-phone"
PCB = os.path.join(OUT, NAME + ".kicad_pcb")
FP_DIR = os.environ.get("KICAD9_FOOTPRINT_DIR", "/usr/share/kicad/footprints")
FREEROUTING = os.environ.get("FREEROUTING_JAR", os.path.expanduser("~/tools/freerouting.jar"))

# Board origin on the KiCad page (keeps the drawing inside the A4 frame)
OX, OY = 100.0, 30.0

# JLCPCB standard 2-layer capabilities, with margin
TRACK = 0.2
CLEARANCE = 0.2
VIA_D, VIA_DRILL = 0.6, 0.3


def mm(v):
    return pcbnew.FromMM(v)


def pt(x, y):
    return pcbnew.VECTOR2I(mm(OX + x), mm(OY + y))


def setup_rules(board):
    ds = board.GetDesignSettings()
    ds.m_TrackMinWidth = mm(0.15)
    ds.m_MinClearance = mm(0.15)
    ds.m_ViasMinSize = mm(0.5)
    ds.m_MinThroughDrill = mm(0.3)
    ds.m_CopperEdgeClearance = mm(0.3)
    ds.m_HoleToHoleMin = mm(0.5)
    ds.m_SolderMaskMinWidth = mm(0)
    ns = ds.m_NetSettings
    default = ns.GetDefaultNetclass()
    default.SetTrackWidth(mm(TRACK))
    default.SetClearance(mm(CLEARANCE))
    default.SetViaDiameter(mm(VIA_D))
    default.SetViaDrill(mm(VIA_DRILL))
    for net, width in D.POWER_NETS.items():
        nc = pcbnew.NETCLASS("PWR_" + net.replace("+", "P"))
        nc.SetTrackWidth(mm(width))
        nc.SetClearance(mm(CLEARANCE))
        nc.SetViaDiameter(mm(0.8))
        nc.SetViaDrill(mm(0.4))
        ns.SetNetclass(nc.GetName(), nc)
        ns.SetNetclassPatternAssignment(net, nc.GetName())


def add_outline(board):
    w, h, r = D.BOARD_W, D.BOARD_H, D.CORNER_R

    def seg(x1, y1, x2, y2):
        s = pcbnew.PCB_SHAPE(board, pcbnew.SHAPE_T_SEGMENT)
        s.SetStart(pt(x1, y1))
        s.SetEnd(pt(x2, y2))
        s.SetLayer(pcbnew.Edge_Cuts)
        s.SetWidth(mm(0.1))
        board.Add(s)

    def arc(cx, cy, sx, sy, deg):
        a = pcbnew.PCB_SHAPE(board, pcbnew.SHAPE_T_ARC)
        a.SetCenter(pt(cx, cy))
        a.SetStart(pt(sx, sy))
        a.SetArcAngleAndEnd(pcbnew.EDA_ANGLE(deg, pcbnew.DEGREES_T), True)
        a.SetLayer(pcbnew.Edge_Cuts)
        a.SetWidth(mm(0.1))
        board.Add(a)

    seg(r, 0, w - r, 0)
    seg(w, r, w, h - r)
    seg(w - r, h, r, h)
    seg(0, h - r, 0, r)
    arc(w - r, r, w - r, 0, 90)
    arc(w - r, h - r, w, h - r, 90)
    arc(r, h - r, r, h, 90)
    arc(r, r, 0, r, 90)


def add_text(board, text, x, y, size=1.0, layer=pcbnew.F_SilkS, rot=0):
    t = pcbnew.PCB_TEXT(board)
    t.SetText(text)
    t.SetPosition(pt(x, y))
    t.SetLayer(layer)
    t.SetTextSize(pcbnew.VECTOR2I(mm(size), mm(size)))
    t.SetTextThickness(mm(size * 0.15))
    t.SetTextAngle(pcbnew.EDA_ANGLE(rot, pcbnew.DEGREES_T))
    if layer == pcbnew.B_SilkS:
        t.SetMirrored(True)
    board.Add(t)


def add_silkscreen(board):
    add_text(board, "DIY PHONE v1.0", 35, 30, 2.0)
    add_text(board, "ESP32-S3 + LTE", 35, 34, 1.2)
    add_text(board, "TFT SOCKET (pin 1 = VCC)", 35, 10.5, 1.0)
    add_text(board, "BOOT", 44, 104, 0.9)
    add_text(board, "RST", 54, 104, 0.9)
    add_text(board, "USER", 64, 104, 0.9)
    add_text(board, "PWR", 62.5, 107.5, 0.9)
    add_text(board, "CHG", 52, 112, 0.8)
    add_text(board, "FULL", 57, 112, 0.8)
    add_text(board, "BATT  -  +", 58.5, 128.5, 0.9)
    add_text(board, "MIC+ MIC- SPK+ SPK-", 33, 98.5, 0.7)
    add_text(board, "DIY PHONE - back", 35, 20, 2.0, pcbnew.B_SilkS)
    add_text(board, "LTE MODULE SOCKET", 54, 37, 1.0, pcbnew.B_SilkS, rot=90)
    labels = ["VBAT", "VBAT", "GND", "GND", "RXD", "TXD", "PWRKEY", "RI", "DTR",
              "STATUS", "MIC+", "MIC-", "SPK+", "SPK-"]
    for i, lab in enumerate(labels):
        add_text(board, lab, 64.0 - 4.2, 40.0 + i * 2.54, 0.8, pcbnew.B_SilkS)
    add_text(board, "LiPo 1S 3.7V - check polarity!", 35, 128, 1.0, pcbnew.B_SilkS)


def place_parts(board):
    nets = {}
    for name in D.NETS:
        ni = pcbnew.NETINFO_ITEM(board, name)
        board.Add(ni)
        nets[name] = ni

    pad_net = {}
    for name, pins in D.NETS.items():
        for ref, pad in pins:
            key = (ref, pad)
            if key in pad_net:
                raise SystemExit(f"pad {ref}.{pad} assigned to both {pad_net[key]} and {name}")
            pad_net[key] = name

    known = set()
    for p in D.PARTS:
        lib, fpname = p.footprint.split(":")
        fp = pcbnew.FootprintLoad(os.path.join(FP_DIR, lib + ".pretty"), fpname)
        if fp is None:
            raise SystemExit(f"footprint not found: {p.footprint}")
        fp.SetReference(p.ref)
        fp.SetValue(p.value)
        fp.SetFPID(pcbnew.LIB_ID(lib, fpname))
        fp.SetPosition(pt(p.x, p.y))
        fp.SetOrientationDegrees(p.rot)
        board.Add(fp)
        if p.side == "B":
            fp.Flip(fp.GetPosition(), pcbnew.FLIP_DIRECTION_LEFT_RIGHT)
        if p.lcsc:
            fp.SetField("LCSC", p.lcsc)
            fp.GetFieldByName("LCSC").SetVisible(False)
        if not p.smt:
            fp.SetLocalZoneConnection(pcbnew.ZONE_CONNECTION_THERMAL)
        fp.Reference().SetTextSize(pcbnew.VECTOR2I(mm(0.8), mm(0.8)))
        fp.Reference().SetTextThickness(mm(0.12))
        for pad in list(fp.Pads()):
            # Library thermal vias (0.2 mm) are below JLCPCB's standard
            # 0.3 mm minimum drill; the exposed pad is still soldered.
            if pad.GetAttribute() == pcbnew.PAD_ATTRIB_PTH and pad.GetDrillSize().x < mm(0.3):
                fp.Remove(pad)
                continue
            num = pad.GetNumber()
            known.add((p.ref, num))
            net = pad_net.get((p.ref, num))
            if net:
                pad.SetNet(nets[net])

    missing = [k for k in pad_net if k not in known]
    if missing:
        raise SystemExit(f"netlist references pads that do not exist: {missing}")
    return nets


def add_gnd_zones(board, gnd):
    w, h = D.BOARD_W, D.BOARD_H
    for layer in (pcbnew.F_Cu, pcbnew.B_Cu):
        z = pcbnew.ZONE(board)
        z.SetLayer(layer)
        z.SetNet(gnd)
        z.SetLocalClearance(mm(0.3))
        z.SetMinThickness(mm(0.25))
        # Solid connections for reflowed SMD pads; the hand-soldered
        # through-hole sockets get thermal reliefs via a footprint override.
        z.SetPadConnection(pcbnew.ZONE_CONNECTION_FULL)
        z.SetThermalReliefGap(mm(0.3))
        z.SetThermalReliefSpokeWidth(mm(0.4))
        z.SetIslandRemovalMode(pcbnew.ISLAND_REMOVAL_MODE_ALWAYS)
        outline = z.Outline()
        outline.NewOutline()
        for x, y in ((0, 0), (w, 0), (w, h), (0, h)):
            outline.Append(mm(OX + x), mm(OY + y))
        board.Add(z)


def fill_zones(path):
    # Filling zones on a board created in memory crashes KiCad 9; reloading
    # the saved file gives the filler the project context it expects.
    board = pcbnew.LoadBoard(path)
    pcbnew.ZONE_FILLER(board).Fill(board.Zones())
    pcbnew.SaveBoard(path, board)


def stitch_gnd(board, gnd):
    """Drop GND vias on a grid wherever both pours have room, tying the planes."""
    via_r = 0.3
    keep = []
    for fp in board.GetFootprints():
        bb = fp.GetBoundingBox(False)
        keep.append((pcbnew.ToMM(bb.GetLeft()) - OX - 0.6, pcbnew.ToMM(bb.GetTop()) - OY - 0.6,
                     pcbnew.ToMM(bb.GetRight()) - OX + 0.6, pcbnew.ToMM(bb.GetBottom()) - OY + 0.6))
    tracks = [t for t in board.GetTracks()]
    added = 0
    y = 6.0
    while y < D.BOARD_H - 5:
        x = 6.0
        while x < D.BOARD_W - 5:
            # stay out of the ESP32 antenna strip on the left edge
            if x < 9 and 90 < y < 145:
                x += 6
                continue
            if any(a <= x <= c and b <= y <= d for a, b, c, d in keep):
                x += 6
                continue
            p = pt(x, y)
            clear = True
            for t in tracks:
                if t.GetNetCode() == gnd.GetNetCode():
                    continue
                dist = pcbnew.ToMM(int(t.GetEffectiveShape().GetClearance(
                    pcbnew.SHAPE_CIRCLE(p, mm(via_r)))))
                if dist < CLEARANCE + 0.2:
                    clear = False
                    break
            if clear:
                v = pcbnew.PCB_VIA(board)
                v.SetPosition(p)
                v.SetWidth(mm(VIA_D))
                v.SetDrill(mm(VIA_DRILL))
                v.SetNet(gnd)
                board.Add(v)
                added += 1
            x += 6
        y += 6
    return added


def preroute_usb_vbus(board, vbus):
    """Bridge the two USB-C VBUS pad pairs on the back layer.

    Between them sit the CC and D+/D- pads at 0.5 mm pitch, which the
    autorouter struggles to escape around, so this short link is fixed.
    """
    j1 = board.FindFootprintByReference("J1")
    pads = [p for p in j1.Pads() if p.GetNumber() in ("A4", "A9")]
    vias = []
    for pad in pads:
        pos = pad.GetPosition()
        vpos = pcbnew.VECTOR2I(pos.x, pos.y - mm(1.6))
        t = pcbnew.PCB_TRACK(board)
        t.SetStart(pos)
        t.SetEnd(vpos)
        t.SetWidth(mm(0.4))
        t.SetLayer(pcbnew.F_Cu)
        t.SetNet(vbus)
        t.SetLocked(True)
        board.Add(t)
        v = pcbnew.PCB_VIA(board)
        v.SetPosition(vpos)
        v.SetWidth(mm(VIA_D))
        v.SetDrill(mm(VIA_DRILL))
        v.SetNet(vbus)
        v.SetLocked(True)
        board.Add(v)
        vias.append(vpos)
    t = pcbnew.PCB_TRACK(board)
    t.SetStart(vias[0])
    t.SetEnd(vias[1])
    t.SetWidth(mm(0.5))
    t.SetLayer(pcbnew.B_Cu)
    t.SetNet(vbus)
    t.SetLocked(True)
    board.Add(t)


def unrouted_count(board):
    board.BuildConnectivity()
    return board.GetConnectivity().GetUnconnectedCount(False)


def autoroute(board, attempts=3):
    # GND links left by the router are normally completed by the copper pours;
    # the DRC run after zone filling is the authoritative connectivity check.
    previous = None
    for i in range(attempts):
        run_freerouting(board)
        left = unrouted_count(board)
        print(f"autoroute pass {i + 1}: {left} connections left to the GND pour/DRC", flush=True)
        if left == 0 or left == previous:
            return
        previous = left


def run_freerouting(board):
    dsn = os.path.join(OUT, NAME + ".dsn")
    ses = os.path.join(OUT, NAME + ".ses")
    if not pcbnew.ExportSpecctraDSN(board, dsn):
        raise SystemExit("DSN export failed")
    if os.path.exists(ses):
        os.remove(ses)
    # -mt 0 skips the post-route optimiser, which can spin forever on
    # boards with pre-routed (locked) tracks.
    cmd = ["java", "-jar", FREEROUTING, "-de", dsn, "-do", ses, "-mp", "40", "-mt", "0",
           "-host", "KiCad"]
    print("running:", " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True, stdout=open(os.path.join(OUT, "freerouting.log"), "w"),
                   stderr=subprocess.STDOUT, timeout=1800)
    if not os.path.exists(ses):
        raise SystemExit("freerouting produced no session file, see output/freerouting.log")
    if not pcbnew.ImportSpecctraSES(board, ses):
        raise SystemExit("SES import failed")
    os.remove(dsn)
    os.remove(ses)


def verify_nets(path):
    """Check the saved board still matches the design netlist pad for pad."""
    board = pcbnew.LoadBoard(path)
    expected = {}
    for name, pins in D.NETS.items():
        for ref, pad in pins:
            expected[(ref, pad)] = name
    bad = []
    for fp in board.GetFootprints():
        for pad in fp.Pads():
            key = (fp.GetReference(), pad.GetNumber())
            if not pad.GetNumber():
                continue
            want = expected.get(key, "")
            if pad.GetNetname() != want:
                bad.append(f"{key[0]}.{key[1]}: board={pad.GetNetname()!r} design={want!r}")
    if bad:
        raise SystemExit("netlist mismatch:\n  " + "\n  ".join(bad))
    print(f"netlist verified: {len(expected)} pads on {len(D.NETS)} nets match the design")


def kicad_cli(*args):
    print("kicad-cli", " ".join(args), flush=True)
    subprocess.run(["kicad-cli", *args], check=True)


def run_drc():
    report = os.path.join(OUT, "drc.json")
    kicad_cli("pcb", "drc", "--format", "json", "--all-track-errors", "-o", report, PCB)
    data = json.load(open(report))
    errors = [v for v in data.get("violations", []) if v.get("severity") == "error"]
    unconnected = data.get("unconnected_items", [])
    by_type = defaultdict(int)
    for v in errors:
        by_type[v["type"]] += 1
    print(f"DRC: {len(errors)} errors {dict(by_type)}, {len(unconnected)} unconnected items")
    return errors, unconnected


def export_fab():
    if os.path.exists(FAB):
        shutil.rmtree(FAB)
    gdir = os.path.join(FAB, "gerbers")
    os.makedirs(gdir)
    layers = "F.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts"
    kicad_cli("pcb", "export", "gerbers", "--layers", layers, "--subtract-soldermask",
              "--no-protel-ext", "-o", gdir + "/", PCB)
    kicad_cli("pcb", "export", "drill", "--format", "excellon", "--excellon-separate-th",
              "-o", gdir + "/", PCB)
    zpath = os.path.join(FAB, "gerbers.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(os.listdir(gdir)):
            z.write(os.path.join(gdir, f), f)
    print("wrote", zpath)

    pos = os.path.join(OUT, "pos.csv")
    kicad_cli("pcb", "export", "pos", "--format", "csv", "--units", "mm", "--side", "both",
              "--use-drill-file-origin", "-o", pos, PCB)
    write_bom_cpl(pos)
    os.remove(pos)


def write_bom_cpl(pos_csv):
    parts = {p.ref: p for p in D.PARTS}
    assembled = [p for p in D.PARTS if p.lcsc and (p.smt or D.ASSEMBLE_THT)]
    groups = defaultdict(list)
    for p in assembled:
        groups[(p.value, p.footprint, p.lcsc)].append(p.ref)

    def natural(r):
        i = next(i for i, c in enumerate(r) if c.isdigit())
        return (r[:i], int(r[i:]))

    with open(os.path.join(FAB, "bom.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["Comment", "Designator", "Footprint", "LCSC Part #"])
        for (value, fp, lcsc), refs in sorted(groups.items(), key=lambda g: natural(sorted(g[1], key=natural)[0])):
            w.writerow([value, ",".join(sorted(refs, key=natural)), fp.split(":")[1], lcsc])

    with open(os.path.join(FAB, "bom-hand-solder.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["Designator", "Value", "Footprint", "LCSC Part #", "Note"])
        for p in D.PARTS:
            if p.lcsc and not (p.smt or D.ASSEMBLE_THT):
                w.writerow([p.ref, p.value, p.footprint.split(":")[1], p.lcsc,
                            "through-hole, solder by hand (" + ("back" if p.side == "B" else "front") + " side)"])

    rows = list(csv.DictReader(open(pos_csv)))
    with open(os.path.join(FAB, "cpl.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["Designator", "Mid X", "Mid Y", "Layer", "Rotation"])
        for r in sorted(rows, key=lambda r: natural(r["Ref"])):
            p = parts.get(r["Ref"])
            if p is None or p not in assembled:
                continue
            rot = (float(r["Rot"]) + D.JLC_ROTATION_OFFSETS.get(p.footprint, 0)) % 360
            layer = "Top" if r["Side"] == "top" else "Bottom"
            w.writerow([r["Ref"], f"{float(r['PosX']):.4f}mm", f"{float(r['PosY']):.4f}mm",
                        layer, f"{rot:g}"])
    print("wrote bom.csv and cpl.csv for", len(assembled), "assembled parts")


def render():
    for side in ("top", "bottom"):
        out = os.path.join(OUT, f"render-{'front' if side == 'top' else 'back'}.png")
        try:
            kicad_cli("pcb", "render", "--side", side, "--width", "1000", "--height", "1600",
                      "--quality", "high", "--background", "opaque", "-o", out, PCB)
        except subprocess.CalledProcessError as e:
            print("render failed (needs an OpenGL capable display):", e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-route", action="store_true", help="skip the autorouter")
    ap.add_argument("--no-render", action="store_true")
    args = ap.parse_args()

    os.makedirs(OUT, exist_ok=True)
    board = pcbnew.BOARD()
    board.SetCopperLayerCount(2)
    setup_rules(board)
    add_outline(board)
    nets = place_parts(board)
    add_silkscreen(board)
    board.GetDesignSettings().SetAuxOrigin(pt(0, D.BOARD_H))
    board.BuildConnectivity()

    preroute_usb_vbus(board, nets["VBUS"])
    if not args.no_route:
        autoroute(board)
        stitched = stitch_gnd(board, nets["GND"])
        print("GND stitching vias:", stitched)
    add_gnd_zones(board, nets["GND"])

    pcbnew.SaveBoard(PCB, board)
    fill_zones(PCB)
    verify_nets(PCB)
    errors, unconnected = run_drc()
    export_fab()
    if not args.no_render:
        render()
    if args.no_route:
        return 0
    return 1 if (errors or unconnected) else 0


if __name__ == "__main__":
    sys.exit(main())
