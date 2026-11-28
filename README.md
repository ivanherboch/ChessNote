# ChessDuck 🦆♞

Application Android francophone d'apprentissage, d'entraînement et de jeu aux échecs,
100 % hors-ligne : Kotlin, Jetpack Compose, Material 3, Room, DataStore, Coroutines et Stockfish embarqué.

| Module | Contenu |
|---|---|
| ⌨️ Notation | Exercices de notation anglaise FIDE (SAN) sur positions réelles, clavier virtuel, diagnostic pédagogique, Mémo Anglais, Vision des cases |
| 🎓 Académie | 211 leçons en 6 catégories (16 bases, 53 ouvertures, 61 milieux de partie, 18 stratégie, 55 finales, 8 parties modèles) : Comprendre (démonstration commentée, voix hors-ligne) puis Pratiquer ; révisions espacées, séance du jour, finales et réflexes contre l'Ordinateur, tests de palier |
| 🧩 Problèmes | 150 000 problèmes Lichess hors-ligne, 50 thèmes, « Mon niveau » (ELO tactique), longueur de solution, À refaire et Favoris, réfutation sereine, indice en 2 temps, partage PNG |
| ⚔️ Jeu Libre | Ordinateur local (Stockfish) 600–2400 ELO, partie libre (par défaut, avec filet de sécurité) ou classée (ELO FIDE, K = 32), 2 joueurs, historique et revue annotée |
| 🧒 Mode enfant | Écran joyeux à 2 joueurs, grand échiquier, cavalier-girafe, leçons animées du déplacement de chaque pièce (Paramètres) |

Téléphone : portrait, ou paysage compact (rail de navigation, sans en-tête). Tablette : deux colonnes en paysage, échiquier à gauche et commandes à droite.

**Sauvegarde** : Paramètres > « Sauvegarde hors-ligne » exporte toute la progression (leçons, problèmes, parties, tests, ELO,
réglages) dans un fichier JSON et la réimporte, sans réseau ni permission. La base des problèmes est exclue de la
sauvegarde Android (plus de 25 Mo), qui garde ainsi les réglages.

Chaque APK poussé dans `releases/` est publié automatiquement en release GitHub (`.github/workflows/release.yml`).
Chaque push et chaque pull request lancent tous les tests (`.github/workflows/ci.yml`).

## Compiler

```bash
./gradlew :app:assembleRelease      # APK : app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest    # tests (règles, leçons, base Room, captures d'écran)
```

Prérequis : JDK 17+, SDK Android 35. Le NDK n'est nécessaire que pour recompiler Stockfish.

Signature : les variables `CHESSDUCK_STORE_FILE`, `CHESSDUCK_STORE_PASSWORD`, `CHESSDUCK_KEY_ALIAS` et
`CHESSDUCK_KEY_PASSWORD` (environnement ou `keystore.properties`, non versionné) priment ; à défaut, la clé de
démonstration `keystore/chessduck-release.jks` est utilisée (voir la Phase B dans `docs/contexte-session.md`).

## Données et scripts

- `scripts/build_stockfish.sh` : compile Stockfish 11 (`stockfish/src`) pour arm64-v8a, armeabi-v7a et x86_64.
- `scripts/build_puzzle_db.py` : construit `app/src/main/assets/chessduck.db` depuis `lichess_db_puzzle.csv.zst`
  (150 000 problèmes, les plus joués en priorité parmi ceux notés ≥ 90 % par les joueurs ; au moins 300 par difficulté × thème quand la base Lichess en contient assez ; total arrondi avec des mats populaires ; aucun doublon).
- `scripts/gen_lessons.py` : génère les leçons Kotlin à partir de `scripts/lessons/*.txt`.
- `art/` : sources SVG de la mascotte et des pièces.

## Licences des composants tiers

- **Stockfish 11** — GPLv3 (`stockfish/Copying.txt`). Exécutable distinct piloté en UCI ; sources complètes fournies.
- **Problèmes Lichess** — base ouverte database.lichess.org, CC0.
- **Pièces d'échecs** — série « No background » de Cburnett, et pièce féerique Girafe (`Chess_G?t45`) du mode enfant, Wikimedia Commons, CC BY-SA 3.0.
- **Police Fredoka** (mode enfant) — Google Fonts, SIL Open Font License (`art/fonts/Fredoka-OFL.txt`).

© Ivan Herboch, 2026
