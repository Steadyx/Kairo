# Kairo code audit findings

- Audit date: 19 October 2026
- Audited revision: `630fdc086378385edef7551d198448614b70883b`
- Release: `1.4.0`
- Fix status: All ten findings fixed on `fix/october-audit-bugs` (24 October 2026), with automated regression coverage. Android runtime checks are delegated to the owner at their request.

This document retains the original audit evidence and records the repairs below. The findings affect persistence, development restore tooling, reading accuracy, navigation, document imports, and onboarding.

## Scope and confidence

This was a repository-wide search and risk-focused review of the main flows across core logic, tokenization, persistence, imports, search, annotations, export, reading statistics, reader and RSVP state, settings, themes, onboarding, updates, build configuration, and scripts. Kairo has one Android Gradle module, `app`, with these areas organized into packages.

**This was not a file-by-file review of every file, and it does not establish that these are all the bugs in the app.** Some UI layouts, real-device behavior, third-party dependency internals, and exhaustive malformed-input testing remain outside the completed validation.

Eight failure cases were reproduced with temporary executable checks. Oversized metadata acceptance was also reproduced, but its Android database failure path was assessed from the application source and platform behavior. The onboarding recreation finding was traced through source and still needs device confirmation.

At the audit, 63 relevant existing tests passed. Passing tests did not cover the reproduced failures. Temporary checks did not add permanent regression tests to the repository. No Android device was connected.

## Ranked backlog

P1 means high impact. P2 means a normal-priority functional bug. Rank combines impact and the value of addressing the finding; the restore-script issue affects development installations rather than the production application flow.

| Rank | Priority | Finding | Evidence | Status |
| --- | --- | --- | --- | --- |
| 1 | P1 | Oversized metadata can prevent library loading | Acceptance reproduced; device failure pending | Fixed |
| 2 | P1 | Restore scripts delete data before validating backups | Reproduced with a fake device directory | Fixed |
| 3 | P2 | Normalized words cause incorrect search and anchor navigation | Reproduced | Fixed |
| 4 | P2 | Pagination omits opening punctuation | Reproduced | Fixed |
| 5 | P2 | Rapid Previous and Next navigation inherits the wrong page | Reproduced | Fixed |
| 6 | P2 | FictionBook imports omit body-level epigraphs | Reproduced | Fixed |
| 7 | P2 | Quoted HTML charset declarations are ignored | Reproduced | Fixed |
| 8 | P2 | URL cleanup removes valid closing parentheses | Reproduced | Fixed |
| 9 | P2 | Article extraction duplicates nested quotations | Reproduced | Fixed |
| 10 | P2 | Onboarding loses navigation context after recreation | Source-traced; device confirmation pending | Fixed |

## 1 Oversized metadata can prevent library loading

**Priority:** P1. **Status:** Fixed; automated checks added.

**Problem and impact:** Imported titles and author metadata are not bounded before persistence. Library queries retrieve those fields whole. A malformed import can therefore create a database row larger than the device's cursor window, the buffer used to return query results. Subsequent library loads can fail, including after restarting the app; the library UI may then be unavailable to delete the offending book.

**Evidence:** A FictionBook fixture with a 3,145,728-byte title was accepted by the current compiled parser. The persistence path preserves the title, and the library query selects it without a size guard. The standard Android cursor-window configuration is 2,097,152 bytes, although device configurations can differ. The on-device failure was not reproduced.

