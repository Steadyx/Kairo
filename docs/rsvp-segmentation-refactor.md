# RSVP scored segmentation refactor

Status: second debug-only functional slice implemented on `feat/rsvp-scored-segmentation`.

Implementation status (2026-08-24): the current slice adds explicit language/strategy generation
options, request-local immutable one-to-one expanded-token atoms, a deterministic six-word
`SCORED_DP_V2` segmenter, named-entity and immutable dialogue-role scores, and graded boundary
input to the existing `RhythmState`. Repository/cache/preview propagation, route language
readiness, and strategy-aware pace estimation remain part of the same unshipped change.

Dialogue and parenthetical annotation are independently gated by their existing configuration
flags. The request-local role pass also tracks unambiguous speech-quote pairs used in CJK/RTL
text without mutable tokenizer state; Hebrew gershayim and CJK book-title brackets are deliberately
excluded. English lexical speaker-tag evidence remains English-only. Graded punctuation strength
reuses the engine's contextual abbreviation/decimal/sentence classifier, gates English lexical
comma promotion to the English policy, then adds conservative universal Unicode comma/clause
tiers. Timing boundaries therefore agree with existing punctuation semantics without changing
legacy generation.

Debug resolution can exercise V2 for explicit English, default-non-English, CJK, and RTL policies;
non-English policies use conservative universal/script-aware load only and are capped at two-word
frames. `UNKNOWN`, unsupported persisted widths, and every release build remain
`LEGACY_GREEDY`. Disabling phrase chunking still yields single-word frames, while V2 boundary
timing can be evaluated independently. Non-English synthetic pace estimation explicitly retains
the legacy English sample until language-matched samples exist.

This bounded atom layer preserves a one-to-one cursor/source-range mapping and is authoritative for
V2 candidate selection and selected-word materialisation. The existing builder remains the sole
punctuation owner, and V2 does not cross punctuation or structural tokens. Soft-punctuation
crossing, full ownership extraction, per-word timing aggregation, release default-on, greedy
removal, performance ratification, and device QA (especially CJK/RTL rendering) remain deferred.

## 1. Executive summary

Kairo already has a capable, deterministic RSVP pipeline. It tokenizes by language,
annotates words with readability metadata, preserves punctuation and structural breaks,
tracks dialogue and parenthetical context, applies focal and anticipatory contours, and
smooths word-duration changes before adding punctuation pauses. The missing piece is not
another timing engine. It is a segmentation stage that can compare several plausible frame
sequences instead of accepting the first locally valid adjacent-word merge.

The proposed refactor inserts two internal stages between expanded-token creation and the
existing timing code:

1. immutable linguistic annotation, including phrase affinity, graded boundary strength,
   cognitive load, dialogue role, and lightweight entity spans;
2. a deterministic, six-word rolling dynamic-programming segmenter.

The segmenter operates on expanded-token cursors so it can preserve exact resume and source
character coordinates. It considers only legal frames within the existing
`maxWordsPerUnit` and `maxCharsPerUnit` limits, scores alternative sequences within a bounded
six-word horizon, commits the best first segment, and repeats. Six words is enough to compare
two complete three-word alternatives while keeping CPU and memory bounded. With horizon `H`
fixed at six and legal frame width `k` small, segmentation is `O(n * H * k)`, effectively
`O(n * k)`, after an `O(n)` annotation pass.

Existing timing remains sequential and authoritative. `ProseState`, `RhythmState`, and
`FlowState` must not be evaluated speculatively inside dynamic programming because they are
mutable and order-dependent. Once segmentation is selected, the current punctuation,
prosody, difficulty, flow, smoothing, session-ramp, and blink stages run over the chosen
segments.

Delivery must be incremental. The greedy builder remains an internal oracle and rollback
path until the scored implementation passes golden phrasing, token-conservation, cursor and
display-range invariants, preview and cache parity, Bionic integration, multilingual
fallback, deterministic replay, and measured performance gates.

## 2. Current RSVP architecture

### 2.1 Runtime path

The observed production path is:

```text
Chapter
  -> TokenRepositoryImpl
  -> TokenizerRegistry.resolve(languageTag)
  -> List<Token>
  -> RsvpFrameRepositoryImpl
  -> ComprehensionRsvpEngine.generateFrames(...)
  -> List<RsvpFrame>
  -> RSVP or Bionic playback
```

`TokenizerRegistry` selects the default `Tokenizer`, `CjkTokenizer`, or `RtlTokenizer`.
The default tokenizer calls `WordAnalyzer.analyze` for every word and stores the resulting
ORP index, syllable count, frequency score, complexity multiplier, and clause-boundary flag
on `Token`. It also annotates dialogue state. Punctuation, paragraph breaks, and page breaks
remain separate tokens.

`ComprehensionRsvpEngine.generateFrames` currently performs these steps:

1. clamp the supplied `RsvpConfig` with `normalizedForPlayback`;
2. use `resolveAnalysisStartIndex` to recover preceding context, bounded by 240 source tokens
   and structural or punctuation boundaries;
3. split long or hyphenated words with `splitTokenForRsvp`;
4. map each result to `ExpandedToken`, preserving original index, expanded index, and source
   character offsets;
5. run `analyzeExpandedTokens`, producing `RsvpTokenAnalysis`;
6. repeatedly call `appendNextFrame` and greedily `buildUnit`;
7. calculate each unit's duration with `computeUnitDurationMs`;
8. apply session ramps and optional blink separation.

### 2.2 Current greedy decision

`RsvpUnitBuilder.buildUnit` uses a mutable `UnitCursor` to consume:

```text
leading punctuation -> first word -> optional phrase words -> trailing punctuation
```

`UnitCursor.consumePhraseWords` examines only the most recently accepted word and the
immediate next token. A word is appended when all of the following hold:

- phrase chunking is enabled;
- the frame remains below `maxWordsPerUnit`;
- combined word characters remain below `maxCharsPerUnit`;
- the next token is a word;
- `isPhraseChunkCandidate(previousWord, nextWord)` returns true.

The first failed pair ends the frame. No competing two- or three-frame sequence is scored.

### 2.3 Existing analysis worth preserving

The current engine contains useful features that should feed the new model rather than be
rewritten:

| Existing system | Current location | Reuse in the refactor |
| --- | --- | --- |
| syllables, frequency, complexity, ORP | `WordAnalyzer`, `Token` | lexeme load and timing inputs |
| clause starters, phrase leaders/enders, coherence | `ClauseDetector` | affinity and boundary features |
| tight pairs, glue words, bridges, semantic anchors | `RsvpWordPacing` | scored affinity or hard legality |
| focal words, landings, paired dashes, phrase contours | `RsvpPhraseAnalysis` | immutable annotations and later timing |
| punctuation hierarchy and special cases | `RsvpPunctuationTimingPolicy`, `RsvpTextRules` | boundary kind/strength and legal punctuation ownership |
| dialogue state and speaker-tag heuristics | `Token.isDialogue`, `DialogueAnalyzer` | immutable dialogue roles and timing |
| word difficulty and floors | `RsvpTiming`, `RsvpWordPacing` | cognitive load and final timing |
| parenthetical, givenness, semantic and prosodic shaping | `RsvpUnitTiming` | final sequential timing |
| EMA and rate-change clamps | `RhythmState` | temporal smoothing implementation |
| difficulty-flow EMA | `FlowState` | final sequential timing |
| session ramps and blink separation | `RsvpFramePostProcessor` | unchanged post-processing |

### 2.4 Coordinate and repository contracts

`RsvpFrame` is more than visible text and duration. It contains:

- `originalTokenIndex`, the source-token position anchor;
- `resumeCursor`, the exact expanded-token cursor;
- `nextOriginalTokenIndex`, progress after consuming the frame;
- `displayOriginalStartIndex` and `displayOriginalEndExclusive`;
- source-character offsets for the first and last displayed original tokens.

These fields are consumed by `RsvpFrameIndexMap`, RSVP position saving and restoration,
`ReadingSessionFrameWords`, context assist, estimated pace, and Bionic highlighting/chunking.
Repeated original indexes are valid for split words and inserted blink frames.

`RsvpFrameRepositoryImpl` quantizes cache *start* positions to 512-token boundaries but asks
the engine to generate from that start to the chapter end. The 512 value is therefore not an
end-of-window boundary. The real comparison seams are:

- base generation from a quantized start versus exact generation from a requested start;
- preview generation truncated by `maxTokenCount`;
- preview index offsetting, where `resumeCursor` is intentionally set to `-1`;
- config changes included or excluded by `frameTimingKey`.

## 3. Current limitations

### 3.1 Local optimality

`isPhraseChunkCandidate` collapses several graded signals to a Boolean. A decision such as
joining `into` and `the` is made without comparing the later alternatives:

```text
[into the] [room] [where he] [waited]
[into the room] [where he waited]
```

Both begin with a locally plausible pair, but only sequence-level scoring can prefer the
more balanced phrasing.

