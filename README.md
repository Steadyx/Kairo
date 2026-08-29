# <p align="center">Kairo</p>

<p align="center">
  <img src="docs/assets/kairo_logo.png" alt="Kairo logo" width="220" />
</p>

<p align="center"><strong>An Android ebook reader built for momentum, focus, and high-speed reading.</strong></p>

Kairo is an RSVP-first ebook reader for Android. It imports DRM-free ebooks and text-bearing documents, presents them in a comfortable scrollable reader, and jumps into tuned RSVP playback the moment you want to accelerate.

This project is for readers who want less friction between opening a book and actually moving through it. It is also for people who enjoy experimenting with reading speed, pacing, typography, and focus-friendly interfaces.

## About Kairo

Most reading apps treat rapid serial visual presentation as a side feature. Kairo starts from the opposite direction.

Kairo is designed around a simple idea: reading should feel intentional. Sometimes that means a clean, traditional reader with chapter navigation, bookmarks, and progress tracking. Sometimes it means a minimal RSVP mode that surfaces one word or short phrase at a time with pacing that respects punctuation, sentence flow, long words, and readability.

The result is an Android reader that sits somewhere between an ebook app, a speed-reading tool, and a focused reading environment.

## What It Does

- Imports DRM-free ebooks and text-bearing documents from device storage
- Builds a local library with covers, progress, and resume state
- Opens books in a scrollable reader with chapter and page-aware navigation
- Launches RSVP reading from your current reading position
- Persists reading position, bookmarks, highlights, notes, and reader preferences
- Searches book titles, full-text passages, highlights, and notes entirely on-device
- Builds a local Momentum profile from reading sessions without requiring an account
- Gives fine-grained control over RSVP timing, display, rhythm, and readability
- Supports focus mode options for a quieter reading experience
- Includes language-aware tokenization foundations for Latin, CJK, and RTL text flows

## Features

### Library

- Import books directly from Android's document picker
- Browse your collection with extracted cover art and metadata
- Track completion progress and estimated time remaining
- Switch between Books, Saved, and Momentum views
- Filter the library by reading, completed, or all books
- Search across book titles, passages, highlights, and notes
- Review bookmarks, colour-coded highlights, and notes in one Saved view
- Track weekly minutes, words, consistency, preferred mode, and effective pace locally

### Reader

- Scrollable reading view optimized for long-form text
- Chapter navigation and table of contents access
- Page-aware progress indicators and ETA
- Inline image handling for illustrated books
- Quick handoff into RSVP mode from the current reading position
- Long-press passage selection with highlight, note, and search actions
- Full-text search within the current book with previous and next match navigation

### RSVP

- Clean full-screen RSVP playback with minimal chrome
- ORP highlighting support
- Adaptive pacing based on word length, syllables, punctuation, clause boundaries, and difficulty
- Optional peripheral context cues that stay inside the ORP focal band
- Phrase replay by double tap or playback control, with temporary rereading-aware pace easing
- Phrase chunking, blink modes, readability floors, and punctuation tuning
- Built-in reading profiles such as Balanced, Flow, Sprint, Narrative, and Study
- Live quick-tuning for tempo, typography, and layout bias

### Personalization

- Reader font size and text brightness controls
- Multiple reader themes including Light, Sepia, Dark, Nord, Cyberpunk, and Forest
- RSVP font family, font weight, brightness, and positioning controls
- Focus mode settings, including optional status bar hiding and Do Not Disturb integration
- Persistent preferences powered by DataStore

## Who It Is For

- Readers who want to move through books faster without sacrificing control
- Students and knowledge workers reading dense material on mobile
- People who like tuning interfaces to match how they think and read
- Readers who want a calmer, more stripped-back Android reading experience
- Developers interested in ebook parsing, tokenization, and RSVP engine design

## Why Kairo Feels Different

Kairo is not trying to be a generic bookstore app or a shelf full of features for their own sake. It is a reading tool built around flow.

The reader, tokenizer, persistence layer, and RSVP engine all work toward the same goal: make it easy to import a book, find your place, adjust the experience, and keep going.

## Built With

- Kotlin 2.4.10
- Jetpack Compose
- AndroidX Navigation
- Room
- DataStore
- Coil
- KSP
- Detekt and Ktlint
- Gradle Wrapper 9.7.1

## Requirements

To build and run Kairo locally you will need:

- Android Studio with Android SDK 37 installed
- JDK 17
- An Android emulator or device running Android 7.0 (API 24) or newer
- Internet access the first time Gradle dependencies are resolved

The project uses the checked-in Gradle wrapper, so you do not need to install Gradle separately.

## Build Instructions

### Android Studio

1. Clone the repository.
2. Open the project root in Android Studio.
3. Let Android Studio sync the Gradle project and install any missing SDK components.
4. Select an emulator or connected Android device running API 24+.
5. Run the `app` configuration.

Android Studio uses the `debug` build type by default. It installs as **Kairo Dev**
with application ID `com.kairo.reader.debug`, so it can coexist with the Play
Store app (`com.kairo.reader`) on the same device. The two installations keep
separate libraries, settings, and app data.

### Command Line

Build a debug APK:

```bash
./gradlew assembleDebug
```

Install the debug build on a connected device or running emulator:

```bash
./gradlew installDebug
```

Release builds do not use the debug suffix or dev label. They retain the
production application ID `com.kairo.reader` and app name `Kairo`; follow
[`docs/branching-and-releases.md`](docs/branching-and-releases.md) when
preparing a Play Store build.

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run static analysis:

```bash
./gradlew ktlintCheck detektFull
```

If you want the Android lint pass as well:

```bash
./gradlew lintDebug
```

## Project Structure

The app currently ships as a single Android module, with the code organized by responsibility:

- `app/src/main/java/com/kairo/reader/core` for domain models, tokenization, linguistics, and RSVP logic
- `app/src/main/java/com/kairo/reader/data` for parsing, persistence, repositories, and import flows
- `app/src/main/java/com/kairo/reader/ui` for library, reader, RSVP, settings, and theming
- `app/src/main/res` for Android resources, strings, and image assets

## Supported Formats

- EPUB
- MOBI
- PRC and AZW (DRM-free PalmDOC/Mobipocket variants)
- Plain text and Markdown
- HTML
- FictionBook (`.fb2` and `.fb2.zip`)
- Word documents (`.docx`)
- PDFs containing selectable text

Kairo is intended for personal reading of DRM-free files. Locked or vendor-protected ebooks, image-only PDFs, and OCR are outside the scope of the current parser pipeline.

## Current State

Kairo already includes the core reading loop:

- import a book
- browse it in your library
- read in the standard reader
- switch into RSVP playback
- save progress, bookmarks, highlights, notes, reading sessions, and preferences locally
- search the library or the current book without uploading reading data
- review a private, account-free reading profile in Momentum

The project is still evolving, especially around polish, performance, and the more experimental RSVP tuning surfaces.

## Contributing

Contributions are welcome. If you want to improve Kairo, strong areas to contribute include:

- ebook and document parsing robustness across real-world supported files
- reader and RSVP UX refinements
- tokenization and multilingual text handling
- performance work for large books
- tests for parsing, pacing, and reading-state persistence

Read [CONTRIBUTING.md](CONTRIBUTING.md) before making a change. Install the repository-managed checks once per clone:

```bash
./scripts/setup-dev.sh
```

Before opening a change, run the required quality gate:

```bash
./gradlew qualityGate
```

## Vision

Kairo is aimed at readers who want speed without chaos, focus without clutter, and a reading app that feels like it was built for deliberate forward motion.

If that sounds like your kind of reader, you are in the right place.
