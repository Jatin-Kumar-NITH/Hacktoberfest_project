"""Adds a unit -> sub-unit -> topic outline to each official subject in data/syllabus.json.

The study planner's topic picker uses it. Reads the per-subject PDFs in static/docs/syllabus/year1/
(cut from the official NIT Hamirpur first-year curriculum by build_docs.py) with pdfplumber, which keeps
the "Unit | Course Content | Contact Hours" table rows together, then splits each unit into its
"Heading: topic, topic, ..." parts. Lab courses get their "List of Experiments" as one unit.

Topics are the syllabus wording, only split at commas. Where the PDF's punctuation doesn't mark a
heading, HEADINGS below says where it starts.

    pip install pdfplumber
    python tools/build_syllabus_outline.py
"""
import json
import re
from pathlib import Path

import pdfplumber

ROOT = Path(__file__).resolve().parent.parent
PDFS = ROOT / "static" / "docs" / "syllabus" / "year1"
SYLLABUS = ROOT / "data" / "syllabus.json"

# headings the PDF runs into the previous sentence or leaves without a colon
HEADINGS = {
    "CE-101": ["Orthographic Projection", "Isometric Projections", "Projections of Lines", "Projections of Solids"],
    "CY-101": ["Applications of electrochemistry in corrosion and its control"],
    "EC-101": ["Rectifiers and Filter Circuit"],
    "HS-101": ["Communication Process", "Effective Speaking Skills", "Public Speaking", "SWOT Analysis",
               "Referencing Skills", "Comprehension Skills", "Reading for literary appreciation",
               "Paragraph Writing", "Letter and Email Writing", "Statement of Purpose (SoP)",
               "Cover letter and resume", "Report Writing"],
    "HS-103": ["Ethics and Human Interface", "Human Values", "Need for developing an Industry-wide Code of Ethics",
               "Environmental ethics", "Ethical Decision-making"],
    "MB-101": ["Marketing Dynamics"],
}
# "A: b, c" where A is a topic with details, not a new sub-unit
NOT_HEADINGS = {"Interviews", "Free electron theory (Classical & Quantum)", "Attitude", "Influence of Ethical Decision Making",
                "Emotional Intelligence", "Alternative sources of energy", "Transistor Configuration", "Ecology",
                "Classification of materials"}
