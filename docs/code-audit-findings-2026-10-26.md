# Kairo bug audit — 26 October 2026

**What this repo does:** Kairo is an offline Android reading app. It imports books and documents, saves reading positions and annotations, and offers standard and timed reading, search, and local reading statistics. This review assumes a single person using a normal Android device, sometimes with large or illustrated documents; the backup scripts are development tools for debuggable installations.

**Updated:** Device follow-up on 26 October 2026. Original finding numbers remain stable; finding 10 is withdrawn. New findings are 13–20.

**Audited revision:** `40bf4515feca465e319dfbeb30cfb3025a435768` (`main`). The findings below describe the audited revision; repair status is recorded at the end of this document. Priority reflects impact and scope: P1 means potential data loss or significant missing content; P2 means a consequential functional error with a narrower trigger. Reproduction level is stated for each item.

## Must fix

1. **A failed backup can be followed by uninstalling the only app copy — P1** (`scripts/clean-demo-install.sh:74`, `scripts/clean-demo-install.sh:84`, `scripts/clean-demo-install.sh:89`)
   - **What this is:** The clean-demo script backs up a debug installation and then uninstalls it.
   - **Problem:** `adb exec-out` returns transport success without the remote `tar` process's exit status. A failed tar can still write a nonempty partial archive, which passes the only check, and the script then uninstalls the app. A disposable fake ADB that emitted partial bytes and returned success caused the unmodified script to save the invalid archive and invoke uninstall. No device was modified. The status behavior follows the [ADB exec implementation](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/client/commandline.cpp#1818).
   - **Fix:** Use a binary-safe transport that reports the remote command's status, fully validate the archive, and only then uninstall.
   - **If we skip it:** A failed backup can permanently remove the debug library, notes, and settings.

2. **A failed migration export overwrites the previous backup — P1** (`scripts/migrate-app-id-data.sh:7`, `scripts/migrate-app-id-data.sh:65`)
   - **What this is:** The migration script exports private data to a fixed backup filename by default.
   - **Problem:** Shell redirection truncates that file before ADB starts. A disposable fake ADB failure changed a pre-existing good backup into an empty file. The same `exec-out` status issue in finding 1 can also allow a partial replacement to be reported as successful.
   - **Fix:** Export to a separate temporary file, check remote status and validate it, then atomically replace the destination only after success.
   - **If we skip it:** A failed retry can destroy the last usable backup of the old installation.

3. **Failed restores can be reported as successful — P1** (`scripts/restore-app-data.py:73`, `scripts/reimport-demo-data.sh:94`, `scripts/migrate-app-id-data.sh:91`)
   - **What this is:** Both restore entry points use the shared staged restore and rollback script.
   - **Problem:** `adb exec-in` likewise does not return the remote shell's exit status, so Python's `check=True` cannot detect a remote extraction, replacement, or rollback failure. With a fake ADB adjusted to match that behavior, six injected failures across both entry points exited successfully and printed `Restore complete`; the existing fake assumes the remote exit code is propagated. Rollback is attempted, but its failure can also be hidden.
   - **Fix:** Transfer the archive with a method that exposes the remote exit status, propagate failure to both callers, and make the fake ADB model real status behavior.
   - **If we skip it:** Users may believe their data was restored when it was not, or miss an incomplete rollback.

4. **A first-chapter illustration can disappear — P1** (`app/src/main/java/com/kairo/reader/ui/reader/ReaderRenderState.kt:120`, `app/src/main/java/com/kairo/reader/ui/reader/ReaderRenderState.kt:140`)
   - **What this is:** The reader presents a stored cover separately from chapter images.
   - **Problem:** Whenever a book has cover bytes, the reader assumes the first image in chapter zero is that cover and removes it from the inline blocks and header images, without checking its identity. A Compose instrumentation fixture on CPH2493 using the actual presentation code, separate cover bytes, and one `diagram.png` returned no diagram in any output; this did not exercise full EPUB import. The EPUB parser obtains cover metadata separately from the images referenced by chapter HTML.
   - **Fix:** Identify an actual cover image explicitly; preserve ordinary chapter images in reading order.
   - **If we skip it:** Diagrams and other meaningful images can be silently absent from a book.

