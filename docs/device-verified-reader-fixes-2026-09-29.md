# Device-verified reader fixes

- Date: 2026-09-29
- Baseline: `main` at `3ba2873`
- Branch: `fix/device-verified-reader-issues`
- Device: CPH2493, Android 16
- Package tested: `com.kairo.reader.debug`

## Reproduction before fixes

The three regression tests were installed and run on the physical handset with
production sources unchanged from the baseline. All three failed for the reported
behavior, before any production fixes were applied.

| Issue | Device evidence before fixing | Result after fixing |
| --- | --- | --- |
| Exit saves a stale location after a chapter change | The real Reader route paged into chapter index 1 at token 0. Returning to the library saved token 678 instead. Assertion: `expected:<0> but was:<678>`. | The saved chapter and token match the visible location; reopening restores that location. |
| Accepted large text cannot be reopened | The file importer accepted a 1,475,000-byte TXT file, producing 3,099,997 bytes of HTML plus plain text in one chapter. Reading it through Room threw `SQLiteBlobTooBigException: Row too big to fit into CursorWindow`. | The imported chapter's HTML and plain text are read back in full and compare exactly to the imported content. |
| Focus navigation on the same page does not scroll | A Compose device fixture using the reader's actual list-state controller moved focus from paragraph 0 to paragraph 12 without changing page identity. The first visible paragraph stayed 0. Assertion: `expected:<12> but was:<0>`. | Paragraph 12 becomes visible. Focusing a paragraph already in view preserves the viewport's scroll offset. |

The scrolling reproduction tests the shared reader component on the handset;
it does not automate typing into the full book-search dialog.

## Changes

- Save a complete current Reader location from the ViewModel at exit, instead of
  combining an old remembered token index with a new chapter. Preserve a pending
  RSVP result while the ViewModel catches up.
- Respond to focus changes within the same page, scrolling only when the target
  paragraph is outside the visible list items.
- Read chapter bodies in 128 KiB byte chunks inside a transaction. Decode UTF-8
  after reassembly, preserving characters across chunk boundaries. Apply the same
  bounded reads to book loading, duplicate-content comparison, passage search,
  and EPUB navigation repair. No schema or chapter-coordinate migration is needed.
- Distinguish a missing chapter from an existing empty body, including Android's
  handling of zero-length BLOB results.

## Verification

- `qualityGate` and `:app:assembleDebugAndroidTest`: passed using JDK 19.
- JVM suite: 882 tests, zero failures, errors, or skips.
- Physical-device run: 39 tests, all passed in 19.497 seconds.
- New device suites: `LargeChapterDeviceTest`, `ReaderFocusNavigationDeviceTest`,
  and `ReaderPositionDeviceTest`.
- Existing device suites: `ImportMaintenanceDeviceTest`, `PersistenceIntegrityTest`,
  `Migration12To13Test`, `Migration13To14Test`, `ReaderCoverChapterTest`, and
  `ReaderNoteDialogTest`.
- Additional storage coverage: existing oversized chapters, supplementary Unicode
  characters across chunk boundaries, embedded NUL, search offsets near the end
  of a large chapter, empty bodies, exact chunk-size bodies, and missing chapters.
- `git diff --check`: passed.

Database tests used in-memory databases. The real Reader-route test imported a
uniquely named disposable EPUB and removed its book, position, and session records
in cleanup. The release app was not installed over or cleared. The debug app's
preferences SHA-256 stayed identical before and after the device runs:
`79e756d01e72d69ba0b94ef3faa985ce0d2ce51f5b559883129d8e6b4cfede20`.

Local diagnostic logs from this run:

- `/private/tmp/kairo-reader-baseline-device.txt`
- `/private/tmp/kairo-reader-final-device.txt`
- `/private/tmp/kairo-reader-quality-gate.txt`