# fixes for the PDF's punctuation, applied to a unit's text before it is split ("||" starts a sub-unit, "::" ends its heading)
REPLACE = {
    "CY-101": [("Introduction to spectroscopy, UV-Visible spectroscopy-Absorption laws", "Introduction to spectroscopy || UV-Visible spectroscopy:: Absorption laws"),
               ("; IR spectroscopy - Principle", " || IR spectroscopy:: Principle"),
               (" NMR spectroscopy-Principle", " || NMR spectroscopy:: Principle")],
    "CS-121": [("Exceptions -try, except, else, pass, raise", "Exceptions - try - except - else - pass - raise"),
               ("decorators. inheritance, multiple inheritances, properties, decorators", "decorators"),
               ("Lists- Using Lists", "Lists - Using Lists")],
    "EC-101": [("Techniques Transistor Switch", "Techniques, Transistor Switch")],
    "EE-101": [("AC Circuits,Domestic Electric Wiring & Storage Batteries:", "|| AC Circuits, Domestic Electric Wiring & Storage Batteries::"),
               ("machines.Principles", "machines, Principles")],
    "EN-101": [("Deforestation)", "Deforestation")],
    "HS-101": [("Techniques Improving", "Techniques, Improving")],
    "ME-102": [("operations.Brief", "operations. Brief"), ("such as chipping, fitting. Scraping. Drilling, reaming", "such as chipping, fitting, scraping, drilling, reaming")],
    "PH-101": [("time independent, time dependent", "time independent and time dependent"), ("viz.,", "viz."),
               ("Classification of materials : Metals", "Classification of materials: Metals")],
}
# phrases whose commas don't separate topics
KEEP = {
    "MA-111": ["Theorems of Green, Stokes and Gauss", "Gradient, Divergence and Curl"],
    "MA-121": ["Laplace transform, inverse Laplace transform, and their properties"],
    "PH-101": ["ruby, He-Ne, CO2 and semiconductor lasers", "Free electron theory (Classical & Quantum): Assumptions, Merits and drawbacks",
               "Classification of materials: Metals, semiconductors and insulators"],
    "ME-101": ["Efficiency of Carnot, Otto, Diesel cycle", "Sand Casting, Forging, Rolling, Extrusion",
               "Arc Welding, Soldering, Brazing", "Turning, Drilling, Milling and Grinding"],
    "ME-102": ["chipping, fitting, scraping, drilling, reaming & thread cutting"],
    "HS-103": ["Difference between Ethics, Values, and Morals", "Essence, Determinants and Consequences",
               "Emotional Intelligence: Concepts, and their Utilities", "Ethics, Metacognition & Mindfulness",
               "Ethics, Corporate social responsibility", "Attitude: Content, Structure, Function"],
    "CY-101": ["Alternative sources of energy: Photo-catalysis, Solar cells"],
    "CS-121": ["Tuples, Dictionaries, and Sets"],
    "EC-101": ["L,C and Pi Filters", "CB, CE, CC Configuration"],
    "EE-101": ["Concept of voltage, current and power"],
}
# subjects whose sub-units are "Heading - topic - topic, Heading - topic"
DASH_PARTS = {"CS-121"}
# unit headings the PDF doesn't print: code -> {unit number: heading}
UNIT_TITLES = {
    "ME-102": {1: "Workshop and Smithy Shop", 2: "Machining and Joining Processes", 3: "Foundry and Fitting Shop",
               4: "Carpentry and Sheet Metal Shop"},
}


def clean(s):
    s = s.replace("‗", "'").replace("‘", "'").replace("’", "'").replace("‗", "'").replace("‘", "'")
    s = re.sub(r"\s+", " ", s).strip()
    return s


def split_top(s, keep=()):
    """Split at commas/semicolons (and ". " between sentences) that are not inside brackets or a KEEP phrase."""
    for i, k in enumerate(keep):
        s = s.replace(k, f"\x01{i}\x01")
    out, depth, cur = [], 0, ""
    for i, ch in enumerate(s):
        depth += ch == "("
        depth -= ch == ")" and depth > 0
        sentence_end = ch == "." and depth == 0 and s[i + 1:i + 2] == " " and (s[i + 2:i + 3].isupper() or s[i + 2:i + 3] == "\x01") \
            and not re.search(r"(\b[A-Z]|\bviz|\bvs)$", cur)
        if depth == 0 and (ch in ",;" or sentence_end):
            out.append(cur)
            cur = ""
        else:
            cur += ch
    out.append(cur)
    topics = []
    for t in out:
        t = re.sub(r"\x01(\d+)\x01", lambda m: keep[int(m.group(1))], t).strip(" .:-–")
        t = re.sub(r"^(and|&)\s+", "", t)
        if len(t) > 1 and t[0].upper() + t[1:] not in topics:
            topics.append(t[0].upper() + t[1:])
    return topics


def dash_parts(body, keep):
    """CS-121 style: 'Heading - topic - topic, Heading - topic, topic'"""
    parts = []
    for chunk in split_top(body.replace(" – ", " - "), keep):
        bits = [b.strip() for b in chunk.split(" - ") if b.strip()]
        if len(bits) > 1 or not parts:
            parts.append({"title": bits[0], "topics": bits[1:] or bits[:1]})
        else:
            parts[-1]["topics"].append(bits[0])
    for p in parts:
        p["topics"] = [t[0].upper() + t[1:] for t in p["topics"]]
    return parts


