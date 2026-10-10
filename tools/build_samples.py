"""Builds the SAMPLE data files (years 2-4 timetables, assignments, exams, syllabus, notices).
Everything here is demo data, clearly labelled in the app. Replace with official data."""
import json
from pathlib import Path
D = Path(__file__).resolve().parent.parent / "data"
SECS = list("ABCDEFGHIJ") + ["Z"]
DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]

# ---------------- years 2-4: sample timetables in the same format as year 1
YEAR_SUBJECTS = {
  2: (["Data Structures", "Discrete Mathematics", "Digital Electronics", "OOP with Java", "Engineering Mathematics III"], ["Data Structures Lab", "Java Lab", "Digital Electronics Lab"]),
  3: (["Operating Systems", "Computer Networks", "Database Systems", "Theory of Computation", "Software Engineering"], ["OS Lab", "DBMS Lab", "Networks Lab"]),
  4: (["Machine Learning", "Cloud Computing", "Information Security", "Elective I", "Project Management"], ["ML Lab", "Major Project", "Major Project"]),
}
def sample_year(y):
    lect, labs = YEAR_SUBJECTS[y]
    sections = []
    for n, sid in enumerate(SECS):
        room = f"LH-{y}{n+1:02d}"
        classes = []
        for d, day in enumerate(DAYS):
            for p, start in enumerate([9, 10, 11, 12]):
                sub = lect[(n + d * 2 + p) % 5]
                classes.append({"day": day, "start": start, "end": start + 1, "code": sub, "label": sub, "faculty": "", "group": None,
                                "type": "Tutorial" if (p == 3 and d in (1, 3)) else "Lecture", "room": room})
            if not (d in (2, 4) and (n + d) % 2 == 0):
                lab = labs[(n + d) % 3]
                classes.append({"day": day, "start": 14, "end": 16, "code": lab, "label": lab, "faculty": "", "group": None, "type": "Lab", "room": "Dept. lab"})
        sections.append({"id": sid, "name": f"Section {sid}", "classroom": room, "branches": "", "groups": [], "classes": classes})
    return {"year": y, "title": f"{y}{'nd' if y==2 else 'rd' if y==3 else 'th'} Year class timetable (sample)", "source": "Sample data",
            "official": False, "note": "Sample timetable for the demo. The official timetable for this year has not been added yet.",
            "classrooms": {}, "sections": sections}
for y in (2, 3, 4):
    (D / f"year{y}_timetable.json").write_text(json.dumps(sample_year(y), indent=1, ensure_ascii=False), encoding="utf-8")

# ---------------- syllabus outlines (sample), keyed by subject code
dept = {"MA": "Mathematics", "PH": "Physics", "CY": "Chemistry", "CS": "Computer Science & Engineering", "EE": "Electrical Engineering",
        "EC": "Electronics & Communication Engineering", "ME": "Mechanical Engineering", "CE": "Civil Engineering",
        "HS": "Humanities & Social Sciences", "AR": "Architecture"}
