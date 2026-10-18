# Cars & Dragons — Implementation Plan

Voice-driven **AD&D 2nd Edition** for the car. An **Android Auto** app is the Dungeon Master: it narrates by voice **in Czech**, takes spoken player actions, and shows party status on the car screen.

Personal, sideloaded project. No Play Store release. Target device: **Pixel 7a**.

---

## 1. Rules for the implementing agent

These are non-negotiable unless the owner changes them.

1. **All game math is deterministic Kotlin.** Dice, THAC0, damage, saving throws, HP, spell slots, and XP live in `:rules`. No LLM ever computes or decides an outcome.
2. **The narrator only narrates and proposes.** It turns player speech into *proposed* structured actions. The engine validates them, rejects illegal ones, and resolves the legal ones.
3. **Jev only chooses among legal options.** The engine enumerates legal monster actions; Jev picks one. If Jev's confidence is low or the call fails, a simple heuristic decides.
4. **Seeded RNG everywhere.** Inject the random source so every test and replay is reproducible.
5. **Language boundary.** Code, identifiers, logs, internal data, and Jev prompts are English. Everything players hear or see is Czech.
6. **Secrets.** API keys live in `local.properties` and are exposed via `BuildConfig`. Never commit them.
7. **Git workflow.** Branches are named `crabhi/claude/<topic>`. Set up pre-commit (ktlint, detekt) in M0 and run `pre-commit run --all-files` before every commit.
8. **Driver safety.** Nothing in the game may require looking at or touching the screen. The screen shows status only. No text input of any kind while driving.

---

## 2. Architecture

### Modules

| Module | Type | Responsibility |
|---|---|---|
| `:rules` | Kotlin/JVM, no Android deps | Characters, races, classes, combat, saves, spells, monster stat blocks. Heavily unit-tested. |
| `:engine` | Kotlin/JVM | Game state, turn loop, adventure outline, narrator client, Jev client, JSON save/load, transcripts. |
| `:cli` | Kotlin/JVM app | Desktop text harness (Czech) to play a full game in a terminal. Most development and testing happens here. |
| `:app` | Android | Car App Library screens, speech in/out, audio focus, wiring to `:engine`. |

### Interfaces (swappable implementations)

- `Narrator`: default implementation calls **OpenAI GPT-6 Sol** (`gpt-6-sol`). Must be swappable (e.g. Claude) without touching the engine.
- `TacticsAdvisor`: default implementation calls **Jev** (TypeSafe AI). Fallback: `HeuristicTactics`.
- `SpeechInput`: default `AndroidSpeechInput` (`SpeechRecognizer`, `cs-CZ`). Planned alternative: `VoskSpeechInput`.
- `SpeechOutput`: default `AndroidTts` (`TextToSpeech`, `cs-CZ`).
- `Dice`: seeded RNG wrapper.

### Voice loop

```
mic → SpeechInput (cs-CZ text)
    → Narrator: parse intent → ProposedAction (English IDs, e.g. attack, cast:sleep)
    → Engine: validate + resolve via :rules → Outcome
    → Narrator: describe Outcome in Czech
    → SpeechOutput → car speakers
    → Car screen refresh from GameState
```

Monster turns: Engine enumerates legal actions → `TacticsAdvisor` picks → Engine resolves → Narrator describes.

---

## 3. Game design decisions

### Rules scope (v1)

- AD&D **2nd Edition**, character levels **1–3**.
- Classes: fighter, cleric, mage, thief.
- Races: human, dwarf, elf, halfling.
- Group initiative with d10 (lower acts first).
- THAC0 combat with descending AC: hit if `d20 >= THAC0 - targetAC`.
- Five saving-throw categories (paralyzation/poison/death magic, rod/staff/wand, petrification/polymorph, breath weapon, spell).
- Death at **−10 HP**; unconscious between 0 and −9.
- About 10 low-level spells (e.g. Sleep, Magic Missile, Cure Light Wounds, Bless, Light, Charm Person, Shield, Command, Protection from Evil, Detect Magic).
- Simple non-weapon proficiencies.
- **Excluded:** weapon speed factors, encumbrance tracking, spell components, psionics, grid tactics, levels above 3.

### Players and turns

- 2–4 players, one character each. Pre-generated characters in v1 (no voice character creation).
- The DM calls each player **by name** in initiative order and opens the mic for ~8 seconds; re-prompt on silence.
- A steering-wheel media button press interrupts narration so a player can speak. No wake word.
- The driver may play, voice only.

### Adventure

- **Pre-game phase:** one player dictates a short premise in the car (e.g. "goblins stole the mill's millstone").
- The narrator expands it into a structured outline: 3–5 scenes, locations, NPCs, encounters.
- Every monster in the outline must reference a stat block known to `:rules`; the engine rejects anything else and asks the narrator to fix it.
- The outline is saved with the game. During play the narrator improvises within it, so games resume consistently across drives.
- Content needed in the repo: ~20 low-level 2E monster stat blocks and a set of pre-generated characters. No hand-written adventures.

### Tone

- Generic default: classic heroic fantasy, moderate violence.
- Tone is a single config value injected into the narrator prompt. Audience-specific tuning is **out of scope for v1**.

