"""Builds the document library used by the Assignments and Syllabus pages.

Inputs (put the original PDFs in tools/source/):
  curriculum.pdf            - First Year course curriculum (NEP-2020)
  MA-111_assignment_1.pdf   - MA-111 Unit 1 assignment
  MA-111_assignment_2.pdf   - MA-111 Unit 2 assignment
  MA-111_assignment_3.pdf   - MA-111 Unit 3 assignment

Outputs:
  static/docs/...           - original PDFs, one PDF per subject, generated sample PDFs
  data/syllabus.json        - syllabus per year and subject (PDF link + text)
  data/assignments.json     - assignments with descriptions and PDF links
"""
import json
import re
import shutil
import subprocess
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "source"
DOCS = ROOT / "static" / "docs"
DATA = ROOT / "data"

# ------------------------------------------------------------------ year 1: real curriculum
COURSE_PAGES = {  # code -> pages in curriculum.pdf
    "MA-111": [7], "PH-101": [8], "CS-111": [9], "ME-101": [10], "HS-101": [11], "HS-103": [12, 13, 14],
    "PH-102": [15], "HS-102": [16], "ME-102": [17], "MA-121": [18], "CY-101": [19], "CS-121": [20],
    "EE-101": [21], "EC-101": [22], "EN-101": [23], "MB-101": [24], "CY-102": [25], "EE-102": [26], "CE-101": [27],
}
NAMES = {  # as printed in the curriculum's "Course Name" headings
    "MA-111": "Linear Algebra & Calculus", "PH-101": "Engineering Physics", "CS-111": "Introduction to Computer and C Programming",
    "ME-101": "Basics of Mechanical Engineering", "HS-101": "Communication Skills", "HS-103": "Human Values and Ethical Mindfulness",
    "PH-102": "Physics Lab", "HS-102": "Communication Skills Lab", "ME-102": "Engineering Workshop Practice",
    "MA-121": "Differential Equations", "CY-101": "Engineering Chemistry", "CS-121": "Introduction to Python and Data Structures",
    "EE-101": "Basic Electrical Engineering", "EC-101": "Basic Electronics Engineering", "EN-101": "Energy, Environment and Life Science",
    "MB-101": "Entrepreneurship & Marketing Dynamics", "CY-102": "Engineering Chemistry Lab",
    "EE-102": "Electrical and Electronics Engineering Lab", "CE-101": "Engineering Graphics",
}
SEMESTER = {c: 1 for c in ["MA-111", "PH-101", "CS-111", "ME-101", "HS-101", "HS-103", "PH-102", "HS-102", "ME-102"]}
SEMESTER.update({c: 2 for c in ["MA-121", "CY-101", "CS-121", "EE-101", "EC-101", "EN-101", "MB-101", "CY-102", "EE-102", "CE-101"]})


def pdf_text(pdf, first, last, layout=False):
    args = ["pdftotext", "-f", str(first), "-l", str(last)] + (["-layout"] if layout else []) + [str(pdf), "-"]
    return subprocess.run(args, capture_output=True, text=True, check=True).stdout


def extract_pages(pdf, pages, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["qpdf", str(pdf), "--pages", str(pdf), ",".join(map(str, pages)), "--", str(out)], check=True)


def clean(text):
    text = text.replace("―", "“").replace("‖", "”")
    text = text.replace("", "•").replace("‘", "'").replace("’", "'").replace("\x0c", "\n")
    text = re.sub(r"(?<=\S)(Unit-\d+)", r"\n\1", text)          # "EigenUnit-02" -> new line
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def field(text, label):
    m = re.search(label + r"\s*:?\s*\n?\s*([^\n]+)", text)
    return m.group(1).strip(" :") if m else None


