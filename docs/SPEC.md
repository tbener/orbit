### Product spec
Orbit is a personal Android dialer (for me and maybe one or two others, not a public release) whose main feature is suggesting who to call right now.

UI:
- Home screen shows contacts as circular bubbles (photo or initials), not a list, ordered by priority. The top-priority contact is the largest bubble, then progressively smaller ones.
- Each bubble can show a small badge (for example a missed call) and a short subtitle (for example "3 days", "Sunday").
- An info icon or tap target shows a human-readable explanation of why this contact is shown (LLM-generated later, for example "You wanted to talk every 3 days, and it's been 3").
- A search bar searches ALL contacts, not only the curated ones.
- An "Open dialer" button opens the normal Android dialer with no contact pre-filled.
- A small "updating suggestions" indicator appears while background refresh is running.
- An icon at the top right leads to the Rules page.
- The bubble list is capped and auto-loads on scroll. There is no "show more" button.

Priority logic (computed later by an LLM, inputs come from the call log and user rules):
- Highest: people who called and need a call back; good friends not spoken to in a while; family with a user-defined cadence (for example mother at least every 2-3 days).
- Detect recurring habits (for example someone I call every Sunday) and surface that contact on top on that day.
- No calendar integration at first. WhatsApp integration is a maybe, later.
- Needs READ_CALL_LOG and READ_CONTACTS permissions.

Architecture:
- The LLM does scoring, priority, and the reason texts. Results are cached so they're ready when the app opens.
- Show an initial list fast from the cache or a simple fallback, then update on the fly when fresh LLM results arrive. Refresh in the background every few hours or once a day.
- The user adds rules in free text (or speech). The LLM classifies each as a hard rule (deterministic, for example "always put my brother in slot 1", "never show my boss") or a soft preference.
- A deterministic post-processing layer ALWAYS runs after the LLM answer and enforces the hard rules, because the LLM may get exact placement wrong.
- Two separate rule lists in local storage: free-text rules for the LLM, and typed rules for the deterministic engine.
- A Rules page lets the user view, edit, and delete rules from both lists, kept visibly distinct. It also serves as a debug view for me as the developer.