U = lambda *units: [{"title": t, "topics": tp} for t, tp in units]
syl = {
 "MA-111": U(("Unit 1 · Differential calculus", "Limits, continuity, mean value theorems, Taylor series"), ("Unit 2 · Multivariable calculus", "Partial derivatives, maxima and minima, Lagrange multipliers"), ("Unit 3 · Integral calculus", "Multiple integrals, change of order, applications"), ("Unit 4 · Matrices", "Rank, eigenvalues and eigenvectors, Cayley–Hamilton theorem")),
 "MA-112": U(("Unit 1 · Algebra and geometry", "Coordinate geometry, vectors"), ("Unit 2 · Calculus basics", "Differentiation and integration with design applications"), ("Unit 3 · Statistics", "Data, measures of central tendency, dispersion")),
 "PH-101": U(("Unit 1 · Mechanics and oscillations", "SHM, damped and forced oscillations"), ("Unit 2 · Waves and optics", "Interference, diffraction, polarisation"), ("Unit 3 · Quantum physics", "Wave–particle duality, Schrödinger equation"), ("Unit 4 · Materials", "Semiconductors, lasers, fibre optics")),
 "PH-102": U(("Lab", "Experiments on optics, oscillations and semiconductor devices; lab record and viva")),
 "CY-101": U(("Unit 1 · Atomic and molecular structure", "Bonding theories, MO theory"), ("Unit 2 · Thermodynamics and kinetics", "Laws of thermodynamics, rate laws"), ("Unit 3 · Electrochemistry and corrosion", "Cells, batteries, corrosion control"), ("Unit 4 · Polymers and water chemistry", "Polymers, hardness of water, treatment")),
 "CY-102": U(("Lab", "Titrations, water analysis, instrumental methods; lab record and viva")),
 "CS-111": U(("Unit 1 · Problem solving", "Algorithms, flowcharts, basics of programming"), ("Unit 2 · C programming", "Data types, control structures, functions"), ("Unit 3 · Arrays, strings and pointers", "1-D/2-D arrays, string handling, pointers"), ("Unit 4 · Structures and files", "Structures, unions, file handling"), ("Lab (group sessions)", "Programming exercises for each unit")),
 "EE-101": U(("Unit 1 · DC circuits", "Ohm's and Kirchhoff's laws, network theorems"), ("Unit 2 · AC circuits", "Phasors, RLC circuits, power factor"), ("Unit 3 · Machines", "Transformers, DC and induction machines (basics)")),
 "EE-102": U(("Lab", "Verification of network theorems, AC circuit measurements")),
 "EC-101": U(("Unit 1 · Semiconductor devices", "Diodes, rectifiers, BJTs"), ("Unit 2 · Amplifiers and op-amps", "Biasing, op-amp applications"), ("Unit 3 · Digital basics", "Number systems, logic gates, Boolean algebra")),
 "ME-101": U(("Unit 1 · Statics", "Force systems, equilibrium, free-body diagrams"), ("Unit 2 · Friction and trusses", "Friction, analysis of trusses"), ("Unit 3 · Dynamics", "Kinematics and kinetics of particles")),
 "ME-102": U(("Workshop practice", "Fitting, carpentry, welding, sheet metal and machining; job reports")),
 "CE-101": U(("Engineering drawing", "Projections of points, lines, planes and solids; sections; isometric views (theory + DRG Hall practice)")),
}
syllabus = {"note": "Sample syllabus outlines for the demo, not the official syllabus. Check the institute website for the official syllabus of each subject code.",
            "departments": dept, "subjects": syl}
(D / "syllabus.json").write_text(json.dumps(syllabus, indent=1, ensure_ascii=False), encoding="utf-8")

# ---------------- assignments (sample)
AE, FJ = list("ABCDE"), list("FGHIJ")
assignments = [
 {"id": "as1", "year": 1, "sections": AE + FJ, "code": "CS-111", "title": "Programming Assignment 2: Arrays and strings", "posted": "2026-10-05", "due": "2026-10-16", "marks": 10,
  "details": "Write C programs for: (1) reversing an array in place, (2) checking a palindrome string, (3) finding the second largest element. Submit source files and output screenshots.", "submit": "Upload on the course page / hand in at the lab"},
 {"id": "as2", "year": 1, "sections": AE + FJ, "code": "MA-111", "title": "Tutorial sheet 4: Partial derivatives", "posted": "2026-10-07", "due": "2026-10-14", "marks": 5,
  "details": "Solve questions 1–12 from tutorial sheet 4. Show all steps. Handwritten, A4 sheets.", "submit": "Hand in during the tutorial / lecture"},
 {"id": "as3", "year": 1, "sections": AE, "code": "PH-102", "title": "Lab record: Experiments 1–4", "posted": "2026-10-03", "due": "2026-10-15", "marks": 10,
  "details": "Complete observation tables, calculations and graphs for experiments 1–4 in the lab record and get them checked.", "submit": "Show to the lab instructor in your group's lab slot"},
 {"id": "as4", "year": 1, "sections": AE, "code": "ME-101", "title": "Problem set: Equilibrium of rigid bodies", "posted": "2026-10-08", "due": "2026-10-19", "marks": 10,
  "details": "Draw free-body diagrams and solve 8 problems on equilibrium of rigid bodies.", "submit": "Submit to the course instructor"},
 {"id": "as5", "year": 1, "sections": FJ, "code": "CE-101", "title": "Drawing sheet 3: Projections of solids", "posted": "2026-10-06", "due": "2026-10-17", "marks": 10,
  "details": "Complete sheet 3 (projections of prisms and pyramids) on A2 drawing sheet in pencil.", "submit": "Submit in the DRG Hall practical session"},
 {"id": "as6", "year": 1, "sections": FJ, "code": "CY-101", "title": "Assignment: Electrochemistry numericals", "posted": "2026-10-08", "due": "2026-10-20", "marks": 5,
  "details": "Solve 10 numericals on EMF of cells and the Nernst equation.", "submit": "Submit to the course instructor"},
 {"id": "as7", "year": 1, "sections": FJ, "code": "EE-101", "title": "Assignment: Network theorems", "posted": "2026-10-09", "due": "2026-10-21", "marks": 10,
  "details": "Apply Thevenin's, Norton's and superposition theorems to the 6 circuits given in class.", "submit": "Submit to the course instructor"},
 {"id": "as8", "year": 1, "sections": ["Z"], "code": "AR-111", "title": "Studio sheet: Basic design composition", "posted": "2026-10-05", "due": "2026-10-18", "marks": 20,
  "details": "Prepare a composition on A1 sheet using principles of balance, rhythm and contrast. Bring the sheet for the studio review.", "submit": "Pin up in Studio I for the review"},
 {"id": "as9", "year": 1, "sections": ["Z"], "code": "MA-112", "title": "Tutorial: Coordinate geometry", "posted": "2026-10-09", "due": "2026-10-16", "marks": 5,
  "details": "Solve the tutorial problems on straight lines and circles.", "submit": "Hand in during the tutorial"},
 {"id": "as10", "year": 2, "sections": SECS, "code": "Data Structures", "title": "Linked list implementation", "posted": "2026-10-06", "due": "2026-10-18", "marks": 10,
  "details": "Implement singly and doubly linked lists with insert, delete and search.", "submit": "Upload on the course page"},
 {"id": "as11", "year": 3, "sections": SECS, "code": "Operating Systems", "title": "CPU scheduling simulator", "posted": "2026-10-04", "due": "2026-10-20", "marks": 15,
  "details": "Simulate FCFS, SJF and Round Robin scheduling and compare waiting times.", "submit": "Upload on the course page"},
 {"id": "as12", "year": 4, "sections": SECS, "code": "Major Project", "title": "Mid-term project report", "posted": "2026-10-01", "due": "2026-10-23", "marks": 25,
  "details": "Submit a 10-page progress report: problem, literature review, methodology and progress so far.", "submit": "Email to your project supervisor"},
]
(D / "assignments.json").write_text(json.dumps({"note": "Sample assignments for the demo. In real use, faculty would publish these.", "assignments": assignments}, indent=1, ensure_ascii=False), encoding="utf-8")

