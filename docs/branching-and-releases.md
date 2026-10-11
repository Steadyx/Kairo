# Branching and Releases

Kairo uses a simple trunk-based flow:

- `main` is stable and should always be close to releasable.
- Day-to-day work happens on short-lived branches from `main`.
- Release branches are temporary and only used when a version needs a short freeze/QA period.
- Public release points are marked with SemVer-style git tags, for example `v0.2.0`.

## Branch Names

Use branch names that describe the area and outcome:

- `feat/core-rsvp-improvements`
- `feat/library-search`
- `fix/mobi-import-crash`
- `fix/core-tokenization`
- `refactor/reader-screen`
- `chore/gradle-cleanup`

Prefer scoped but flexible names. For example, `feat/core-rsvp-improvements` is better than a very narrow name when the change may touch the RSVP engine, tokenization, model helpers, and related tests.

Avoid broad names that can live forever, such as `core-fixes`, `misc`, or `cleanup`.

## Release Branches

Most releases can be tagged directly from `main`.

Use a release branch only when `main` is stable enough to ship but needs final release work before tagging:

```bash
git checkout main
git pull
git checkout -b release/v0.2.0
```

Allowed release-branch work:

- bump Android `versionName` and `versionCode`
- update release notes
- run final QA
- fix small release-blocking bugs

Do not add new features on a release branch.

### In-app changelog and feature tour

Settings → What’s new contains the offline changelog and a replayable feature
tour. Maintain its content in
`app/src/main/java/com/kairo/reader/ui/updates/ReleaseNotes.kt` and the
`values*/release_notes.xml` resources for every supported language.

During development, keep the newest entry's `version` and `versionCode` null.
It appears as Upcoming and can be toured manually, but never prompts users.
During release preparation, assign the actual `versionName` and `versionCode`
from `app/build.gradle.kts` to that entry. Keep older entries in descending order.
When starting the next version, prepend a new Upcoming entry. A fixes-only
release may leave its tour empty and offer just the changelog.

Review `git log --no-merges <previous-release-tag>..HEAD` and the corresponding
diffs. Summarise every user-facing addition and fix in the changelog, grouping
related commits; omit internal build, test, and refactoring work. Pick only a few
features for the tour. Keep its steps on Library or Settings so it works without
an imported book. New targets must also register their highlight bounds and
scroll requesters on those screens. The app uses packaged notes, not Git or a
network service at runtime.

The initial history covers `v1.0.7..v1.0.8`, `v1.0.8..v1.1.0`,
`v1.1.0..v1.2.0`, and `v1.2.0..v1.3.0`. The 1.4.0 entry covers
`v1.3.0..2785d82` plus the new changelog and tour itself.

Before shipping, run `./gradlew qualityGate :app:compileDebugAndroidTestKotlin`
and the `WhatsNewDeviceTest` instrumentation tests on a device. Check an upgrade
from the previous published version: the invitation appears once in an idle
library, all dismissal paths suppress it, and Settings can replay the tour.
Check a fresh install too: only the starting tutorial should appear. Verify
rotation, Back, large text, landscape, and pending external imports. Resetting
display preferences must not reset the acknowledged release version.

When the release is ready:

```bash
git checkout main
git merge --no-ff release/v0.2.0
git tag v0.2.0
git push origin main v0.2.0
git branch -d release/v0.2.0
```

## Versioning

Use SemVer-style versions:

- `MAJOR.MINOR.PATCH`
- `MINOR` for meaningful user-facing features or capability changes
- `PATCH` for bug fixes, parser hardening, and small polish
- `MAJOR` for incompatible changes or a clear production milestone

While Kairo is still evolving quickly, `0.x.y` versions are a good fit. Move to `1.0.0` when the core import, library, reader, RSVP playback, settings, and persistence flows are stable enough to treat as production-ready.

Android releases also need a monotonically increasing `versionCode`.

## Release Signing

Release builds read signing credentials from `keystore.properties` in the
project root. The generated properties file and keystore are intentionally
ignored by git.

To create a local Play upload key:

```bash
./scripts/setup-release-signing.sh
```

Then build the signed Android App Bundle:

```bash
./gradlew bundleRelease
```

The generated bundle is written to
`app/build/outputs/bundle/release/app-release.aab`.

Back up `keystore.properties` and `keystores/kairo-upload-key.jks` somewhere
private. Losing the upload key will block future updates unless Play Console
resets the upload certificate.

## Cleanup

After a branch is merged and no longer needed, delete it locally and remotely:

```bash
git branch -d feat/example-branch
git push origin --delete feat/example-branch
```
