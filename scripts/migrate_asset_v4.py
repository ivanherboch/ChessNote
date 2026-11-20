#!/usr/bin/env python3
"""Migre sur place app/src/main/assets/chessduck.db du schéma Room v3 vers le v4.

La table des problèmes (150 000 lignes) est conservée telle quelle ; les tables de l'utilisateur, vides dans la base
livrée, sont recréées d'après app/schemas/.../4.json (progression des leçons enrichie, résultats des tests de
l'Académie, position de départ des parties). Renseigne room_master_table (identity hash) et PRAGMA user_version = 4.
À lancer depuis la racine du dépôt : python3 scripts/migrate_asset_v4.py
"""
import json, os, shutil, sqlite3, tempfile
DIR = 'app/schemas/com.ivanherboch.chessduck.data.DuckDatabase/'
v3 = {e['tableName']: e for e in json.load(open(DIR + '3.json'))['database']['entities']}
schema = json.load(open(DIR + '4.json'))['database']
src = 'app/src/main/assets/chessduck.db'
tmp = os.path.join(tempfile.mkdtemp(), 'chessduck_v4.db')
shutil.copy(src, tmp)
db = sqlite3.connect(tmp)
assert db.execute('PRAGMA user_version').fetchone()[0] == 3, 'la base embarquée doit être en v3'
for e in schema['entities']:
    t = e['tableName']
    if t == 'puzzles':
        assert e['createSql'] == v3[t]['createSql'], 'la table des problèmes ne doit pas changer'
        continue
    if t in v3:
        n = db.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0]
        assert n == 0, (t, n)
        db.execute(f'DROP TABLE {t}')
    db.execute(e['createSql'].replace('${TABLE_NAME}', t))
    for ix in e.get('indices', []):
        db.execute(ix['createSql'].replace('${TABLE_NAME}', t))
for q in schema['setupQueries']:
    db.execute(q)
db.execute(f"INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '{schema['identityHash']}')")
db.execute('PRAGMA user_version = 4')
db.commit()
db.execute('VACUUM')
db.close()
shutil.copy(tmp, src)
print('base embarquée migrée en v4 :', src)
