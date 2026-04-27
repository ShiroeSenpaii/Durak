# Durak Roguelike — **FOOL'S TABLE**

A native Android portrait roguelike card battler inspired by Durak. You play against 3 shady table personalities in quick chaotic encounters. Lose reputation and your run ends.

## Prototype Concept

- **Theme:** dark Eastern European tavern card table vibe (smoke, warm lamp light, red/gold/cream UI).
- **Core Loop:**
  1. Title
  2. Character Select
  3. Table Encounter vs 3 AI
  4. Relic Reward
  5. Shop/Event
  6. Progress to harder rounds and boss table (Round 5)
  7. Reach victory or run over

## Controls

- Tap a card in your hand to select it.
- Tap **ATTACK** when you are the attacker.
- Tap **DEFEND** when you are the defender and selected card is valid.
- Tap **TAKE** to pick up cards and absorb reputation damage.
- Tap **PASS** to skip when pass is available.
- Character skill button (for Card Shark) appears as **PEEK** once per table.

## Implemented MVP Features

- Native Android, Java, custom Canvas `View` renderer.
- Portrait-only experience.
- 36-card Durak-style deck (6..A), random trump per encounter.
- 4 classes:
  - Card Shark
  - Old Master
  - Market Hustler
  - Lucky Fool
- 3 AI personalities active in encounters:
  - The Hoarder
  - The Bully
  - The Cleaner
- Relic progression with 3-choice reward after table wins.
- Table modifiers + Round 5 boss table modifier.
- Run progression with Reputation, Coins, Round tracking.

## Build Locally

Requirements:
- JDK 17
- Android SDK

Commands:

```bash
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions APK

This repository includes workflow:

- `.github/workflows/android-debug.yml`

It builds with:

```bash
./gradlew assembleDebug
```

And uploads artifact:

- **DurakRoguelike-debug-apk**

### Manual run button (important)

If you don't see a **Run workflow** button in GitHub:

1. Make sure this workflow file exists on the repository's default branch (`main`).
2. Open **GitHub → Actions → Android Debug APK**.
3. Use **Run workflow** (enabled by `workflow_dispatch`) and pick branch.
4. After it finishes, open the run and download artifact **DurakRoguelike-debug-apk**.

If runs fail immediately with `./gradlew` issues, confirm `gradlew` is committed and that the workflow still contains the **Setup Gradle** step (`gradle/actions/setup-gradle`).

> Note: this repo intentionally avoids committing binary wrapper jars so PR creation works in environments that reject binary files.

## Current Prototype Limitations

- Durak rules are simplified for quick mobile play.
- AI is personality-driven but intentionally lightweight.
- Visuals are fully Canvas-drawn placeholders (no external art assets yet).
- No save/load, no meta-progression between runs, no multiplayer.