5. **Word documents can lose text inside content controls — P1** (`app/src/main/java/com/kairo/reader/data/books/DocxBookParser.kt:143`)
   - **What this is:** The DOCX importer reads paragraphs and tables from the document body.
   - **Problem:** It visits only direct `w:p` and `w:tbl` children, ignoring content-control (`w:sdt`) and custom XML wrappers. A compiled-parser fixture with an ordinary paragraph and a content-control paragraph imported only the ordinary paragraph. A document entirely inside those wrappers can be rejected as empty. Nested tables are also ignored by the table renderer (`DocxBookParser.kt:263`).
   - **Fix:** Traverse supported body wrappers and nested table contents in document order, retaining existing paragraph and table rendering.
   - **If we skip it:** A successful import can silently omit authored text, including whole sections.

6. **Different illustrated editions can be discarded as duplicates — P1** (`app/src/main/java/com/kairo/reader/data/books/ImportFingerprint.kt:53`, `app/src/main/java/com/kairo/reader/data/books/BookRepositoryImpl.kt:358`, `app/src/main/java/com/kairo/reader/data/books/BookRepositoryImpl.kt:419`)
   - **What this is:** After parsing, Kairo compares a book with existing library entries and removes the new assets when it finds a duplicate.
   - **Problem:** The content fingerprint includes chapter titles, plain text, and contents entries, but not HTML, images, cover, or language. An executable fingerprint fixture confirmed identical hashes for books with the same text but different image paths and HTML; source tracing shows the duplicate path returns the existing book and deletes the new assets. The database-backed flow was not exercised end to end.
   - **Fix:** Include meaningful rendered content, image content, and language in duplicate equivalence, or only deduplicate when the imported sources are actually equivalent.
   - **If we skip it:** Importing a corrected or illustrated edition can silently leave the user with the old version and discard its new figures.

7. **A related-story teaser can replace the article — P1** (`app/src/main/java/com/kairo/reader/data/books/WebArticleExtractor.kt:187`)
   - **What this is:** Web import selects readable content from an article page.
   - **Problem:** The extractor accepts the first qualifying element matching a priority selector, including any class containing `article`. A synthetic fixture against current compiled code placed a long `article-related` teaser before the real `main` article; the imported chapter contained the teaser and none of the main text.
   - **Fix:** Select the actual main article using content scoring and structural context rather than the first matching element; test related-content elements before the main body.
   - **If we skip it:** A successful web import can save the wrong content while omitting the requested article. Live sites were not tested.

8. **Image-only chapters lose the saved page — P2** (`app/src/main/java/com/kairo/reader/ui/reader/ReaderScreenPaging.kt:11`, `app/src/main/java/com/kairo/reader/ui/navigation/ReaderRouteCallbacks.kt:141`, `app/src/main/java/com/kairo/reader/ui/reader/ReaderRenderState.kt:219`)
   - **What this is:** Image-only chapters generate one visual page per image, and reader positions are stored for resumption.
   - **Problem:** Every image page has token index zero, and the stored position contains no image-page index. A Compose instrumentation fixture on CPH2493 using the actual presentation code selected the third of three images and resolved the saved token back to page zero; this did not exercise full navigation or persistence. Also, entering an image-only chapter and leaving without turning a page can leave the previous chapter saved because the chapter-change and exit paths skip empty token lists (`ReaderRoute.kt:348`, `ReaderRouteCallbacks.kt:185`).
   - **Fix:** Persist and restore an unambiguous visual-page location, and save chapter changes even when there are no tokens.
   - **If we skip it:** Readers of illustrated or image-only books repeatedly lose their place. Full lifecycle behavior needs device confirmation.