def build_year1():
    cur = SRC / "curriculum.pdf"
    out_dir = DOCS / "syllabus" / "year1"
    out_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy(cur, out_dir / "first_year_curriculum.pdf")
    extract_pages(cur, [6], out_dir / "teaching_scheme.pdf")
    extract_pages(cur, [28], out_dir / "evaluation_scheme.pdf")

    # L-P-credits from the teaching scheme table (page 6)
    scheme = {}
    for line in pdf_text(cur, 6, 6, layout=True).splitlines():
        for m in re.finditer(r"([A-Z]{2}-\d{3})\s+(.*?)\s+(\d)\s+(\d)\s+(\d)(?=\s|$)", line):
            scheme[m.group(1)] = {"L": int(m.group(3)), "P": int(m.group(4)), "credits": int(m.group(5))}

    subjects = {}
    for code, pages in COURSE_PAGES.items():
        english = [p for p in pages if p not in (13, 14)]           # pages 13-14 are the Hindi version of HS-103
        raw = pdf_text(cur, english[0], english[-1])
        name = NAMES[code]
        ctype = field(raw, r"Course Type")
        if ctype and ("Course" in ctype or ctype == name):
            ctype = None
        contact = field(raw, r"Contact Hours\s*/\s*Week")
        if contact:
            contact = re.split(r"\s{2,}|Course Credits", contact)[0].strip()
        pdf_rel = f"docs/syllabus/year1/{code}.pdf"
        extract_pages(cur, pages, ROOT / "static" / pdf_rel)
        units = []
        for m in re.finditer(r"Unit-(\d+)", raw):
            units.append(f"Unit {int(m.group(1))}")
        subjects[code] = {
            "name": name, "type": ctype, "contact": contact, "semester": SEMESTER[code],
            **scheme.get(code, {}), "pdf": pdf_rel, "pages": pages,
            "units": len(set(units)), "text": clean(raw),
            "note": "Pages 13-14 of the PDF contain the Hindi version (text not extracted)." if code == "HS-103" else None,
        }
    return {
        "official": True,
        "title": "Course Curriculum for Bachelor Programmes (B.Tech./Dual Degree), First Year · NEP-2020",
        "source": "NIT Hamirpur first-year curriculum PDF",
        "note": "Semester 1 and 2 courses run together: about half the students take the 1st-semester list and the rest the 2nd-semester list, then they swap (MA and CS courses excepted).",
        "documents": [
            {"title": "Full first-year curriculum", "pdf": "docs/syllabus/year1/first_year_curriculum.pdf"},
            {"title": "Teaching scheme (page 6)", "pdf": "docs/syllabus/year1/teaching_scheme.pdf"},
            {"title": "Evaluation scheme (page 28)", "pdf": "docs/syllabus/year1/evaluation_scheme.pdf"},
        ],
        "missing": {"MA-112": "B.Arch (Section Z) courses are not in this B.Tech curriculum PDF.",
                    "AR-111": "B.Arch course, not in this B.Tech curriculum PDF.", "AR-112": "B.Arch course, not in this B.Tech curriculum PDF.",
                    "AR-113": "B.Arch course, not in this B.Tech curriculum PDF.", "AR-114": "B.Arch course, not in this B.Tech curriculum PDF.",
                    "AR-115": "B.Arch course, not in this B.Tech curriculum PDF."},
        "subjects": subjects,
    }


