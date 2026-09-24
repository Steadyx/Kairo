# RSVP reading demand and comprehension timing

Kairo uses a deterministic reading-demand calculation. There is no trained timing
model, reader history, network inference, or runtime Python dependency.

## One word allowance

`ReadingDemandAnalyzer` computes recognition demand once from a smooth logarithmic
length curve and word rarity. For English, a structural decoding floor also accounts
for longer spelling and estimated vowel groups. It is a minimum recognition score,
not a second timing allowance; familiarity cannot erase this decoding work. English rarity uses a bundled table of 29,665 words
and contractions, derived from wordfreq 3.1.1. Familiar base forms can support simple
plurals and possessives: recognition uses the higher of the surface frequency and a
known base frequency minus 0.15 Zipf units. Unknown bases contribute no evidence. Unlisted English words use a moderate
spelling fallback, with a bounded consonant-cluster adjustment. Missing from the
table does not imply maximum difficulty. Digits receive a structural allowance.
Other language policies use script-appropriate length scaling and digit structure;
they do not inherit English frequency or pronunciation assumptions. UNKNOWN stays
conservative until the book has an explicit language policy.

Repeated English terms receive less of a small novelty allowance. Recognition time
never decreases because of repetition. Counts use normalized source words, reset at
paragraph/page boundaries, saturate at eight prior occurrences and retain at most
2,048 distinct recent words. Seeking seeds these counters from the containing
paragraph, so a reload does not invent a first occurrence. This is local text state,
not evidence that the reader understands a term.

The extra word time is:

```
cap = clamp(0.8 × base tempo, 80 ms, 180 ms)
allowance = support × cap × (1 − exp(−recognition − novelty))
```

Both recognition and novelty share this cap. At Strong (200%), the maximum is twice
the cap. Constants are engineering defaults, not coefficients fitted to eye tracking.
Split-word frames divide one source allowance by their displayed letter/digit counts;
they cannot multiply novelty or manufacture another whole-word allowance.

The old syllable/rarity/complexity stack, complex-word threshold hold, difficulty-flow
EMA and given/new beat compression no longer affect playback. Phrase scoring obtains
its difficulty evidence from the same recognition calculation, independently of
tempo and support strength. Automatic word-part expansion is separate.

## A separate word-part presentation policy

`RsvpWordPartSupport` decides whether moving highlights can help with a long spelling.
It combines family-level vocabulary familiarity with spelling shape, independently of
the timing score, token difficulty metadata, tempo, or repetition. There is no timing-score cutoff that converts a small timing
change into extra display frames. Words below the spelling thresholds still receive
their gradual reading allowance while remaining whole.

Automatic support is English-only. Selection uses the regular noun base:

- Very long spellings (14 letters, four estimated vowel groups) receive parts regardless of familiarity.
- Known uncommon vocabulary (Zipf at most 3.5) needs eight letters and three vowel groups.
- Moderately familiar vocabulary (Zipf at most 4.3) needs ten letters and four vowel groups.
- Missing dictionary entries need stronger spelling evidence: ten letters and three groups,
  or eight letters and four groups. Absence alone never triggers highlighting.
- Familiar words below the very-long-word threshold remain whole. Attested stems with regular
  `-ed`, `-ing`, `-ly`, `-ness`, `-ment`, `-less`, and `-en` endings provide familiarity evidence
  with a small 0.15 Zipf cost per step. At most two steps are considered, including silent-e,
  doubled-consonant and y/i spelling changes; unattested stems supply no evidence.

These are legibility and general familiarity heuristics, not predictions of the reader's vocabulary.
Regular plurals and possessives share
that base even when it is missing from the frequency table. Ambiguous `-es` / `-is`
inflections also share a base when it is attested in the dictionary. Selection uses that
base's familiarity, so rarer inflections cannot flip the decision. The base is used only to
plan the display; the original spelling and capitalisation stay on screen.

Base words are divided into balanced parts of up to six characters. Inflection
endings stay on the final part, preserving the number of beats and earlier boundaries
across the family. These are character chunks, not exact spoken syllables. Existing
explicit chunk limits remain authoritative, including tighter limits that can require
more parts. Numbers, hyphenated words, other scripts and non-English policies retain
their configured splitting behavior. A zero limit disables splitting.

