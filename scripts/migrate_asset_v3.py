#!/usr/bin/env python3
"""Migre sur place app/src/main/assets/chessduck.db vers le schéma Room v3 (sans le CSV Lichess).

Recrée les tables à partir de app/schemas/.../3.json, recopie les 150 000 problèmes en calculant primaryTheme
avec exactement la liste de priorité de PuzzleThemes.kt, renseigne room_master_table (identity hash) et
PRAGMA user_version = 3. À lancer depuis la racine du dépôt : python3 scripts/migrate_asset_v3.py
"""
import json, os, re, shutil, sqlite3, tempfile
schema=json.load(open('app/schemas/com.ivanherboch.chessduck.data.DuckDatabase/3.json'))['database']
src='app/src/main/assets/chessduck.db'
kt=open('app/src/main/java/com/ivanherboch/chessduck/content/PuzzleThemes.kt').read()
block=kt[kt.index('internal val PRIORITY = listOf('):]
block=block[:block.index(')\n')]
prio=re.findall(r'"(\w+)"', block)
keys=set(re.findall(r'ThemeInfo\("(\w+)"', kt))
prio=[k for k in prio if k in keys]
print('priorité', len(prio), 'thèmes', len(keys))
def primary(themes):
    toks=set(themes.split())
    for k in prio:
        if k in toks: return k
    return 'middlegame'
old=sqlite3.connect(src)
out_path=os.path.join(tempfile.gettempdir(), 'chessduck_v3.db')
if os.path.exists(out_path): os.remove(out_path)
new=sqlite3.connect(out_path)
for e in schema['entities']:
    sql=e['createSql'].replace('${TABLE_NAME}', e['tableName'])
    new.execute(sql)
    for ix in e.get('indices',[]):
        new.execute(ix['createSql'].replace('${TABLE_NAME}', e['tableName']))
for q in schema['setupQueries']:
    new.execute(q)
new.execute(f"INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '{schema['identityHash']}')")
rows=old.execute('SELECT id, fen, moves, rating, popularity, plays, themes FROM puzzles ORDER BY rowid').fetchall()
new.executemany('INSERT INTO puzzles (id, fen, moves, rating, popularity, plays, themes, primaryTheme) VALUES (?,?,?,?,?,?,?,?)', [r+(primary(r[6]),) for r in rows])
for t in ['puzzle_progress','lesson_progress','games']:
    n=old.execute(f'select count(*) from {t}').fetchone()[0]
    assert n==0, (t,n)
new.execute('PRAGMA user_version = 3')
new.commit()
new.execute('VACUUM')
new.close()
print('lignes', len(rows))
shutil.copy(out_path, src)
