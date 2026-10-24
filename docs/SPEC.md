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
