"""Builds data/year1_timetable.json from a hand transcription of the official
1st Year Odd Semester 2026-27 timetable (Office of Dean Academic, effective 20-08-2026).
Line format:  Day Start-End | CODE | Faculty | @Room (optional)"""
import json, re
from pathlib import Path

RAW = {
"F": ("B2", """
Mo 9-11 | CY-102-Gr.1 | Varun
Mo 9-11 | EE-102-Gr.2 | Ashok K, Avinash
Mo 9-11 | CS-111-Gr.3 | Vidyotima
Mo 11-13 | CY-102-Gr.3 | K S Ghosh
Mo 11-13 | EE-102-Gr.1 | R. Chandel, Ravi
Mo 11-13 | CS-111-Gr.2 | Vidyotima
Mo 14-15 | CS-111 | Vidyotima
Mo 15-16 | EN-101 | Adya
Mo 16-17 | MB-101 | Kamboj
Tu 9-11 | CY-102-Gr.2 | K.S Ghosh
Tu 9-11 | EE-102-Gr.3 | Krishan K, Vishal
Tu 9-11 | CS-111-Gr.1 | Vidyotima
Tu 14-15 | CS-111 | Vidyotima
Tu 15-16 | EN-101 | Adya
We 11-12 | MA-111 | Maan
We 12-13 | CY-101 | Raj K
We 14-15 | CE-101 | Harshita | @DRG Hall
We 15-17 | CE-101(P) | Harshita, Anita, Amit | @DRG Hall
Th 9-10 | EE-101 | Ravi
Th 10-11 | EC-101 | Deepanshu
Th 11-12 | MA-111 | Maan
Th 12-13 | CY-101 | Raj K
Fr 9-10 | EE-101 | Ravi
Fr 10-11 | EC-101 | Deepanshu
Fr 11-12 | MA-111 | Maan
Fr 12-13 | CY-101 | Raj K
"""),
"A": ("B2", """
Mo 9-10 | MA-111 | Tanya
Mo 10-11 | PH-101 | Navneet
Mo 11-12 | ME-101 | Nitish
Mo 12-13 | CS-111 | Keshav
Mo 16-18 | CS-111-Gr.1 | Keshav
Mo 16-18 | PH-102-Gr.2 | Navneet
Mo 16-18 | HS-102-Gr.3 | Manoj
Tu 9-10 | MA-111 | Tanya
Tu 10-11 | PH-101 | Navneet
Tu 11-12 | ME-101 | Nitish
Tu 12-13 | CS-111 | Keshav
Tu 16-18 | CS-111-Gr.3 | Keshav
Tu 16-18 | PH-102-Gr.1 | Navneet
Tu 16-18 | HS-102-Gr.2 | Manoj
We 9-10 | MA-111 | Tanya
We 10-11 | PH-101 | Navneet
We 14-15 | ME-101 | Nitish
We 15-16 | ME-102 | K Dass
We 16-18 | ME-102 Workshop | K Dass | @Workshop
Th 9-11 | CS-111-Gr.2 | Keshav
Th 9-11 | PH-102-Gr.3 | Navneet
Th 9-11 | HS-102-Gr.1 | Manoj
Th 14-15 | HS-103 | Spriha
Th 15-16 | HS-101 | Manoj
Fr 14-15 | HS-103 | Spriha
Fr 15-16 | HS-101 | Manoj
"""),
"G": ("G1", """
Mo 9-10 | EE-101 | Neeraj
Mo 10-11 | EC-101 | Abhishek
Mo 11-12 | MA-111 | Monika
Mo 12-13 | CY-101 | J. Prakash
Mo 15-16 | CE-101 | Rajat | @DRG Hall
Mo 16-18 | CE-101(P) | Rajat, Ram Raj, Amit | @DRG Hall
Tu 9-10 | EE-101 | Neeraj
Tu 10-11 | EC-101 | Abhishek
Tu 11-12 | MA-111 | Monika
Tu 12-13 | CY-101 | J. Prakash
We 9-10 | MA-111 | Monika
We 10-11 | CY-101 | J. Prakash
We 11-13 | CS-111-Gr.1 | M. Nayyer
We 11-13 | CY-102-Gr.2 | J. Prakash
We 11-13 | EE-102-Gr.3 | Angira, Neeraj
Th 9-11 | CS-111-Gr.3 | M. Nayyer
Th 9-11 | CY-102-Gr.1 | J. Prakash
Th 9-11 | EE-102-Gr.2 | M Bharti, Abhishek
Th 11-13 | CS-111-Gr.2 | M. Nayyer
Th 11-13 | CY-102-Gr.3 | P. Awasthi
Th 11-13 | EE-102-Gr.1 | Krishan K, Vishal
Th 14-15 | CS-111 | M. Nayyer
Th 15-16 | EN-101 | Ravin
Fr 14-15 | CS-111 | M. Nayyer
Fr 15-16 | EN-101 | Ravin
Fr 16-17 | MB-101 | Richa
"""),
"B": ("G1", """
Mo 9-11 | CS-111-Gr.1 | Jyoti
Mo 9-11 | PH-102-Gr.2 | Suresh
Mo 9-11 | HS-102-Gr.3 | P. Puri
Mo 11-13 | ME-102 Workshop | K Dass | @Workshop
Mo 14-15 | ME-101 | Deepak
Mo 15-16 | ME-102 | K Dass
Tu 9-11 | CS-111-Gr.2 | Jyoti
Tu 9-11 | PH-102-Gr.3 | Shailja
Tu 9-11 | HS-102-Gr.1 | P. Puri
Tu 11-13 | CS-111-Gr.3 | Jyoti
Tu 11-13 | PH-102-Gr.1 | Navneet
Tu 11-13 | HS-102-Gr.2 | P. Puri
Tu 14-15 | HS-103 | Vishal
Tu 15-16 | CS-111 | Jyoti
We 11-12 | MA-111 | Pawan K
We 12-13 | PH-101 | B. Das
We 14-15 | HS-103 | Vishal
We 15-16 | CS-111 | Jyoti
Th 9-10 | ME-101 | Deepak
Th 10-11 | HS-101 | P. Puri
Th 11-12 | MA-111 | Pawan K
Th 12-13 | PH-101 | B. Das
Fr 9-10 | ME-101 | Deepak
Fr 10-11 | HS-101 | P. Puri
Fr 11-12 | MA-111 | Pawan K
Fr 12-13 | PH-101 | B. Das
"""),
"H": ("G2", """
Mo 14-15 | CS-111 | Pratibha
Mo 15-16 | EE-101 | Vishal
Tu 11-13 | CY-102-Gr.2 | J. Kuchlyan
Tu 11-13 | EE-102-Gr.3 | Saurabh, Avinash
Tu 11-13 | CS-111-Gr.1 | Pratibha
Tu 14-15 | CS-111 | Pratibha
Tu 15-16 | EE-101 | Vishal
Tu 16-17 | MB-101 | Pratima
We 9-11 | CY-102-Gr.1 | J. Kuchlyan
We 9-11 | EE-102-Gr.2 | Aman, Neeraj
We 9-11 | CS-111-Gr.3 | Pratibha
We 11-12 | MA-111 | Shruti
We 12-13 | CY-101 | J. Kuchlyan
We 14-16 | CY-102-Gr.3 | Arun
We 14-16 | EE-102-Gr.1 | Rakesh, Neeraj
We 14-16 | CS-111-Gr.2 | Pratibha
Th 9-10 | EN-101 | Ravin
Th 10-11 | EC-101 | Abhishek
Th 11-12 | MA-111 | Shruti
Th 12-13 | CY-101 | J. Kuchlyan
Th 15-16 | CE-101 | Shubham | @DRG Hall
Th 16-18 | CE-101(P) | Shubham, Sumit, Rajat | @DRG Hall
Fr 9-10 | EN-101 | Ravin
Fr 10-11 | EC-101 | Abhishek
Fr 11-12 | MA-111 | Shruti
Fr 12-13 | CY-101 | J. Kuchlyan
"""),
"C": ("G2", """
Mo 9-10 | MA-111 | R. K. Vats
Mo 10-11 | PH-101 | Abhishek
Mo 11-12 | ME-101 | Shania
Mo 12-13 | CS-111 | Priyanka
Tu 9-10 | MA-111 | R. K. Vats
Tu 10-11 | PH-101 | Abhishek
Tu 11-12 | ME-101 | Shania
Tu 12-13 | ME-102 | K Dass
Tu 16-18 | ME-102 Workshop | Param | @Workshop
We 9-10 | MA-111 | R. K. Vats
We 10-11 | PH-101 | Abhishek
We 14-15 | ME-101 | Shania
We 15-16 | CS-111 | Priyanka
We 16-17 | HS-101 | Zareena
Th 11-13 | CS-111-Gr.2 | Priyanka
Th 11-13 | PH-102-Gr.3 | Navneet
Th 11-13 | HS-102-Gr.1 | Zareena
Th 15-16 | HS-103 | Vishal
Th 16-18 | CS-111-Gr.1 | Priyanka
Th 16-18 | PH-102-Gr.2 | S Kaur
Th 16-18 | HS-102-Gr.3 | Zareena
Fr 14-15 | HS-101 | Zareena
Fr 15-16 | HS-103 | Vishal
Fr 16-18 | CS-111-Gr.3 | Priyanka
Fr 16-18 | PH-102-Gr.1 | Suresh
Fr 16-18 | HS-102-Gr.2 | Zareena
"""),
"I": ("F1", """
Mo 9-10 | EE-101 | Abhishek
Mo 10-11 | EC-101 | Apoorva
Mo 11-12 | MA-111 | Suket
Mo 12-13 | CY-101 | Varun
Tu 9-10 | EE-101 | Abhishek
Tu 10-11 | EC-101 | Apoorva
Tu 11-12 | MA-111 | Suket
Tu 12-13 | CY-101 | Varun
Tu 15-16 | CE-101 | Anita | @DRG Hall
Tu 16-18 | CE-101(P) | Anita, Ekta, Rajat | @DRG Hall
We 9-10 | MA-111 | Suket
We 10-11 | CY-101 | Varun
We 16-18 | CS-111-Gr.1 | Shobhna
We 16-18 | CY-102-Gr.2 | Varun
We 16-18 | EE-102-Gr.3 | CS Prasad, Avinash
Th 14-15 | EN-101 | Neha
Th 15-16 | CS-111 | Shobhna
Th 16-18 | CS-111-Gr.3 | Shobhna
Th 16-18 | CY-102-Gr.1 | Varun
Th 16-18 | EE-102-Gr.2 | Avijit, Avinash
Fr 9-11 | CS-111-Gr.2 | Shobhna
Fr 9-11 | CY-102-Gr.3 | Varun
Fr 9-11 | EE-102-Gr.1 | Avijit, Avinash
Fr 14-15 | EN-101 | Neha
Fr 15-16 | CS-111 | Shobhna
Fr 16-17 | MB-101 | Pratima
"""),
"D": ("F1", """
Mo 11-13 | CS-111-Gr.1 | Khalid
Mo 11-13 | PH-102-Gr.2 | Shailja
Mo 11-13 | HS-102-Gr.3 | Sumita
Mo 14-15 | ME-101 | Abhishek
Mo 15-16 | CS-111 | Khalid
Mo 16-18 | HS-102-Gr.1 | Sumita
Tu 14-15 | HS-101 | Sumita
Tu 15-16 | HS-103 | Spriha
We 9-11 | CS-111-Gr.2 | Khalid
We 9-11 | PH-102-Gr.3 | Shailja
We 11-12 | MA-111 | Shilpa
We 12-13 | PH-101 | Shailja
We 14-15 | HS-101 | Sumita
We 15-16 | HS-103 | Spriha
Th 9-10 | ME-101 | Abhishek
Th 10-11 | ME-102 | Ankush
Th 11-12 | MA-111 | Shilpa
Th 12-13 | PH-101 | Shailja
Th 14-16 | CS-111-Gr.3 | Khalid
Th 14-16 | PH-102-Gr.1 | Shailja
Th 14-16 | HS-102-Gr.2 | Sumita
Th 16-18 | ME-102 Workshop | Ankush | @Workshop
Fr 9-10 | ME-101 | Abhishek
Fr 10-11 | CS-111 | Khalid
Fr 11-12 | MA-111 | Shilpa
Fr 12-13 | PH-101 | Shailja
"""),
"J": ("F2", """
Mo 9-10 | EC-101 | Kodeshwari
Mo 10-11 | EN-101 | Ravin
Mo 11-12 | CY-101 | Arun
Mo 12-13 | MA-111 | Akanksha
Mo 14-16 | CS-111-Gr.1 | Tanuj
Mo 14-16 | CY-102-Gr.2 | Arun
Mo 14-16 | EE-102-Gr.3 | Vinod, Nimish
Tu 9-10 | EC-101 | Kodeshwari
Tu 10-11 | EN-101 | Ravin
Tu 11-12 | CY-101 | Arun
Tu 12-13 | MA-111 | Akanksha
Tu 14-16 | CS-111-Gr.2 | Tanuj
Tu 14-16 | CY-102-Gr.3 | Arun
Tu 14-16 | EE-102-Gr.1 | Shiva, Neeraj
We 11-12 | CY-101 | B. Gaur
We 12-13 | MA-111 | Akanksha
Th 9-10 | CE-101 | Anshul | @DRG Hall
Th 10-12 | CE-101(P) | Anshul, Mohan, Payel | @DRG Hall
Th 14-15 | CS-111 | Tanuj
Th 15-16 | EE-101 | Abhishek
Th 16-17 | MB-101 | Pratima
Fr 11-13 | CS-111-Gr.3 | Tanuj
Fr 11-13 | CY-102-Gr.1 | Arun
Fr 11-13 | EE-102-Gr.2 | Gagnesh, Avinash
Fr 14-15 | CS-111 | Tanuj
Fr 15-16 | EE-101 | Abhishek
"""),
"E": ("F2", """
Mo 14-15 | ME-101 | Rohit
Mo 15-16 | PH-101 | S Kaur
Tu 14-15 | HS-103 | SK Negi
Tu 15-16 | HS-101 | Sumita
We 9-10 | MA-111 | Soniya
We 10-11 | PH-101 | S Kaur
We 11-13 | CS-111-Gr.1 | Akanksha
We 11-13 | PH-102-Gr.2 | S Kaur
We 11-13 | HS-102-Gr.3 | Sumita
We 14-15 | HS-103 | SK Negi
We 15-16 | HS-101 | Sumita
We 16-18 | CS-111-Gr.2 | Akanksha
We 16-18 | PH-102-Gr.3 | S Kaur
We 16-18 | HS-102-Gr.1 | Sumita
Th 9-10 | ME-101 | Rohit
Th 10-11 | CS-111 | Akanksha
Th 11-12 | MA-111 | Soniya
Th 12-13 | PH-101 | S Kaur
Fr 9-10 | ME-101 | Rohit
Fr 10-11 | CS-111 | Akanksha
Fr 11-12 | MA-111 | Soniya
Fr 12-13 | ME-102 | Ankush
Fr 14-16 | CS-111-Gr.3 | Akanksha
Fr 14-16 | PH-102-Gr.1 | S Kaur
Fr 14-16 | HS-102-Gr.2 | Sumita
Fr 16-18 | ME-102 Workshop | Ankush | @Workshop
"""),
"Z": ("Studio I", """
Mo 9-10 | MA-112 (L) | MS
Mo 13-15 | AR-115 (L) | SR
Mo 15-16 | AR-113 (L) | AMI
Mo 16-17 | AR-113 (T) | AMI
Tu 9-10 | MA-112 (L) | MS
Tu 11-13 | AR-112 | SD, RS, DB, AB, AG
Tu 14-17 | AR-114 | BM, DB, VS, AP, AG
We 9-11 | AR-112 | SD, RS, VF1, DB, AB
We 11-12 | AR-111 | RK, NK, BP, VS, MH
We 13-17 | AR-111 | RK, NK, BP, VS, MH, VF1
Th 9-10 | MA-112 (L) | MS
Th 10-13 | AR-111 | RK, NK, BP, VS, MH
Th 15-16 | AR-115 (L) | SR
Th 16-17 | AR-115 (T) | SR
Fr 9-10 | MA-112 (T) | MS
Fr 10-13 | AR-114 | BM, DB, VS, AP, AG
Fr 14-16 | AR-113 (L) | AMI
"""),
}