### Runtime behavior

- **Interruptions:** navigation prompts and phone calls pause the game (audio focus loss). On resume, speak a one-sentence recap.
- **No signal:** narrator and Jev need the network. Pause with a spoken message and retry. Rules and dice keep working offline; no offline story mode in v1.
- **Persistence:** auto-save game state as JSON after every resolved action. Resume across drives.
- **Cost guard:** per-session API spending cap (configurable, default a few USD), with a spoken warning near the limit. Cap narrator response length; short narration is better in a car.
- **Transcripts:** saved locally on the phone for debugging and recaps. Never uploaded except as part of API calls. Auto-delete after 30 days.

---

## 4. Czech localization

- **Speech recognition:** `SpeechRecognizer` with `cs-CZ`. Prefer on-device; on Android 13+ query on-device support at runtime and offer to download the Czech pack. Fall back to Google's online recognition. Bias recognition toward party names and common game verbs.
- **Speech output:** `TextToSpeech` with `cs-CZ`. Check at startup that the Czech voice is installed for offline use; prompt to download if not.
- **Glossary:** one file, `glossary.cs`, used by the narrator prompt, the car screen, and TTS so terminology stays consistent. Starting terms:

  | English | Czech |
  |---|---|
  | HP | životy |
  | AC | třída zbroje |
  | saving throw | záchranný hod |
  | initiative | iniciativa |
  | level | úroveň |
  | experience | zkušenosti |

  THAC0 is never spoken. The narrator says the attack *zasáhl* or *minul*.
- **Grammar:**
  - App-generated strings use Android plural resources (Czech one/few/many/other: "1 život, 3 životy, 7 životů").
  - The car screen uses nominative labels only.
  - Full sentences, including names in the vocative ("Thorine, co uděláš?"), come from the narrator. Never assemble sentences containing names in code.
- **Tests:** verify every player-facing string resource has a Czech version and complete plural forms. The `:cli` harness runs in Czech.

---

## 5. Android Auto

- Built with the **Car App Library** (`androidx.car.app`), templated UI.
- **Party screen:** list of characters with name, HP (current/max), AC, status (e.g. unconscious), and whose turn it is. Refresh from `GameState`, staying within the host's limits on list length and template refreshes while driving.
- Sideloading requires Android Auto developer mode with **Unknown sources** enabled.
- **Known risk:** the Games category is parked-only; the host exits the app when the car moves. M0 must find a declared category under which the app keeps running while driving.

---

## 6. Milestones

Each milestone ends with a short report to the owner: what works, what doesn't, and what changed in this plan.

### M0 — Feasibility spike (do first; can invalidate the design)

Build a minimal car app showing a **fake party HP screen**, plus a test harness for audio. Verify on the Desktop Head Unit and on the real car with the Pixel 7a, over both **wired and wireless** Android Auto:

- [ ] The app keeps running while driving under the declared category. Document which categories were tried and the result.
- [ ] Mic capture works while Android Auto is connected and holds audio focus (car mic via Car App Library, or phone mic).
- [ ] On-device Czech speech recognition is available on the Pixel 7a (or document the fallback).
- [ ] Offline Czech TTS voice is available.
- [ ] Measure round-trip audio latency (speech end → TTS start, with a stub response) for wired vs. wireless.
- [ ] Gradle multi-module skeleton (`:rules`, `:engine`, `:cli`, `:app`), ktlint, detekt, pre-commit configured.

**Stop and report before continuing.**

### M1 — Rules core

- [ ] Characters, races, classes (levels 1–3), ability scores and modifiers.
- [ ] THAC0 combat, damage, HP, death at −10.
- [ ] d10 group initiative.
- [ ] Five saving-throw categories with class/level tables.
- [ ] ~10 spells with memorization and slots.
- [ ] ~20 monster stat blocks.
- [ ] Pre-generated characters.
- [ ] Unit tests with seeded dice for all of the above.

### M2 — Playable in the terminal

- [ ] `:cli` runs a full encounter in Czech: narrator (GPT-6 Sol) parses typed player input and narrates; Jev runs monsters.
- [ ] Adventure pre-game phase: premise → validated outline.
- [ ] Illegal proposed actions are rejected and re-prompted.
- [ ] Save/load and resume mid-adventure.
- [ ] Cost guard and transcripts.

### M3 — Voice loop

- [ ] Speech in/out wired to the engine on the phone.
- [ ] Turn-taking by name, 8-second listen window, re-prompt on silence, media-button interrupt.
- [ ] Audio focus handling: pause on navigation prompts and calls, recap on resume.
- [ ] No-signal pause and retry.

### M4 — Live car screen

- [ ] Party screen bound to real `GameState`.
- [ ] Current turn indicator.
- [ ] Respects driving-mode template limits.

### M5 — Content and polish

- [ ] Full premise-to-finale play session in the car.
- [ ] Glossary review with the owner.
- [ ] Tune narration length and latency.

---

## 7. Out of scope for v1

Grid tactics, psionics, levels above 3, voice character creation, audience-specific tone tuning, offline story mode, text input while driving, Play Store release.
