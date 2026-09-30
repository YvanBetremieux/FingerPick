# FingerPick — Design Spec

Date: 2026-09-30
Status: Approved (conversation), pending written-spec review

## Purpose

A personal Android app for picking people at random at a table. Everyone puts a
finger on the phone screen; after a short countdown the app keeps N fingers lit
as the "chosen" ones. It must feel fluid and look striking.

Audience: the owner and friends, sideloaded via APK. No Play Store.

## Scope

In scope:
- Android only (no iOS for now).
- Setup screen: number of players and number of winners.
- Multi-touch play screen with glowing lights under each finger.
- Automatic 3-2-1 countdown once the expected number of fingers is down.
- Random selection of winners; winners stay lit.
- "Replay" with the same settings; "Settings" to go back.
- CI that builds a signed APK and publishes it to a GitHub Release.

Out of scope (YAGNI): accounts, sound, history/stats, team split modes,
localization beyond French UI strings, tablets/landscape, Play Store.

## Game rules

- Players `P`: integer in `[2, 12]`. Default 4.
- Winners `W`: integer in `[1, P - 1]`. Default 1. Lowering `P` clamps `W`.
- Last used `P` and `W` are persisted (SharedPreferences) and restored on launch.

Play screen states:

1. **Waiting** — Fingers can be added and removed freely. Each tracked finger
   gets a light. A counter shows `n / P`.
   - Only the first `P` simultaneous fingers are tracked. Additional fingers
     are ignored (no light, no effect) for as long as they stay down, even if a
     tracked finger lifts later.
   - When the tracked count reaches `P`, transition to **Countdown**.
2. **Countdown** — Shows 3, 2, 1 (one second each), with a haptic tick per
   second and lights contracting/intensifying.
   - If any tracked finger lifts, cancel: back to **Waiting**, remaining
     fingers keep their lights.
   - Finger movement does not cancel; lights follow the fingers.
   - At the end of "1", transition to **Result**.
3. **Result** — `W` fingers are chosen uniformly at random among the `P`
   tracked fingers. Losers fade out; winners bloom (expanding shockwave) with a
   strong haptic. Winner lights freeze at the finger's last position and stay
   visible after fingers lift. Touch input no longer affects the game.
   - Buttons appear after the reveal animation: **Rejouer** (new round, same
     `P`/`W`, back to Waiting with no lights) and **Réglages** (back to setup).
   - Any finger that is already down when Rejouer is tapped is ignored until
     it lifts (same rule as extra fingers), so it is not instantly tracked in
     the new round.

System back: from Play → Setup; from Setup → exit.

## Visual direction

"Bioluminescent neon":
- Near-black ink background with subtle animated grain/vignette.
- Each finger is a luminous orb: bright core, several soft additive glow
  layers, and a breathing outer ring. A small spawn animation on touch-down
  and a quick fade on lift.
- 12 distinct, saturated colors assigned so simultaneous fingers never share
  a color.
- Strong display typeface (bundled font) for the countdown digits and title;
  clean secondary typeface for labels.
- Setup screen shares the same world (dark, glowing accents), with large,
  thumb-friendly steppers.

Exact palette, fonts and motion curves are finalized during implementation
with the frontend-design skill.

Performance target: smooth at the device refresh rate (60/120 Hz) with 12
fingers. All drawing in a single Compose `Canvas`; no per-frame allocations in
the draw loop.

## Architecture

Single-module Android app, Kotlin, Jetpack Compose, single Activity, no
navigation library.

Units:

- **`GameEngine`** (pure Kotlin, no Android deps) — owns the state machine.
  Inputs: `fingerDown(id, x, y)`, `fingerMove(id, x, y)`, `fingerUp(id)`,
  `tick(nowMillis)`, `reset()`. Exposes an immutable `GameState`
  (phase, tracked fingers with color index and position, countdown value,
  winner ids). Takes an injectable `Random` and clock for tests.
- **`TouchLayer`** — Compose `pointerInput` that forwards raw multi-touch
  pointer events (by pointer id) to the engine. No game logic.
- **`GlowCanvas`** — draws orbs, rings, countdown effects and reveal
  animations from `GameState` plus per-finger animation values.
- **`SetupScreen`** / **`PlayScreen`** — Compose screens.
- **`MainActivity`** — hosts the screen state (Setup vs Play), keeps the
  screen on, locks portrait, edge-to-edge, hides system bars during play.
- **`Haptics`** — small wrapper around `Vibrator`/`HapticFeedback`.
- **`SettingsStore`** — persists `P` and `W`.

Target: minSdk 26, targetSdk/compileSdk latest stable.

## Build & distribution

- Gradle Kotlin DSL with version catalog.
- Local toolchain: JDK 17 + Android command-line tools via Homebrew.
- GitHub repo: `YvanBetremieux/FingerPick`, public.
- GitHub Actions:
  - On push / PR: build debug + run unit tests.
  - On tag `v*`: build signed release APK and attach it to a GitHub Release.
- Signing: a keystore generated once locally (`openssl`/`keytool`), stored
  base64-encoded in repo secrets with its passwords; never committed.
  Same key for every release so updates install over previous versions.
- Install flow: open the Release page on the phone, download the APK, allow
  "install unknown apps" for the browser once.

## Testing

- Unit tests for `GameEngine`:
  - countdown starts exactly when tracked count reaches `P`;
  - lifting a finger during countdown cancels back to Waiting;
  - extra fingers beyond `P` are ignored, including after a tracked lift;
  - result has exactly `W` distinct winners among tracked fingers;
  - winners keep their positions after lift; input ignored in Result;
  - reset returns to an empty Waiting state;
  - colors are unique among simultaneous fingers.
- Manual on-device test for touch, visuals, haptics and performance
  (emulators cannot simulate many fingers).