# ---------------- exam schedules (sample)
def row(date, time, code, secs, venue="As per seating plan"):
    return {"date": date, "time": time, "code": code, "sections": secs, "venue": venue}
M, A_ = "9:30 AM – 11:00 AM", "2:00 PM – 3:30 PM"
y1 = [row("2026-11-02", M, "MA-111", AE + FJ), row("2026-11-02", M, "MA-112", ["Z"], "Studio I"),
      row("2026-11-03", M, "PH-101", AE), row("2026-11-03", M, "CY-101", FJ), row("2026-11-03", M, "AR-113", ["Z"], "Studio I"),
      row("2026-11-04", M, "CS-111", AE + FJ), row("2026-11-04", M, "AR-115", ["Z"], "Studio I"),
      row("2026-11-05", M, "ME-101", AE), row("2026-11-05", M, "EE-101", FJ),
      row("2026-11-05", A_, "HS-101", AE), row("2026-11-05", A_, "EC-101", FJ),
      row("2026-11-06", M, "HS-103", AE), row("2026-11-06", M, "EN-101", FJ),
      row("2026-11-06", A_, "MB-101", FJ)]
def yrows(y, start_day):
    lect = YEAR_SUBJECTS[y][0]
    return [row(f"2026-10-{start_day + i:02d}", M, s, SECS) for i, s in enumerate(lect)]
exams = {"note": "Sample exam schedule for the demo, not the official date sheet. Always check the official notice from the examination office.",
         "years": {"1": {"name": "Mid-Semester Examination, Odd Semester 2026-27", "rows": y1},
                   "2": {"name": "Mid-Semester Examination, Semester III", "rows": yrows(2, 27)},
                   "3": {"name": "Mid-Semester Examination, Semester V", "rows": yrows(3, 26)},
                   "4": {"name": "Mid-Semester Examination, Semester VII", "rows": yrows(4, 26)}},
         "rules": ["Carry your college ID card and admit card.", "Reach the exam hall 15 minutes early.", "Mobile phones and smart watches are not allowed."]}
(D / "exams.json").write_text(json.dumps(exams, indent=1, ensure_ascii=False), encoding="utf-8")

