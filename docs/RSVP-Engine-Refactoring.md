You are working in the **Kairo** Android repository.

Your task is to inspect the existing RSVP reading pipeline and create a new Markdown planning document in the repository that lays out a concrete refactor for making RSVP playback feel more natural, readable, and cognitively efficient.

Do **not** implement the refactor yet.

Create a file such as:

`docs/rsvp-segmentation-refactor.md`

The document should explain the current architecture, identify the limitations of the current greedy/local approach, propose a new architecture, define the major data structures and stages, and break the work into safe implementation phases.

## Context

Kairo currently uses a deterministic, rule-based RSVP pipeline built around:

* tokenization
* linguistic word analysis
* punctuation handling
* dialogue tracking
* clause and phrase heuristics
* phrase chunking
* RSVP frame construction
* pacing/timing
* prosodic adjustments

Relevant areas include, but are not limited to:

* `core/tokenization/`
* `core/rsvp/`
* `core/rsvp/analysis/`
* `core/rsvp/engine/`
* `core/rsvp/text/`
* `core/rsvp/timing/`
* `core/linguistics/`
* `core/model/Token*`
* RSVP tests under `src/test`

Inspect the repository before producing the plan. Do not assume the current implementation matches this prompt exactly.

## Main problem to solve

The current RSVP segmentation is largely **local and greedy**.

The engine often decides whether the current word should join the next word based on pairwise/local heuristics.

This is fast and deterministic, but it can create locally reasonable yet globally awkward frame sequences.

For example, instead of evaluating:

`[into the room] [where he waited]`

the engine may make several local decisions that result in less natural phrasing.

The goal is to evolve the existing system into a more globally aware, scored segmentation system while preserving the important properties of Kairo:

* offline operation
* deterministic behaviour
* low CPU usage
* low memory usage
* fast preprocessing
* explainable decisions
* no dependency on large language models
* no network dependency
* graceful behaviour across supported languages
* maintainability and strong test coverage

## Proposed direction

The planning document should evaluate and describe the following changes.

### 1. Replace binary phrase chunking with scored phrase affinity

The current concept of something like:

`isPhraseChunkCandidate(prev, next): Boolean`

should evolve toward a score-based abstraction such as:

`phraseAffinity(prev, next): Double`

or a richer scoring result.

The score should represent how strongly two adjacent words belong together perceptually or linguistically.

Possible inputs include:

* function-word relationships
* existing `ClauseDetector` coherence
* word frequency
* word difficulty
* syllable count
* semantic anchors
* glue words
* auxiliary/pronoun relationships
* punctuation
* clause boundaries
* dialogue state
* subword state
* hyphenation
* proper nouns
* named entities
* word length

The plan should discuss whether the existing heuristics can be reused as scoring features rather than discarded.

### 2. Introduce continuous boundary strength

Where practical, replace hard binary boundary concepts with a graded boundary signal.

Example:

* `0.0` = no meaningful break
* weak phrase transition
* moderate phrase boundary
* clause boundary
* strong sentence boundary
* `1.0` = paragraph/page-level hard boundary

A boundary-strength signal should potentially influence:

* whether words may share an RSVP frame
* duration
* pause length
* anticipatory slowing
* restart contour
* phrase segmentation

The plan should determine where this belongs architecturally and whether existing boolean fields should remain for compatibility during migration.

### 3. Introduce a linguistic intermediate representation

Consider adding an internal representation between raw `Token` objects and final RSVP frames.

For example:

```kotlin
data class RsvpLexeme(
    val token: Token,
    val difficulty: Double,
    val semanticWeight: Double,
    val boundaryBefore: Double,
    val boundaryAfter: Double,
    val affinityWithNext: Double,
    val dialogueRole: DialogueRole,
    val phraseRole: PhraseRole,
)
```

The exact shape should be determined from the existing codebase.

The objective is to separate:

`Tokenization -> Linguistic Analysis -> Segmentation -> Timing -> Display Frames`

rather than having linguistic discovery, grouping, and timing concerns too tightly coupled.

The planning document should identify which existing calculations could move into this intermediate representation.

### 4. Add bounded lookahead segmentation

The new engine should consider a small number of upcoming words before selecting frame boundaries.

Target a bounded window such as approximately **5-8 words**, but the document should recommend an appropriate value based on the existing engine.

The lookahead must remain:

* cheap
* deterministic
* bounded
* suitable for Android devices

Do not propose parsing entire books with a heavyweight grammar engine.

### 5. Use dynamic programming for RSVP segmentation

Evaluate replacing greedy frame construction with a small dynamic-programming segmentation stage.

For each position, generate legal candidate frames up to configured limits, score them, and select the sequence of frames with the best total score.

Conceptually:

```text
tokens
  |
candidate frame sizes
  |
score each candidate
  |
dynamic programming
  |
best segmentation
```

Because RSVP frames only contain a small number of words, expected complexity should remain approximately:

`O(n * k)`

where `k` is the small bounded number of candidate frame widths/options.

The document should define:

* candidate generation
* candidate scoring
* invalid-frame rules
* score composition
* tie-breaking
* deterministic behaviour
* interaction with punctuation
* interaction with paragraph/page boundaries
* interaction with existing `maxWordsPerUnit`
* interaction with `maxCharsPerUnit`
* migration from the existing greedy builder

### 6. Introduce cognitive-load-aware frame sizing

Frame construction should not depend primarily on word count and character count.

Evaluate a cognitive-load budget based on existing Kairo metadata such as:

* word length
* syllables
* word frequency
* complexity multiplier
* semantic importance
* subword state
* visual width / character length
* number of words
* punctuation complexity

Example goal:

Easy phrases such as:

`[in the]`

may be combined aggressively.

Difficult combinations such as:

`[epistemological uncertainty]`

should probably be split even if they technically fit within character and word limits.

Define a conceptual:

`frameLoad`

and a configurable maximum acceptable load.

The document should explain how this interacts with user-selected speed and RSVP profile settings.

### 7. Improve proper-noun and named-entity grouping

Evaluate lightweight offline heuristics for preserving common named entities and proper-noun groups such as:

* New York City
* United Kingdom
* World Health Organization
* Arthur Conan Doyle
* The University of Oxford

Do not introduce a heavyweight ML model unless the document strongly justifies it.

Prefer deterministic heuristics using:

* capitalisation
* connectors such as "of"
* titles
* acronym patterns
* adjacent proper nouns
* existing metadata where available

Explain how named-entity affinity should interact with cognitive load. A named entity may have high phrase affinity but still be too visually complex for one frame.

### 8. Improve dialogue and conversational prosody

Kairo already tracks dialogue and speaker-related information.

Evaluate introducing clearer roles such as:

* dialogue content
* speaker attribution/tag
* narration
* parenthetical aside

Example:

```text
"I don't know," she whispered, "but we need to leave."
```

could be internally interpreted as:

```text
DIALOGUE
SPEAKER_TAG
DIALOGUE
```

The plan should describe how these roles might affect:

* chunking
* timing
* speaker-tag compression
* pauses
* prosody

This must remain heuristic and lightweight.

### 9. Add sentence-level rhythm

Kairo already performs word-level and punctuation-level timing adjustments.

Evaluate adding a subtle sentence or phrase rhythm model.

Possible behaviour:

* clear sentence onset
* slight acceleration through low-information middle sections
* anticipatory slowing near phrase/clause endings
* slightly stronger sentence landing
* restart shaping after boundaries

Changes should remain subtle and should not cause the nominal WPM setting to feel misleading.

### 10. Add temporal smoothing

Investigate smoothing frame durations so linguistically calculated timing does not create visually jarring jumps.

Example problematic sequence:

```text
180ms
178ms
182ms
310ms
169ms
176ms
```

Consider a rate-of-change limiter or smoothing stage that operates after raw duration calculation.

Hard punctuation and explicit structural pauses must still be allowed to override smoothing.

The planning document should discuss:

* where smoothing belongs
* whether it should operate on base word duration, frame duration, pause duration, or final duration
* acceptable bounds
* preserving strong linguistic boundaries

### 11. Preserve the strengths of the current engine

The refactor must not casually discard existing behaviour.

The plan should explicitly catalogue current useful systems and show how they survive or feed the new scorer.

Examples may include:

* `WordAnalyzer`
* `ClauseDetector`
* dialogue analysis
* phrase contour calculations
* punctuation timing tiers
* function-word handling
* semantic anchors
* focal stress
* anticipatory landing
* givenness
* ORP
* hyphen/subword handling
* multilingual tokenization
* CJK handling
* RTL handling
* page/paragraph breaks
* existing profile/config settings

Prefer migration and reuse over wholesale rewrites.

## Proposed architecture

The document should evaluate an architecture broadly resembling:

```text
Book text
   |
Tokenizer
   |
List<Token>
   |
Linguistic analysis
   |
List<Annotated/RsvpLexeme>
   |
Boundary + affinity analysis
   |
Candidate frame generation
   |
Scored DP segmentation
   |
List<RsvpSegment>
   |
Timing + prosody
   |
Temporal smoothing
   |
List<RsvpFrame>
```

Do not force this exact design if the repository suggests a cleaner one.

## Scoring model

Define a proposed frame scoring model.

For example:

```text
frameScore =
    + phrase affinity
    + syntactic/coherence affinity
    + named-entity integrity
    + function-word cohesion
    + visual balance
    + natural phrase completion

    - cognitive load
    - crossing boundary strength
    - excessive character width
    - semantic-anchor collision
    - multiple difficult words
    - awkward punctuation grouping
```

