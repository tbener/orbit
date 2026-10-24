# Orbit (smart-dialer)

## Docs
- Product spec and build plan: `docs/SPEC.md` (read it before starting any stage; we build one stage at a time).
- Home screen mockup: `docs/design/home-mockup.png`.

## App context
Orbit is a personal Android smart dialer. Contacts are shown as priority-ranked bubbles. The app reads the call log, sends signals plus user rules to an LLM API, and gets back a ranked list with short reasons. A small deterministic layer enforces hard rules (pin a contact to a slot, never show a contact) after the LLM answers. Rules are stored locally in two separate lists: free-text rules and typed rules. Personal use, not a public release.

## Stack
Native Android, Kotlin, Jetpack Compose, Material 3. Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`). Room (rules, cached rankings). OkHttp + kotlinx.serialization for LLM calls. Package `com.smartdialer`. Permissions: READ_CALL_LOG, READ_CONTACTS (runtime flow in `ui/OrbitApp.kt`, explainer in `ui/home/PermissionPrompt.kt`). Contact photos load with Coil.

## Package layout (`app/src/main/java/com/smartdialer/`)
- `ui/` screens and components: `home/` (bubbles, quick-actions card, gesture map in `BubbleGestures.kt`), `details/` (per-contact screen), `rules/` (Rules page and shared rule components); `ContactActions.kt` hands a contact to the phone, WhatsApp or the contacts app
- `data/` contacts and call log access (`data/device/`), the `FallbackRanker`, Room storage for rules (`data/db/`), and `SuggestionPipeline` (ranker, then enforcer, then UI)
- `ranking/` `RuleEnforcer`: pure, deterministic enforcement of typed rules, with a trace; conflict rules are documented in its KDoc
- `llm/` API client, prompt building, response parsing
- `rules/` free-text rules and typed rules, as separate models

## Design
The visual design is described in `docs/DESIGN.md` and implemented as tokens in `ui/theme` (`Orbit.colors`, `Orbit.spacing`, `Orbit.motion`, `Modifier.glass`, `OrbitBackground`). Screens use only tokens; do not hard-code colors, sizes or durations. Dark is the primary design; light is its own design. Respect reduced motion (`Orbit.motion.reduced`), 48dp touch targets and content descriptions.

## Design principle: nothing hidden
Everything Orbit stores or decides must be visible and editable by the user: free-text rules, typed rules, per-contact and general instructions, hidden contacts, and (from stage 4) how the LLM classified each instruction and the reasons it generated. Anything created automatically must be correctable (for example changing a rule's classification from soft to hard, or deleting it). Never create hidden state the user can't inspect.

## Stage 4 requirements (LLM)
- The LLM receives (a) general instructions and (b) each contact's own instructions alongside that contact's call data, and classifies new instructions as soft or hard.
- General instructions like "ignore missed calls" are SOFT instructions for the LLM, not enforced by the deterministic layer.
- Stage 4 includes a screen showing how each instruction was classified, with a way to correct it.
- Later item (not yet built): a "Hidden contacts" section listing all contacts under a NEVER_SHOW rule, each with a "Bring back" action.

## Conventions
- Hard-rule enforcement stays deterministic and separate from the LLM layer: `ranking/` must not call or depend on `llm/`, and its output is final over the LLM's.
- Keep free-text rules and typed rules as separate models and lists. Rules reference contacts by lookup key (`Contact.id`), never by name or number.
- NEVER_SHOW applies to automatic suggestions only, never to search results.
- A free-text rule with a `contactKey` is about that contact; without one it is a general instruction. Orbit never reads anything from WhatsApp; it only opens `wa.me` links.
- Room schemas are exported to `app/schemas/` and committed. Changing an entity means bumping the version, adding a `Migration` and a migration test.
- Tests: `./gradlew testDebugUnitTest` (JVM); Room tests are instrumented, run with `ANDROID_SERIAL=<emulator> ./gradlew connectedDebugAndroidTest` (they uninstall the app from every connected device otherwise).
- Never commit secrets. The API key lives in `local.properties` (`LLM_API_KEY` -> `BuildConfig`). Keystores and env files are gitignored.
- Build: `./gradlew assembleDebug`.