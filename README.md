# Toolbox (LMToolbox)

[![Tests](https://github.com/LukeMech-PlayStore/toolbox-app/actions/workflows/tests.yml/badge.svg?branch=main)](https://github.com/LukeMech-PlayStore/toolbox-app/actions/workflows/tests.yml?query=branch%3Amain)
[![Release](https://github.com/LukeMech-PlayStore/toolbox-app/actions/workflows/build-app.yml/badge.svg?event=push)](https://github.com/LukeMech-PlayStore/toolbox-app/actions/workflows/build-app.yml)
[![Latest release](https://img.shields.io/github/v/release/LukeMech-PlayStore/toolbox-app?label=release)](https://github.com/LukeMech-PlayStore/toolbox-app/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

[![Min API](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FLukeMech-PlayStore%2Ftoolbox-app%2Fmain%2Fgradle.properties&search=sdkMin%3D(%5Cd%2B)&replace=%241&label=min%20API&color=3DDC84&logo=android&logoColor=white)](gradle.properties)
[![Target API](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2FLukeMech-PlayStore%2Ftoolbox-app%2Fmain%2Fgradle.properties&search=sdkTarget%3D(%5Cd%2B)&replace=%241&label=target%20API&color=3DDC84&logo=android&logoColor=white)](gradle.properties)
[![Kotlin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2FLukeMech-PlayStore%2Ftoolbox-app%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.kotlin&label=Kotlin&color=7F52FF&logo=kotlin&logoColor=white)](gradle/libs.versions.toml)
[![Last commit](https://img.shields.io/github/last-commit/LukeMech-PlayStore/toolbox-app)](https://github.com/LukeMech-PlayStore/toolbox-app/commits/main)
[![Commit activity](https://img.shields.io/github/commit-activity/m/LukeMech-PlayStore/toolbox-app)](https://github.com/LukeMech-PlayStore/toolbox-app/pulse)
[![Stars](https://img.shields.io/github/stars/LukeMech-PlayStore/toolbox-app?style=flat)](https://github.com/LukeMech-PlayStore/toolbox-app/stargazers)
<!-- Once the app is public on Google Play (playbadges answers "App not found" until then):
[![Installs](https://playbadges.pavi2410.com/badge/downloads?id=org.lukemech.lmtoolbox)](https://play.google.com/store/apps/details?id=org.lukemech.lmtoolbox)
[![Rating](https://playbadges.pavi2410.com/badge/ratings?id=org.lukemech.lmtoolbox)](https://play.google.com/store/apps/details?id=org.lukemech.lmtoolbox)
-->

**One app, a drawer full of tools.** Measure with a ruler, a spirit level, a compass, a sound level meter, a
light meter, a metal detector, a vibration meter and a stroboscope; work out a resistor's color code or a 555
timer with 30 electronics calculators; look up pinouts and tables; convert currencies; download, convert and
shrink videos, music and images; edit a photo's metadata; share a folder with any browser; or run your own
Minecraft server, Vanilla to NeoForge, straight from your phone and reachable by your friends through Bore, a
Cloudflare tunnel or Tailscale.

The heavier tools run real Linux programs (Python, FFmpeg, yt-dlp, ExifTool, Java, cloudflared, bore) inside a
PRoot-based Linux subsystem. You never set it up: the app installs it part by part, only when a tool you open
needs it, and removes what no tool uses anymore. No root, no terminal, and in English and Polish.

<a href="https://play.google.com/store/apps/details?id=org.lukemech.lmtoolbox"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80"></a>

The app is open source: read it, learn from it, build it yourself. Buying it on
[Google Play](https://play.google.com/store/apps/details?id=org.lukemech.lmtoolbox) is the easy way to get it,
with automatic updates, and it's what supports its development. Thank you!

## For developers

Written in Kotlin with Jetpack Compose (Material 3). The Linux subsystem is built in its own repository,
[`toolbox-linux-subsystem`](https://github.com/LukeMech-PlayStore/toolbox-linux-subsystem).

### Tools

| Category    | Tool                                                                                           | Runs on                                                                                                                                                                      | Module              |
|-------------|------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------|
| Measure     | Ruler                                                                                          | Android                                                                                                                                                                      | `app`               |
| Measure     | Spirit level (with a widget)                                                                   | Android sensors                                                                                                                                                              | `app`               |
| Measure     | Compass (with a widget, GPS altitude)                                                          | Android sensors                                                                                                                                                              | `app`               |
| Measure     | Sound level meter (dB(A), calibration, Leq)                                                    | Android microphone                                                                                                                                                           | `app`               |
| Measure     | Light meter (lux, min/max/average, hold)                                                       | Android sensors                                                                                                                                                              | `app`               |
| Measure     | Magnetic field and metal detector (µT, x/y/z, tone or vibration)                               | Android sensors                                                                                                                                                              | `app`               |
| Measure     | Vibration meter (RMS, peak, main frequency by FFT)                                             | Android sensors                                                                                                                                                              | `app`               |
| Measure     | Stroboscope / tachometer (Hz or RPM, tap tempo)                                                | Android torch                                                                                                                                                                | `app`               |
| Electronics | 30 calculators (listed below)                                                                  | Android                                                                                                                                                                      | `app`               |
| Reference   | 29 references: tables, pinouts drawn from data, decoders (listed below)                        | Android                                                                                                                                                                      | `app`               |
| Finance     | Currency converter (ECB rates, amounts shared from other apps)                                 | Python (`CurrencyConverter`)                                                                                                                                                 | `features:pypkgs`   |
| Media       | QR generator                                                                                   | Python (`qrcode`, Pillow)                                                                                                                                                    | `features:pypkgs`   |
| Media       | Metadata editor                                                                                | ExifTool                                                                                                                                                                     | `features:exiftool` |
| Media       | Media downloader                                                                               | yt-dlp (with FFmpeg, QuickJS)                                                                                                                                                | `features:ytdlp`    |
| Media       | Media encoder (video, GIF, sound; cut, resize, a target file size)                             | FFmpeg                                                                                                                                                                       | `features:ffmpeg`   |
| Media       | Image encoder (resizing, a target file size)                                                   | Android (`ImageDecoder`, `Bitmap`)                                                                                                                                           | `app`               |
| Media       | File size (a shortcut to the media or the image encoder)                                       | Android                                                                                                                                                                      | `app`               |
| Media       | File hash (MD5, SHA-1, SHA-2, SHA-3, CRC32; checked against a hash or a checksum file)         | Android (`MessageDigest`, `CRC32`); SHA-3 the app's own (Android has none)                                                                                                   | `app`               |
| Servers     | Minecraft server (Vanilla, Paper, Fabric, Forge, NeoForge; Bore, Cloudflare tunnel, Tailscale) | The core; the Java its Minecraft is made for (`features:jdk8`, `jdk17`, `jdk21`, `jdk25`); bore or cloudflared once a server's tunnel is on (`features:bore`, `cloudflared`) | `app`               |
| Servers     | Folder share (a folder in any browser: ranges, ZIPs, uploads, a password; Bore, Cloudflare)    | Android (a web server of its own); bore or cloudflared once a tunnel is on (`features:bore`, `cloudflared`)                                                                  | `app`               |

The electronics calculators: color code, SMD resistor code, Ohm's law, electric power, reactance and
resonance, RC, RL and RLC filters, voltage divider, resistor ratio, series and parallel, capacitor charging,
op-amp amplifier, transistor (BJT switch and bias), LED resistor, Zener regulator, LM317 regulator, 555 timer,
heat sink, battery life, energy converter, air-core coil, cable voltage drop, PCB trace width, star-delta,
PT100 sensor, decibels, frequency and wavelength, ADC converter, range converter, number bases and bits,
RMS and peak.

The references: standard values (E-series), PT100 table, decibel table, SI prefixes, wire gauges (AWG, SWG),
capacitor codes, IP code, SMD package sizes; the pinouts of the Raspberry Pi's GPIO, Arduino Uno and Nano,
USB (A, B, mini-B, micro-B, 2.0 and 3.x, and C), Ethernet RJ45 (with PoE), RS-232 (DB9, DB25), ARM JTAG and
SWD, AVR ISP and PIC ICSP, display connectors (HDMI full, mini and micro, DisplayPort and Mini DisplayPort, DVI,
VGA), OBD-II, PC power connectors (ATX, EPS, ATX12V, PCIe, 12V-2x6, SATA, Molex, floppy), SD cards, XLR and
DMX, audio jacks (TS, TRS, balanced TRS, TRRS), ISO 10487 car radio and trailer sockets; and tables of ASCII,
logic gates, resistivity, thermocouple types, car fuses and battery sizes.

### Project layout

```
toolbox-app/
├── app/                              The app: home, settings, navigation, the native tools, the subsystem's runtime
│   └── src/
│       ├── main/java/org/lukemech/lmtoolbox/
│       │   ├── MainActivity.kt           Entry point; "Share to" / "Open in" intents → a tool's page
│       │   ├── features/<category>/<route>/   Each tool's definition and its logic (maths, jobs, parsing)
│       │   │   └── electronics/common/   Units, SI prefixes, E-series: what the calculators share
│       │   ├── helpers/                  The tool registry, Play delivery, preferences, files, resource monitor
│       │   ├── linux/                    The Linux subsystem: its parts, running commands, tasks (see below)
│       │   │   └── parts/                APIs of parts several tools pick among: Java, Tunnel (cloudflared), Bore
│       │   └── ui/
│       │       ├── components/           Shared building blocks (headings, setting rows, tiles, results, step bar…)
│       │       ├── helpers/              Navigation, the bottom bar, the install prompt, the subsystem's setup
│       │       ├── pages/                Home (Features.kt), settings, and each tool's pages under features/
│       │       │   └── features/electronics/common/   The calculators' page, fields, linked fields, formula
│       │       └── widgets/              Home screen widgets (spirit level, compass)
│       ├── main/res/                     The app's strings and those several tools share
│       ├── main/res-features/<category>/<route>/   Each tool's own resources (strings, drawables, widget layouts)
│       └── androidTest/                  End-to-end tests on a device: each tool's test (OhmTest...) in its
│                                         package, the pages' in theirs, the harness in e2e/
├── features/<part>/                  On-demand Play modules, one per part of the Linux subsystem
│   └── src/main/                         The part's archive (jniLibs, downloaded at build time) and, for a
│                                         tool alone on that part, its pages, strings and scripts (assets/)
├── buildSrc/                         The build's own code: the Linux packages' tasks, Play local testing
├── gradle/libs.versions.toml         Dependency versions
├── gradle.properties                 App version, SDK levels, JDK and bytecode levels, the subsystem's release
└── whatsNew/                         Play Store release notes (en-US, pl-PL)
```

### How a tool is made

Every tool is an `AppFeature`, listed in `FeatureRegistry.features`
([`helpers/FeatureRegistry.kt`](app/src/main/java/org/lukemech/lmtoolbox/helpers/FeatureRegistry.kt)): add a
new one there. Its pages are opened by its `route` (`feature/<route>`, its settings at
`settings/features/<route>`, see `FeatureRegistry.byRoute`), so the list's order is only the order shown. A test
checks that the routes are unique.

- Every tool has a title, search keywords and a short catchy line under its name on the home page
  (`descriptionResId`, searched too). It may list the tiles its work's load shows in (`resourceTiles`, see
  [UI conventions](#ui-conventions)).
- A **native tool** (e.g. `RulerFeature`, `ImageResizerFeature`) is an `object : AppFeature` in
  `features/<category>/<route>/<Name>Fun.kt`, with its logic next to it (e.g. `OhmCalc.kt`, tested). Its pages
  are in `ui/pages/features/<category>/<route>/`, and its strings in
  `res-features/<category>/<route>/values{,-pl-rpl}/strings.xml`.
- A **tool on the Linux subsystem** is a `ModuleFeature` (`features/<category>/<route>/<Name>Feature.kt`),
  holding only what the app needs before its module is installed: title, icon, keywords, preference keys.
  It lists the parts it runs on (`linuxExtensions`, each with what it builds on). Its pages are a `FeatureUi`
  object in the part's module (`uiClass`, loaded by name through `FeatureDelivery`). Until its parts are
  there, `ExtensionGate` shows what they take and installs them.
- A tool on several parts (the Minecraft server) is the app's: there's no module of code alone. The parts it
  picks among or turns on (`extraExtensions`: a Java version, cloudflared, bore) have their API in the app
  (`linux/parts/`: `Java`, `Tunnel`, `Bore`), and their modules are just their archives. So a tool gets one only once
  it needs it, and no part's module depends on another's.
- A module lays its tools out as the app does: `features/<category>/<route>/` for what isn't UI (its
  `FeatureUi`, jobs, maths and parsing; its end-to-end test in the app's `src/androidTest/`, same package),
  `ui/pages/features/<category>/<route>/` for its pages, and `assets/features/<route>/` for its scripts
  (installed into the subsystem by `LinuxScripts` each time they run, as the app ships them; folders no tool
  has anymore go with the subsystem's update).
- What a tool makes is saved with [`helpers/SavedFiles.kt`](app/src/main/java/org/lukemech/lmtoolbox/helpers/SavedFiles.kt):
  into the folder picked in its settings (`OutputFolder`), else into MediaStore (`saveToMediaStore`). Its page
  then opens or shares it (`openFile`, `shareFile`, which leaves the tool's own "Share to" out).
- "Share to" and "Open in" entries are `<activity-alias>`es in the manifest, listed in `SystemEntries` (a
  test checks the two match). Each is switched on or off in the system section of the tool's settings
  (`AppEntrySetting`), on only while the tool is installed.
- A tool's preferences are listed in its `preferenceKeys`, and its files in `dataFiles`, so that "Delete
  data" in its settings removes everything it keeps.

#### The electronics calculators

They share one page (`CalculatorPage`), with fields that take values with SI prefixes (`UnitField`,
`parseValue`, `formatValue`). Where any of several values follows from the others (Ohm's law's voltage,
current, resistance and power), the fields are linked (`rememberLinkedFields`): the last ones typed are the
inputs, the rest are computed and marked as such, and tapping a computed one makes it an input. Each
calculator's formula is shown from the button in its corner (`Formula`), and a resistor value can be
rounded to the nearest value of an E-series (`ESeries`, `ESeriesSetting`). The maths that's more than a
line lives in the tool's `<Name>Calc.kt`, with tests.

#### The references

Each reference is a `ReferenceFeature` (`features/reference/<route>/<Name>Fun.kt`) holding only data, its
`sheets`, which a single page shows (`ReferencePage`, picked by chips when there are several): a `TableSheet`,
searched as it's typed, its headings staying put and scrolling sideways with its rows, a cell held down
copied; a `PinoutSheet`, the connector drawn on a canvas from its `Pin`s (placed by `inRow`, `inRows`,
`dualRow`, `staggered` or by hand, colored by `PinKind` or by their wires), a pin tapped there or in the list
marked in both; or a `ToolSheet` for a decoder. Texts are `Txt`: as written, or a string resource. What can
be worked out is (the E-series, the PT100 and AWG tables, the truth tables); the rest is rebuilt from the
standards named in each one's notes.

### The Linux subsystem

The subsystem is a Termux-based rootfs, split into parts (`core`, `python`, `pypkgs`, `ffmpeg`, `exiftool`,
`ytdlp`, `jdk8`, `jdk17`, `jdk21`, `jdk25`, `cloudflared`, `bore`), plus PRoot. Each part is an on-demand Play module under
`features/`, installed with the first tool that needs it (the core, with PRoot, comes with the first one). A
part may not be built for every ABI (`jdk25`: no OpenJDK 25 for x86); a tool none of whose parts are there
says it isn't available. Its commands run as argv lists through PRoot, with the Termux prefix bound to
`/data/data/com.termux/files/usr`.

The code is in [`app/…/linux/`](app/src/main/java/org/lukemech/lmtoolbox/linux):

| File                | What it does                                                                                            |
|---------------------|---------------------------------------------------------------------------------------------------------|
| `LinuxParts.kt`     | What the app ships of the parts (this ABI's, from `libparts.json.so`) and what's downloaded or unpacked |
| `LinuxInstaller.kt` | Unpacks a part from its module's archive, updates the outdated ones, deletes them                       |
| `PartUsers.kt`      | Which tool uses which part (or was unlocked by another's installation): a part goes once none uses it   |
| `LinuxEnv.kt`       | Runs a command through PRoot (`exec`), with the environment Termux's programs expect                    |
| `LinuxTasks.kt`     | Every command, installation and update as a task: the bottom bar, the notification, cancelling          |
| `LinuxScripts.kt`   | Installs the tools' scripts (their modules' assets) into the subsystem                                  |
| `InstallPrompt.kt`  | Asks before installing what a tool needs now                                                            |
| `parts/`            | `Java`, `Tunnel` and `Bore`: the APIs of the parts a tool picks among or turns on                       |

From UI code, use `LinuxTasks.run` or `runInBackground` (and `runService` for what runs until it's stopped,
e.g. a server). A task carries the tool that started it (`LinuxTask.source`), which titles it and opens its
page from the bottom bar. Each command gets its task's id in its environment (`LMTOOLBOX_TASK`); that's how
what it takes is measured (`ResourceScope.Task`).

A tool can install parts itself when it needs them (`LinuxTasks.installParts`, e.g. the server's Java
version) and they're uninstalled once nothing uses them. A tool another's installation brought all its parts
for (the media encoder with the downloader's FFmpeg) is unlocked: there to use, but it keeps no part.

#### The packages in the build

The parts aren't stored in this repository. `gradle.properties` pins the subsystem's release the app ships
(`linuxRelease`), and `:app:updateLinuxPackages` (it runs before every build) downloads it and puts each
archive into its module's `jniLibs`. It only downloads when what's there is of another release, so it works
offline after that, and builds never ask GitHub's API. `./gradlew updateLinuxRelease` does: it pins the newest
published release (set `GITHUB_TOKEN` to avoid its rate limit). Commit that line with what uses the new release.

To add a program, define it in the subsystem's `config.yaml` (a Termux or pip package in a part), let its
workflow release it, pin that release, and use it from the module of the part it's in.

### UI conventions

- **Errors are told in one place**: the red line of the bottom bar (`TaskStatusBar`). Tapping it opens the
  tool that failed; the log is in the task list. A failed command shows there on its own. A failure outside a
  command (a download of the tool's own, a file it couldn't save, a server that crashed) goes through
  `LinuxTasks.reportFailure`. Pages never show an error themselves. Error messages are short English text in
  code, not string resources, to keep the app small. Cancelling isn't an error.
- **Progress is the steps' bar**, at the bottom of the app over its other bars. A page's `Steps` (given to its
  `PageHeading`) are shown by `StepBarSlot`: what the work is at, how far (a percentage) and a short detail
  over the bars, and a button cancelling it.
- **What a job takes** shows in tiles (`ResourceTiles`, read by `ResourceMonitor` from `/proc` and
  `TrafficStats`): CPU, memory, storage and network, as the tool lists them (`resourceTiles`), of its task's
  processes or of the whole app. They, a finished job's actions and an installation's button sit at the bottom
  of the page, where its buttons go.
- **Installing is asked first**: `LinuxTasks.installParts` shows what's to get and what it takes
  (`InstallPrompt`, shown by `InstallPromptHost`) and returns `InstallResult` (`Installed`, `Declined`,
  `Failed`). Declining isn't an error. A page that tells it all itself (full screen) passes `confirm = false`;
  `ExtensionGate` is the installation page.
- **Everything that appears or disappears animates**, through `Reveal` (and `revealEnter`, `revealExit`): one
  duration and curve across the app. Nothing pops in or out.
- **A number field holds a number**: no switch (a unit, a mode, a component) may leave letters in it. A value
  with a prefix the new unit doesn't take is turned into a plain number ("4.7 k" → 4700).
- A setting that can't be changed now is grayed out (`OptionSetting`'s `enabled`); one whose choices are loading
  shows a spinner instead of its arrow (`loading`). What's typed into a calculator is kept when the screen turns.
- **The shared pieces are used, not rebuilt**: `InputField` for every text field (the app's corners, Done closing
  the keyboard), `RowColors` for a row's colors (`default`, `danger`, `alert`, `changed`, `highlighted`,
  `tinted`), `PermissionDialog` for every permission asked, `PromptBar` for a bar asking for something on top of
  a page (`SensorWarning` for the measuring tools' sensors), `ConfirmDialog` before what can't be undone.
- **A page is a plain function** named `<Name>Page`, in its tool's own package (so its helpers' names never
  clash with another tool's). A settings sub-page is a `…Page` too; a part of the main settings page is a
  `…Section`.

### Building

Requirements:
- Android Studio (its bundled JDK runs Gradle; Gradle provisions JDK 25 for the code through the foojay
  resolver)
- Android SDK with platform 37 (`local.properties`: `sdk.dir`)
- Network access on the first build (the subsystem's release)

The Android Gradle Plugin stays at 9.1.1: it's the newest Android Studio's plugin syncs. Its warnings about a
newer version, and about deprecated Gradle features it uses itself, are expected.

```sh
./gradlew assembleDebug          # debug APKs (applicationId org.lukemech.lmtoolbox.dbg)
./gradlew bundleRelease          # release bundle (needs the signing config, see the release workflow)
./gradlew updateLinuxRelease     # pin the subsystem's newest release
```

The build files share what they can: the root `build.gradle.kts` configures every Play module and the Kotlin
JDK and bytecode level of all of them, and `buildSrc/` holds the app's tasks (`LinuxPackages.kt`,
`LocalTesting.kt`).

#### Running on a device

- **app**: Android Studio's plain run configuration installs the app *with every module*, so there's nothing
  to download.
- **app (Play local testing)**: runs `:app:installLocalTesting`. It builds the debug bundle and installs it with
  bundletool's local testing, so the modules are "downloaded" the way Play would deliver them. The core module
  is installed for real, because PRoot has to be executed from `nativeLibraryDir`. Pick a device with
  `-Pdevice=<serial>` (or `ANDROID_SERIAL`) when several are connected.

```sh
./gradlew :app:installLocalTesting -Pdevice=<serial>
```

### Testing

The tests run the app on emulators as a user would: [`tests.yml`](.github/workflows/tests.yml) installs
the debug bundle with Play's local testing (so the tools' modules download as from Play) and runs
`app/src/androidTest/` on every pull request, every push to main and by hand. Every run tests everything.

**What's tested.** Each tool has a test named after it in its own package
(`features/electronics/ohm/OhmTest.kt`), which opens it from the home page and goes through every button, switch,
field and slider (`e2e/Explorer.kt`), installing the tool's Linux packages first if it's on the subsystem. The
Minecraft server's test makes every type of server (Vanilla, Paper, Fabric, Forge, NeoForge) on every Java, each on
the newest Minecraft made for it: starts it, uses its console and options, changes its loader, exports its world.
The pages' own tests (home, search, navigation, settings, tasks) sit next to them.

**Where.** The legs come from what the app supports and what Google publishes, worked out on every run
([`matrix.py`](.github/scripts/tests/e2e/matrix.py)): for x86_64, the oldest Android the app supports (`sdkMin`,
API 29), the one it targets (`sdkTarget`, 37) and the newest canary (16 KB pages); for x86, whose images end at
API 30, the oldest and its newest. No ARM: GitHub's runners can't run an ARM emulator, and ARM translated on the
x86_64 images can't run the Linux subsystem.

**How.** Each leg runs in 3 shards. Each test goes to the shard with the least work so far, longest first, by how long
it took before (`durations.json`, kept in the Actions cache: the report job saves each run's, and the next run's plan job restores
the newest its branch can see, its own or main's, for all its shards alike; [`cron.yml`](.github/workflows/cron.yml)
restores main's every 3 days, before GitHub drops it unused), so the shards finish at about the same time. A new test counts as
the median until it's measured. With no cache yet, the tests are dealt by the listing alone. On a shard,
[`instrument.py`](.github/scripts/tests/e2e/instrument.py) runs the tests class by class, each in a fresh
process. After a crash, the tests it cut off are run one by one. A failed test runs once more; passing then
counts as flaky, not failed.

**Results.** The `report` job ([`merge.py`](.github/scripts/tests/e2e/merge.py)) puts every shard's results in one
table in the run's summary: each test on each leg, failed first, then why each failed. Failures are annotations on
the pull request. Screenshots, logcat and the raw output are each shard's `e2e-results-*` artifact. The job fails
if a test failed or didn't run (a shard that crashed or was canceled).

**Inspections.** Alongside them, the `inspect` job inspects the code as Android Studio's Inspect Code does, with the
project's profile (`.idea/inspectionProfiles`), in Qodana Community for Android's newest image, with the Jetpack
Compose and Android Design Tools plugins added. It reports as soon as it's done, while the emulators still run
([`report.py`](.github/scripts/tests/inspections/report.py)): every problem in the run's summary. The ones that fail
it (critical and high) also go to code scanning, so they show on their lines in the pull request's changes and in the
Security tab; typos and the like stay in the summary. Qodana's HTML report is the `inspections` artifact. Critical and high problems (the IDE's errors and warnings,
an outdated library among them) fail it. Moderate, low and info problems (weak warnings, typos, grammar) are only
reported.

**On a pull request.** The `comment` job ([`comment.py`](.github/scripts/tests/comment.py)) puts both reports in one
comment: a line for each on top, the whole summaries folded under it. Every run updates the same comment, found by
its hidden marker, instead of adding a new one. It's skipped for pull requests from forks and Dependabot, whose token
can't comment.

```sh
./gradlew :app:installLocalTesting :app:installDebugAndroidTest   # on a connected device
adb shell am instrument -w -e class org.lukemech.lmtoolbox.features.electronics.ohm.OhmTest   org.lukemech.lmtoolbox.dbg.test/androidx.test.runner.AndroidJUnitRunner
```

### Versioning and releases

A release's version comes from its tag (`v<gplayCode>_<versionName>`), which `build-app.yml` passes to Gradle
(`-PgplayCode=… -PversionName=…`). The tests' build is named after its commit's short hash, version code 1. Any
other build is `<YY.MM.DD>-b<n>`, the day's n-th build on that machine, version code 1
([`AppVersion.kt`](buildSrc/src/main/kotlin/AppVersion.kt), its count in `.gradle/dailyBuild.properties`). The
[`build-app.yml`](.github/workflows/build-app.yml) workflow builds the debug and the
signed release artifacts, and can push them to Play tracks as drafts. A version's tag
(`v<gplayCode>_<versionName>`, e.g. `v76_26.10.24`) runs it on its own: the release goes to internal and alpha, internal's published.
[`publish-draft.yml`](.github/workflows/publish-draft.yml) publishes the drafts of the tracks picked (by hand,
or as the build's last job). The release notes come from `whatsNew/`. A tag also makes a GitHub release,
titled by the tag: the English release notes (`whatsnew-en-US`), then the commits since the previous tag.
[`cron.yml`](.github/workflows/cron.yml)'s `daemon-jvm` job opens a pull request each week when the
Gradle daemon's JDK has newer builds. [Dependabot](.github/dependabot.yml) keeps the actions' pins and the Gradle
wrapper current. The libraries and plugins (`gradle/libs.versions.toml`) are left to the inspections, which fail
on an outdated one.

### Conventions

- Comments say *why* and *what for*, in plain words. Match the surrounding style.
- Every user-facing string exists in both `values` and `values-pl-rpl` (error messages aside, see
  [UI conventions](#ui-conventions)), and none is left unused.
- Logic that isn't UI (maths, parsing, commands) sits apart from the pages, in the tool's
  `features/<category>/<route>/`. Each tool has an end-to-end test named after it (see [Testing](#testing)).

## License

Toolbox is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License v3.0](LICENSE) as published by the Free Software Foundation. It comes with no
warranty. A modified version you distribute has to be under the same license, with its source.

The license covers the code, not the brand: the names "Toolbox" and "LMToolbox", the app's icon and its Google
Play listing are LukeMech's and aren't licensed under the GPL. A fork you publish must use its own name, icon
and application ID. See [TRADEMARKS.md](TRADEMARKS.md).

The Linux subsystem's programs (PRoot, Termux packages, OpenJDK and the rest) are their authors', under their
own licenses.
