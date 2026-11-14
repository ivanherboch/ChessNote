#!/usr/bin/env python3
"""Construit app/src/main/assets/chessduck.db à partir de la base officielle des problèmes Lichess.

Source : https://database.lichess.org/lichess_db_puzzle.csv.zst (licence CC0).
Usage : build_puzzle_db.py lichess_db_puzzle.csv.zst app/schemas/.../2.json app/src/main/assets/chessduck.db

Sélection, toujours « les plus populaires en priorité » (les plus joués parmi les mieux notés) :
  1. un socle de 100 000 problèmes (identique aux versions précédentes : la progression des joueurs est conservée) ;
  2. au moins 300 problèmes pour chaque couple difficulté × thème (thème = idée la plus caractéristique,
     même règle que PuzzleThemes.PRIORITY dans l'application) ;
  3. des mats populaires pour arrondir le total.
Aucun doublon : chaque identifiant Lichess n'apparaît qu'une fois.
Le schéma SQL est repris du schéma exporté par Room pour garantir la compatibilité.
"""
import csv, io, json, os, sqlite3, sys
from collections import Counter
import zstandard

THEMES = """opening middlegame endgame fork pin skewer discoveredAttack discoveredCheck doubleCheck deflection
attraction sacrifice hangingPiece trappedPiece capturingDefender clearance interference intermezzo xRayAttack
quietMove defensiveMove zugzwang kingsideAttack queensideAttack exposedKing attackingF2F7 advancedPawn promotion
underPromotion enPassant castling mateIn1 mateIn2 mateIn3 mateIn4 mateIn5 backRankMate smotheredMate anastasiaMate
arabianMate bodenMate doubleBishopMate dovetailMate hookMate pawnEndgame rookEndgame bishopEndgame knightEndgame
queenEndgame queenRookEndgame""".split()
# Du plus caractéristique au plus général — à garder identique à PuzzleThemes.PRIORITY (Kotlin).
PRIORITY = """smotheredMate anastasiaMate arabianMate bodenMate doubleBishopMate dovetailMate hookMate backRankMate
mateIn1 mateIn2 mateIn3 mateIn4 mateIn5
underPromotion enPassant castling doubleCheck discoveredCheck zugzwang interference xRayAttack
trappedPiece capturingDefender intermezzo clearance attraction deflection skewer pin fork
discoveredAttack hangingPiece quietMove defensiveMove sacrifice promotion advancedPawn
attackingF2F7 exposedKing kingsideAttack queensideAttack
pawnEndgame rookEndgame bishopEndgame knightEndgame queenEndgame queenRookEndgame
opening endgame middlegame""".split()
MATES = set(PRIORITY[:13])
BASE = 100_000           # socle historique
MIN_PER_THEME = 300      # règles du socle (inchangées)
MIN_PER_BAND = 9_000
MIN_PER_CELL = 300       # difficulté × thème
ROUND_TO = 50_000        # le total est arrondi au multiple supérieur
FLOOR_POPULARITY = 50    # en dernier recours, pour les cases rares

def band(r):
    return 0 if r < 1200 else 1 if r < 1600 else 2 if r < 2000 else 3

def primary(themes):
    s = set(themes.split())
    return next((t for t in PRIORITY if t in s), "middlegame")

def read(src):
    with open(src, "rb") as f:
        rd = csv.reader(io.TextIOWrapper(zstandard.ZstdDecompressor().stream_reader(f), encoding="utf-8"))
        next(rd)
        for r in rd:
            yield (r[0], r[1], r[2], int(r[3]), int(r[5]), int(r[6]), r[7]), int(r[4])

def main(src, schema, out):
    assert len(THEMES) == 50 and sorted(THEMES) == sorted(PRIORITY)
    # Problèmes fiables et appréciés : popularité >= 90 %, classement stabilisé.
    rows = [r for r, dev in read(src) if r[4] >= 90 and dev <= 90]
    rows.sort(key=lambda r: (-r[5], -r[4], r[0]))   # les plus joués d'abord
    chosen, ids = [], set()
    def take(it, n):
        k = 0
        for r in it:
            if k >= n: break
            if r[0] not in ids:
                ids.add(r[0]); chosen.append(r); k += 1
        return k
    # 1. Socle (algorithme historique, strictement inchangé).
    for t in THEMES:
        have = sum(1 for r in chosen if t in r[6].split())
        take((r for r in rows if t in r[6].split()), max(0, MIN_PER_THEME - have))
    for b in range(4):
        have = sum(1 for r in chosen if band(r[3]) == b)
        take((r for r in rows if band(r[3]) == b), max(0, MIN_PER_BAND - have))
    take(rows, BASE - len(chosen))
    assert len(chosen) == BASE

    # 2. Au moins 300 problèmes par difficulté × thème.
    cell = Counter((band(r[3]), primary(r[6])) for r in chosen)
    by_cell = {}
    for r in rows:
        if r[0] not in ids: by_cell.setdefault((band(r[3]), primary(r[6])), []).append(r)
    short = {}
    for b in range(4):
        for t in PRIORITY:
            need = MIN_PER_CELL - cell[(b, t)]
            if need > 0:
                got = take(by_cell.get((b, t), []), need)
                cell[(b, t)] += got
                if got < need: short[(b, t)] = need - got
    if short:
        # Cases trop rares parmi les problèmes « fiables » : on élargit, toujours par popularité décroissante.
        extra = {}
        for r, dev in read(src):
            k = (band(r[3]), primary(r[6]))
            if k in short and r[0] not in ids and r[4] >= FLOOR_POPULARITY: extra.setdefault(k, []).append(r)
        for k, need in list(short.items()):
            cand = sorted(extra.get(k, []), key=lambda r: (-r[4], -r[5], r[0]))
            got = take(cand, need)
            cell[k] += got
            if got >= need: del short[k]
            else: short[k] = need - got

    # 3. Des mats populaires pour arrondir le total.
    total = -(-len(chosen) // ROUND_TO) * ROUND_TO
    take((r for r in rows if primary(r[6]) in MATES), total - len(chosen))
    assert len(chosen) == total and len(ids) == total == len({r[0] for r in chosen}), "doublon ou total incorrect"
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

    cell = Counter((band(r[3]), primary(r[6])) for r in chosen)
    print("total", total)
    print("bandes", sorted(Counter(band(r[3]) for r in chosen).items()))
    print("plus petites cases", sorted((cell[(b, t)], b, t) for b in range(4) for t in PRIORITY)[:10])
    print("cases sous %d (introuvables dans la base Lichess)" % MIN_PER_CELL, {f"{b}:{t}": cell[(b, t)] for (b, t) in short})
    print("popularité minimale", min(r[4] for r in chosen), "taille", os.path.getsize(out))

if __name__ == "__main__":
    main(*sys.argv[1:4])