9. **EPUBs can append non-reading files as chapters — P2** (`app/src/main/java/com/kairo/reader/data/books/epub/EpubChapterOrdering.kt:39`, `app/src/main/java/com/kairo/reader/data/books/EpubChapterBuilder.kt:63`, `app/src/main/java/com/kairo/reader/data/books/EpubBookParser.kt:305`)
   - **What this is:** EPUB spine order describes the book's reading sequence, including entries marked non-linear.
   - **Problem:** The order resolver excludes ordinary non-linear spine entries, but the chapter builder appends every other HTML or eligible XML file even when the spine is complete. A compiled builder fixture with preferred chapters 1 and 2 and a non-linear `notes.xhtml` produced chapters 1, 2, then notes. Unreferenced files may be appended as well. The fixture tested the builder, while the parser-to-builder call was traced in source.
   - **Fix:** Use the resolved reading order when it is valid; reserve ZIP-wide fallback for cases where the declared order cannot be recovered, while preserving intentional navigation handling.
   - **If we skip it:** Supplemental notes or unrelated markup can appear as ordinary reading content and distort progress and search.

10. **Withdrawn: Unicode title and note search — device result contradicts the original finding** (`app/src/main/java/com/kairo/reader/data/search/SearchText.kt:13`, `app/src/main/java/com/kairo/reader/data/local/SearchDao.kt:54`, `app/src/main/java/com/kairo/reader/data/local/SavedAnnotationDao.kt:44`)
   - The original review inferred a search failure from desktop SQLite's ASCII-only case folding. On CPH2493 running Android 16 (API 36), an isolated instrumentation test using the app's actual Room DAOs found `ÉCOLE`, `МОСКВА`, accented saved text, and a Cyrillic note when searched using their uppercase text. The ASCII control also matched. The test failed its original expectation that those searches would return zero, so this is **not a confirmed bug** and should not be fixed on that evidence.
   - Behavior on other Android versions or with other scripts was not tested. The number is retained to avoid changing references to the original report.

11. **Library passage search silently hides matches in later books — P2** (`app/src/main/java/com/kairo/reader/data/search/LibrarySearchRepositoryImpl.kt:83`, `app/src/main/java/com/kairo/reader/data/search/LibrarySearchRepositoryImpl.kt:196`, `app/src/main/java/com/kairo/reader/ui/search/LibrarySearchOverlay.kt:139`)
   - **What this is:** Passage search scans the library in title order and displays the matches.
   - **Problem:** It stops after 60 results or 32 matching chapters, with at most three matches per chapter. A common term in an alphabetically earlier book can exhaust the limit before later books are examined, yet the UI presents the results as complete, without a truncation indicator or a way to continue. This is source-traced, not device-reproduced.
   - **Fix:** Support continuing the search or make truncation explicit and provide a way to reach omitted books and matches.
   - **If we skip it:** Search can make existing passages appear absent from the library.

12. **Web import deletes authored paragraphs containing common phrases — P2** (`app/src/main/java/com/kairo/reader/data/books/WebArticleExtractor.kt:223`, `app/src/main/java/com/kairo/reader/data/books/WebArticleExtractor.kt:296`)
   - **What this is:** Web extraction filters text that looks like navigation or boilerplate.
   - **Problem:** A whole text block is discarded if it contains any marker such as `privacy policy` or `subscribe to`, even in normal prose. A compiled-code fixture imported the remaining article while silently dropping a paragraph discussing a privacy policy.
   - **Fix:** Filter actual boilerplate elements or narrowly identified notices instead of deleting any paragraph containing these phrases.
   - **If we skip it:** Articles can be saved with meaningful passages missing and no warning.

