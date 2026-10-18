# Status report

What works, what doesn't, and what changed against [PLAN.md](../PLAN.md). Everything below was built and
unit-tested on a development machine; nothing has run yet on the Desktop Head Unit, the Pixel 7a or the car.
Those checks are listed under **To verify in the car**.

## Done

### M0 – skeleton and feasibility scaffolding
- Gradle multi-module project `:rules`, `:engine`, `:cli`, `:app`; ktlint, detekt and pre-commit configured.
- Minimal car app with a live party screen (better than the planned fake one: it shows the real game state).
- Declared Android Auto category: **IOT** (`androidx.car.app.category.IOT`). GAMES is parked-only; IOT apps
  use the same templates and are allowed while driving. Fallback candidates if IOT gets the app closed
  while driving: POI, then NAVIGATION (heavier requirements).
- Audio test harness in the phone app: microphone permission, Czech TTS voice check and install,
  on-device Czech recognition check with model download (Android 13+), and a latency probe
  (end of speech → TTS start with a stub answer).
- CI on GitHub Actions: lint, tests, release APK, and a GitHub release per `master` build.

### M1 – rules core (`:rules`, 42 unit tests with seeded dice)
- Abilities and modifiers (PHB tables 1–5), races (human, dwarf, elf, halfling), classes (fighter, cleric,
  mage, thief), levels 1–3 with XP tables and level-up hit points.
- THAC0 combat with descending AC (`d20 + bonus >= THAC0 − AC`, natural 20/1), strength/dexterity
  modifiers, weapon damage by target size, unconscious at 0, death at −10 for characters.
- d10 group initiative (lower first), five saving-throw categories with class/level tables and racial bonuses.
- Ten 1st-level spells with memorization and slots (including wisdom bonus spells for priests):
  Sleep, Magic Missile, Shield, Charm Person, Light, Detect Magic, Protection from Evil,
  Cure Light Wounds, Bless, Command.
- 21 monster stat blocks; poison and ghoul paralysis.
- Six pre-generated characters (Thorin, Lianna, Bedřich, Pip, Kira, Míra).
- Simple non-weapon proficiency checks.

### M2 – playable in the terminal (`:engine`, `:cli`, 30 unit tests)
- Narrator interface with an OpenAI implementation (`gpt-6-sol` by default, any OpenAI-compatible Chat
  Completions endpoint) and an offline implementation.
- Pre-game phase: premise → outline (3–5 scenes) → validation against the bestiary → feedback and up to
  three attempts → offline outline as a last resort.
- Engine validates every proposed action, rejects illegal ones with a reason code (Czech message from the
  glossary) and re-prompts.
- Monster turns: the engine lists legal actions, Jev picks one; low confidence, a bad answer or an
  unreachable endpoint falls back to `HeuristicTactics`.
- Save after every resolved action, resume mid-turn; seeded RNG state is part of the save.
- Cost guard with spoken warning at 80 % of the cap; transcripts with 30-day retention.
- One Czech glossary (`engine/src/main/resources/glossary.cs`) used by prompts, screens and offline narration;
  tests check that every id has a Czech entry and every countable entry has all three plural forms.

### M3 – voice loop (`:app`)
- `AndroidSpeechInput` (cs-CZ, on-device first, online fallback, biasing toward names and game verbs),
  `AndroidTts` (cs-CZ, offline voice preferred).
- Turn-taking by name, ~8 s window to start speaking, re-prompt on silence, turn skipped after repeated silence.
- Media button barge-in via a media session.
- Audio focus: navigation prompts and calls pause the game (ducking counts as a pause); recap on resume.
- No signal: spoken notice, retries with backoff, then offline narration for that step.
- Optional car microphone (Car App API level 5, Android 13+) piped into the recognizer. Off by default.

### M4 – live car screen
- Party list bound to the real game state: name, HP, AC, status, turn marker, plus one info row
  (enemies / exploration / paused). Constant title and row count during a game so updates are refreshes;
  updates throttled to 1 per second.

## To verify in the car (M0 checklist, still open)
- [ ] IOT category keeps the app running while driving (wired and wireless).
- [ ] Phone microphone works while Android Auto holds audio; whether the car microphone option works.
- [ ] On-device Czech recognition on the Pixel 7a (the app reports it under *Zkontrolovat…*).
- [ ] Offline Czech TTS voice installed.
- [ ] Latency numbers for wired vs. wireless (use *Změřit odezvu*).
- [ ] Steering-wheel media buttons actually reach the game's media session.
- [ ] The foreground service starts from the car session on Android 14+ (otherwise the game still runs
      while the car screen is shown, but may stop when the phone sleeps).

## Not done yet
- M5: a full premise-to-finale session in the car, glossary review with the owner, tuning of narration
  length and latency.
- A Claude narrator backend (the `Narrator` interface is ready for it).
- Vosk speech input.

## Changes and assumptions against the plan
- **Offline narrator.** The plan has no offline story mode. A simple offline narrator exists anyway: it is
  used without an API key, after the spending cap is reached, and when the network stays down after
  retries, so a drive never dead-ends. It keeps names in the nominative and avoids gendered past tense.
- **Jev API.** The real TypeSafe AI API was not available, so the client assumes a JSON contract:
  POST `{task, monster, party, allies, options}` → `{"choice": <index>, "confidence": <0..1>}` with a
  bearer key. Adjust `JevTacticsAdvisor` once the actual API is known.
- **GPT-6 Sol.** Model id `gpt-6-sol` and token prices (2.50 / 10 USD per million) are defaults, both
  configurable (prices in `GameConfig`, model in the phone settings).
- **Rules simplifications:** initiative ties are rerolled; unconscious characters do not bleed; only
  1st-level spell slots exist (no 2nd-level spells in v1); a rest (once per scene) restores spells and
  1 HP per level; fleeing succeeds on 1–5 on d10 for the whole party; morale is checked once per
  encounter when half the monsters are beaten; XP is awarded for defeated (not fled) monsters.
- **API keys** are entered in the phone app (the repository is public, so CI builds contain no keys);
  `local.properties` still provides defaults for personal builds.
- **Host validation** accepts any host, which is fine for a personal sideloaded app.
- **Signing:** a sideload keystore is committed so CI builds upgrade each other. It is not a secret.
