#!/usr/bin/env python3
"""Migre sur place app/src/main/assets/chessduck.db du schéma Room v4 vers le v5 (sans le CSV Lichess).

Les 150 000 problèmes restent strictement identiques (vérifié par une empreinte de toutes les lignes) ; seuls changent :
- les index de la table des problèmes, remplacés par les index couvrants (rating, id) et (primaryTheme, rating, id) ;
- la table puzzle_progress (vide dans la base livrée), recréée avec ses colonnes v5 ;
- la nouvelle table de liaison puzzle_themes (thème officiel, classement, problème), créée WITHOUT ROWID pour rester
  compacte et remplie depuis la colonne `themes` avec les seuls thèmes officiels du catalogue (PuzzleThemes.kt),
  exactement comme MIGRATION_4_5 et DuckDatabase.themeLinksSql.
Renseigne room_master_table (identity hash de 5.json) et PRAGMA user_version = 5.
À lancer depuis la racine du dépôt, après la compilation qui exporte 5.json : python3 scripts/migrate_asset_v5.py
"""
import hashlib, json, os, re, shutil, sqlite3, tempfile

DIR = 'app/schemas/com.ivanherboch.chessduck.data.DuckDatabase/'
SRC = 'app/src/main/assets/chessduck.db'
schema = json.load(open(DIR + '5.json'))['database']
entities = {e['tableName']: e for e in schema['entities']}
keys = re.findall(r'ThemeInfo\(\s*"([A-Za-z0-9]+)"', open('app/src/main/java/com/ivanherboch/chessduck/content/PuzzleThemes.kt').read())
assert len(keys) == 50, len(keys)


def fingerprint(db):
    h = hashlib.sha256()
    for row in db.execute('SELECT id, fen, moves, rating, popularity, plays, themes, primaryTheme FROM puzzles ORDER BY id'):
        h.update(repr(row).encode())
    return h.hexdigest()


tmp = os.path.join(tempfile.mkdtemp(), 'chessduck_v5.db')
shutil.copy(SRC, tmp)
db = sqlite3.connect(tmp)
assert db.execute('PRAGMA user_version').fetchone()[0] == 4, 'la base embarquée doit être en v4'
before = fingerprint(db)
count = db.execute('SELECT COUNT(*) FROM puzzles').fetchone()[0]
assert count == 150_000, count
assert db.execute('SELECT COUNT(*) FROM puzzle_progress').fetchone()[0] == 0, 'la base livrée ne doit contenir aucune progression'

# Index couvrants de la table des problèmes (tels que déclarés dans 5.json).
db.execute('DROP INDEX IF EXISTS `index_puzzles_rating`')
db.execute('DROP INDEX IF EXISTS `index_puzzles_primaryTheme_rating`')
for ix in entities['puzzles']['indices']:
    db.execute(ix['createSql'].replace('${TABLE_NAME}', 'puzzles'))

# Progression (vide) recréée en v5.
db.execute('DROP TABLE puzzle_progress')
db.execute(entities['puzzle_progress']['createSql'].replace('${TABLE_NAME}', 'puzzle_progress'))

# Table de liaison : même schéma que l'entité, en WITHOUT ROWID (la clé primaire est l'unique arbre).
db.execute(entities['puzzle_themes']['createSql'].replace('${TABLE_NAME}', 'puzzle_themes') + ' WITHOUT ROWID')
inlist = ','.join("'%s'" % k for k in keys)
db.execute(
    "WITH RECURSIVE split(id, rating, rest, tag) AS (SELECT id, rating, trim(themes) || ' ', '' FROM puzzles "
    "UNION ALL SELECT id, rating, substr(rest, instr(rest, ' ') + 1), substr(rest, 1, instr(rest, ' ') - 1) FROM split WHERE rest <> '') "
    f"INSERT OR IGNORE INTO puzzle_themes (theme, rating, puzzleId) SELECT tag, rating, id FROM split WHERE tag IN ({inlist})"
)
links = db.execute('SELECT COUNT(*) FROM puzzle_themes').fetchone()[0]

for q in schema['setupQueries']:
    db.execute(q)
db.execute(f"INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '{schema['identityHash']}')")
db.execute('PRAGMA user_version = 5')
db.commit()
db.execute('VACUUM')
assert fingerprint(db) == before, 'les problèmes ont changé'
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
db.close()
shutil.copy(tmp, SRC)
print(f'base embarquée migrée en v5 : {SRC} ({count} problèmes inchangés, {links} liens de thèmes)')