13. **MOBI imports can omit text before the first chapter heading — P1** (`app/src/main/java/com/kairo/reader/data/books/mobi/MobiContentProcessor.kt:334`, `app/src/main/java/com/kairo/reader/data/books/mobi/MobiParserEngine.kt:114`)
   - **What this is:** The MOBI importer splits HTML into chapters when it finds headings.
   - **Problem:** With two or more headings it starts the first slice at the first heading, discarding all preceding content. An executable fixture using current compiled code omitted a preface before two headings while keeping both chapter bodies.
   - **Fix:** Preserve content before the first heading in source order and cover prefaces, images, and leading markup in a regression test.
   - **If we skip it:** A successful import can silently lose the introduction or other opening material.

14. **FictionBook image-only sections disappear — P1** (`app/src/main/java/com/kairo/reader/data/books/Fb2BookParser.kt:128`)
   - **What this is:** The FictionBook importer converts each rendered section into a chapter.
   - **Problem:** It keeps a draft only when its plain text is nonblank, even if it contains a valid image. A compiled-parser fixture with a text section, an image-only section, and another text section returned only the two text sections and no image paths.
   - **Fix:** Retain a section when it has readable text or successfully resolved images, and verify their source order.
   - **If we skip it:** Full-page illustrations can disappear silently from otherwise successful imports.

15. **Plain text can be corrupted by a charset phrase in its content — P1** (`app/src/main/java/com/kairo/reader/data/books/BookTextDecoder.kt:28`, `app/src/main/java/com/kairo/reader/data/books/BookTextDecoder.kt:91`, `app/src/main/java/com/kairo/reader/data/books/TextFileBookParser.kt:34`)
   - **What this is:** The shared text decoder looks for encoding declarations before decoding text documents.
   - **Problem:** It interprets any `charset=...` in the first 4,096 bytes as a declaration, including ordinary prose in TXT or Markdown. A UTF-8 fixture containing `charset=windows-1252` converted `Café résumé naïve` into `CafÃ© rÃ©sumÃ© naÃ¯ve`.
   - **Fix:** Detect declarations only in the appropriate markup context, and avoid applying HTML/XML declaration detection to plain text.
   - **If we skip it:** Imported text can be silently corrupted and then saved in that form.

16. **Deleting and re-importing a book can remove the new book's images — P1** (`app/src/main/java/com/kairo/reader/data/library/LibraryRepositoryImpl.kt:71`, `app/src/main/java/com/kairo/reader/data/books/BookRepositoryImpl.kt:69`, `app/src/main/java/com/kairo/reader/ui/navigation/LibraryRoute.kt:222`)
   - **What this is:** Deletion removes the database entry, then deletes the book's asset directory; importing the same file derives the same book ID from its fingerprint.
   - **Problem:** Deletion and import do not share a lock. If the old deletion commits, the same file is re-imported and its assets written, and the old deletion then removes the shared directory, the new database entry remains but its images are gone. This is a concrete source-traced interleaving; it has not been reproduced on a device.
   - **Fix:** Coordinate deletion and import for the same book, including database and asset cleanup, and test the delayed-cleanup interleaving.
   - **If we skip it:** A quickly re-imported illustrated book can have broken or missing images.

17. **Long MOBI chapters lose images and formatting when split — P1** (`app/src/main/java/com/kairo/reader/data/books/mobi/MobiContentProcessor.kt:549`, `app/src/main/java/com/kairo/reader/data/books/mobi/MobiParserEngine.kt:120`)
   - **What this is:** The MOBI importer splits unusually long chapters into smaller reading chapters.
   - **Problem:** When the text exceeds the split threshold, it rebuilds each part from plain text rather than the original HTML. A fixture using compiled code with long paragraphs and two image tags produced three parts with no image tags. The subsequent parser recomputes image paths from the rewritten HTML, so the images are not recovered.
   - **Fix:** Split the original content while retaining its images and markup in source order; cover the long-chapter path with illustrated fixtures.
   - **If we skip it:** Long chapters can import without their figures, links, and formatting.

