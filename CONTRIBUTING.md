# Contributing to Kairo

Kairo keeps a zero-baseline quality policy. New code must meet the current standards when it enters the repository; existing rules must not be weakened to accommodate a change.

## Set up the checkout

Use JDK 17 and the checked-in Gradle wrapper. Install the repository-managed Git hooks once per clone:

```bash
./scripts/setup-dev.sh
```

The hooks are an early warning system. The GitHub `Quality gate` check is authoritative and should be configured as a required branch-protection check for `main`.

## Required commands

For ordinary Kotlin, test, or resource work:

```bash
./gradlew qualityCheck
```

For UI, Android integration, manifest, dependency, build, or release work:

```bash
./gradlew qualityGate
```

`qualityCheck` runs ktlint, Detekt, debug compilation, and the debug unit-test suite. `qualityGate` also runs Android Lint and assembles the complete debug APK so Android resources, packaging, and manifests are verified.

Run Android Lint directly when you need its focused diagnostics:

```bash
./gradlew lintDebug
```

Do not report Lint as passing without a completed run. If the analyzer stalls in a local environment, report that separately; do not weaken the gate or add a lint baseline to hide it.

Before committing, also run:

```bash
git diff --check
```

Connected instrumentation tests remain required for changes to device behavior when an emulator or handset is available:

```bash
./gradlew connectedDebugAndroidTest
```

Connected tests leave the app and test APKs installed by default. The project sets
`android.injected.androidTest.leaveApksInstalledAfterRun=true` in `gradle.properties`
to prevent Gradle's post-test uninstall from deleting your debug library and
preferences. This does not prevent a test from explicitly clearing data; keep
destructive tests on a disposable emulator and use isolated test databases and
preferences. Do not override this setting or uninstall/clear the debug app during
routine testing on a personal device.

## Keep local builds incremental

Gradle's daemon, build cache, and incremental task outputs make repeated local
builds much faster. Preserve them and run the narrowest relevant task while
developing.

- Do not routinely add `--no-daemon`, run `clean`, use `--rerun-tasks`, or
  delete Gradle caches. Use those tools only when a specific diagnosis requires
  invalidating normal reuse.
- Do not launch concurrent Gradle builds against the same checkout. They compete
  for CPU, memory, and cache locks and can make both builds slower or unstable.
- Keep `qualityCheck`, `qualityGate`, Android Lint, release compilation, R8, and
  resource shrinking intact. Local performance tuning is not a reason to skip
  or weaken repository and release checks.

## Kotlin and architecture standards

- Keep domain code independent from Compose and Android where practical.
- Prefer immutable data and explicit state/action/request/result contracts.
- Keep functions cohesive. Extract named stages when parsing, timing, rendering, or navigation begins coordinating multiple responsibilities.
- Use named constants for business constraints, timing factors, protocol offsets, masks, and limits.
- Keep raw numeric values only in self-explanatory static tables or narrow documented representations.
- Preserve stable token indices, reading positions, and parser fallbacks when refactoring.
- Keep Compose functions declarative. State derivation and business decisions belong in testable Kotlin helpers or state holders.
- Reuse existing repository, tokenizer, RSVP, preference, and navigation seams.
- Do not leave unused compatibility parameters, constant-return helpers, debug output, commented-out implementations, or generated artifacts.

## Tests

Every bug fix needs a regression test that fails for the broken behavior. New behavior needs focused tests at the lowest useful level:

- parser tests for malformed, truncated, boundary, and fallback inputs;
- tokenization tests for indices, punctuation, paragraphs, links, CJK, and RTL text;
- RSVP tests for frame boundaries, duration, resume cursors, and punctuation policy;
- preference tests for defaults, normalization, serialization, and migration;
- Compose/instrumentation tests for user-visible navigation and interaction behavior.

Tests must assert behavior rather than implementation structure unless the structure itself is a supported contract.

## Static-analysis policy

- Detekt and ktlint run with no debt baseline.
- Do not add baseline files or configure `ignoreFailures`.
- Do not disable a rule, broaden an exclusion, or raise a threshold as part of an unrelated feature or bug fix.
- A policy change requires explicit justification, evidence that the old rule produces structural noise, and a clean `qualityGate` run.
- Use a suppression only when extraction would make an inherent declarative or tabular representation less clear. Scope it to one declaration and explain it next to the annotation.

## Pull requests

Keep changes reviewable and domain-focused. The PR description must explain behavior, risk, tests, and any deliberate suppression or policy change. Do not merge until the required `Quality gate` check passes.