SECTION_INFO = {
 "A": ("CE: Civil Engg", [("Gr.1","26BCE001–26BCE029"),("Gr.2","26BCE030–26BCE059"),("Gr.3","26BCE060–26BCE090")]),
 "B": ("ME: Mech Engg", [("Gr.1","26BME001–26BME029"),("Gr.2","26BME030–26BME059"),("Gr.3","26BME060–26BME090")]),
 "C": ("PH: Physics, ME: Mech Engg", [("Gr.1","26BPH001–26BPH029"),("Gr.2","26BPH030–26BPH050"),("Gr.3","26BME091–26BME125")]),
 "D": ("DCS: Dual Deg. Comp. Sc., DEC: Dual Deg. Electronics, CE: Civil", [("Gr.1","26DCS001–26DCS028"),("Gr.2","26DEC001–26DEC028"),("Gr.3","26BCE091–26BCE123")]),
 "E": ("MA: Mathematics, MS: Mater. Sc.", [("Gr.1","26BMA001–26BMA029"),("Gr.2","26BMA030–26BMA050"),("Gr.3","26BMS001–26BMS040")]),
 "F": ("CS: Comp. Sc.", [("Gr.1","26BCS001–26BCS029"),("Gr.2","26BCS030–26BCS059"),("Gr.3","26BCS060–26BCS090")]),
 "G": ("EC: Electronics", [("Gr.1","26BEC001–26BEC029"),("Gr.2","26BEC030–26BEC059"),("Gr.3","26BEC060–26BEC090")]),
 "H": ("EE: Electrical", [("Gr.1","26BEE001–26BEE029"),("Gr.2","26BEE030–26BEE059"),("Gr.3","26BEE060–26BEE090")]),
 "I": ("CH: Chemical, EC: Electronics", [("Gr.1","26BCH001–26BCH038"),("Gr.2","26BCH039–26BCH076, 25BCH015"),("Gr.3","26BEC091–26BEC117")]),
 "J": ("CS: Comp. Sc., EE: Electrical", [("Gr.1","26BCS091–26BCS130"),("Gr.2","26BEE091–26BEE125, 25BEE121")]),
 "Z": ("AR: Architecture", [("Gr.1","26BAR001–26BAR025"),("Gr.2","26BAR026–26BAR058")]),
}
DAYS = {"Mo":"Monday","Tu":"Tuesday","We":"Wednesday","Th":"Thursday","Fr":"Friday"}

