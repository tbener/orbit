# Orbit (smart-dialer)

## Docs
- Product spec and build plan: `docs/SPEC.md` (read it before starting any stage; we build one stage at a time).
- Home screen mockup: `docs/design/home-mockup.png`.

## App context
Orbit is a personal Android smart dialer. Contacts are shown as priority-ranked bubbles. The app reads the call log, sends signals plus user rules to an LLM API, and gets back a ranked list with short reasons. A small deterministic layer enforces hard rules (pin a contact to a slot, never show a contact) after the LLM answers. Rules are stored locally in two separate lists: free-text rules and typed rules. Personal use, not a public release.

## Stack
Native Android, Kotlin, Jetpack Compose, Material 3. Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`). Room (rules, cached rankings). OkHttp + kotlinx.serialization for LLM calls. Package `com.smartdialer`. Permissions declared: READ_CALL_LOG, READ_CONTACTS (runtime permission logic not yet written).

## Package layout (`app/src/main/java/com/smartdialer/`)
- `ui/` screens and components
- `data/` Room, call log access, contacts access
- `ranking/` post-LLM deterministic enforcement of hard rules
- `llm/` API client, prompt building, response parsing
- `rules/` free-text rules and typed rules, as separate models

## Conventions
- Hard-rule enforcement stays deterministic and separate from the LLM layer: `ranking/` must not call or depend on `llm/`, and its output is final over the LLM's.
- Keep free-text rules and typed rules as separate models and lists.
- Never commit secrets. The API key lives in `local.properties` (`LLM_API_KEY` -> `BuildConfig`). Keystores and env files are gitignored.
- Build: `./gradlew assembleDebug`.