18. **Long MOBI text can be discarded before an incidental chapter reference — P1** (`app/src/main/java/com/kairo/reader/data/books/mobi/MobiContentProcessor.kt:224`, `app/src/main/java/com/kairo/reader/data/books/mobi/MobiContentProcessor.kt:549`)
   - **What this is:** The fallback splitter tries to identify chapter boundaries in text extracted from long MOBI chapters.
   - **Problem:** Its pattern matches `chapter`, `part`, `section`, or `book` followed by a number anywhere in prose, and starts the first output at the first match. A compiled-code fixture with about 30,000 characters followed by ordinary references to `chapter 1` and `chapter 2` dropped all text before the first reference.
   - **Fix:** Recognize only credible headings and always preserve the content before the first boundary.
   - **If we skip it:** A successful import can omit a large part of the book without warning.

19. **Repeated DOCX or FictionBook images can display the wrong figure — P1** (`app/src/main/java/com/kairo/reader/data/books/DocxBookParser.kt:183`, `app/src/main/java/com/kairo/reader/data/books/Fb2BookParser.kt:239`, `app/src/main/java/com/kairo/reader/ui/reader/ReaderContentBlockParser.kt:296`)
   - **What this is:** Document parsers retain image references in HTML and supply the reader a list of image paths.
   - **Problem:** The parsers deduplicate the paths, while the reader ignores the actual DOCX and FictionBook image source and consumes that list in sequence. A synthetic DOCX passed through the compiled DOCX parser and reader block parser had image order A, A, B but produced blocks A, B: B appears at the second A and the final B is omitted.
   - **Fix:** Resolve each local image reference by its actual path, validating it against the chapter's permitted assets.
   - **If we skip it:** Repeated figures can cause a different image to appear alongside the wrong text and later images to disappear. Full device rendering has not been tested.

20. **EPUB image limits silently omit later figures — P1** (`app/src/main/java/com/kairo/reader/data/books/EpubBookParser.kt:256`, `app/src/main/java/com/kairo/reader/data/books/EpubChapterBuilder.kt:89`)
   - **What this is:** EPUB import bounds individual and total image extraction to control resource use.
   - **Problem:** The individual-image limit already rejects oversized entries, but once cumulative extracted images exceed 25 MiB the old loop stopped extracting further images. Import proceeds without a warning; unresolved images cannot enter the chapter image list, and image-only chapters can consequently be discarded. This is source-traced, not yet reproduced with an EPUB on a device.
   - **Fix:** Keep resource bounds but explicitly reject or report an incomplete import when required images cannot be extracted; do not silently claim the complete book was imported.
   - **If we skip it:** Illustrated books can lose figures or entire image-only sections based on archive order and size.

## Validation and coverage

`./gradlew testDebugUnitTest --console=plain` passed on this checkout: 939 JVM tests, zero failures, errors, or skips. Temporary fixtures exercised the DOCX parser, EPUB chapter builder, deduplication fingerprint, reader image and page algorithms, article extraction, MOBI chapter splitting, FictionBook image sections, plain-text decoding, and fake-ADB backup/restore failure paths; the SQLite case was checked locally. These checks do not establish full Android integration behavior. The initial review had no connected device. Follow-up checks used a separately named temporary test installation on CPH2493, preserving the existing production and debug installations.

**Android follow-up:** On CPH2493 (Android 16, API 36), two isolated Compose instrumentation tests passed by asserting the defective presentation outcomes in findings 4 and 8. A third instrumentation test using the actual Room DAOs expected the originally predicted Unicode search failure but failed because all five searches matched; finding 10 is withdrawn. An additional timed-reading lifecycle fixture observed no frame advancement during a two-second stopped interval; it does not rule out other lifecycle races. These are targeted tests with synthetic data, not full device import or navigation tests.