def parts_of(code, n, body):
    """'Heading: a, b, c Heading2: d, e' -> [{title, topics}]"""
    body = clean(body)
    keep = KEEP.get(code, [])
    for old, new in REPLACE.get(code, []):
        body = body.replace(old, new)
    if code in DASH_PARTS:
        return dash_parts(body, keep)
    for h in HEADINGS.get(code, []):
        body = re.sub(r"\s*(?:[.;,]\s*)?" + re.escape(h) + r"\s*[:\-–]?\s*", f" || {h}:: ", body, count=1)
    parts = []
    for i, piece in enumerate(re.split(r"\s*\|\|\s*", body)):
        if not piece.strip():
            continue
        forced = re.match(r"^(.+?)::\s*(.*)$", piece)
        m = forced or re.match(r"^([^:,;()]{2,70}(?:\([^)]*\))?)\s*:\s*(.*)$", piece)
        head, rest = (m.group(1).strip(), m.group(2)) if m and m.group(1).strip() not in NOT_HEADINGS else (None, piece)
        # the curriculum repeats some unit headings ("Entrepreneurship: Introduction to Entrepreneurship: ...")
        again = re.match(r"^([^:,;()]{2,70})\s*:\s*(.*)$", rest)
        if head and not forced and not parts and again and again.group(1).strip() not in NOT_HEADINGS:
            head, rest = again.group(1).strip(), again.group(2)
        topics = split_top(rest, keep)
        if head is None and parts:  # text after a sub-unit's list without a heading of its own
            parts[-1]["topics"] += topics
            continue
        parts.append({"title": head or UNIT_TITLES.get(code, {}).get(n) or f"Unit {n}", "topics": topics})
    return [p for p in parts if p["topics"]]


def experiments(text):
    text = clean(text)
    text = re.split(r"\s(?:\*?NOTE|Note)\s*:", text)[0]
    text = re.sub(r"^.*?List of Experiments\s*", "", text)
    text = re.sub(r"^Activities based on language software [^1]*", "", text)
    items = re.split(r"(?:^|\s)\d{1,2}\.\s+", text)
    return [i.strip(" .") for i in items if i.strip(" .")]


def outline(code):
    units, lab = [], []
    with pdfplumber.open(PDFS / f"{code}.pdf") as pdf:
        rows = [r for page in pdf.pages for t in page.extract_tables() for r in t]
    cur = None
    for r in rows:
        cells = [c or "" for c in r]
        first = clean(cells[0])
        m = re.match(r"^unit[\s\-]*0?(\d+)\s*(.*)$", first, re.I)
        if m and len(cells) > 1:
            # CS-121's PDF splits the first word across the unit cell: "UNIT-03W riti" | "ng Functions"
            spill = m.group(2).replace(" ", "")
            body = ("The " if spill == "The" else spill) + cells[1]
            cur = {"n": int(m.group(1)), "body": body, "hours": _hours(clean(cells[-1]))}
            units.append(cur)
        elif cur is not None and not first and len(cells) > 1 and cells[1].strip():
            cur["body"] += " " + cells[1]  # unit continues on the next table row
        if "List of Experiments" in first:
            lab = experiments(first)
            cur = None
        if first.startswith(("Course Outcomes", "खंड")) or re.match(r"^CO\d", first):
            cur = None
    out = []
    for u in units:
        parts = parts_of(code, u["n"], u["body"])
        out.append({"n": u["n"], "title": " · ".join(p["title"] for p in parts), "hours": u["hours"], "parts": parts})
    if lab:
        out.append({"n": len(out) + 1, "title": "Lab experiments", "hours": "", "lab": True,
                    "parts": [{"title": "List of experiments", "topics": lab}]})
    return out


def _hours(s):
    nums = [int(x) for x in re.findall(r"(\d+)\s*L", s)]
    return f"{sum(nums)}L" if nums else ""


def main():
    data = json.loads(SYLLABUS.read_text(encoding="utf-8"))
    subjects = data["years"]["1"]["subjects"]
    for code, sub in subjects.items():
        sub["outline"] = outline(code)
        sub["units"] = sum(1 for u in sub["outline"] if not u.get("lab"))
        print(code, len(sub["outline"]), "units,", sum(len(p["topics"]) for u in sub["outline"] for p in u["parts"]), "topics")
    SYLLABUS.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