### 3.2 Analysis, grouping, and timing are interleaved

Some analysis is precomputed by `analyzeExpandedTokens`; other calls to `ClauseDetector`,
dialogue helpers, difficulty functions, and prosody rules happen while a frame is timed.
This makes features harder to explain, reuse, or tune and makes speculative frame evaluation
unsafe because timing mutates state.

### 3.3 Hard boundaries hide useful gradation

`BoundaryBefore` and `RsvpPunctuationTier` correctly preserve important categories, but they
do not provide one continuous signal that can consistently influence segmentation,
anticipatory slowing, restart shaping, and smoothing. Boolean token flags have the same
limitation.

### 3.4 Word and character counts approximate cognitive load

Two easy function words and two rare multisyllabic terms can satisfy identical count limits
while presenting very different perceptual loads. `frameDifficulty` and `wordEase` already
contain much of the missing information, but the greedy builder does not use it when choosing
a frame.

### 3.5 Proper nouns and dialogue are timing-oriented

Capitalisation, acronym emphasis, `Token.isDialogue`, and speaker-tag compression exist, but
there is no explicit entity span or dialogue-role annotation that segmentation can use.

### 3.6 Explainability is implicit

Rules are testable individually, but there is no structured record explaining why a pair was
joined, why a boundary was selected, or which load feature rejected a candidate.

## 4. Refactor goals

The implementation is successful when it:

- produces more natural phrase sequences through bounded sequence scoring;
- stays offline, deterministic, explainable, and dependency-free;
- preserves tokenizer, frame, position, progress, and persistence contracts;
- reuses existing linguistic and timing behaviour;
- keeps preprocessing effectively negligible beside import and rendering;
- falls back conservatively when language-specific metadata is weak;
- can be compared with and rolled back to the greedy segmenter;
- has golden, invariant, integration, multilingual, and performance coverage.

The first scored release should not expose scorer weights, horizon, or load budget as user
settings. Existing RSVP profiles and saved preferences retain their meaning.

## 5. Proposed architecture

```text
Book text
  -> normalized language tag
  -> TokenizerRegistry / language tokenizer + immutable RsvpLanguagePolicy
  -> List<Token>
  -> existing RSVP token expansion
  -> List<ExpandedToken>
  -> immutable linguistic context and punctuation ownership analysis
  -> List<RsvpAtom> + List<RsvpBoundary>
  -> affinity, load, entity, dialogue and contour annotation
  -> RsvpAnnotatedStream
  -> legal candidate generation
  -> six-word rolling DP segmentation
  -> List<RsvpSegment>
  -> existing sequential timing and prosody
  -> existing RhythmState / FlowState behaviour
  -> existing session ramp and blink post-processing
  -> List<RsvpFrame>
```

The seam belongs after expansion because DP needs the expanded cursor and split-word source
offsets. It belongs before timing because segmentation scores must be immutable and stateless.

The generation request must carry two explicit, non-persisted app-domain inputs in addition to config:
`RsvpLanguagePolicy` and `RsvpSegmentationStrategy`. The route already resolves the book's
language tag, and the screen already knows the selected profile ID. A rollout resolver converts
those values into generation inputs and passes the same inputs through preview, full generation,
prefetch, exact-start fallback, and cache identity. Pace estimation receives the same resolved
segmentation strategy but separately identifies the language of its synthetic sample. Direct
engine callers must choose an explicit policy; `UNKNOWN` is conservative rather than an alias
for English.

Language readiness is explicit rather than inferred from a nullable tag. Extend the launch
snapshot to carry its resolved nullable language tag. Without a resolved snapshot, the route may
load tokens concurrently but must not publish a frame-ready `RsvpBookContext` or start autoplay
until the book-language lookup completes. Once resolved, freeze the language policy for that
book/session; a metadata change takes effect on the next session, never by swapping frame
segmentation underneath active playback. A resolved null tag legitimately selects `UNKNOWN`.

`RsvpUnitBuilder` remains as `LegacyGreedySegmenter` during migration. Punctuation ownership
should first be extracted into a shared, immutable atomizer so legacy and scored paths cannot
silently diverge on quotes, currency, or hard punctuation.

## 6. Proposed data structures

Names are provisional, but responsibilities should remain separate.

```kotlin
internal data class RsvpAtom(
    val startExpandedCursor: Int,
    val endExpandedCursorExclusive: Int,
    val tokens: List<Token>,
    val word: Token?,
    val originalTokenIndex: Int,
    val sourceCharacterStart: Int,
    val sourceCharacterEndExclusive: Int,
    // Linguistic lookback only; never used as the live timing ContextState.
    val linguisticContext: RsvpContextSnapshot,
) {
    val wordCount: Int get() = if (word == null) 0 else 1
    val wordCharacterCount: Int get() = word?.text?.length ?: 0
}

enum class RsvpLanguagePolicy {
    ENGLISH,
    DEFAULT_NON_ENGLISH,
    CJK,
    RTL,
    UNKNOWN,
}

enum class RsvpSegmentationStrategy {
    LEGACY_GREEDY,
    SCORED_DP_V2,
}

data class RsvpGenerationOptions(
    val languagePolicy: RsvpLanguagePolicy,
    val segmentationStrategy: RsvpSegmentationStrategy,
)

data class RsvpPaceEstimationOptions(
    val sampleLanguagePolicy: RsvpLanguagePolicy,
    val segmentationStrategy: RsvpSegmentationStrategy,
)

internal data class RsvpBoundary(
    val leftAtomIndex: Int,
    val rightAtomIndex: Int,
    val kind: RsvpBoundaryKind,
    val strength: Int, // fixed-point 0..1000
    val hard: Boolean,
    val reasons: Set<RsvpBoundaryReason>,
)

internal enum class RsvpBoundaryKind {
    NONE,
    PHRASE,
    CLAUSE,
    SENTENCE,
    PARAGRAPH,
    PAGE,
}

internal data class RsvpLexemeFeatures(
    val atomIndex: Int,
    val normalizedText: String,
    val difficulty: Int,
    val semanticWeight: Int,
    val visualLoad: Int,
    val dialogueRole: DialogueRole,
    val entitySpanId: Int?,
    val entityPosition: EntityPosition,
    val phraseContour: PhraseContour,
    val reasons: Set<RsvpFeatureReason>,
)

internal data class PhraseAffinity(
    val leftAtomIndex: Int,
    val rightAtomIndex: Int,
    val score: Int,
    val hardDisqualifier: Boolean,
    val reasons: Set<AffinityReason>,
)

internal data class RsvpAnnotatedStream(
    val atoms: List<RsvpAtom>,
    // Stable bridge between the word-indexed DP and cursor/index-bearing atoms.
    val atomIndexByWordPosition: IntArray,
    val wordPositionByAtomIndex: IntArray, // -1 for structural/context-only atoms
    val features: List<RsvpLexemeFeatures>,
    val boundaries: List<RsvpBoundary>,
    val affinities: List<PhraseAffinity>,
)
```

`RsvpLanguagePolicy`, `RsvpSegmentationStrategy`, `RsvpGenerationOptions`, and
`RsvpPaceEstimationOptions` are deliberately public app-domain types because the existing
`RsvpEngine` and `RsvpFrameRepository` interfaces are public. The annotation, candidate, scorer,
and rollout-resolver implementations remain `internal`. This avoids exposing an internal
parameter type from a public Kotlin function while keeping persistence and UI settings unchanged.

`RsvpAtom` owns or accounts for each punctuation cursor exactly once and carries the
coordinates needed to build a frame. Ownership must reproduce the three existing paths:

- opening punctuation selected by `consumeLeadingPunctuation` belongs to the following word
  atom and is displayed with it;
- punctuation accepted by `consumeTrailingPunctuation` belongs to the preceding word atom and
  is displayed with it;
- punctuation advanced only for state by `consumeContextPunctuation` is represented by a
  context-only atom/range so its cursor and quote/parenthetical transition are retained without
  adding visible text.

A structural break is represented as its own barrier atom. When another readable word follows,
it later produces the same blank break frame as today; a terminal break with no following word
is omitted, matching `appendBreakFrame`.

Punctuation ownership and linguistic lookback are separate passes. Linguistic annotation may
scan from `analysisStartIndex`, but the request-local ownership atomizer begins at the resolved
first playback cursor with a fresh `RsvpPunctuationOwnershipState()`. That state mirrors the
quote/parenthesis transitions and leading/trailing/context-only decisions of today's fresh
`ContextState` without mutating the later live timing state. Both states must call one extracted
transition primitive; do not duplicate quote/parenthesis rules. In particular, a prefix-aware
linguistic snapshot must never decide whether a straight quote is opening or closing for display
ownership. The timing pass starts a second fresh `ContextState()` and replays the already-owned
atoms in order. Parity fixtures compare frame text, ranges, cursors, and durations when exact
generation begins inside an open straight quote or parenthesis.