def parse_line(line, room):
    head, code, faculty, *rest = [p.strip() for p in line.split("|")]
    d, span = head.split()
    start, end = (int(x) for x in span.split("-"))
    text = code
    group = None
    m = re.search(r"-Gr\.(\d)", code)
    if m:
        group = f"Gr.{m.group(1)}"; code = code[:m.start()]
    kind = "Lecture"
    if "(P)" in code or "Workshop" in code or group: kind = "Lab"
    if "(T)" in code: kind = "Tutorial"
    if code.startswith("AR-11") and "(L)" not in code and "(T)" not in code: kind = "Studio"
    base = re.match(r"[A-Z]{2}-\s?\d{3}", code).group(0).replace(" ", "")
    where = rest[0][1:] if rest else ("Respective Dept./Centre lab" if group else room)
    return {"day": DAYS[d], "start": start, "end": end, "code": base, "label": text.replace("AR- ", "AR-"),
            "faculty": faculty, "group": group, "type": kind, "room": where}

sections = []
order = list("ABCDEFGHIJ") + ["Z"]
for sid in order:
    room, raw = RAW[sid]
    branches, groups = SECTION_INFO[sid]
    classes = [parse_line(l, room) for l in raw.strip().splitlines() if l.strip()]
    sections.append({"id": sid, "name": f"Section {sid}", "classroom": room, "branches": branches,
                     "groups": [{"id": g, "rolls": r} for g, r in groups], "classes": classes})

out = {
  "year": 1, "title": "1st Year Odd Semester Class Timetable, Academic Year 2026-2027",
  "source": "Office of Dean Academic · Effective from 20-08-2026",
  "official": True,
  "note": "Labs will be conducted in the respective Department/Center. For subject names against subject codes, see the institute website.",
  "classrooms": {"B2": "Vivekananda Lecture Hall Complex, Basement", "G1": "Vivekananda Lecture Hall Complex, Ground Floor",
                 "G2": "Vivekananda Lecture Hall Complex, Ground Floor", "F1": "Vivekananda Lecture Hall Complex, First Floor",
                 "F2": "Vivekananda Lecture Hall Complex, First Floor", "DRG Hall": "Administrative Block, First Floor (Drawing Hall)",
                 "Workshop": "Near State Bank of India", "Studio I": "Department of Architecture"},
  "sections": sections,
}
Path(__file__).resolve().parent.parent.joinpath("data", "year1_timetable.json").write_text(json.dumps(out, indent=1, ensure_ascii=False), encoding="utf-8")
print("sections:", len(sections), "classes:", sum(len(s["classes"]) for s in sections))