# ------------------------------------------------------------------ years 2-4: generated SAMPLE documents
SAMPLE_SYLLABUS = {
    2: {
        "Data Structures": ["Arrays, stacks and queues; applications", "Linked lists: singly, doubly, circular", "Trees: binary trees, BST, AVL, heaps", "Graphs: representation, BFS, DFS, shortest paths", "Sorting and searching; hashing"],
        "Discrete Mathematics": ["Sets, relations and functions", "Propositional and predicate logic", "Counting: permutations, combinations, pigeonhole principle", "Recurrence relations and generating functions", "Graph theory basics"],
        "Digital Electronics": ["Number systems and Boolean algebra", "K-maps and logic minimisation", "Combinational circuits: adders, multiplexers, decoders", "Sequential circuits: flip-flops, counters, registers", "Introduction to memories and PLDs"],
        "OOP with Java": ["Java basics, classes and objects", "Inheritance, interfaces and polymorphism", "Exception handling and packages", "Collections framework and generics", "Multithreading and file I/O"],
        "Engineering Mathematics III": ["Laplace transform and applications", "Fourier series", "Fourier transform", "Partial differential equations", "Numerical methods"],
        "Data Structures Lab": ["Implementation of stacks, queues and linked lists", "Tree and graph traversal programs", "Sorting algorithm comparison"],
        "Java Lab": ["Programs on classes, inheritance and interfaces", "Exception handling and collections", "Mini project in Java"],
        "Digital Electronics Lab": ["Verification of logic gates", "Design of adders and multiplexers", "Flip-flops and counters on breadboard"],
    },
    3: {
        "Operating Systems": ["Processes and threads", "CPU scheduling", "Synchronisation and deadlocks", "Memory management and virtual memory", "File systems and I/O"],
        "Computer Networks": ["Network models: OSI and TCP/IP", "Data link layer and MAC protocols", "Network layer: IP addressing and routing", "Transport layer: TCP and UDP", "Application layer protocols"],
        "Database Systems": ["ER model and relational model", "SQL and relational algebra", "Normalisation", "Transactions and concurrency control", "Indexing and query processing"],
        "Theory of Computation": ["Finite automata and regular languages", "Context-free grammars and pushdown automata", "Turing machines", "Decidability", "Complexity classes P and NP"],
        "Software Engineering": ["Software process models", "Requirements engineering", "Design and UML", "Testing strategies", "Project management and maintenance"],
        "OS Lab": ["Shell scripting", "Scheduling and synchronisation programs", "Memory allocation simulation"],
        "DBMS Lab": ["SQL queries and joins", "Views, triggers and procedures", "Mini database project"],
        "Networks Lab": ["Socket programming", "Packet capture with Wireshark", "Network simulation"],
    },
    4: {
        "Machine Learning": ["Supervised learning: regression and classification", "Decision trees and ensembles", "Support vector machines", "Unsupervised learning and clustering", "Neural networks basics"],
        "Cloud Computing": ["Cloud service and deployment models", "Virtualisation and containers", "Cloud storage and databases", "Security in the cloud", "Case studies"],
        "Information Security": ["Classical and symmetric cryptography", "Public-key cryptography", "Authentication and digital signatures", "Network and web security", "Security policies and auditing"],
        "Elective I": ["Topics depend on the elective chosen by the student"],
        "Project Management": ["Project life cycle", "Planning and scheduling: CPM and PERT", "Cost estimation", "Risk management", "Team management"],
        "ML Lab": ["Regression and classification experiments", "Clustering experiments", "Small neural network project"],
        "Major Project": ["Problem identification and literature review", "Design and implementation", "Report writing and presentation"],
    },
}

SAMPLE_ASSIGNMENTS = {
    2: [("Data Structures", "Linked list implementation", ["Implement a singly linked list with insert at front, insert at end and delete by value.", "Implement a doubly linked list and print it forwards and backwards.", "Write a function to detect a cycle in a linked list.", "Compare array and linked list for insertion in the middle."], "2026-10-06", "2026-10-18", 10),
        ("Engineering Mathematics III", "Laplace transform problem set", ["Find the Laplace transform of t·sin(2t).", "Find the inverse Laplace transform of 1/(s(s+1)).", "Solve y'' + 4y = 0, y(0)=1, y'(0)=0 using Laplace transforms.", "State and prove the first shifting theorem."], "2026-10-08", "2026-10-21", 10)],
    3: [("Operating Systems", "CPU scheduling simulator", ["Simulate FCFS, SJF and Round Robin scheduling for a given set of processes.", "Print the Gantt chart for each algorithm.", "Compare average waiting and turnaround times.", "Explain which algorithm suits interactive systems and why."], "2026-10-04", "2026-10-20", 15),
        ("Database Systems", "Normalisation exercise", ["Identify functional dependencies for the given student-course relation.", "Normalise the relation to 3NF.", "Check whether the decomposition is lossless.", "Write SQL to create the final tables."], "2026-10-07", "2026-10-19", 10)],
    4: [("Major Project", "Mid-term project report", ["Problem statement and motivation (1 page).", "Literature review of at least 5 papers.", "Methodology and system design.", "Progress so far and plan for the remaining semester."], "2026-10-01", "2026-10-23", 25),
        ("Machine Learning", "Classification mini-project", ["Choose a public dataset and describe it.", "Train logistic regression and a decision tree.", "Report accuracy, precision and recall.", "Discuss which model works better and why."], "2026-10-05", "2026-10-22", 15)],
}


