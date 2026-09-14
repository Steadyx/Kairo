# RSVP reading demand and comprehension timing

Kairo uses a deterministic reading-demand calculation. There is no trained timing
model, reader history, network inference, or runtime Python dependency.

## One word allowance

`ReadingDemandAnalyzer` computes recognition demand once from a smooth logarithmic
length curve and word rarity. English rarity uses a bundled table of 29,665 words
and contractions, derived from wordfreq 3.1.1. Unlisted English words use a moderate
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
EMA and given/new beat compression no longer affect playback. Segmentation obtains
its difficulty evidence from the same recognition calculation; it remains independent
of tempo and support strength.

## A separate phrase allowance

Phrase processing uses distinct information-word count, numbers and phrase length,
not another spelling-complexity sum. Dense phrases receive at most 70 ms at 100%
support. The same support slider scales this allowance. Existing phrase distribution
is retained: 40% through a multiword phrase and 60% at its landing. A single source
word receives any allowance at its landing. Split words count once.

Rhythm smoothing affects the beat only. Word allowances and phrase processing bypass
it, remain protected during live speed changes and cannot be consumed by word
separation. Minimum display times and explicit split-word pauses remain authoritative.
Overlapping automatic landing cues use the strongest cue; explicit punctuation pauses
remain additive.

## Controls and compatibility

The everyday sliders are **Speed** and **Reading support** (Off–200%, labelled Strong
at the maximum). The separate phrase slider and six difficulty fine-tuning controls
are removed from both settings and search. **Automatic cadence** is in Advanced →
Rhythm and controls existing clause/sentence shaping independently of Reading support.
Punctuation preferences and readability floors remain separate.

Reading support retains the `difficult_word_support` DataStore key and
`difficultWordSupport` profile JSON property. Existing values are preserved and clamped
to 0–2; older profiles without this field receive 100%. The retired numeric fields
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
