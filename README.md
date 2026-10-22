# Auta a draci (Cars & Dragons)

Voice-driven **AD&D 2nd Edition** for the car. An **Android Auto** app is the Dungeon Master: it narrates
in Czech, listens to what the players say, resolves everything with deterministic rules and shows the
party on the car screen. See [PLAN.md](PLAN.md) for the design and [docs/STATUS.md](docs/STATUS.md) for
what is done and what still needs testing in a real car.

## Install on the phone (Pixel 7a)

1. On the phone, open the [latest release](https://github.com/crabhi/cars-and-dragons/releases/latest)
   and download `cars-and-dragons-*-release.apk`. Open it and allow installing apps from the browser.
2. Open **Auta a draci** on the phone:
   - tap *Povolit mikrofon* (microphone and notifications),
   - enter your **OpenAI API key** (and optionally a model, default `gpt-6-sol`, and a Jev endpoint/key),
     then *Uložit nastavení*,
   - tap *Zkontrolovat český hlas a rozpoznávání*. If the Czech voice is missing, tap
     *Nainstalovat český hlas*. On Android 13+ the Czech on-device recognition model download starts
     automatically when it is supported but missing.
3. Android Auto: Settings → Android Auto → tap *Version* 10× to enable developer mode → ⋮ →
   *Developer settings* → enable **Unknown sources**. Reconnect the phone to the car.
4. In the car, open *Auta a draci* from the Android Auto launcher. The game starts by voice; nothing
   needs to be touched afterwards.

Without an API key the game still works with a simple offline narrator (keyword parsing and template
narration), which is handy for testing.

Each CI build on `master` publishes a release signed with the same committed sideload key, so newer
builds install over older ones. To use your own key, set `SIGNING_KEYSTORE_PATH`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` for the build (switching keys
requires uninstalling once).

## Playing

The DM asks how many players there are (1–4), introduces the pre-generated party, asks one player for a
short premise ("skřeti ukradli mlýnský kámen") and builds a 3–5 scene adventure from it. Then it calls
each character by name; the player has about 8 seconds to start speaking.

Things you can say (examples): *útočím na druhého goblina*, *střílím z luku*, *sesílám spánek*,
*vyléčím Thorina*, *vypiju lektvar*, *bráním se*, *utečeme*, *prohledám místnost*, *plížím se*,
*odpočineme si*, *jdeme dál*. Out of game: *jak jsme na tom* (status), *zopakuj*, *konec hry*
(save and quit).

- A steering-wheel media button interrupts the narration (or resumes a paused game).
- Navigation prompts and phone calls pause the game; it continues with a one-sentence recap.
- The game is saved after every action and resumes on the next drive.
- Transcripts are kept on the phone for 30 days and never uploaded.

## Run in the emulator

The car side is the Android Auto **Desktop Head Unit (DHU)**, which connects to a phone. The phone can
be an emulator (below) or a real phone over USB. The commands use Google's
[Android CLI](https://developer.android.com/tools/agents/android-cli) (`android`); the emulator needs
hardware virtualization (KVM on Linux).

```bash
# 1. Install the Android CLI (macOS: brew tap android/tap && brew install android-cli).
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
android info      # shows which SDK it uses; point sdk.dir in local.properties at the same SDK
                  # (or pass --sdk=<path> to every android command)

# 2. Emulator, platform tools and the Android Auto Desktop Head Unit.
android sdk install emulator platform-tools extras/google/auto

# 3. A phone emulator. The medium_phone profile uses a Google Play image, which Android Auto needs.
android emulator create medium_phone
android emulator start medium_phone

# 4. Build, install and open the app. -g grants the microphone permission up front.
./gradlew :app:assembleDebug
android run --install-options=-g --apks=$(ls app/build/outputs/apk/debug/*.apk)
```

5. In the emulator, sign in to the Play Store and install or update **Android Auto**. Then open
   Settings → Connected devices → Connection preferences → Android Auto, tap *Version* about 10× to
   enable developer mode, and in the ⋮ menu:
   - *Developer settings* → enable **Unknown sources**,
   - **Start head unit server**.
6. Connect the head unit:

   ```bash
   SDK=<the SDK path from android info>
   $SDK/platform-tools/adb forward tcp:5277 tcp:5277
   $SDK/extras/google/auto/desktop-head-unit
   ```

7. Open **Auta a draci** in the DHU launcher. The game starts by voice and the party screen shows on
   the DHU. Stop the emulator with `android emulator stop medium_phone`.

Audio in the emulator:
- Microphone: in the emulator window open ⋯ *Extended controls* → *Microphone* and enable
  *Virtual microphone uses host audio input*; otherwise the game hears silence and skips turns.
- Czech voice: Settings → System → Languages → Text-to-speech → Google → install voice data →
  *Čeština*.
- On-device Czech recognition is likely missing in the emulator; the app falls back to Google's online
  recognition (needs the Google app and network). *Zkontrolovat český hlas a rozpoznávání* in the app
  shows what is available.

Without the DHU, *Spustit / pokračovat ve hře na telefonu* in the app plays the whole voice game on the
emulator's phone screen.

## Development

| Module | What |
|---|---|
| `:rules` | Pure Kotlin 2E rules: dice, abilities, classes, THAC0 combat, saves, 10 spells, 21 monsters, pregens, XP. |
| `:engine` | Game state, turn loop, adventure outline validation, narrators (OpenAI + offline), Jev tactics, save/load, transcripts, Czech glossary. |
| `:cli` | Terminal harness playing the same game in Czech. |
| `:app` | Android Auto (Car App Library) screens, speech in/out, audio focus, media buttons. |

The build runs on **Java 27** (Gradle 9.8, Kotlin 2.4, Android Gradle Plugin 9.4). Any installed JDK
can launch `./gradlew`; Gradle then runs the build on JDK 27 (`gradle/gradle-daemon-jvm.properties`),
using an installed one or downloading it into `~/.gradle/jdks` on first use. All modules, including the
Android app, compile to Java 26 bytecode, the newest target Kotlin 2.4 can emit.

```bash
./gradlew test                    # all unit tests
./gradlew ktlintCheck detekt      # style and static analysis
./gradlew :app:assembleRelease    # APK in app/build/outputs/apk/release/
./gradlew :cli:installDist && cli/build/install/cli/bin/cli --new   # play in the terminal
```

The CLI reads `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_BASE_URL`, `JEV_ENDPOINT` and `JEV_API_KEY`
from the environment or `local.properties` (`openai.apiKey`, …). `--offline` forces the offline
narrator, `--seed N` makes a game reproducible, `--script FILE` feeds player lines from a file.
The installed CLI needs Java 26 or newer at runtime (`JAVA_HOME` pointing to JDK 27 works).

Secrets go to `local.properties` (git-ignored); the published APK contains no keys.

Pre-commit runs ktlint and detekt: `pip install pre-commit && pre-commit install`.
