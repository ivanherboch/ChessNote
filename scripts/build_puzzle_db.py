#!/usr/bin/env python3
"""Construit app/src/main/assets/chessduck.db : les 50 000 problèmes Lichess les plus populaires.

Source : https://database.lichess.org/lichess_db_puzzle.csv.zst (licence CC0).
Usage : build_puzzle_db.py lichess_db_puzzle.csv.zst app/schemas/.../1.json app/src/main/assets/chessduck.db
Le schéma SQL est repris tel quel du schéma exporté par Room pour garantir la compatibilité.
"""
import csv, io, json, os, sqlite3, sys
import zstandard

THEMES = """opening middlegame endgame fork pin skewer discoveredAttack discoveredCheck doubleCheck deflection
attraction sacrifice hangingPiece trappedPiece capturingDefender clearance interference intermezzo xRayAttack
quietMove defensiveMove zugzwang kingsideAttack queensideAttack exposedKing attackingF2F7 advancedPawn promotion
underPromotion enPassant castling mateIn1 mateIn2 mateIn3 mateIn4 mateIn5 backRankMate smotheredMate anastasiaMate
arabianMate bodenMate doubleBishopMate dovetailMate hookMate pawnEndgame rookEndgame bishopEndgame knightEndgame
queenEndgame queenRookEndgame""".split()
TOTAL = 50_000
MIN_PER_THEME = 300      # chaque thème du catalogue reste jouable hors-ligne
MIN_PER_BAND = 9_000     # chaque niveau de difficulté est bien fourni

def band(r):
    return 0 if r < 1200 else 1 if r < 1600 else 2 if r < 2000 else 3

def main(src, schema, out):
    rows = []
    with open(src, "rb") as f:
        rd = csv.reader(io.TextIOWrapper(zstandard.ZstdDecompressor().stream_reader(f), encoding="utf-8"))
        next(rd)
        for r in rd:
            # Problèmes fiables et appréciés : popularité >= 90 %, classement stabilisé.
            if int(r[5]) >= 90 and int(r[4]) <= 90:
                rows.append((r[0], r[1], r[2], int(r[3]), int(r[5]), int(r[6]), r[7]))
    assert len(THEMES) == 50
    # « Les plus populaires » = les plus joués parmi les mieux notés par les joueurs.
    rows.sort(key=lambda r: (-r[5], -r[4], r[0]))
    chosen, ids = [], set()
    def take(it, n):
        k = 0
        for r in it:
            if k >= n: break
            if r[0] not in ids:
                ids.add(r[0]); chosen.append(r); k += 1
    for t in THEMES:
        have = sum(1 for r in chosen if t in r[6].split())
        take((r for r in rows if t in r[6].split()), max(0, MIN_PER_THEME - have))
    for b in range(4):
        have = sum(1 for r in chosen if band(r[3]) == b)
        take((r for r in rows if band(r[3]) == b), max(0, MIN_PER_BAND - have))
    take(rows, TOTAL - len(chosen))
    assert len(chosen) == TOTAL
    chosen.sort(key=lambda r: r[0])

    if os.path.exists(out): os.remove(out)
    db = sqlite3.connect(out)
    sch = json.load(open(schema))["database"]
    for e in sch["entities"]:
        db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for i in e.get("indices", []):
            db.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    db.executemany("INSERT INTO puzzles (id, fen, moves, rating, popularity, plays, themes) VALUES (?,?,?,?,?,?,?)",
                   [(r[0], r[1], r[2], r[3], r[4], r[5], " " + r[6] + " ") for r in chosen])
    db.execute("PRAGMA user_version = %d" % sch["version"])
    db.commit(); db.execute("VACUUM"); db.close()
    from collections import Counter
    print("bandes", sorted(Counter(band(r[3]) for r in chosen).items()))
    th = Counter(t for r in chosen for t in r[6].split())
    print("thèmes min", sorted((th[t], t) for t in THEMES)[:8])
    print("taille", os.path.getsize(out))

if __name__ == "__main__":
    main(*sys.argv[1:4])