Playback state seeding then has three deliberately distinct contracts. Unit
punctuation/dialogue `ContextState` preserves current behaviour: initialise it empty at the
resolved first playback cursor and advance it sequentially only while emitting selected frames;
do not seed that state from the analysis prefix. In contrast, `createProseState(expanded,
cursor, config)` must continue reconstructing sentence-word count and adaptive givenness from
the prefix, while `RhythmState` and `FlowState` continue fresh. A future correction for entering
an already open quotation or parenthesis is a separate behaviour change with its own duration
goldens.

The DP is indexed by compact word position for bounded array storage, while candidates and
frames use atom indexes and expanded cursors. `atomIndexByWordPosition` and
`wordPositionByAtomIndex` are constructed once, are strictly ordered, and are the only allowed
conversion between those coordinate spaces.

Candidate and result types remain separate from analysis:

```kotlin
internal data class RsvpSegmentCandidate(
    val startAtom: Int,
    val visibleEndAtomExclusive: Int,
    // Cursor after replaying adjacent context-only atoms; this starts the next selection.
    val nextSelectionAtom: Int,
    val nextSelectionWordPosition: Int,
    val startExpandedCursor: Int,
    val visibleEndExpandedCursorExclusive: Int,
    val nextSelectionExpandedCursor: Int,
    val wordCount: Int,
    val wordCharacters: Int,
    val frameLoad: Int,
    val rightEdgeKind: RsvpRightEdgeKind,
    val score: SegmentScore,
)

internal enum class RsvpRightEdgeKind {
    OBSERVED_GAP,
    HARD_OR_SOURCE_END,
    ARTIFICIAL_HORIZON,
}

internal data class SegmentScore(
    val total: Int,
    val components: List<ScoreComponent>,
)

internal data class RsvpSegment(
    val candidate: RsvpSegmentCandidate,
    val selectedPathScore: Int,
)
```

Score objects should exist in normal internal code so tests can inspect them. The component
list can be omitted in release execution through a no-op collector; selection must never
depend on whether explanations are enabled.

## 7. Phrase-affinity model

`isPhraseChunkCandidate` should initially be decomposed, not deleted. Its current rules fall
into two groups.

Hard disqualifiers:

- structural breaks;
- an intervening punctuation atom that legacy code does not cross;
- unrelated subword chunks;
- trailing hyphen continuation semantics;
- explicit clause-boundary or coordinating-conjunction rules during parity phases;
- any join that violates word or character limits.

Scored features:

- `ClauseDetector.getCoherenceScore`;
- tight-pair hints;
- pronoun/auxiliary and auxiliary/content bridges;
- function-word and glue-word relationships;
- phrase leader/ender relationships;
- frequency and word ease;
- semantic-anchor relationships;
- entity-span continuity;
- dialogue-role continuity;
- balanced visual width.

Affinity is pairwise and answers: “How costly would it be to place a frame boundary between
these two atoms?” It does not decide a frame by itself.

```text
affinity(left, right) =
    coherence
  + function-word cohesion
  + bridge or tight-pair evidence
  + entity continuity
  + dialogue-role continuity
  - semantic-anchor collision
  - difficult-word adjacency
```

The language policy gates each lexical feature before scoring. Unsupported evidence contributes
zero rather than a generic penalty; low confidence may attenuate a supported signal toward zero
but may not invent negative affinity.

Each input is normalized to `0..1000`, multiplied by a versioned integer weight, and summed
with saturating integer arithmetic. Using fixed-point values avoids platform-sensitive
floating tie behaviour. If existing `Double` helpers are reused, convert them exactly once
with a documented `roundToInt` rule before DP.

Weights belong in one immutable `RsvpSegmentationWeights` value with:

- a schema/version integer;
- named fields rather than positional arrays;
- documented input ranges;
- unit tests for defaults and bounds;
- no user preference or remote tuning in the first release.

`ClauseDetector`, tight-pair, function-word, bridge, and phrase leader/ender features are
English lexical evidence and are enabled only by `RsvpLanguagePolicy.ENGLISH`. Other policies
retain universal punctuation, length, visual, numeric, and structural features.

## 8. Boundary-strength model

Boundary strength belongs on the gap between atoms, not solely on either token.

Recommended initial mapping:

| Kind | Provisional strength | Initial join policy |
| --- | ---: | --- |
| none | `0` | legal |
| weak phrase transition | `150..300` | legal, scored |
| phrase boundary | `350..500` | legal only after parity phase, strongly penalised |
| clause boundary | `600..750` | initially illegal to cross |
| sentence boundary | `850..950` | illegal |
| paragraph/page | `1000` | structural barrier |

The precise values must be calibrated by golden tests; the ordering is the invariant.
`BoundaryBefore` and `RsvpPunctuationTier` remain during migration. The graded analyser wraps
their output and adds reason codes such as:

- punctuation tier;
- `Token.isClauseBoundary`;
- `ClauseDetector.isPhraseLeader` or `isPhraseEnder`;
- coordinating conjunction;
- dialogue entry/exit;
- parenthetical or paired-em-dash edge;
- paragraph/page break.

Boundary strength may later drive:

- join penalties or hard legality;
- anticipatory landing intensity;
- start/restart contour intensity;
- pause interpolation;
- smoothing reset strength.

Those consumers must be introduced separately. One signal should not change segmentation
and timing simultaneously without tests that isolate each effect.

## 9. Cognitive-load model

`frameLoad` is a stateless estimate used for candidate selection. It is not the final frame
duration.

For word `w`, normalize existing metadata into `0..1000` components:

```text
wordLoad(w) =
    base recognition cost
  + normalized character/visual width
  + normalized extra syllables
  + inverse frequency
  + normalized complexity multiplier
  + semantic or numeric emphasis
  + subword continuation cost
```

For a candidate frame:

```text
frameLoad =
    sum(wordLoad)
  + multi-word integration cost
  + punctuation complexity
  + difficult-word collision penalty
  + visual imbalance penalty
  - bounded function-word cohesion credit
```

The load budget is a soft penalty below the existing hard limits. A high-affinity named
entity can therefore stay together when readable, while `epistemological uncertainty` can
split even if it satisfies raw character and word caps.

Initially derive the budget internally from existing profile values:

- `maxWordsPerUnit` remains a hard upper bound, while `maxCharsPerUnit` is a hard cap on
  extending a frame beyond its mandatory first word/subword; an over-limit first word remains
  the documented safe fallback;
- the baseline budget corresponds to the load of that profile's intended count of common,
  medium-length words;
- the budget excludes `tempoMsPerWord`, `baseWpm`, and live session tempo. Segmentation is
  tempo-independent in the first scored release because live speed changes scale existing frame
  durations without regenerating frames;
- no new DataStore field, custom-profile field, or settings UI is added until evidence shows
  a user control is necessary.

Changing live tempo must therefore leave frame boundaries and cache identity unchanged. If a
later experiment proposes tempo-aware segmentation, it must first specify regeneration,
alignment, preview, cache, and in-flight playback behaviour as a separate feature.

The same normalized difficulty primitives should be shared with, or extracted from,
`wordEase` and `frameDifficulty` to prevent two definitions drifting.

## 10. Candidate frame generation

Candidate generation enforces legality before scoring. For each readable start atom:

1. always emit a one-word candidate unless the atom is a structural break;
2. when `enablePhraseChunking` is false, stop there and emit no multi-word candidates;
3. otherwise extend one word at a time up to `maxWordsPerUnit` and the remaining six-word
   horizon;
4. stop on the first hard boundary or punctuation ownership barrier;
5. reject an *extension* once word characters exceed `maxCharsPerUnit`; the mandatory
   one-word/single-subword candidate remains legal even when that first word alone exceeds the
   character cap, matching the current builder;
6. reject coordinate-invalid or subword-invalid spans;
7. calculate load and score for remaining candidates.

Pseudocode:

```text
generateCandidates(startWordPosition, horizonEndWordPosition):
    candidates = []
    words = 0
    chars = 0
    startAtomIndex = atomIndexByWordPosition[startWordPosition]
    previousWordAtomIndex = NONE

    for endWordPosition in startWordPosition until horizonEndWordPosition:
        endAtomIndex = atomIndexByWordPosition[endWordPosition]
        atom = atoms[endAtomIndex]
        if atom is structural break:
            break
        if crossesStructuralOrContextBarrier(startAtomIndex, endAtomIndex):
            break
        if endWordPosition > startWordPosition and
           punctuationOwnershipBlocksJoin(previousWordAtomIndex, endAtomIndex):
            break

        words += atom.wordCount
        chars += atom.wordCharacterCount
        if words > config.maxWordsPerUnit:
            break
        if words > 1 and chars > config.maxCharsPerUnit:
            break

        candidate = buildCandidate(
            startAtom = startAtomIndex,
            visibleEndAtomExclusive = endAtomIndex + 1,
            startWordPosition = startWordPosition,
            endWordPositionExclusive = endWordPosition + 1,
        ).withContextOnlyAdvance()
        if coordinatesAreValid(candidate) and subwordsAreValid(candidate):
            candidates += score(candidate)

        if not config.enablePhraseChunking:
            break

        previousWordAtomIndex = endAtomIndex

    require candidates contains a safe single-word fallback
    return candidates
```