# ---------------- notices: year-aware audience
old = {n["id"]: n for n in json.load(open(D / "notices.json"))["notices"]}
F = "\n\n[Fictional sample notice for the CampusMate AI demo]"
def N(id, date, title, frm, years, sections, text): return {"id": id, "date": date, "title": title, "from": frm, "years": years, "sections": sections, "text": text}
notices = [
 N("hostel-fee", "2026-10-07", old["hostel-fee"]["title"], old["hostel-fee"]["from"], ["all"], ["all"], old["hostel-fee"]["text"]),
 N("long-notice", "2026-10-08", old["long-notice"]["title"], old["long-notice"]["from"], ["all"], ["all"], old["long-notice"]["text"]),
 N("vague-notice", "2026-10-06", old["vague-notice"]["title"], old["vague-notice"]["from"], ["all"], ["all"], old["vague-notice"]["text"]),
 N("y1-midsem", "2026-10-09", "1st year mid-semester exams: admit cards and rules", "Examination Office", [1], ["all"],
   "SAMPLE INSTITUTE NOTICE\nExamination Office\nDate: 09 October 2026\n\nSUBJECT: Mid-Semester Examinations for 1st Year (Odd Semester 2026-27)\n\n1. Mid-semester examinations for all 1st year sections will be held from 2 November 2026 to 6 November 2026. The subject-wise schedule is available in the Exam Schedule section.\n2. Admit cards can be downloaded from the student portal between 26 October 2026 and 31 October 2026.\n3. Students with attendance below 75% as on 24 October 2026 will not be issued admit cards. Condonation requests with valid documents must reach the Head of Department by 26 October 2026.\n4. Students must carry their admit card and college ID card. Mobile phones, smart watches and programmable calculators are not allowed." + F),
 N("exam-schedule", "2026-10-05", old["exam-schedule"]["title"], old["exam-schedule"]["from"], [2], ["all"], old["exam-schedule"]["text"]),
 N("y1-a-ph102", "2026-10-09", "PH-102 lab: record check before mid-sems", "Department of Physics", [1], ["A"],
   "SAMPLE INSTITUTE NOTICE\nDepartment of Physics\nDate: 09.10.2026\n\nNOTICE – 1st Year, Section A\n\nAll students of Section A must get their PH-102 lab records (experiments 1 to 4) checked in their group's lab slot before 15.10.2026. Students whose records are not checked will be marked incomplete for the internal assessment.\n\nBring the lab record and calculations. Graph sheets must be pasted." + F),
 N("y1-b-extra", "2026-10-09", "Extra class: MA-111 on Saturday", "Department of Mathematics", [1], ["B"],
   "SAMPLE INSTITUTE NOTICE\nDepartment of Mathematics\n\nNOTICE – 1st Year, Section B\n\nAn extra class of MA-111 will be held on Saturday, 17 October 2026, from 10:00 AM to 12:00 noon in classroom G1 to complete the unit on multivariable calculus before the mid-semester examinations. Attendance will be counted.\n\nBring your notes and tutorial sheet 4." + F),
 N("y1-cd-guest", "2026-10-07", "Guest lecture for Sections C and D", "Office of Dean Academic", [1], ["C", "D"],
   "SAMPLE INSTITUTE NOTICE\n\nNOTICE – 1st Year, Sections C and D\n\nA guest lecture on 'Engineering in Everyday Life' will be held on Wednesday, 14 October 2026, at 5:00 PM in the Seminar Hall. Attendance is compulsory for Sections C and D.\n\nStudents should register attendance at the entrance using their college ID card." + F),
 N("y1-ej-room", "2026-10-09", "Tutorial room change for Sections E to J", "Office of Dean Academic", [1], list("EFGHIJ"),
   "SAMPLE INSTITUTE NOTICE\nDate: 09 October 2026\n\nNOTICE – 1st Year, Sections E, F, G, H, I and J\n\nDuring the mid-semester examination week (2–6 November 2026), classrooms F1, F2 and G1 will be used as exam halls. Any make-up classes in that week will be held in the Vivekananda Lecture Hall Complex basement. Timings remain unchanged.\n\nOffice of Dean Academic" + F),
 N("y1-z-jury", "2026-10-08", "AR-111 studio review (jury)", "Department of Architecture", [1], ["Z"],
   "SAMPLE INSTITUTE NOTICE\nDepartment of Architecture\nDate: 08 October 2026\n\nNOTICE – 1st Year, Section Z\n\nThe first studio review (jury) for AR-111 will be held on Monday, 19 October 2026, in Studio I. All students must pin up their basic design composition sheets by 9:00 AM. Late submissions will not be reviewed.\n\nBring all process sketches along with the final sheet." + F),
]
(D / "notices.json").write_text(json.dumps({"note": "Sample notices for the demo. In real use, the institute offices would publish these.", "notices": notices}, indent=1, ensure_ascii=False), encoding="utf-8")
print("ok", len(notices), "notices", len(assignments), "assignments")