Each part keeps its reading beat and display floor, while the source word's extra
recognition allowance is shared across the parts. No blank separation frame is
inserted between parts of the same source word. **Word-part guidance** in Advanced →
Readability controls this presentation separately from Reading support. Turning it
off restores configured length-only splitting; a zero chunk length disables splitting.

## A separate phrase allowance

For English, phrase processing uses distinct information-word count, numbers and
phrase length, not another spelling-complexity sum. Other language policies and
UNKNOWN use phrase length and numbers only; without language-specific familiarity
data, distinct words alone do not imply density. Phrases receive at most 70 ms at
100% support. The same support slider scales this allowance. Existing phrase distribution
is retained: 40% through a multiword phrase and 60% at its landing. A single source
word receives any allowance at its landing. Split words count once.

Rhythm smoothing affects the beat only. Word allowances and phrase processing bypass
it, remain protected during live speed changes and cannot be consumed by word
separation. Word allowances are added after the minimum display floor, so a short
beat or a high readability floor cannot swallow the difficult-word support. Minimum display times and explicit split-word pauses remain authoritative.
Overlapping automatic landing cues use the strongest cue; explicit punctuation pauses
remain additive.

## Controls and compatibility

The everyday sliders are **Speed** and **Reading support** (Off–200%, labelled Strong
at the maximum). Reading support scales extra time without changing word-part frame
count. **Word-part guidance** is a separate Advanced switch, on by default; it is
English-only and does not change the extra-time strength. The separate phrase slider and six difficulty fine-tuning controls
are removed from both settings and search. **Automatic cadence** is in Advanced →
Rhythm and controls existing clause/sentence shaping independently of Reading support.
Punctuation preferences and readability floors remain separate.

Reading support retains the `difficult_word_support` DataStore key and
`difficultWordSupport` profile JSON property. Existing values are preserved and clamped
to 0–2; older profiles without this field receive 100%. Word-part guidance persists as
`show_difficult_word_parts` / `showDifficultWordParts`. Older profiles default to on,
except when their stored Reading support was Off; that choice keeps guidance off until
the new switch is changed.
The retired numeric fields
still round-trip to avoid destructive changes to saved profiles, but playback ignores
them. This is a new timing policy, not an exact reproduction of old custom tuning.
Study selects 150% support and Sprint 50%; other existing presets retain 100%.

## Lookup data and reproducibility

The compressed lookup occupies 132,195 bytes and is loaded lazily once. It is shipped
as a Java resource, including in the Android APK. `scripts/export-reading-frequency.py`
reproduces it with wordfreq 3.1.1; it exports statistics and does not train weights.
The previous experimental `scripts/reading-model/train.py` is not used by this path.
See `app/src/main/assets/licenses/wordfreq-NOTICE.md` for attribution, upstream
sources and the CC BY-SA 4.0 license covering the derived frequency table.

## Validation boundary

Regression coverage checks monotonic bounded word time, real frequency lookup,
independence from old token scores and controls, limited novelty decay, language
fallbacks, locale-independent normalization, source-based seeking, split-word
conservation, final/grouped words, smoothing, floors, word separation and persistence.
Device tests exercise real exposure and the two-slider settings UI. These checks
establish implementation behavior; improved comfort or comprehension still needs
reading trials at matched total presentation time.

### Device measurements (2026-09-13)

All eight targeted checks passed on the connected CPH2493: three settings parity/
control tests, two real-exposure tests, two sweep/reload tests, and one packaged
English-lookup/determinism test. The final settings wording was rechecked separately.
The mixed 5,000-word English fixture took 1,658 ms on its first full generation and
1,000/894 ms on two warm full generations. The separate 5,000-word repository fixture
took 1,333 ms initially and 21/21/22 ms on cached reloads. These fixtures measure
different operations and are smoke measurements, not a comparison with the old engine.

Final `qualityGate` and instrumented APK assembly passed: 860 unit tests, no failures,
errors or skips; formatting and Detekt passed; Android Lint reported no errors or warnings.