Initial invalid-frame rules must preserve current behaviour for:

- opening and closing quotes;
- nested brackets and parentheses;
- currency prefixes and signed numbers;
- decimals and thousands separators;
- abbreviations and ellipses;
- em/en dashes and paired asides;
- trailing sentence punctuation;
- split long words and repeated hyphen fragments;
- paragraph and page break frames;
- CJK paired punctuation.

Candidate creation must carry expanded start/end cursors forward. Frame coordinates are
derived from those cursors exactly as `appendReadingFrame` does today, not reconstructed from
word counts.

`maxWordsPerUnit` is persisted without a codec/normalisation upper bound even though the
current settings UI clamps normal edits to two or three words. Scored segmentation must not
silently cap an older/imported custom value above three. The first implementation should:

- use six-word DP for supported values `1..3`;
- route `maxWordsPerUnit > 3` to `LegacyGreedySegmenter`, preserving current output;
- record a reason-coded debug fallback and add codec/strategy tests for the case;
- audit real persisted values in Phase 0 before considering an explicit normalisation and
  backward-compatible profile migration in a later, separately reviewed change.

After DP selects a candidate, frame construction uses only its visible atom/cursor end for
`tokens`, display ranges, and `nextOriginalTokenIndex`. It then performs one deterministic
post-segment advance over immediately adjacent context-only atoms, mirroring
`consumeContextPunctuation`: replay those punctuation tokens into sequential context state,
set `nextSelectionAtom`/`nextSelectionExpandedCursor`, and begin the next candidate there.
Context-only atoms are never scored as visible words, are never added to the prior frame's
display range, and cannot be revisited by the next candidate. A focused invariant must prove
each such atom is advanced exactly once and that `nextSelectionWordPosition` is the first
word-bearing position at or after `nextSelectionAtom`.

The configuration gates are behavioural contracts, not merely score weights:

- `enablePhraseChunking = false` permits only one-word candidates (plus their owned
  punctuation), regardless of affinity or load;
- `useDialogueDetection = false` removes dialogue-role affinity and speaker-tag shaping;
- `useParentheticalAside = false` removes aside-role grouping/timing features while retaining
  punctuation legality and ordinary parenthetical handling;
- `useProsodyPacing = false` removes prosody-derived affinity/phrase-shape contributions;
- focal stress and anticipatory landing continue to respect their existing independent flags.

Add focused tests for every gate so a zeroed score cannot accidentally differ from the
feature being structurally disabled.

## 11. Dynamic-programming segmentation algorithm

### 11.1 Window choice

Use a rolling horizon of six upcoming word atoms. Current profiles commonly cap units at two
words and tests exercise three-word units. Six words therefore lets DP compare at least two
complete maximum-width frames, including alternatives such as `3+3`, `2+2+2`, and
`1+2+3`, without scanning an entire chapter.

Punctuation atoms do not consume the word budget. A sentence, paragraph, page, or other hard
barrier may end the window early.

### 11.2 Recurrence

Within one window, number word-bearing positions from `0` to `m`. Resolve each position through
`atomIndexByWordPosition` when generating a candidate; candidate end positions map back through
`wordPositionByAtomIndex`. Structural and context-only atoms live between word positions and
terminate or constrain candidate spans but never disappear from cursor accounting. A DP edge
lands at the next word position after `nextSelectionAtom`, while visible frame metadata stops at
`visibleEndAtomExclusive`.

The right edge of a window is classified explicitly. A chapter/region end or observed hard
barrier is real; exhaustion of six words while more readable input exists is an artificial
horizon. Artificial exhaustion is not a punctuation boundary or phrase completion. Compute the
best path to the window end with a neutral terminal value:

```text
best[m] = 0

for position from m - 1 down to 0:
    best[position] = max over candidate in candidates(position):
        candidate.observableContentScore
      + rightEdgeScore(candidate)
      + best[candidate.nextSelectionWordPosition]

rightEdgeScore(candidate):
    if candidate.rightEdgeKind == ARTIFICIAL_HORIZON:
        return 0
    return cutScore(candidate.observedEndBoundary)
```

All paths consume the same window, avoiding a built-in reward for processing more words.
Candidate content score contains only features observable inside the window, including load and
visual balance. At an artificial horizon, suppress right-orphan, natural-completion, and other
edge components that require an unseen successor. At a real observed gap, `cutScore` rewards a
strong natural cut and penalises splitting a high-affinity pair. `best[m] = 0` is fixed, not a
tunable terminal bonus. There is no mutable timing state in the recurrence.

Commit only the first segment of the best path, advance to its end, create a fresh six-word
window, and repeat. Structural break atoms bypass DP and produce existing break frames.

Focused tests must distinguish artificial horizon exhaustion from source end and hard barriers,
and prove that no sentence-end/cut reward appears merely because the sixth word was reached.

### 11.3 Score composition

```text
segmentScore =
    + internal pair affinity
    + function-word cohesion
    + entity integrity
    + dialogue-role continuity
    + natural phrase completion
    + visual balance

    - frame-load excess
    - boundary strength crossed
    - difficult-word collision
    - semantic-anchor collision
    - awkward punctuation ownership
    - orphan function word at either edge
```

Score ownership is explicit:

| Component | Scope |
| --- | --- |
| difficulty, semantic weight, visual load | token/lexeme |
| affinity, entity continuity | adjacent pair |
| strength and cut reward | boundary/gap |
| total load, balance, collision, completion | whole candidate |
| cut at candidate end | transition between frames |

Do not add a generic reward per frame: it would bias path length. Any preference for fewer
visual changes must be a named, bounded component covered by tests.

### 11.4 Deterministic tie-breaking

Paths are compared lexicographically:

1. larger fixed-point total score;
2. fewer hard-rule compatibility deviations (zero during initial rollout);
3. lower maximum frame-load excess in the window;
4. fewer cuts through positive affinity;
5. earlier first cut, giving the conservative shorter frame;
6. smaller end expanded cursor.

No iteration order of `HashMap` or `Set` may participate. Candidate lists and reason-code
rendering use stable enum/index order.

### 11.5 Complexity

Annotation is `O(n)`. With horizon `H = 6` and at most `k` legal widths from each position,
rolling segmentation is `O(n * H * k)` time and `O(H * k)` working memory. Since `H` is a
compile-time bounded constant, this is effectively `O(n * k)` with small constants.

## 12. Named-entity strategy

Add a lightweight immutable pass over word atoms. It assigns candidate entity spans using:

- two or more adjacent title-case words;
- known titles followed by a capitalised name;
- all-cap acronym patterns;
- capitalised words joined by a small connector set such as `of`, `the`, `and`, `de`, `van`,
  or `von` when both sides provide entity evidence;
- a conservative maximum span length;
- sentence-start disambiguation that requires supporting evidence beyond initial capitalisation.

Examples include `New York City`, `United Kingdom`, `World Health Organization`, and
`Arthur Conan Doyle`.

Entity membership creates strong internal affinity and a penalty for orphaning a connector,
but it never overrides:

- structural or hard punctuation boundaries;
- `maxWordsPerUnit` or `maxCharsPerUnit`;
- subword correctness;
- an excessive cognitive-load penalty.

When a long entity must split, candidate scoring should prefer balanced, non-orphaning cuts.
Scripts without case receive no capitalisation bonus; acronym and punctuation evidence may
still apply. No dictionary, NER library, ML model, or network service is introduced.

## 13. Dialogue and prosody strategy

Introduce an immutable role per word atom:

```kotlin
internal enum class DialogueRole {
    NARRATION,
    DIALOGUE_CONTENT,
    SPEAKER_TAG,
    PARENTHETICAL_ASIDE,
}
```

Roles are derived in one forward analysis pass from:

- `Token.isDialogue` and quote transitions;
- parenthetical and paired-em-dash context;
- `DialogueAnalyzer.isSpeakerVerb` and `isSpeakerTag` helpers;
- punctuation around attribution clauses.

English speaker-verb/tag helpers run only under `ENGLISH`; other policies may use universal
quote and punctuation structure but receive no English lexical attribution bonus.

The pass must not call a mutable global dialogue tracker while generating candidates.
When `useDialogueDetection` is false, emit neutral `NARRATION` roles for scoring and preserve
the current disabled dialogue-timing behaviour. When `useParentheticalAside` is false, paired
dashes still participate in punctuation/boundary legality, but `PARENTHETICAL_ASIDE` must not
change grouping or timing. Prosody-derived grouping effects are likewise gated by
`useProsodyPacing`.

Segmentation effects should be restrained:

- dialogue content gains cohesion within a spoken phrase;
- speaker tags may group compactly and retain current timing compression;
- the transition between speech and attribution receives a moderate boundary;
- a resumed quotation receives a restart contour;
- parenthetical asides preserve current paired-dash and multiplier behaviour.

Existing `speakerTagMultiplier`, dialogue entry/exit holds, punctuation scaling, focal stress,
phrase contours, givenness, and semantic-anchor timing stay in the sequential timing stage.
Role annotations replace repeated discovery only after parity tests demonstrate equivalence.

## 14. Timing smoothing and sentence rhythm

Kairo already performs temporal smoothing correctly in an important respect:
`RhythmState.apply` smooths and rate-limits word duration before punctuation pauses are added,
and hard boundaries reset its EMA. `FlowState` separately smooths difficulty changes.

The first scored phase deliberately preserves the current frame-level annotation contract.
`appendReadingFrame` takes `focalSuppression`, `anticipatoryLanding`, `emDashAside`, and
`phraseContour` from the selected segment's first readable `wordCursor`, and
`RsvpUnitTimingInput` applies them across that frame. Scored boundaries may place an annotated
word second or third, but Phase 4 must not invent a new aggregation rule: the first readable
word remains authoritative, while `pairedEmDashInUnit` continues to inspect the full selected
range. Add explicit two- and three-word tests where focal, landing, aside, and contour annotations
occur at each position so any duration change is understood as a segmentation consequence.
Per-word or strongest-signal aggregation is a later timing change, not part of segmentation
default-on.

The refactor should therefore extract or extend `RhythmState`, not layer a second generic EMA
over final frame duration. A future `RsvpTemporalSmoother` may accept graded boundary
strength and use:

- asymmetric maximum acceleration and deceleration;
- a weak-boundary partial reset;
- a full sentence/structural reset;
- unchanged punctuation and explicit structural pause values;
- bounded multipliers so focal stress and difficult-word floors remain visible.

Sentence rhythm should build on current behaviour:

- `startBoostMultiplier` and boundary micro-holds for a clear onset;
- existing phrase contours and anticipatory landing near endings;
- `sentenceWrapUpFactor` for the landing;
- restrained glide through easy, low-information middles;
- restart shaping after clause and sentence boundaries.

Any new rhythm multiplier should initially remain within a narrow range and be approximately
duration-neutral across a phrase or sentence. Punctuation and readability floors may make
the effective pace lower than nominal WPM, as they do today, but smoothing must not create an
unexplained systematic speed shift. `RsvpEstimatedReadingPace` tests should cover this.

## 15. Multilingual considerations

`TokenizerRegistry` already separates default, CJK, and RTL tokenization. The scored stage
must consume their common `Token` output without assuming English metadata is equally
meaningful.

Derive `RsvpLanguagePolicy` once from the explicit normalized book language tag, using the same
language-family mapping as `TokenizerRegistry`, and pass it with every generation request.
Extract one shared tag-classification helper rather than duplicating `startsWith` lists between
tokenizer and RSVP policy resolution. Do
not infer English from a dominant Latin script: the default tokenizer currently supplies
English `WordAnalyzer` metadata for French, German, and other Latin-script text too, so those
fields can look valid while carrying the wrong semantics. A missing/invalid tag maps to
`UNKNOWN`, never `ENGLISH`.

### English policy

For an explicit English primary tag, enable the complete heuristic set: English
clause/coherence lists, function words, case-based entities, syllables, and frequency.

### Default non-English policy

For explicit non-English languages handled by the default tokenizer, retain universal visual,
length, punctuation, numeric, and structural signals. Disable English lexical lists,
English-derived frequency/syllable load, and English connector/title/entity bonuses until a
language-specific policy and fixtures exist. Case may contribute only a weak generic shape
signal where the token's script supports it, not an English named-entity claim.

### CJK policy

- retain `CjkTokenizer` segmentation and paired punctuation;
- treat punctuation and structural boundaries as the strongest signals;
- use visual character count and tokenizer units for load;
- disable English function-word, syllable, frequency, and capitalisation bonuses when their
  defaults carry no information;
- keep `LEGACY_GREEDY` in production initially, preserving the current profile-dependent CJK
  grouping in release builds, while debug V2 output remains an evaluation path until
  script-specific goldens and device rendering checks justify production eligibility.

### RTL policy

- retain `RtlTokenizer` source order and existing display behaviour;
- preserve Arabic/Hebrew punctuation and combining-mark handling;
- use neutral load defaults where frequency/syllable metadata is synthetic;
- do not apply Latin capitalisation/entity rules;
- verify that segmentation changes never reverse or reshape token order;
- keep `LEGACY_GREEDY` in release builds until RTL scored goldens and BiDi device checks pass.

Mixed-script content uses the book policy plus per-token feature confidence: unsupported
signals contribute zero, not a negative score. Structural and coordinate invariants remain
universal. Script detection may suppress an incompatible token-level feature, but it must not
promote an `UNKNOWN` or generic book to English.

`RsvpBookContext`, `RsvpFrameRepository` preview/full/prefetch calls, `RsvpEngine`, and the
frame cache must propagate the resolved book policy. Cache keys include the strategy version and
language policy; include the normalized tag too if any policy later varies by locale.

Pace estimation separates *sample policy* from *target/session strategy*. The current synthetic
passage is English, so it always uses `sampleLanguagePolicy = ENGLISH`; it uses the strategy
resolved for the target session. Thus English scored playback estimates with
`ENGLISH + SCORED_DP_V2` for an English-scored target. A non-English V2 debug session still uses
`ENGLISH + LEGACY_GREEDY` for this synthetic estimate; chapter-preview pace may use its already
generated V2 frames. Cache keys contain the effective inputs, not a French/CJK/RTL policy applied
to English sample text. Before non-English scoring becomes a release path, add representative
local samples or retain this explicit legacy/fallback estimate.

No scored policy is release-eligible in the current implementation. Debug builds can select
`SCORED_DP_V2` for explicit `ENGLISH`, `DEFAULT_NON_ENGLISH`, `CJK`, and `RTL` policies so their
goldens and runtime behaviour can be evaluated. `UNKNOWN` always receives `LEGACY_GREEDY`.

## 16. Compatibility and migration risks

### Required invariants

| Contract | Required invariant | Primary coverage |
| --- | --- | --- |
| token conservation | selected segments account exactly once for every expanded token through the last readable word | property tests |
| context-only punctuation | post-segment advance replays it once without extending visible frame ranges | atom/cursor property tests |
| order | visible token order equals source expanded order | golden/property tests |
| resume cursor | each frame starts at its first represented expanded cursor | resume tests |
| source range | display ranges and character offsets cover exactly the visible source text | resume/session tests |
| progress | `nextOriginalTokenIndex` is monotonic and represents completed source progress | playback/session tests |
| breaks | non-terminal paragraph/page tokens remain separate blank pause frames; terminal breaks remain omitted | structural tests |
| alignment | token and resume restoration resolve to a readable frame | frame alignment tests |
| Bionic | active ranges and chunk boundaries remain valid for scored frames | Bionic tests |
| preview | shifted source indexes are valid and preview resume cursor remains `-1` | repository tests |
| cache | segmentation-affecting identity cannot reuse incompatible frames | cache-key tests |
| language | explicit policy gates language-specific features and is identical across every generation path | multilingual/repository tests |
| tempo | live tempo scaling never changes scored boundaries | strategy/tempo invariance tests |
| profiles | existing stored and custom profile values retain defaults and meaning | codec/default tests |
| feature toggles | disabled phrase, dialogue, aside, prosody, focal and landing features remain disabled | focused config-gate tests |
| determinism | identical tokens/config/strategy produce identical frames and explanations | repeat tests |

### Specific risks

**Split words.** Several frames can share an original token index. The final split fragment is
the point at which `ReadingSessionFrameWords` counts the source word complete. DP must use
expanded cursors and source-character offsets.

**Punctuation ownership.** Moving a quote from one frame to another can change visible ranges,
pause application, dialogue transitions, and resume alignment. Extract and parity-test atom
ownership before scored grouping. Its request-local state starts empty at the playback cursor,
exactly like today's `buildUnit`; prefix-aware linguistic context cannot classify display
ownership.

**Stateful timing.** `ProseState`, `RhythmState`, and `FlowState` mutate in reading order.
Candidate scoring must not mutate or clone them. Run timing once after selection.

**Analysis-prefix context.** Current generation expands bounded lookback but creates an empty
`ContextState` at the first playback cursor. Linguistic annotation may inspect the prefix, and
`createProseState` intentionally reconstructs sentence position/adaptive givenness from it;
unit timing must not additionally inherit prefix quote/parenthesis state during parity phases.
Compare exact durations for starts inside dialogue, parentheses, paired-dash asides, and
mid-sentence repeated words.

**Frame-level timing annotations.** Current focal, landing, aside, and contour values come from
the first readable word and apply across its frame. Preserve that rule initially and test
annotations at positions two and three; do not silently aggregate per-word signals as part of
the segmentation refactor.