**Audited source:** [BookRepositoryImpl.kt line 400](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/data/books/BookRepositoryImpl.kt#L400), [BookDao.kt line 52](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/data/local/BookDao.kt#L52), and [Fb2BookParser.kt line 47](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/data/books/Fb2BookParser.kt#L47). Platform references: [Android cursor-window configuration](https://android.googlesource.com/platform/frameworks/base/+/android16-release/core/res/res/values/config.xml) and [oversized-row failure handling](https://android.googlesource.com/platform/frameworks/base.git/+/master/core/jni/android_database_SQLiteConnection.cpp).

**Proposed fix:** Apply sensible metadata limits at the shared import persistence boundary. Protect reads of existing oversized records so an affected installation can recover without clearing its library.

**Verification to add:** Import oversized titles and author fields on a disposable Android installation, restart the app, and confirm that the library remains usable. Also test recovery from an oversized row already stored in the database.

## 2 Restore scripts delete data before validating backups

**Priority:** P1. **Status:** Fixed; automated checks added. **Affected area:** Development tooling.

**Problem and impact:** Both restore paths accept a nonempty archive, delete the destination databases, files, and preferences, then attempt extraction. A damaged or invalid backup can destroy the destination installation's existing library before restore fails.

**Evidence:** A fake `adb` implementation ran the restore against a temporary app directory. A nonempty invalid archive passed the initial check; the original fake library was deleted before `tar` reported an unrecognized archive format. No real device data was touched.

**Audited source:** [reimport-demo-data.sh line 94](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/scripts/reimport-demo-data.sh#L94) and [migrate-app-id-data.sh line 91](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/scripts/migrate-app-id-data.sh#L91).

**Proposed fix:** Extract and validate the archive into a temporary destination before replacing live data. Retain the previous data until replacement succeeds so failures can be rolled back.

**Verification to add:** Use fake-device fixtures for invalid and truncated archives, extraction failure, and a successful restore. Every failure must preserve the original data.

## 3 Normalized words cause incorrect search and anchor navigation

**Priority:** P2. **Status:** Fixed; automated checks added.

**Problem and impact:** The token-position resolver searches original text for token text that has already been normalized. Soft hyphens are removed during tokenization. If an unchanged copy of the same word appears later, the resolver can match that later occurrence and return the wrong token for an earlier search result or table-of-contents anchor.

**Evidence:** For source text `co\u00ADoperate alpha cooperate beta`, where `\u00AD` represents a soft hyphen, tokenization produced `cooperate`, `alpha`, `cooperate`, `beta`. Resolving the source offset of `alpha` returned token 0, `cooperate`, instead of token 1.

**Audited source:** [TokenTextPositionResolver.kt line 23](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/core/text/TokenTextPositionResolver.kt#L23).

**Proposed fix:** Preserve source offsets through normalization, or use equivalent normalization with an explicit offset map. Keep search and table-of-contents navigation on the same mapping contract.

**Verification to add:** Resolve search and anchor positions around repeated words containing soft hyphens and other tokenizer substitutions. Assert the exact destination token.

## 4 Pagination omits opening punctuation

**Priority:** P2. **Status:** Fixed; automated checks added.

**Problem and impact:** A page starts at its first word token. Opening punctuation before that word is excluded from the page range and is then omitted when reader blocks are sliced for display.

**Evidence:** Page generation and display slicing converted the token sequence for `(Hello world)` into `Hello`, `world`, `)`. The opening parenthesis was absent from the displayed block.

**Audited source:** [ReaderPaginator.kt line 34](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/ui/reader/ReaderPaginator.kt#L34).

**Proposed fix:** Include leading punctuation in each page's token range while keeping the first readable word as its focus position.

**Verification to add:** Check rendered block slices for opening brackets and quotation marks at the start of a chapter and at page boundaries. Every intended punctuation token must remain visible.

## 5 Rapid Previous and Next navigation inherits the wrong page

**Priority:** P2. **Status:** Fixed; automated checks added.

**Problem and impact:** Pending page and focus values are updated separately. Previous requests the preceding chapter's last page using `Int.MAX_VALUE`. A subsequent Next request can leave that page override in place, opening the destination at its end instead of its beginning.

**Evidence:** With a cached chapter containing two pages, Previous started loading the preceding chapter. Next was requested before that load finished. The destination had focus index 0 but retained `pageIndexOverride = 2147483647`; page resolution clamps that value to the last page. This reproduction follows the application's Previous and Next callback behavior.

**Audited source:** [ReaderViewModel.kt line 165](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/ui/reader/ReaderViewModel.kt#L165).

**Proposed fix:** Replace the complete pending navigation request whenever a new chapter load starts. Ensure a request for the beginning clears any previous last-page override.

**Verification to add:** Hold a previous-chapter load with a deferred result, request Next, and assert both the destination focus and first-page selection. Cover cached and uncached destinations.

## 6 FictionBook imports omit body level epigraphs

**Priority:** P2. **Status:** Fixed; automated checks added.

**Problem and impact:** When a FictionBook body contains sections, chapter construction renders only those sections. Authored body-level content before the sections, such as an epigraph, is silently omitted.

**Evidence:** A fixture containing a body-level epigraph followed by a section imported the section's heading and text but omitted the epigraph sentinel entirely.

**Audited source:** [Fb2BookParser.kt line 134](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/data/books/Fb2BookParser.kt#L134).

**Proposed fix:** Preserve body-level introductory content in reading order before rendering its sections.

**Verification to add:** Import a body with a title, epigraph, and multiple sections. Assert that authored text appears once and in source order.

## 7 Quoted HTML charset declarations are ignored

**Priority:** P2. **Status:** Fixed; automated checks added.

**Problem and impact:** The charset pattern accepts an unquoted value but misses normal quoted forms such as `<meta charset="Shift_JIS">`. When UTF-8 decoding fails, the decoder falls back to Windows-1252, which can corrupt the imported text.

**Evidence:** A Japanese HTML fixture encoded as Shift_JIS with a quoted charset declaration decoded into garbled characters using the current compiled decoder.

**Audited source:** [BookTextDecoder.kt line 104](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/data/books/BookTextDecoder.kt#L104).

**Proposed fix:** Recognize single-quoted, double-quoted, and unquoted charset values while preserving the existing BOM precedence.

**Verification to add:** Test quoted and unquoted Shift_JIS declarations and assert the original Japanese text. Include a conflicting declaration with a BOM to verify precedence.

## 8 URL cleanup removes valid closing parentheses

**Priority:** P2. **Status:** Fixed; automated checks added.

**Problem and impact:** Link cleanup always strips trailing closing parentheses, even when they belong to a valid balanced URL. The resulting request can fail or reach a different address.

**Evidence:** Normalizing `https://en.wikipedia.org/wiki/Function_(mathematics)` removed its final `)`.

**Audited source:** [WebArticleUrl.kt line 62](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/data/books/WebArticleUrl.kt#L62).

**Proposed fix:** Preserve balanced brackets and distinguish surrounding prose punctuation from characters belonging to the URL.

**Verification to add:** Test direct URLs ending in balanced parentheses, links wrapped in prose parentheses, and existing trailing-punctuation cases.

## 9 Article extraction duplicates nested quotations

**Priority:** P2. **Status:** Fixed; automated checks added.

**Problem and impact:** Article extraction selects both a blockquote and its child paragraphs. The parent contributes their combined text, then the paragraphs contribute the same text again. Whole-string deduplication does not remove this overlap.

**Evidence:** A blockquote containing two distinct paragraphs imported each paragraph twice. A unique sentinel from the first paragraph appeared twice in the resulting chapter text.

**Audited source:** [WebArticleExtractor.kt line 220](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/data/books/WebArticleExtractor.kt#L220).

**Proposed fix:** Collect each text block once, avoiding overlapping parent and child selections without losing text directly owned by a parent element.

**Verification to add:** Test nested blockquotes and list items containing paragraphs. Assert that each source passage appears once and retains its reading order.

## 10 Onboarding loses navigation context after recreation

**Priority:** P2. **Status:** Fixed; automated checks added. **Android recreation confirmation delegated to the owner.**

**Problem and impact:** Tutorial activity and step index use `rememberSaveable`, but the full step list and book launch context use ordinary `remember`. After activity recreation, an active Reader or RSVP step can be interpreted against the shorter Library and Settings step list while navigation remains on the reader screen. The expected tutorial controls can become unavailable.

**Evidence:** The saved active flag, saved index, reset step list, and lost launch context were traced through the coordinator. No device rotation or process-recreation reproduction was run.

**Audited source:** [StartingTutorialCoordinator.kt line 56](https://github.com/Steadyx/Kairo/blob/630fdc086378385edef7551d198448614b70883b/app/src/main/java/com/kairo/reader/ui/tutorial/StartingTutorialCoordinator.kt#L56).

**Proposed fix:** Save the launch context and whether Reader and RSVP steps are included alongside the current step index. Derive the restored step list from that saved session state.

**Verification to add:** Recreate the activity at Reader and RSVP tutorial steps on a disposable device or emulator. Confirm that the same step, route, book context, and Next, Previous, and Skip actions remain usable.

## Completed repairs and validation

Android tests were intentionally not run, as requested. The fixes and automated checks are complete; Android CursorWindow recovery and activity recreation are not claimed as device-verified.

- [x] **1 Metadata:** Bound title, serialized authors, language, chapter titles, and contents labels at the shared import/mapping boundary. Migration 14→15 trims oversized legacy metadata in SQLite before cursor reads, preserving chapter bodies and navigation coordinates. `MappersTest` covers oversized and Unicode metadata; `test_metadata_migration.py` executes the production migration SQL against oversized existing records and checks repeat application.
- [x] **2 Restore:** Both entry points use one validated, staged restore with rollback. Invalid/truncated archives, path traversal, links, unreadable directory permissions, extraction failure, failed replacement, failed original-data moves, and successful restores are exercised with fake adb. Original data survives each injected failure. These tests run in `qualityCheck` via `scriptTests`.
- [x] **3 Source positions:** Match normalized token text through a source-offset map. `ReaderTextPositionResolverTest` and `SearchTextTest` cover repeated words with soft hyphens, joiners, hyphen substitutions, temperatures, minus signs, and currencies.
- [x] **4 Pagination:** Include opening punctuation in displayed page ranges and retain the first word as focus. `ReaderPaginationTest` verifies actual block slices at chapter and page starts.
- [x] **5 Navigation:** Every chapter load replaces pending focus/page/offset values, including clearing stale last-page requests. `ReaderViewModelConcurrencyTest` holds the earlier load and checks both cached and uncached later destinations, including stale completion.
- [x] **6 FictionBook:** Preserve body-level content before, between, and after sections in source order. `Fb2ParserEngineTest` checks titles, epigraphs, sections, and closing text appear exactly once.
- [x] **7 Charset:** Recognize quoted and unquoted HTML charsets. `BookTextDecoderTest` verifies Japanese Shift_JIS and BOM precedence over conflicting declarations.
- [x] **8 URLs:** Retain balanced closing brackets while removing surrounding prose punctuation. `WebArticleUrlTest` covers direct, wrapped, and nested-parenthesis URLs.
- [x] **9 Articles:** Traverse non-overlapping text blocks; preserve short parent-owned text, block spacing, and intentional repeated paragraphs. `WebArticleExtractorTest` covers nested quotations and paragraph-containing list items.
- [x] **10 Onboarding:** Save book/chapter/token context and derive the full step list from restored context. `StartingTutorialCoordinatorTest` verifies save/restore at Reader and RSVP steps, including adjacent steps and positions.

**Final automated validation (24 October 2026):** `./gradlew qualityGate` passed, including `qualityCheck`, 933 JVM tests (zero failures or skips), ktlint, Detekt, Android Lint, and debug APK assembly. `scriptTests` passed the SQLite migration check and 18 fake-device restore scenarios. `git diff --check` passed.

## Original completion criteria

For each finding, confirm the trigger on the current revision, add a regression test that fails before the repair, make the smallest complete fix, and record validation beside its status. Recheck source references if the code has moved since the audited commit. Device-dependent findings need device evidence before their runtime consequences are marked verified.

Keep this backlog's coverage boundary explicit: fixing these ten findings does not establish that the rest of the application is bug-free.
