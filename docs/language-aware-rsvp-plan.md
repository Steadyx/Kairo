# Language-Aware RSVP Plan (English-Safe)

Goal: add automatic language-aware tokenization + pacing while keeping English behavior unchanged.

## Principles
- English stays the default path and is not modified.
- Language-specific behavior only activates when detection is confident and supported.
- If detection fails or language unsupported, fall back to English tokenization + pacing.

## Proposed Flow
1. **Detect language**
   - Primary source: EPUB metadata `dc:language`.
   - Fallback: lightweight text detection on the first N words/paragraphs.
   - Store `book.languageTag` (BCP-47, e.g., `en`, `fr`, `ja`, `zh-Hans`).

2. **Tokenizer strategy registry**
   - `TokenizerFactory.get(languageTag)` returns a tokenizer implementation.
   - `en` -> current tokenizer (no change).
   - `es/pt/fr/de` -> current tokenizer (optional punctuation tweaks later).
   - `ja/zh` -> segmentation-aware tokenizer (CJK).
   - `ar/he` -> RTL-aware tokenizer (direction + punctuation).

3. **Pacing profile registry**
   - `PacingProfileRegistry.get(languageTag)` returns overrides to `RsvpConfig`.
   - `en` -> default config (no change).
   - CJK -> character-based pacing, adjusted punctuation weights, ORP center alignment.
   - RTL -> punctuation pacing tweaks and direction-aware ORP.

4. **Automatic RSVP selection**
   - On RSVP start: `lang = book.languageTag ?: detect()`.
   - `tokenizer = TokenizerFactory.get(lang)`.
   - `profile = PacingProfileRegistry.get(lang) ?: default`.
   - Engine runs with `(tokenizer, profile)`.

5. **Manual override (optional, per-book)**
   - Setting: "Language: Auto (detected) / English / ...".
   - Overrides stored per book; default remains Auto.

## Non-Goals (initial)
- No change to English tokenization or timing.
- No attempt to fully optimize every language from day one.

## Testing / Validation
- Snapshot tests for token boundaries on example texts in `en/es/fr/de/ja/zh/ar`.
- Timing regression tests to ensure English pacing is unchanged.
- Basic RSVP playback sanity tests for CJK and RTL samples.

## Incremental Rollout
- [x] Phase 1: language detection + registry wiring, no new tokenizers (still English).
- [x] Phase 2: CJK tokenizer + pacing overrides.
- [x] Phase 3: RTL tokenizer + pacing overrides.
- [ ] Phase 4: per-language fine-tuning based on feedback.