**Start-position differences.** Quantized base generation can start before the requested token;
the repository already falls back to exact generation when the first derived frame starts too
early. Add tests where the six-word horizon straddles requested starts.

**Preview truncation.** Keep the requested preview boundary as a visible-output boundary, but
analyse a bounded tail containing at least `H - 1` additional readable atoms (plus owned
punctuation up to a hard barrier/source end). Emit only frames whose visible source range ends
at or before the requested boundary; discard a selected frame that crosses it. Frames whose
six-word window is fully present then use the same decisions as full playback. Without that
tail, as many as the final `H - 1` readable words---potentially several frames---can differ, so
the plan must never claim only one frame is affected. Preview resume cursors remain `-1` and
preview output is never a persistence oracle.

**Cache identity.** `frameTimingKey` currently removes visual-only fields. A persisted
segmentation mode, load setting, or weight version would need to remain in frame identity.
For an internal constant strategy, changing the implementation naturally restarts the app's
in-memory cache; a runtime-selectable request needs its resolved strategy/version and language
policy in `RsvpFrameRepositoryImpl.CacheKey`. The same request must reach quantized base,
exact-start fallback, prefetch, and preview. Pace estimation receives the same resolved strategy
but keys the effective policy of its chosen sample, not blindly the target book policy.

**Asynchronous language resolution.** Nullable is not the same as unresolved. Store a resolved
nullable tag in `RsvpLaunchSnapshotStore`, add explicit language readiness to `RsvpRouteData`,
and publish non-snapshot tokens as frame-ready only after the concurrent tag lookup completes.
Freeze the resulting policy for the book/session. No preview, full load, or autoplay begins in
the unresolved state, so `null -> en` cannot replace segmentation under the active frame index;
a resolved null starts once with `UNKNOWN`.

**Profile persistence.** Avoid new `RsvpConfig` fields initially. If later added, update
normalisation, `RsvpConfigPreferenceCodec`, `RsvpProfileJsonCodec`, defaults, custom profiles,
and cache-key tests with backward-compatible defaults.

**Out-of-range phrase widths.** The UI exposes two or three words, but persisted/custom configs
are not upper-bounded by `normalizedForPlayback`. Values above three use the legacy segmenter
until an explicit migration decision is made; they are never silently truncated to the
six-word scored horizon.

**Behavioural consumers.** More multi-word frames change ORP layout, context assist, effective
pace, and Bionic chunking even when all indexes remain correct. Their existing tests are part
of the rollout gate.

**Concurrency.** Full generation uses a serial engine dispatcher, while previews can call the
same injected engine on a separate dispatcher. DP arrays, cursors, scratch buffers, and
explanation collectors must be invocation-local. Only immutable weights and feature tables may
be shared. Run parallel full/preview generation repeatedly and assert deterministic independent
results.

## 17. Testing strategy

### 17.1 New focused unit tests

Add tests for:

- `RsvpAtomizer`: request-local empty ownership state, prefix-independent straight-quote
  ownership, structural barriers, coordinates, and immutable linguistic context;
- `BoundaryStrengthAnalyzer`: ordering, hard flags, reason codes, punctuation mapping;
- `PhraseAffinityScorer`: each migrated rule and combined breakdowns;
- `CognitiveLoadEstimator`: easy phrases, difficult pairs, numbers, acronyms, subwords;
- `NamedEntityAnalyzer`: names, connectors, sentence starts, load-forced splits;
- `DialogueRoleAnalyzer`: speech, attribution, resumed speech, narration, asides;
- `RsvpCandidateGenerator`: limits, barriers, fallback, punctuation and subword legality;
- `RsvpSegmentScorer`: component ownership and no frame-count bias;
- `RsvpDpSegmenter`: optimal path within each receding six-word window, neutral artificial
  horizon, observed terminal boundaries, truncation, and deterministic tie-breaking;
- temporal smoothing: rate limits, partial/full resets, punctuation exclusion.

### 17.2 Golden segmentation tests

Create `RsvpSegmentationGoldenTest` with table-driven fixtures. Store token input, config,
expected bracketed frames, and optionally selected reason codes. Categories must include:

- simple prose and function-word chains;
- difficult academic prose;
- subordinate clauses and conjunctions;
- commas, semicolons, colons, ellipses, and em-dash asides;
- dialogue, nested quotations, and speaker tags;
- proper names, organisations, titles, acronyms, and numbers;
- hyphenated and split long words;
- paragraph and page boundaries;
- CJK paired punctuation and mixed Latin/CJK;
- Arabic/Hebrew punctuation and combining marks.

Illustrative target, to be ratified by the scorer rules rather than copied blindly:

```text
Input:    He stepped into the room where she waited.
Expected: [He stepped] [into the room] [where she waited.]
```

Every intentional golden change requires a short rationale in the fixture review. Avoid
rewriting goldens merely to accept whatever the current implementation emits.

### 17.3 Differential and regression tests

Build a test-only comparison harness that runs greedy and scored strategies over the same
fixtures and reports:

- frame texts and boundaries;
- coordinate differences;
- duration differences;
- score breakdown for the first divergence.

Existing suites remain gates, particularly:

- `ComprehensionRsvpChunkingTest`;
- `ComprehensionRsvpResumeCursorTest`;
- `ComprehensionRsvpPunctuationTest`;
- `ComprehensionRsvpStructuralPauseTest`;
- `ComprehensionRsvpContourTest` and `ComprehensionRsvpProsodyTest`;
- `ComprehensionRsvpEngineTest`;
- `RsvpFrameRepositoryImplTest`;
- `RsvpFrameAlignmentTest` and `RsvpPlaybackStateTest`;
- `ReadingSessionFrameWordsTest`;
- `BionicReadingTextTest`;
- tokenizer and `RsvpConfigResolver` tests;
- preference codec and profile default tests.

Exact legacy grouping assertions may change only when a reviewed golden explicitly records the
new desired grouping. Timing, punctuation, cursor, and structural assertions are not weakened.

### 17.4 Property/invariant tests

Generate deterministic token streams and assert:

- concatenating visible and context-only segment atom ranges reconstructs the expanded stream
  through the last readable word, while terminal structural tokens follow existing omission
  behaviour;
- cursors and original indexes are monotonic and in bounds;
- no hard boundary or structural atom is crossed;
- every readable position has a candidate;
- all selected frames obey `maxWordsPerUnit`; multi-word extensions obey `maxCharsPerUnit`,
  with only the documented mandatory first-word/subword fallback allowed to exceed it;
- repeated runs produce byte-for-byte equivalent frame metadata;
- explanation on/off does not alter selection;
- arbitrary preview offsets remain valid and stable frames match full playback after bounded
  lookahead/cropping;
- changing only live/base tempo leaves scored frame boundaries byte-for-byte unchanged;
- explicit English and French/German policies on the same Latin script do not share English-only
  scores, while missing language uses conservative `UNKNOWN`;
- with tokens-first and tag-first completion orders, unresolved language starts no preview/full
  work; `null -> en` resolves once to English before autoplay, while a resolved null starts once
  with frozen `UNKNOWN`;
- pace estimation pairs the English sample with `SCORED_DP_V2` for an English-scored target and
  explicitly uses `LEGACY_GREEDY` for non-English target policies until matched samples exist;
- parallel full and preview calls on one engine instance do not share scratch or explanations.

Include explicit fixtures for an over-character-limit first word, a terminal paragraph/page
break, context-only punctuation cursor accounting, and a persisted `maxWordsPerUnit` above
three falling back to greedy behaviour. Add resume fixtures whose analysis lookback starts inside
straight quotes/dialogue/parentheses: ownership and unit `ContextState` must still start empty
while `ProseState` preserves current prefix reconstruction. Compare frame text, ranges, cursors,
and durations. Also cover two- and three-word frames with
focal/landing/contour annotations away from the first word.

### 17.5 Runtime/manual checks before default-on

On a device, verify start, pause, backtrack, resume, chapter transition, rotation/background,
search/TOC launch, and return-to-reader alignment. Repeat in RSVP and Bionic modes with a split
long word, quotation, and paragraph boundary. Before making CJK or RTL scored-eligible, also run
both modes with CJK paired punctuation and with Arabic/Hebrew mixed numbers, punctuation, and
combining marks; source-order JVM assertions do not establish Compose BiDi/ORP rendering.
These checks are not replaceable by JVM tests.

## 18. Performance considerations

Phase 0 must record current engine baselines before fixing final budgets. Measure separately:

- token expansion;
- annotation;
- candidate generation and DP;
- timing/prosody;
- full frame generation;
- allocations per expanded token.

Use 1k, 10k, and 100k-token synthetic and representative chapters, warm and cold runs, easy
and punctuation-heavy text, and default/CJK/RTL token streams. Use stable JVM benchmarks for
regression and an Android benchmark only if the project already has or can justify that
infrastructure; do not add a large dependency just for the plan.

