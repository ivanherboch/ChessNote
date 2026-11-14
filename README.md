# ChessDuck 🦆♞

Application Android francophone d'apprentissage, d'entraînement et de jeu aux échecs,
100 % hors-ligne : Kotlin, Jetpack Compose, Material 3, Room, DataStore, Coroutines et Stockfish embarqué.

| Module | Contenu |
|---|---|
| ⌨️ Notation | Exercices de notation anglaise FIDE (SAN) sur positions réelles, clavier virtuel, diagnostic pédagogique, Mémo Anglais |
| 🎓 Académie | 113 leçons (37 ouvertures, 38 milieux de partie, 38 finales) en Mode Guidé et Mode Défi |
| 🧩 Problèmes | 150 000 problèmes Lichess hors-ligne, 50 thèmes, réfutation temporisée, indice en 2 temps, partage PNG |
| ⚔️ Jeu Libre | Ordinateur local (Stockfish) 600–2400 ELO, 2 joueurs, ELO FIDE (K = 32), historique et revue annotée |
| 🧒 Mode enfant | Écran joyeux à 2 joueurs, grand échiquier, cavalier-girafe, leçons animées du déplacement de chaque pièce (Paramètres) |

Téléphone : portrait. Tablette (plus petite largeur ≥ 600 dp) : paysage, échiquier à gauche et commandes à droite.

Chaque APK poussé dans `releases/` est publié automatiquement en release GitHub (`.github/workflows/release.yml`).

## Compiler

```bash
./gradlew :app:assembleRelease      # APK : app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest    # tests (règles, leçons, base Room, captures d'écran)
```

Prérequis : JDK 17+, SDK Android 35. Le NDK n'est nécessaire que pour recompiler Stockfish.

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
