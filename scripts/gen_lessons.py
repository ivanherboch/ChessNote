#!/usr/bin/env python3
"""Prépare les 113 leçons à partir de scripts/lessons/*.txt (étape 1 sur 2).

Les leçons « PUZZLE id » reprennent une position réelle de la base Lichess (puzzles.json) :
le premier coup est la faute adverse, le joueur joue ensuite la combinaison.

Étape 1 : python3 scripts/gen_lessons.py   → app/build/lessons/lessons.tsv (coups SAN ou UCI, textes)
Étape 2 : LESSON_COMPILE=app/build/lessons/lessons.tsv ./gradlew :app:testDebugUnitTest --tests '*LessonCompilerTool*'
          → content/Lessons{Openings,Middlegames,Endgames}.kt : structures Kotlin typées, coups convertis en UCI
          (validés) par le moteur de l'application lui-même. Aucun analyse de texte ni de SAN au démarrage.
Option : --tool fichier  (export pour LessonToolTest)
"""
import json, os, sys
ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "lessons")
OUT = os.path.join(ROOT, "..", "app/src/main/java/com/ivanherboch/chessduck/content")
PUZ = json.load(open(os.path.join(SRC, "puzzles.json")))
START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

def parse(name):
    lessons, cur = [], None
    for raw in open(os.path.join(SRC, name), encoding="utf-8"):
        l = raw.strip()
        if not l: continue
        if l.startswith("#"):
            title, summary, third = [x.strip() for x in l[1:].split("|")]
            cur = {"title": title, "summary": summary, "fen": START, "moves": [], "texts": []}
            if third.startswith("PUZZLE"):
                p = PUZ[third.split()[1]]
                cur["fen"] = p["fen"]; cur["pending"] = p["moves"].split()
                cur["hero"] = "b" if p["fen"].split()[1] == "w" else "w"
            else: cur["hero"] = third
            lessons.append(cur)
        elif l.startswith("FEN:"): cur["fen"] = l[4:].strip()
        elif l.startswith("- "):
            cur["moves"].append(cur["pending"].pop(0)); cur["texts"].append(l[2:].strip())
        else:
            san, text = l.split(" : ", 1)
            cur["moves"].append(san.strip()); cur["texts"].append(text.strip())
    for c in lessons:
        assert not c.get("pending"), "commentaires manquants : " + c["title"]
        assert c["moves"], c["title"]
    return lessons

SEP = "\x1f"  # séparateur des commentaires dans le fichier intermédiaire

def emit(lessons, var, fname, rows):
    for c in lessons:
        for field in (c["title"], c["summary"], *c["texts"]):
            assert "|" not in field and SEP not in field and "\n" not in field, field
        rows.append("|".join([var, fname, c["title"], c["summary"], c["hero"], c["fen"], " ".join(c["moves"]), SEP.join(c["texts"])]))

sets = [("openings.txt", "OPENING_LESSONS", "LessonsOpenings.kt", 37),
        ("middlegames.txt", "MIDDLEGAME_LESSONS", "LessonsMiddlegames.kt", 38),
        ("endgames.txt", "ENDGAME_LESSONS", "LessonsEndgames.kt", 38)]
allL = []
rows = []
for src, var, fname, n in sets:
    ls = parse(src); assert len(ls) == n, (src, len(ls)); emit(ls, var, fname, rows); allL += ls
assert len({c["title"] for c in allL}) == 113
if "--tool" in sys.argv:
    with open(sys.argv[sys.argv.index("--tool") + 1], "w") as f:
        for c in allL: f.write(f'{c["title"]}|{c["hero"]}|{c["fen"]}|{" ".join(c["moves"])}\n')
tsv = os.path.join(ROOT, "..", "app", "build", "lessons", "lessons.tsv")
os.makedirs(os.path.dirname(tsv), exist_ok=True)
open(tsv, "w", encoding="utf-8").write("\n".join(rows) + "\n")
print("113 leçons préparées :", os.path.normpath(tsv))
print("Étape 2 : LESSON_COMPILE=app/build/lessons/lessons.tsv ./gradlew :app:testDebugUnitTest --tests '*LessonCompilerTool*'")