def make_pdf(path, title, subtitle, sections):
    path.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    story = [Paragraph(title, styles["Title"]), Paragraph(subtitle, styles["Italic"]), Spacer(1, 12)]
    for heading, items in sections:
        story.append(Paragraph(heading, styles["Heading2"]))
        for i, item in enumerate(items, 1):
            story.append(Paragraph(f"{i}. {item}", styles["BodyText"]))
        story.append(Spacer(1, 8))
    story.append(Spacer(1, 16))
    story.append(Paragraph("SAMPLE document generated for the Zenkai demo. Not an official document.", styles["Italic"]))
    SimpleDocTemplate(str(path), pagesize=A4, title=title).build(story)


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def build_sample_year(y):
    subjects = {}
    for name, units in SAMPLE_SYLLABUS[y].items():
        rel = f"docs/syllabus/year{y}/{slug(name)}.pdf"
        heading = "Experiments" if ("Lab" in name or "Project" in name) else "Units"
        make_pdf(ROOT / "static" / rel, f"{name} – Syllabus", f"Year {y} · Sample syllabus", [(heading, units)])
        text = f"{name}\nYear {y} · SAMPLE syllabus (not official)\n\n" + "\n".join(
            f"{'Experiment' if heading == 'Experiments' else 'Unit'} {i}: {u}" for i, u in enumerate(units, 1))
        subjects[name] = {"name": name, "type": "Lab" if heading == "Experiments" else "Theory", "contact": None,
                          "pdf": rel, "pages": [1], "units": len(units), "text": text, "note": None}
    return {"official": False, "title": f"Year {y} syllabus (sample)", "source": "Sample data",
            "note": "Sample syllabus generated for the demo. Replace with the official curriculum for this year.",
            "documents": [], "missing": {}, "subjects": subjects}