Provisional acceptance budgets, to be ratified after baselining:

- annotation plus segmentation p95 no more than 10% of existing total frame-generation time
  on the CI reference host;
- no worse than 25 ms per 10k expanded tokens on the chosen representative older Android
  device for segmentation alone;
- linear scaling from 1k to 100k tokens with no chapter-size-dependent horizon growth;
- bounded temporary memory, targeting no more than 192 bytes per expanded token for retained
  annotations and constant DP working storage;
- no ANR-sensitive work on the main dispatcher; retain the repository's engine dispatcher.

If measured baselines make an absolute number unrealistic, document and ratify a replacement
before implementation proceeds. Relative regression budgets remain mandatory.

Avoid per-candidate lists and strings in release execution. Precompute prefix sums for frame
load/character counts and reuse small arrays for the six-word DP window, but allocate or obtain
all mutable scratch per invocation. Full and preview calls can overlap on the same engine
instance. Detailed components and collectors are likewise invocation-scoped; only immutable
weights/tables may be shared.

## 19. Debugging and observability

Provide a deterministic test/debug explanation API:

```text
Segment cursors: 42..47
Affinity/coherence        +1420
Function-word cohesion     +280
Entity integrity             +0
Boundary crossed              +0
Cognitive-load excess       -310
Cut reward                  +190
--------------------------------
Path contribution          +1580
Reasons: ARTICLE_CONTENT, PHRASE_END_CUT
```

Recommended components:

- `ScoreComponent(code, amount)` with enum reason codes;
- `SegmentationDecision(windowStart, candidates, selectedCandidate)`;
- a stable renderer used by assertion failures;
- an optional debug-build collector injected into the segmenter;
- a no-op collector in release builds.

Do not log raw book text by default. Cursor ranges, numeric components, token classes, and
reason codes are sufficient for routine diagnostics. A developer explicitly inspecting a
local fixture can ask the renderer for text in a test process. There is no network telemetry.

## 20. Phased implementation plan

Each phase is independently reviewable and retains a rollback point.

Current checkpoint: the unshipped branch combines bounded parts of Phases 2, 3, 4, 6, and 7
behind `SCORED_DP_V2`. Its atom stream is deliberately one-to-one with expanded tokens rather
than the full punctuation-ownership representation described below; this keeps the legacy
builder and all punctuation/range contracts authoritative. Entity/dialogue and multilingual
policies have JVM goldens, and graded rhythm extends the existing smoother. Release rollout,
soft-punctuation crossing, the full performance baseline, and CJK/RTL device evidence are not
complete, so the remaining phase descriptions continue to define the production gate.

### Phase 0: Baseline and freeze invariants

Work:

- capture current greedy goldens and performance baselines;
- add token-conservation, cursor/range, cache-start, preview, and deterministic invariants;
- ratify the explicit language-policy/strategy request, preview lookahead/crop contract, and
  conservative unknown-language behaviour;
- ratify request-local punctuation ownership, route language-readiness/session-freeze, and
  pace sample-versus-target strategy contracts;
- document which current groupings are desired versus merely legacy.

Exit criteria:

- baseline report is checked in or attached to implementation review;
- compatibility tests pass unchanged;
- provisional performance budgets are ratified.

Rollback: tests/documents only; no production behaviour.

### Phase 1: Structured scoring behind legacy behaviour

Work:

- decompose `isPhraseChunkCandidate` into named hard constraints and affinity features;
- add fixed-point score types, reason codes, and versioned weights;
- assert that a compatibility adapter reproduces the Boolean result.

Exit criteria:

- existing chunking tests pass;
- every legacy decision has a reason-code explanation;
- no generated frame changes.

Rollback: retain the original function until parity is established.

### Phase 2: Immutable atom and annotation layer

Work:

- extract punctuation ownership from mutable `UnitCursor`;
- create a request-local ownership state seeded empty at the playback cursor; keep it independent
  of prefix-aware linguistic context and the later live timing `ContextState`;
- create `RsvpAtom`, immutable linguistic context snapshots, boundaries, and lexeme features;
- keep unit `ContextState` empty at the first playback cursor, preserve `createProseState`
  lookback reconstruction, and keep both distinct from linguistic annotations;
- adapt the greedy path to consume atoms while emitting identical frames.

Exit criteria:

- greedy frame text, coordinates, and durations match baseline fixtures;
- exact-start straight-quote/parenthesis ownership, currency, decimal, abbreviation, CJK
  punctuation, subword, break, `ContextState`, and `ProseState` parity tests pass.

Rollback: switch legacy builder back to direct expanded tokens.

### Phase 3: Candidate generation and DP in tests

Work:

- add cognitive-load estimates, legal candidate generation, scorer, and six-word DP;
- add neutral artificial-horizon handling, deterministic tie-breaking, and structured
  explanations;
- keep all mutable DP scratch and explanation collection invocation-local;
- keep it test-only or shadow-only.

Exit criteria:

- focused unit/property/golden tests pass;
- complexity and allocation measurements meet ratified budgets;
- DP never lacks a safe candidate.

Rollback: scored code is not on the production path.

### Phase 4: Explicit generation request, default greedy

Work:

- add non-persisted `RsvpGenerationOptions(languagePolicy, segmentationStrategy)` and propagate
  it through `RsvpScreen`, full/preview/prefetch repository APIs, the engine, exact-start
  fallback, and frame cache;
- add `RsvpPaceEstimationOptions(sampleLanguagePolicy, segmentationStrategy)` so pace uses the
  target strategy without misclassifying its sample text;
- extend `RsvpLaunchSnapshotStore` with a resolved nullable language tag, add readiness to
  `RsvpRouteData`, defer frame loading/autoplay until ready, then freeze the policy for the
  book/session; a resolved null uses `UNKNOWN`;
- include the frozen options in `RsvpScreen` load-effect identity; profile/rollout strategy
  changes may intentionally reload, but asynchronous language lookup never swaps an active
  segmentation;
- make preview analyse `H - 1` readable atoms of bounded lookahead and crop to stable frames;
- resolve `DEFAULT_NON_ENGLISH`, `CJK`, `RTL`, and `UNKNOWN` to `LEGACY_GREEDY` for the first
  production rollout while retaining scored shadow coverage;
- select `LEGACY_GREEDY` when `maxWordsPerUnit > 3` unless a later explicit migration has normalised
  that persisted value;
- build frames from selected segments, then run existing timing sequentially with the first
  readable word retaining the current focal/landing/aside/contour contract;
- add resolved strategy version and book policy to frame-cache identity; add sample policy and
  target strategy/version to estimated-pace identity;
- run differential fixtures in CI/debug tests.

Exit criteria:

- scored strategy passes all coordinate, stable-preview, repository, concurrency, session,
  Bionic, exact-start ownership, language-readiness, timing-annotation, and pace integration
  tests;
- greedy remains production default and immediate rollback.

Rollback: select `LEGACY_GREEDY` without data migration.

### Phase 5: Cognitive-load tuning and golden review

Work:

- calibrate load and score weights from reviewed fixture categories;
- validate easy phrase grouping and difficult phrase splitting across profiles;
- prove segmentation is identical across base/live tempos while scaled durations and estimated
  pace remain credible;

Exit criteria:

- no unexplained golden churn;
- no profile violates word/character or performance constraints;
- timing regressions remain within reviewed bounds.

Rollback: use Phase 4 compatibility weights or greedy strategy.

### Phase 6: Named entities and dialogue roles

Work:

- enable entity-span and immutable dialogue-role features separately;
- reuse speaker-tag and paired-aside timing after segmentation;
- enforce English, default-non-English, CJK, RTL, and unknown policy gates with neutral
  fallbacks;
- add a representative local pace sample or an explicit legacy/fallback estimate before each
  non-English policy becomes scored-eligible.

Exit criteria:

- dedicated entity/dialogue goldens pass;
- load can override entity grouping safely;
- default-non-English, CJK, and RTL baselines do not regress, and each policy becomes
  scored-eligible
  only after its own goldens and device evidence pass.

Rollback: set each feature weight to zero or disable its analysis pass.

### Phase 7: Boundary-driven rhythm refinement

Work:

- feed graded boundary strength into existing contours and an extracted temporal smoother;
- preserve punctuation pauses, structural frames, floors, and session ramps;
- constrain sentence-level duration drift.

Exit criteria:

- smoothing, contour, punctuation, effective-pace, and performance tests pass;
- manual playback shows no jarring jumps or flattened landings.

Rollback: retain current `RhythmState` Boolean hard-boundary behaviour.

### Phase 8: Controlled default-on

Work:

- use a non-persisted rollout resolver to map build channel plus the screen's selected profile
  ID/config to `RsvpSegmentationStrategy`; enable internal/debug builds, then selected profiles,
  then all eligible profiles only after evidence review;
- pass only the resolved strategy/version into frame identity; profile ID itself is not a cache
  key and does not alter segmentation once the strategy is resolved;
