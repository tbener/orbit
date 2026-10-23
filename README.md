# Orbit (smart-dialer)

A personal Android dialer that shows contacts as priority-ranked bubbles. It reads the call log, sends signals plus user rules to an LLM API, and receives a ranked list with short reasons. A deterministic layer then enforces hard rules (pin a contact to a slot, never show a contact). Personal use only; not intended for public release.

**Status:** infrastructure skeleton only. No features yet.

## Stack
Kotlin, Jetpack Compose + Material 3, Gradle Kotlin DSL with version catalog, Room, OkHttp + kotlinx.serialization. Package: `com.smartdialer`.

## Prerequisites
- JDK 17 or newer (set `JAVA_HOME`)
- Android SDK with platform 35 and build-tools 35 (Android Studio recommended)

## Build and run
1. Copy `local.properties.example` to `local.properties` and set `sdk.dir` and `LLM_API_KEY`.
2. `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`)
3. Install the APK from `app/build/outputs/apk/debug/`, or run from Android Studio.

`LLM_API_KEY` is exposed as `BuildConfig.LLM_API_KEY`. `local.properties` is gitignored; never commit secrets.