The initial review mapped the whole repository and traced high-risk flows across imports, persistence, backup scripts, reader and timed reading, search, annotations, export, settings, navigation, startup, updates, build configuration, and tests. It was risk-focused rather than a line-by-line inspection of every file. Third-party dependency internals, every UI state, real provider behavior, all document variants, and Android device lifecycle and rendering were not exhaustively tested. Five lower-impact candidates were omitted to keep this report focused on consequential bugs. The existing untracked work was left untouched. A possible timed-reading completion/save race was not promoted to a finding because its lifecycle ordering was not reproduced.

**Verdict:** Address the backup and restore failures first, then the missing or discarded content and reading-position bugs. Finding 10 was withdrawn after device testing; 19 findings remain.

**Not checked:** Full device import and navigation lifecycles, other Android versions, Play update delivery, exhaustive malformed input, and every UI/layout and dependency implementation. Passing tests do not prove the app is bug-free.

## Repair status — branch `fix/october-26-audit`

The original findings above describe the audited revision. The following repairs are present on this branch; finding 10 remains withdrawn.

- [x] **1–3:** Backup and restore use checked, binary-safe ADB transport. Backups are validated before atomic replacement or uninstall, with failure and rollback regression cases.
- [x] **4, 8, 19:** The reader preserves chapter illustrations, resolves repeated image references by identity, and persists the selected image-only page. A database migration preserves existing positions.
- [x] **5, 14, 15:** DOCX wrappers and nested tables retain their contents; image-only DOCX and FictionBook chapters are retained; plain text is no longer decoded according to an incidental charset phrase.
- [x] **6, 16:** Dedupe compares language and markup and does not infer equality for illustrated books from text alone. Library imports and deletions are serialized so an old cleanup cannot remove newly imported assets.
- [x] **7, 12:** Web extraction scores actual article candidates and no longer drops authored paragraphs merely for containing boilerplate phrases.
- [x] **9:** Declared EPUB reading order excludes unrelated ZIP content while linked auxiliary chapters remain reachable, including numeric note links and fragment targets. Reader, timed reading, progress, and search navigation preserve the linear reading position around notes.
- [x] **11:** Titles, saved items, and passages have bounded, navigable result pages. Reader match controls can move across page boundaries.
- [x] **13, 17, 18:** MOBI prefaces, long-chapter markup, illustrations, and links are preserved. Incidental chapter references no longer discard preceding prose; unsupported image counts fail explicitly.
- [x] **20:** Exceeding the cumulative EPUB image budget or failing to read or write required images now fails the import instead of silently saving an incomplete book. The original per-image limit was already enforced; the earlier wording was corrected above.

Other issues identified during follow-up were also addressed: focus mode in settings subpages, directional dock dragging across punctuation, shared text files, update-status reporting, one-time starter-book seeding, note-export protection, timed-reading completion saves, and theme-editor input, Undo, and resize synchronization.

**Compatibility and limits:** Existing EPUBs can be re-imported from the same source to repair their stored reading-order and note-link metadata without replacing saved coordinates. If old content or anchors cannot be matched unambiguously, the repair fails explicitly and keeps the existing data; the source archive is not otherwise retained. A legacy saved location already inside an auxiliary note cannot reveal the original reading location, so its library progress is withheld until the user returns to a linear chapter. Different source files containing illustrations may now remain separate even when their pictures happen to be identical. Note export refuses to overwrite a nonempty selected document because arbitrary Android document providers do not offer a reliable atomic replacement; choose a new file. The reader image limit and EPUB extraction limits remain in force and are reported instead of silently truncating content.

**Validation:** `./gradlew qualityGate` passed with 990 JVM tests, zero failures, errors, or skips, plus script tests, static checks, Android lint, and debug assembly. The final complete isolated Android suite passed 190 tests with no failures, errors, or skips on CPH2493 (Android 16), including the theme synchronization and resize regressions. The existing Kairo installations and their data were not replaced, and the device notification setting was restored.