Explain which scores should be:

* pairwise
* token-based
* whole-frame
* boundary-based
* transition-based

Avoid an unmaintainable pile of arbitrary magic numbers.

Recommend a strategy for centralising weights and making them testable/tunable.

## Testing strategy

The Markdown plan must include a serious test strategy.

At minimum, propose:

### Unit tests

For:

* phrase affinity
* boundary strength
* candidate frame generation
* cognitive load
* proper noun grouping
* dialogue segmentation
* duration smoothing
* deterministic tie-breaking
* DP optimal segmentation

### Golden segmentation tests

Maintain representative sentences with expected RSVP grouping.

Include categories such as:

* simple prose
* difficult academic prose
* dialogue
* quotations
* em-dash asides
* commas
* semicolons
* conjunctions
* subordinate clauses
* named entities
* numbers
* acronyms
* hyphenated words
* short function-word chains
* long difficult words
* paragraph boundaries
* multilingual examples

Example format:

```text
Input:
"The old man walked slowly towards the river because he was tired."

Expected segmentation:
[The old man]
[walked slowly]
[towards the river]
[because]
[he was tired.]
```

Exact expected grouping should be chosen based on the new scoring rules rather than blindly copying this example.

### Regression tests

Existing RSVP tests must continue to pass unless behaviour is intentionally changed.

The plan should identify likely test suites that may need migration.

### Performance tests

Benchmark:

* token count
* segmentation time
* memory allocations
* large chapters
* full books if feasible
* older Android-class hardware expectations

Set a goal that the new segmentation stage is effectively negligible compared with book import/rendering.

## Observability and debugging

Because the new system is score-based, add a development/debug mechanism for explaining decisions.

For example:

```text
Frame: "towards the river"

phraseAffinity        +1.42
functionWordCohesion  +0.28
boundaryPenalty       -0.05
cognitiveLoad         -0.31
---------------------------
total                  1.34
```

This does not need to ship as a user-facing feature.

It should make tuning and regression analysis easier.

Consider:

* structured scoring result objects
* optional debug logging
* test-only score explanations
* deterministic reason codes

## Migration strategy

Avoid a big-bang rewrite.

Propose phases such as:

### Phase 1

Introduce scoring abstractions while preserving current behaviour.

### Phase 2

Create the linguistic intermediate representation.

### Phase 3

Implement candidate-frame scoring.

### Phase 4

Add DP segmentation behind an internal flag.

### Phase 5

Run existing and golden tests against greedy vs DP output.

### Phase 6

Introduce cognitive-load budgeting.

### Phase 7

Add named-entity and dialogue improvements.

### Phase 8

Add timing smoothing and sentence-level rhythm.

### Phase 9

Remove or simplify obsolete greedy code once parity and improvements are established.

The document should refine these phases based on repository structure.

## Compatibility

The refactor must account for:

* existing saved RSVP settings
* existing reading positions
* frame/index mapping
* resume behaviour
* bookmarks/highlights if frame mappings affect them
* current profile behaviour
* deterministic regeneration of RSVP frames
* old database content
* CJK tokenizers
* RTL tokenizers
* EPUB/PDF/TXT/MOBI/etc. imported content
* Android performance
* tests involving frame alignment or resume cursors

Pay particular attention to anything that assumes a stable relationship between token index, original word index, and RSVP frame index.

## Out of scope for this refactor

Unless clearly necessary, do not propose:

* neural-network NLP
* cloud parsing
* LLM APIs
* external language services
* large dependency additions
* rewriting the entire tokenizer
* replacing existing EPUB/book parsers
* adding bookstore functionality
* unrelated UI redesigns

## Final document requirements

The Markdown document should contain:

1. Executive summary
2. Current RSVP architecture
3. Current limitations
4. Refactor goals
5. Proposed architecture
6. Proposed data structures
7. Phrase-affinity model
8. Boundary-strength model
9. Cognitive-load model
10. Candidate frame generation
11. Dynamic-programming segmentation algorithm
12. Named-entity strategy
13. Dialogue/prosody strategy
14. Timing smoothing strategy
15. Multilingual considerations
16. Compatibility and migration risks
17. Testing strategy
18. Performance considerations
19. Debugging/observability
20. Phased implementation plan
21. Files/modules likely to change
22. Open questions and design decisions
23. Explicit non-goals

Include pseudocode where useful.

Be concrete and repository-specific. Reference actual Kairo classes/functions/files discovered during inspection.

Do not implement production code yet.

The objective is to leave behind a design document detailed enough that another coding agent could implement the refactor incrementally without having to rediscover the architecture from scratch.
