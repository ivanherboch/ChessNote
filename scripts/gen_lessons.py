#!/usr/bin/env python3
"""Prépare les leçons de l'Académie à partir de scripts/lessons/*.txt (étape 1 sur 2).

Format d'une leçon :
  # Titre | Résumé | w, b ou « PUZZLE id » | id=12 level=2 order=12 theme=fork kind=line
  FEN: …                                   (facultatif ; position de départ, sinon position initiale)
  TAKEAWAY: …                              (facultatif ; sinon la phrase « À retenir : … » du commentaire)
  CONDITIONS: …                            (facultatif ; comment reconnaître une position théorique)
  SHOW 3: marks=e4,d5 arrows=f3e5,g5d8     (facultatif ; repères affichés en mode Guidé après 3 demi-coups)
  e4 : commentaire du coup                 (coup SAN ou UCI, puis son commentaire)
  ?Nf3 : commentaire                       (« ? » : arrêt « À vous » du mode Guidé avant ce coup)
  - commentaire                            (leçon « PUZZLE » : coups lus dans puzzles.json ; « -? » pour un arrêt)
  IDEA: …                                  (facultatif, répétable : les idées clés d'une ouverture)
  TRAP: …                                  (facultatif, répétable : piège pour soi, piège contre soi)
  QUIZ: question | bonne réponse | mauvaise | mauvaise | explication   (QCM de fin de leçon, 2 ou 3 choix)
  VARIANT 5 | Titre                        (variante qui part de la position après 5 demi-coups de la ligne principale)
  > Nf6 : commentaire                      (coups de la variante, à la suite de VARIANT)
Métadonnées de l'en-tête : id (obligatoire), level 1–5, order, theme (clé Lichess), kind (line, model_game),
drill (finale jouable : KQK, KRRK, KRK, KBBK, KPK_WIN, LUCENA, PHILIDOR_DRAW, ROOK_PAWN_DRAW).

L'identifiant id= est fixe : il ne change jamais (la progression enregistrée en dépend), même si l'on insère,
déplace ou supprime des leçons. Les autres métadonnées sont facultatives (niveau 2, ordre = id, type « line »).
Les leçons « PUZZLE id » reprennent une position réelle de la base Lichess (puzzles.json) : le premier coup est
la faute adverse, le joueur joue ensuite la combinaison.

Étape 1 : python3 scripts/gen_lessons.py   → app/build/lessons/lessons.tsv
Étape 2 : LESSON_COMPILE=app/build/lessons/lessons.tsv ./gradlew :app:testDebugUnitTest --tests '*LessonCompilerTool*'
          → content/Lessons*.kt : structures Kotlin typées, coups convertis en UCI (validés) par le moteur de
          l'application lui-même. Aucune analyse de texte ni de SAN au démarrage.
Option : --tool fichier  (export pour LessonToolTest)
"""
import json, os, re, sys
ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "lessons")
PUZ = json.load(open(os.path.join(SRC, "puzzles.json")))
START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
KINDS = {"line", "model_game"}
DRILLS = {"KQK", "KRRK", "KRK", "KBBK", "KPK_WIN", "LUCENA", "PHILIDOR_DRAW", "ROOK_PAWN_DRAW"}
SQUARE = re.compile(r"^[a-h][1-8]$")
TAKEAWAY = re.compile(r"À retenir : (.+)$")


def meta(field, title):
    out = {}
    for kv in field.split():
        k, v = kv.split("=", 1)
        assert k in ("id", "level", "order", "theme", "kind", "drill"), (title, k)
        out[k] = v
    assert "id" in out, "id= manquant : " + title
    assert out.get("kind", "line") in KINDS, (title, out["kind"])
    assert 1 <= int(out.get("level", "2")) <= 5, title
    assert out.get("drill", "KQK") in DRILLS, (title, out["drill"])
    return out


def visuals(spec, title):
    marks, arrows = [], []
    for part in spec.split():
        k, v = part.split("=", 1)
        items = [x for x in v.split(",") if x]
        if k == "marks":
            assert all(SQUARE.match(x) for x in items), (title, v); marks += items
        elif k == "arrows":
            assert all(len(x) == 4 and SQUARE.match(x[:2]) and SQUARE.match(x[2:]) for x in items), (title, v); arrows += items
        else:
            raise AssertionError((title, k))
    return ",".join(marks) + "/" + ",".join(arrows)