### Design principle: nothing hidden
Everything Orbit stores or decides must be visible and editable by the user. That includes free-text rules, typed rules, per-contact and general instructions, hidden contacts, and (from stage 4) how the LLM classified each instruction and the reasons it generated. Anything created automatically must be correctable (for example changing a rule's classification from soft to hard, or deleting it). Never create hidden state the user can't inspect.

### Instructions: general and per-contact
- A free-text instruction can be about one contact (it has a contact key) or general (no contact key).
- Stage 4 requirement: the LLM receives (a) the general instructions, and (b) each contact's own instructions alongside that contact's call data, and classifies new instructions as soft or hard.
- General instructions such as "ignore missed calls" are SOFT instructions for the LLM. They are not enforced by the deterministic layer.
- Stage 4 also includes a screen showing how each instruction was classified, with a way to correct it.

### Later items
- Hidden contacts: a section listing all contacts under a NEVER_SHOW rule, each with a "Bring back" action. (Not part of stage 3b.)

## Build plan (we will go one stage at a time)
1. UI shell with fake data
2. Real contacts and call log, with permission flow
3. Typed rules storage, deterministic enforcement layer, and Rules page
3b. Quick actions and instructions UI
4. LLM integration: ranking, reasons, rule classification, caching, background refresh
5. Recurring-pattern signals and polish

## Stage 4 as built: LLM integration

- **API:** Anthropic Messages API (`POST /v1/messages`, `anthropic-version 2026-10-21`), structured output via `output_config.format` (JSON schema), no sampling parameters. The model is the single constant `LlmConfig.MODEL` (`claude-sonnet-5-5`; switch to `claude-haiku-5-5` there). Key: `LLM_API_KEY` in `local.properties`.
- **Ranking call (`ranking-v1`):** input is the date, weekday, time of day, time zone, general instructions, pins as context only, and up to 100 candidates (contacts with recent calls plus any with instructions or pins; NEVER_SHOW contacts excluded). Output per contact: `contactKey`, `score` 0-100, `tier` (top/high/normal/low), one-sentence `reason`. The answer is validated strictly (unknown/duplicate keys dropped, values clamped, omitted contacts filled in fallback-ranker order); an unusable answer keeps the previous cache.
- **Classification call (`classification-v1`):** each new free-text rule becomes HARD (maps exactly onto PIN_TO_SLOT or NEVER_SHOW) or SOFT, with a one-sentence explanation. Names are resolved in code against device contacts; ambiguous or missing names become NEEDS_REVIEW and are never guessed. The user can override any classification on the Rules page.
- **Cache and refresh:** latest ranking in Room (`ranking_cache`) with inputs hash, prompt version and model. Shown first on open, always run through `RuleEnforcer`. Refreshed in the background when older than 3 hours or the inputs hash changed; WorkManager every 6 hours (network required); "Refresh now" in Settings. Failures are quiet: last good result kept, small indicator, retry after 15 minutes.
- **Settings:** AI switch (off means fallback ranker only, no network), one-time disclosure required before the first call, last refresh status, and "What the LLM sees" (the last real request, raw answer and validation issues, stored as sent).

## WhatsApp signals

- **What:** a `NotificationListenerService` that reacts only to notifications from WhatsApp and WhatsApp Business (`com.whatsapp`, `com.whatsapp.w4b`). Per event it records only: matched `contactKey`, timestamp, direction. It never reads, stores, logs or sends message text, and ignores every other app.
- **Filtered out:** group chats (MessagingStyle group flag or a conversation title), summary notifications, non-message categories (calls), and any sender that is not matched confidently. Matching: phone-number match, else exact normalized name, else an unambiguous first-name prefix; ambiguous or unmatched means dropped (`WhatsAppSenderMatcher`).
- **Outgoing:** a direction=outgoing event is recorded when the user uses Orbit's WhatsApp quick action. WhatsApp data is therefore partial: replies sent inside WhatsApp are not seen.
- **Retention and control:** 90 days, then pruned. Enabled by default but inactive until notification access is granted; a one-time prompt explains it. Settings: toggle, status (Active / Needs permission / Off), button to the system screen, "Delete WhatsApp data".
- **Signals:** `lastWhatsAppIncomingAt`, `lastWhatsAppOutgoingFromOrbitAt`, incoming notification counts for 7 and 30 days. Sent to the ranking model (prompt `ranking-v2`, which tells it the data is partial) and shown on Details, only when the feature is on and there is data.

## Call signals

"Talked" means an answered incoming or outgoing call of at least 10 seconds. Missed calls and outgoing calls that rang out are counted separately (`missedCount`, `unansweredOutgoingCount`) and never count as a conversation. The ranking payload's `callsInHistoryWindow` is the number of real conversations. WhatsApp activity is a separate signal and is never counted as a call. The prompt text is part of the inputs hash, so editing `ranking_v2.txt` refreshes the cached ranking.

## Hardening against off-topic or hostile text

- **Data, not commands:** instructions, contact names and (never sent) WhatsApp sender names are DATA. The payload is structured JSON (`generalInstructions`, `candidates[].instructions`, `name`, `hardRules` objects); nothing is concatenated into prompt text. Prompts `ranking-v3` and `classification-v2` tell the model to treat those fields as data, ignore requests to change format, reveal instructions or do anything but rank and explain.
- **Sanitizing:** control, invisible-format and private-use characters are removed from names (max 60) and instructions; reasons are plain text, max 120 characters, with markup and URLs stripped; an empty reason falls back to the template reason. Reasons are shown as plain, non-clickable text.
- **Off-topic:** classification can return `NOT_RANKING_RELATED`; the rule gets status `REJECTED_OFF_TOPIC`, is shown on the Rules page ("This doesn't affect who to call, so Orbit ignores it"), is never sent to ranking, and editing it re-classifies it. Unclassified rules are also not sent to ranking.
- **Limits:** 300 characters per instruction (typing stops with a message), at most 30 (most recent) instructions per ranking request (a note on the Rules page says how many are kept but unused), and a 120,000-character payload cap (contacts from the end of the list are left out; the note appears in "What the LLM sees").
- **Model output** is only ever used for ranking scores, reasons and rule classification (contact lookup by name for a hard rule). No code path opens, runs or sends anything based on model text.