def build_assignments():
    out = []
    a_dir = DOCS / "assignments"
    a_dir.mkdir(parents=True, exist_ok=True)
    real = [
        ("ma111-a1", "MA_111_assignment_1.pdf", "MA-111_assignment_1.pdf", "2026-08-28",
         "Assignment 1 · Unit 1: Introduction to Linear Algebra",
         "26 questions in three parts. Part A (Q1–Q10): matrices and their order, matrix operations, equivalent and elementary matrices, "
         "row-echelon form, Gauss–Jordan reduction, inverse of a matrix, linear dependence, rank, and systems of linear equations. "
         "Part B (Q11–Q18): longer problems on row reduction, inverse by Gauss–Jordan, linear independence with a parameter k, rank, "
         "and consistency of systems. Part C (Q19–Q26): conceptual and reasoning questions on rank, consistency and infinitely many solutions.",
         ["Matrices", "Elementary row operations", "Gauss–Jordan reduction", "Inverse of a matrix", "Linear dependence", "Rank", "Systems of linear equations"]),
        ("ma111-a2", "MA_111_assignment_2.pdf", "MA-111_assignment_2.pdf", "2026-09-02",
         "Assignment 2 · Unit II: Eigenvalues and Eigenvectors",
         "18 questions in three parts. Part A (Q1–Q5): characteristic equation, eigenvalues and eigenvectors of 2×2 matrices, diagonal and "
         "triangular matrices, trace and determinant properties, orthogonal vectors. Part B (Q6–Q10): 3×3 eigenvalue problems, properties of "
         "eigenvalues (A², A⁻¹, 3A, A+2I), a parameter-based question, orthogonality of eigenvectors. Part C (Q11–Q18): Cayley–Hamilton theorem, "
         "finding the inverse and higher powers with it, and conceptual questions.",
         ["Characteristic equation", "Eigenvalues and eigenvectors", "Properties of eigenvalues", "Orthogonal vectors", "Cayley–Hamilton theorem"]),
        ("ma111-a3", "MA_111_assignment_3.pdf", "MA-111_assignment_3.pdf", "2026-09-09",
         "Assignment 3 · Unit III: Differential Calculus",
         "About 27 questions in three parts. Part A (Q1–Q8): functions of two variables, limits and continuity, partial derivatives and their "
         "geometric meaning, homogeneous functions, Euler's theorem, total differentials. Part B: differentiability, composite functions and the "
         "chain rule, Jacobians, ε-δ limits, Euler's theorem extension, Taylor and Maclaurin expansions. Part C: stationary points, the second "
         "derivative test, maxima and minima with the method of undetermined multipliers, and curve tracing. "
         "(Note: question numbers 19–20 appear twice in the PDF, once in Part B and once in Part C.)",
         ["Functions of two variables", "Limits and continuity", "Partial differentiation", "Euler's theorem", "Jacobians", "Taylor and Maclaurin series", "Maxima and minima", "Curve tracing"]),
    ]
    for aid, out_name, src_name, posted, title, details, topics in real:
        shutil.copy(SRC / src_name, a_dir / out_name)
        out.append({"id": aid, "year": 1, "sections": list("ABCDEFGHIJ"), "code": "MA-111", "title": title,
                    "posted": posted, "due": None, "marks": None, "official": True, "details": details, "topics": topics,
                    "submit": "Due date and submission mode are not stated in the PDF. Confirm with your MA-111 instructor.",
                    "pdf": f"docs/assignments/{out_name}"})
    for y, items in {}.items():  # sample assignments removed
        for i, (code, title, questions, posted, due, marks) in enumerate(items, 1):
            rel = f"docs/assignments/year{y}_{slug(code)}_{i}.pdf"
            make_pdf(ROOT / "static" / rel, f"{code}: {title}", f"Year {y} · Sample assignment · Due {due} · {marks} marks",
                     [("Instructions", ["Attempt all questions.", "Show all steps.", "Submit before the due date."]), ("Questions", questions)])
            out.append({"id": f"y{y}-{i}", "year": y, "sections": list("ABCDEFGHIJ") + ["Z"], "code": code, "title": title,
                        "posted": posted, "due": due, "marks": marks, "official": False,
                        "details": " ".join(f"Q{n}. {q}" for n, q in enumerate(questions, 1)), "topics": [],
                        "submit": "Submit to the course instructor (sample).", "pdf": rel})
    return out


if __name__ == "__main__":
    syllabus = {"note": "Year 1 is the official first-year curriculum. Years 2–4 are sample syllabi for the demo.",
                "years": {"1": build_year1()}}
    (DATA / "syllabus.json").write_text(json.dumps(syllabus, indent=1, ensure_ascii=False), encoding="utf-8")
    assignments = build_assignments()
    (DATA / "assignments.json").write_text(json.dumps(
        {"note": "1st-year MA-111 assignments are the real assignment PDFs. Years 2–4 assignments are samples for the demo.",
         "assignments": assignments}, indent=1, ensure_ascii=False), encoding="utf-8")
    print("syllabus subjects:", {y: len(v["subjects"]) for y, v in syllabus["years"].items()}, "| assignments:", len(assignments))