def parse(name):
    lessons, cur = [], None
    for raw in open(os.path.join(SRC, name), encoding="utf-8"):
        l = raw.strip()
        if not l: continue
        if l.startswith("# "):
            parts = [x.strip() for x in l[2:].split("|")]
            assert len(parts) == 4, "en-tête invalide : " + l
            title, summary, third, fields = parts
            cur = {"title": title, "summary": summary, "fen": START, "moves": [], "texts": [], "pauses": [],
                   "visuals": [], "takeaway": "", "conditions": "", "meta": meta(fields, title),
                   "ideas": [], "traps": [], "quiz": "", "variants": []}
            if third.startswith("PUZZLE"):
                p = PUZ[third.split()[1]]
                cur["fen"] = p["fen"]; cur["pending"] = p["moves"].split()
                cur["hero"] = "b" if p["fen"].split()[1] == "w" else "w"
            else: cur["hero"] = third
            lessons.append(cur)
        elif l.startswith("FEN:"): cur["fen"] = l[4:].strip()
        elif l.startswith("TAKEAWAY:"): cur["takeaway"] = l[9:].strip()
        elif l.startswith("CONDITIONS:"): cur["conditions"] = l[11:].strip()
        elif l.startswith("IDEA:"): cur["ideas"].append(l[5:].strip())
        elif l.startswith("TRAP:"): cur["traps"].append(l[5:].strip())
        elif l.startswith("QUIZ:"):
            q = [x.strip() for x in l[5:].split("|")]
            assert len(q) in (4, 5), "QCM : question | bonne | mauvaise(s) | explication — " + cur["title"]
            cur["quiz"] = SEP.join(q)
        elif l.startswith("VARIANT "):
            n, vt = l[8:].split("|", 1)
            cur["variants"].append({"from": int(n), "title": vt.strip(), "moves": [], "texts": []})
        elif l.startswith("> "):
            san, text = l[2:].split(" : ", 1)
            cur["variants"][-1]["moves"].append(san.strip()); cur["variants"][-1]["texts"].append(text.strip())
        elif l.startswith("SHOW "):
            n, spec = l[5:].split(":", 1)
            cur["visuals"].append(n.strip() + ":" + visuals(spec, cur["title"]))
        elif l.startswith("- ") or l.startswith("-? "):
            if l.startswith("-?"): cur["pauses"].append(len(cur["moves"]))
            cur["moves"].append(cur["pending"].pop(0)); cur["texts"].append(l[l.index(" ") + 1:].strip())
        else:
            if l.startswith("?"): cur["pauses"].append(len(cur["moves"])); l = l[1:]
            san, text = l.split(" : ", 1)
            cur["moves"].append(san.strip()); cur["texts"].append(text.strip())
    for c in lessons:
        assert not c.get("pending"), "commentaires manquants : " + c["title"]
        assert c["moves"], c["title"]
        if not c["takeaway"]:
            found = [m.group(1) for t in c["texts"] for m in [TAKEAWAY.search(t)] if m]
            assert found, "« À retenir » manquant : " + c["title"]
            c["takeaway"] = found[-1][0].upper() + found[-1][1:]
        for v in c["visuals"]:
            assert 0 <= int(v.split(":")[0]) <= len(c["moves"]), (c["title"], v)
        for v in c["variants"]:
            assert v["moves"] and 0 < v["from"] < len(c["moves"]), (c["title"], v["title"])
    return lessons

SEP = "\x1f"  # séparateur des listes dans le fichier intermédiaire

def emit(lessons, var, fname, rows):
    for c in lessons:
        vtexts = [t for v in c["variants"] for t in [v["title"], *v["texts"]]]
        for field in (c["title"], c["summary"], c["takeaway"], c["conditions"], *c["texts"], *c["ideas"], *c["traps"], *vtexts):
            assert "|" not in field and SEP not in field and "\n" not in field, field
        m = c["meta"]
        rows.append("|".join([
            var, fname, c["title"], c["summary"], c["hero"], c["fen"], " ".join(c["moves"]), SEP.join(c["texts"]),
            m["id"], m.get("level", "2"), m.get("order", m["id"]), m.get("theme", ""), m.get("kind", "line"),
            ",".join(map(str, c["pauses"])), c["takeaway"], c["conditions"], SEP.join(c["visuals"]),
            m.get("drill", ""), SEP.join(c["ideas"]), SEP.join(c["traps"]), c["quiz"],
            # Variantes : « départ~titre~coups~commentaires » (commentaires séparés par \x1e), séparées par \x1d.
            "\x1d".join(f'{v["from"]}~{v["title"]}~{" ".join(v["moves"])}~' + "\x1e".join(v["texts"]) for v in c["variants"]),
        ]))

sets = [("basics.txt", "BASICS_LESSONS", "LessonsBasics.kt"),
        ("openings.txt", "OPENING_LESSONS", "LessonsOpenings.kt"),
        ("middlegames.txt", "MIDDLEGAME_LESSONS", "LessonsMiddlegames.kt"),
        ("strategy.txt", "STRATEGY_LESSONS", "LessonsStrategy.kt"),
        ("endgames.txt", "ENDGAME_LESSONS", "LessonsEndgames.kt"),
        ("model_games.txt", "MODEL_GAME_LESSONS", "LessonsModelGames.kt")]
allL = []
rows = []
for src, var, fname in sets:
    ls = parse(src); emit(ls, var, fname, rows); allL += ls
ids = [int(c["meta"]["id"]) for c in allL]
assert len(set(ids)) == len(ids), "identifiants en double"
assert len({c["title"] for c in allL}) == len(allL), "titres en double"
if "--tool" in sys.argv:
    with open(sys.argv[sys.argv.index("--tool") + 1], "w") as f:
        for c in allL: f.write(f'{c["title"]}|{c["hero"]}|{c["fen"]}|{" ".join(c["moves"])}\n')
tsv = os.path.join(ROOT, "..", "app", "build", "lessons", "lessons.tsv")
os.makedirs(os.path.dirname(tsv), exist_ok=True)
open(tsv, "w", encoding="utf-8").write("\n".join(rows) + "\n")
print(len(allL), "leçons préparées :", os.path.normpath(tsv))
print("Étape 2 : LESSON_COMPILE=app/build/lessons/lessons.tsv ./gradlew :app:testDebugUnitTest --tests '*LessonCompilerTool*'")