- perform device QA in RSVP and Bionic;
- monitor crash/performance evidence without collecting reading text.

Exit criteria:

- all automated gates pass on the exact candidate revision;
- device QA is recorded;
- rollback strategy is exercised once.

Rollback: make the resolver return `LEGACY_GREEDY`; no preference or database migration.

### Phase 9: Remove obsolete greedy code

Work:

- wait at least one stable release after default-on;
- remove only rules proven represented by constraints/features;
- keep shared punctuation, coordinate, and timing utilities.

Exit criteria:

- no supported path or test depends on the legacy segmenter;
- final performance and compatibility suites pass.

Rollback: defer removal. There is no value in deleting the oracle early.

## 21. Files and modules likely to change

Likely existing files:

- `core/rsvp/RsvpEngine.kt`: orchestration and selected-segment to frame conversion;
- `core/rsvp/RsvpPaceEstimator.kt` and `RsvpEstimatedReadingPace.kt`: explicit strategy/language
  separation for the hardcoded English sample and displayed estimates;
- `core/tokenization/TokenizerRegistry.kt`: consume a shared normalized language-family
  classifier also used by RSVP policy resolution;
- `core/rsvp/engine/RsvpUnitBuilder.kt`: extract request-local ownership rules/state and retain
  the legacy adapter;
- `core/rsvp/engine/RsvpEngineTypes.kt`: move or narrow shared state/types;
- `core/rsvp/analysis/RsvpWordPacing.kt`: decompose Boolean heuristics and share load inputs;
- `core/rsvp/analysis/RsvpPhraseAnalysis.kt`: integrate existing contours with annotations;
- `core/rsvp/text/RsvpTextRules.kt`: expose punctuation/boundary evidence without duplication;
- `core/rsvp/timing/RsvpUnitTiming.kt`: accept selected segment context, not candidate state;
- `core/rsvp/engine/RsvpFramePostProcessor.kt`: only if smoother extraction changes ownership;
- `core/rsvp/engine/RsvpConfigNormalization.kt`: cache identity if config fields are later added;
- `data/rsvp/RsvpFrameRepository.kt` and `RsvpFrameRepositoryImpl.kt`: generation options,
  bounded preview lookahead/crop, and strategy/language cache identity;
- `ui/rsvp/RsvpScreen.kt` and `RsvpScreenModels.kt`: pass language policy and resolved rollout
  strategy consistently to preview and full loads, gated by language readiness;
- `ui/navigation/RsvpLaunchSnapshotStore.kt`, `RsvpRouteData.kt`, `RsvpRoute.kt`, and
  `ReaderRouteCallbacks.kt`: snapshot the resolved nullable tag, await readiness when absent,
  freeze session policy, and carry it into `RsvpBookContext`;
- `core/model/RsvpFrame.kt`: expected to remain unchanged initially;
- `core/model/Models.kt`: expected to remain unchanged initially.

Suggested new files (`RsvpGenerationOptions` is public; implementation classes are internal):

```text
core/language/LanguageFamily.kt
core/rsvp/RsvpGenerationOptions.kt
core/rsvp/analysis/RsvpAnnotationTypes.kt
core/rsvp/analysis/RsvpAtomizer.kt
core/rsvp/analysis/RsvpBoundaryStrengthAnalyzer.kt
core/rsvp/analysis/RsvpPhraseAffinityScorer.kt
core/rsvp/analysis/RsvpCognitiveLoadEstimator.kt
core/rsvp/analysis/RsvpNamedEntityAnalyzer.kt
core/rsvp/analysis/RsvpDialogueRoleAnalyzer.kt

core/rsvp/segmentation/RsvpSegmentationTypes.kt
core/rsvp/segmentation/RsvpSegmentationWeights.kt
core/rsvp/segmentation/RsvpCandidateGenerator.kt
core/rsvp/segmentation/RsvpSegmentScorer.kt
core/rsvp/segmentation/RsvpDpSegmenter.kt
core/rsvp/segmentation/RsvpSegmentationExplainer.kt
core/rsvp/segmentation/LegacyGreedySegmenter.kt
```

Likely new tests mirror those files under `app/src/test/java/com/kairo/reader/core/rsvp` or
matching subpackages. Existing repository, session, UI, Bionic, tokenizer, and preference
tests should be extended rather than duplicated.

No database schema change, parser change, or new external dependency is expected.

## 22. Open questions and recommended decisions

1. **Rolling six-word DP or full hard-region DP?**
   Use rolling six-word DP first. It directly satisfies the bounded-device requirement and is
   easier to benchmark. Revisit only if goldens demonstrate repeatable horizon failures.

2. **`Double` or fixed-point score?**
   Use integer fixed-point `0..1000` features and integer weights in the selection path.
   Convert existing doubles once at annotation boundaries.

3. **Should boundary strength replace `BoundaryBefore` immediately?**
   No. Keep the enum and punctuation tiers as compatibility classifications, derive continuous
   strength alongside them, and migrate consumers independently.

4. **Should load budget be persisted or user-visible?**
   No for the first release. Derive it from existing limits/profile semantics. Persist only if
   user research later identifies an understandable control.

5. **Should the engine API receive `languageTag`?**
   Yes, indirectly through an explicit immutable `RsvpLanguagePolicy` resolved from the
   normalized tag. Pass it end-to-end in the first scored integration phase. Dominant script may
   suppress token features but must not classify Latin text as English.

6. **Can punctuation be crossed by a multi-word candidate?**
   Not during parity and first default-on. Current multi-word units contain adjacent word tokens
   only. Consider soft internal punctuation only as a separately specified future feature.

7. **How should preview end-of-window differences behave?**
   Analyse a bounded tail containing at least `H - 1` readable atoms, then emit only frames fully
   inside the requested visible boundary. This avoids claiming false one-frame parity: without
   lookahead, the final `H - 1` words can affect several frames. Preview resume cursors remain
   `-1` and preview frames never persist exact position.

8. **How are weights tuned?**
   Through named versioned weights, reviewed golden fixtures, and performance/differential
   reports. Do not tune directly against a single novel or let unexplained golden updates drive
   values.

9. **When can greedy code be removed?**
   Only after a stable release with scored segmentation default-on and no unresolved rollback,
   coordinate, multilingual, performance, or Bionic issues.

10. **Should score explanations include source text?**
    No by default. Use cursors, token classes, reason codes, and numbers. Test fixtures can opt in
    locally.

11. **Should cognitive-load segmentation respond to live tempo?**
    No in the first scored release. Live tempo scales already-generated frame durations, so
    tempo-aware boundaries would require a separately designed regeneration and alignment flow.

12. **How should focal, landing, aside, and contour annotations combine in a multi-word frame?**
    Preserve the current first-readable-word frame-level rule through default-on. Consider
    per-word or strongest-signal aggregation only as a separately reviewed timing change.

13. **May the segmenter reuse mutable scratch across calls?**
    No. Full and preview generation can overlap on one engine instance; mutable arrays, cursors,
    and collectors are invocation-local.

14. **May prefix-aware linguistic context decide punctuation ownership?**
    No. Ownership is request-local and starts with an empty mirror of current `ContextState` at
    the playback cursor. Linguistic lookback cannot reclassify a closing/opening straight quote.

15. **What happens when the language tag resolves after snapshot tokens?**
    Nothing starts while it is unresolved. Carry a resolved nullable tag in launch snapshots;
    otherwise await the lookup, then freeze policy for the session. Never replace active frames
    because `null` later became `en`.

16. **Which language policy does synthetic pace estimation use?**
    The policy matching the sample text, currently `ENGLISH`, combined with the target session's
    resolved segmentation strategy. Cache those effective inputs separately.

## 23. Explicit non-goals

This refactor does not include:

- neural NLP, language models, cloud parsing, or external language services;
- a heavyweight grammar, named-entity, or dictionary dependency;
- rewriting `Tokenizer`, CJK/RTL segmenters, or book import parsers;
- changing EPUB, PDF, TXT, MOBI, FB2, or DOCX parsing;
- changing database content or reading-position persistence formats;
- redesigning RSVP, Bionic, Reader, settings, ORP, or context-assist UI;
- exposing scorer weights, horizon, entity mode, or load budget to users initially;
- changing frame boundaries when live tempo changes or regenerating playback on speed changes;
- swapping language policy or frame segmentation underneath an active reading session;
- crossing soft punctuation inside multi-word frames in the first rollout;
- calculating final stateful timing inside DP;
- replacing the current first-readable-word focal/landing/aside/contour aggregation during the
  segmentation rollout;
- replacing existing punctuation timing, prosody, session ramps, or blink behaviour wholesale;
- logging or transmitting book text;
- removing the greedy path before a proven stable scored release.

The implementation should remain a small, reversible evolution of Kairo's existing RSVP
engine: annotate once, choose boundaries with bounded global context, then reuse the timing and
frame contracts that already work.
