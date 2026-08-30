# Material 3 Expressive migration assessment

- Status: Native alpha Expressive migration implemented for the app theme, Library, Settings,
  and import progress; automated and visual/device verification status recorded below
- Created: 30 August 2026
- Original audit baseline: b0dc6ca on chore/lint-enforcement
- Implementation baseline: 5f11a98 on main
- Implementation branch: feat/material3-expressive

## Purpose

This document records the repository investigation, the user's decision to
adopt native Material 3 Expressive on this isolated feature branch, the source
scope completed, and the remaining release qualification work.

The migration should make Kairo feel more polished, tactile, coherent, and
adaptive without compromising the calm reading experience, Reader pagination,
RSVP timing, ORP geometry, gestures, accessibility, or saved state.

## Accepted production direction

The user explicitly selected the literal native Material 3 Expressive direction
and accepted alpha risk on `feat/material3-expressive`. Kairo therefore uses
the public `MaterialExpressiveTheme`, `MotionScheme.expressive()`, expressive
typography roles and the increased shape scale from Material 3
`1.5.0-alpha27`. This supersedes the earlier stable-1.4 recommendation.

The alpha dependency is deliberately isolated and exactly pinned. It is not a
recommendation to merge or ship without completing the qualification work in
this document. The rollback is to change `compose-bom-alpha` back to the stable
`compose-bom`, remove the two alpha27 overrides and adaptive navigation-suite
dependency, then restore the prior `MaterialTheme` root while retaining any
independently approved surface styling.

Library and Settings are the broad native Expressive reference slice. Reader,
RSVP, and Bionic destinations keep their existing content metrics and run
inside `KairoFocusedReadingTheme`, which uses standard motion and the previous
calm typography/shapes. The compact `SettingsNavRow` also remains for Reader,
RSVP, Info, and other callers that rely on its existing density.

## Executive summary

Kairo is already a Compose Material 3 application. This is not a Material 2 to
Material 3 rewrite and it is not primarily an import or dependency migration.
The work is a design-system and interaction migration across a UI that contains
many intentionally custom reading surfaces.

The central theme seam made a convincing shell and standard-screen migration
moderate work. A genuinely release-complete migration remains a large visual
release because Reader and RSVP chrome still require redesign and device-level
qualification without changing their domain-specific behaviour.

Estimated effort for one experienced Compose engineer, assuming prompt product
and design decisions:

| Scope | Definition | Estimated effort |
| --- | --- | ---: |
| Theme foundation | Expressive colour, typography, shapes and motion; shared primitives; Settings and Library reference screens | 5–10 engineering days |
| Primary journeys | Library, Settings, search, import, saved content, tutorial, Reader chrome and RSVP chrome share one visual language | 25–40 engineering days |
| Release-complete | Every visible state migrated or explicitly excepted; native API decision; visual baselines; all themes; accessibility, RTL, font scaling and performance qualification | 50–80 engineering days |

These ranges carry roughly 30–50 percent uncertainty. If the visual
specification must be invented and approved while implementation is under way,
allow an additional one to three calendar weeks. Repeated upstream alpha
upgrades would be ongoing maintenance outside these estimates.

## Resolved AndroidX graph

The implementation declares `androidx.compose:compose-bom-alpha:2026.08.01`.
That BOM aligns Compose UI, Foundation, and Runtime to `1.13.0-alpha02` and
selects Material 3 and the adaptive navigation suite at `1.5.0-alpha27`. The
version catalogue retains explicit alpha27 pins for both Material 3 artifacts
so the native Expressive dependency boundary remains intentional and visible
rather than relying on implicit BOM alignment.

Gradle dependency insight for `debugRuntimeClasspath` resolves:

- `androidx.compose:compose-bom-alpha:2026.08.01`
- `androidx.compose.ui:ui:1.13.0-alpha02`
- `androidx.compose.foundation:foundation:1.13.0-alpha02`
- `androidx.compose.runtime:runtime:1.13.0-alpha02`
- `androidx.compose.material3:material3:1.5.0-alpha27`
- `androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha27`
- `androidx.compose.material3:material3-ripple:1.5.0-alpha27` transitively

The explicit alpha27 overrides remain intentional even though the selected BOM
currently aligns the same version. Some native Expressive components still
require `ExperimentalMaterial3ExpressiveApi`; those opt-ins are kept at the
narrowest theme or component boundary. This graph must be reviewed again before
merge or release because all three Compose lines are prerelease dependencies.

Official references:

- [Compose Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [MaterialExpressiveTheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable)
- [MotionScheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
- [ExperimentalMaterial3ExpressiveApi](https://developer.android.com/reference/kotlin/androidx/compose/material3/ExperimentalMaterial3ExpressiveApi)
- [Compose BOM guidance](https://developer.android.com/develop/ui/compose/bom)

## Current Kairo findings

### Foundation

- Material 3 is declared through gradle/libs.versions.toml and
  app/build.gradle.kts.
- Material 2 component coexistence is effectively icons-only. No Material 2
  button, card, dialog, theme, or typography usage was found.
- The platform themes inherit Theme.Material3.DayNight.NoActionBar.
- MainActivity uses edge-to-edge rendering and applies KairoTheme at the
  application root.
- At the original audit, `KairoTheme` supplied a `ColorScheme` and typography
  but no explicit shapes. It now supplies `MaterialExpressiveTheme`,
  `MotionScheme.expressive()`, an eight-role expressive shape scale, and a
  token-preserving expressive typography scale including emphasized roles.
- `KairoFocusedReadingTheme` preserves the former calm reading typography and
  default shapes while changing motion back to `MotionScheme.standard()`.
- Reader and RSVP have their own bundled font families and domain-specific text
  sizing.
- Dynamic colour is not currently used.

### Colour and theme model

- ReaderTheme currently has 12 persisted values:
  LIGHT, LINEN, MIST, SAGE, SEPIA, DARK, INK, PLUM, EMBER, NORD, CYBERPUNK,
  and FOREST.
- Color.kt maps all 12 themes to complete Material colour schemes.
- The mappings already provide modern roles including surface container,
  inverse, fixed, outline, and error roles.
- Surface tint is intentionally transparent.
- KairoThemeContrastTest verifies opacity, contrast, and role mappings across
  the palettes.

The existing palettes should remain authoritative unless a separate product
decision introduces optional Android dynamic colour. Expressive migration must
not silently replace the user's selected reading theme.

### UI footprint

Static inspection found:

- 137 Kotlin files under the UI package.
- 54 production Kotlin files importing Material 3.
- 26 UI files referencing explicit shape classes such as RoundedCornerShape or
  CircleShape.
- No Compose Preview annotations.
- No screenshot, golden, Paparazzi, or Roborazzi baseline framework.
- Nine Compose instrumentation test classes focused primarily on behaviour,
  semantics, accessibility, input, and scrolling.

Frequently used Material 3 building blocks include Text, Surface, Icon,
IconButton, TextButton, AlertDialog, Button, OutlinedButton,
OutlinedTextField, FilterChip, progress indicators, dividers, tabs, menus,
Switch, Slider, RadioButton, Scaffold, TopAppBar, and ModalBottomSheet.

Changing only KairoTheme would affect component defaults, but it would not
produce a complete migration. Many screens explicitly set colours, alpha,
borders, shapes, gradients, elevations, sizes, and animation values.

### Straightforward migration surfaces

These areas already rely heavily on standard Material 3 components and are good
pilot candidates:

- SettingsComponents and SettingsSectionPrimitives
- SettingsHomeScreen and the feature-specific settings screens
- LibraryScreen and LibraryBookCard
- Library import actions and supported-formats sheet
- Library momentum cards, goals, and history
- Library search
- Saved-annotation dialogs and controls
- Standard dialogs, menus, fields, chips, switches, sliders, and tabs
- KairoSnackbarHost and global non-blocking prompt styling

### Custom and higher-risk surfaces

The following areas require product and interaction decisions rather than
mechanical component replacement:

- ReaderScreenHeader and ReaderScreenContent
- Reader menus, table of contents, image viewer, search, selection, notes, and
  RSVP launcher
- RsvpScreenUi, RsvpScreenChrome, RsvpScreenControls, and
  RsvpScreenQuickSettings
- StartingTutorial and its geometry-dependent targets
- ImportProgressOverlay
- BionicReadingText presentation

Reader and RSVP contain translucent chrome, custom progress treatment,
gradients, gesture regions, fixed reading geometry, local animation, text
measurement, and specialised typography. Stock components are not automatically
better here. Custom components remain valid within a Material 3 Expressive
application.

## Implemented source slice

The current feature-branch implementation includes:

- the exact prerelease dependency graph above and native
  `MaterialExpressiveTheme` foundation;
- all 12 persisted Kairo colour schemes unchanged;
- expressive motion, increased shapes, and token-preserving emphasized
  typography for conventional application surfaces;
- a focused-reading theme boundary around Reader, RSVP, and Bionic
  destinations, without changing routes, state, paginator inputs, paragraph
  metrics, gestures, RSVP scheduling, ORP placement, or frame geometry;
- a Library shell with `LargeFlexibleTopAppBar`, filled-tonal header actions,
  adaptive `NavigationSuiteScaffold`, prominent import action, expressive book
  and import cards, and updated Saved and Momentum containers;
- preserved `LibraryTab` local saved state and a group-level tutorial target
  spanning all adaptive navigation items;
- native segmented Settings Home rows, an expressive flexible detail-app-bar
  scaffold, and expressive shared settings surfaces while retaining the compact
  row variant for focused-reading callers;
- native determinate `ContainedLoadingIndicator` in the import overlay while
  preserving progress and file/status semantics; and
- targeted instrumentation source coverage for Library navigation/restoration
  and Settings row click/minimum-height behaviour.

This is a broad reference slice, not a claim that every Kairo screen has been
visually migrated. Search, snackbar/tutorial chrome, Reader/RSVP chrome,
goldens, and the full accessibility/device matrix remain later work unless
separately accepted as intentional focused-reading exceptions.

## Validation status

- Dependency insight has confirmed the exact graph recorded above.
- Production compilation exposed one alpha27 source-compatibility change:
  `ExposedDropdownMenu` is now imported as an extension; behaviour is unchanged.
- `:app:ktlintCheck` passes.
- `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` passes with
  Kotlin warnings treated as errors.
- `qualityCheck` passes, including detekt, production and unit-test compilation,
  and the debug unit-test suite.
- Gradle reports only the repository's existing warning that deprecated Gradle
  features will be incompatible with Gradle 10; no new Kotlin/API deprecation
  warning remains after using the auto-mirrored Momentum icon.
- No connected tests, emulator, physical Android device, screenshots, or visual
  baselines have been used. Visual quality, compact/wide rendering, tutorial
  geometry, accessibility, and runtime motion therefore remain unverified.

## Target experience

Expressiveness should vary by context:

| Area | Intended treatment |
| --- | --- |
| Library, Settings, search, onboarding and import | Fully expressive hierarchy, shapes, component states and motion |
| App shell, prompts, dialogs and sheets | Expressive and consistent, with adaptive behaviour where useful |
| Reader menus and chrome | Expressive but visually restrained |
| Reading content | Preserve typography, pagination, density and calm presentation |
| RSVP controls and quick settings | Tactile state feedback with restrained spatial motion |
| Per-word RSVP playback | No decorative animation or motion that competes with timing and ORP focus |

The desired rhythm is for Kairo to feel alive while navigating and configuring,
then become calm and stable once reading begins.

## Architectural direction

### Preserve the existing root seam

KairoTheme should remain the application-facing theme provider. Native
Expressive APIs, whether stable or experimental, should be isolated behind the
theme and a small set of shared primitives rather than imported throughout
feature screens.

The foundation now includes:

- A token-preserving Kairo typography scale with emphasized roles.
- Explicit Material shapes including the increased expressive roles.
- Expressive application motion and standard focused-reading motion.
- A small set of Kairo-specific semantic tokens for reading chrome needs that
  Material does not model directly.
- Shared treatments for recurring Kairo cards, grouped settings, icon controls,
  and transient chrome.

Do not create a token for every existing dimension. RSVP collision bounds,
Reader pagination measurements, gesture thresholds, text geometry, and similar
values are domain constants rather than design tokens.

### Preserve behavioural ownership

The migration should normally leave these boundaries untouched:

- core/rsvp and RSVP frame generation
- RSVP runtime state and frame scheduling
- Reader pagination, position, focus, progress, and selection algorithms
- repositories and Room
- preference schema and existing theme persistence
- navigation routes and callback contracts

Visual composables can change around these boundaries. Their keys and state
ownership should not.

Avoid maintaining entirely separate legacy and Expressive Reader or RSVP trees
behind a runtime toggle. Replacing a whole subtree can recreate remembered
state and doubles the verification matrix. Prefer the same stateful structure
with theme-driven or leaf-component substitutions.

### Treat reduced motion as a first-class requirement

Expressive motion should emphasise occasional, meaningful transitions. It
should not be applied to:

- RSVP frame changes
- rapidly recurring reading interactions
- Reader pagination measurement
- scroll-linked effects that reduce text stability
- content changes that users need to track continuously

A reduced-motion behaviour should be specified before the custom surfaces are
migrated.

## Recommended migration sequence

### Phase 0: refresh the assessment

Before editing:

1. Refresh origin/main and select the actual implementation baseline.
2. Re-run dependency insight for Material 3 and Compose.
3. Recheck the Material 3 release notes, public theme API, opt-ins, and BOM
   guidance.
4. Recount the UI surface and inspect changes made since this document.
5. Confirm that the 12 ReaderTheme values and their persistence model remain
   current.

### Phase 1: define and baseline

1. Inventory every important normal, loading, empty, error, menu, dialog,
   sheet, overlay, and focused-reading state.
2. Approve reference designs for Settings, Library, Reader chrome, and RSVP
   chrome.
3. Record explicit exceptions for Reader content, RSVP ORP geometry, and
   Bionic text.
4. Agree a motion and reduced-motion policy.
5. Introduce screenshot or golden baselines before broad visual changes.

Do not start a wide code migration until the target visual language and the
domain exceptions are clear.

### Phase 2: theme foundation

1. Expand typography deliberately while preserving Reader-owned text.
2. Add the explicit shape system.
3. Define motion roles and restrained reading-mode behaviour.
4. Reconcile all colour roles and translucent composites across the 12 themes.
5. Extend tests for any new or changed roles.
6. Build shared primitives only where recurring usage justifies them.

### Phase 3: Settings and Library pilot

The current branch completes the broad Settings, Library, import, Saved, and
Momentum source slice. Its review should:

- validate the theme and component abstraction;
- establish real migration velocity;
- expose large-text and narrow-width issues early;
- provide product feedback before Reader and RSVP work;
- create reference screenshot coverage, which is still outstanding.

Use the pilot as a go/no-go gate. Re-estimate the full project if actual effort
differs from the planning range by more than roughly 50 percent.

### Phase 4: global shell and secondary surfaces

Migrate:

- KairoNavChrome and KairoSnackbarHost
- tutorial and update prompts
- loading and import overlays
- shared dialogs, menus, selectors, and feedback surfaces

Reverify tutorial targets after any geometry changes.

### Phase 5: Reader chrome

Migrate the header, menus, table of contents, image viewer, saved tools,
selection/search controls, and launchers around the reading content.

Keep paragraph typography, pagination inputs, position handling, gesture
semantics, selection behaviour, and focus behaviour unchanged. Stop and
investigate if the visual work changes visible word counts, restored position,
tap geometry, long-press handling, or pagination.

### Phase 6: RSVP chrome

Migrate top chrome, progress treatment, quick settings, playback controls, and
transient panels.

Keep frame generation, timing, ORP placement, sentence context, gesture
handling, play/pause state, and session keys unchanged. Expressive motion must
never run per word. Stop and investigate any missed, duplicated, delayed, or
visually unstable frame transition.

### Phase 7: native Expressive release hardening

Native alpha adoption and dependency pinning are complete on the isolated
branch. Before merge or release:

1. Complete the visual-state inventory and documented exception list.
2. Add and review screenshot baselines across representative themes and widths.
3. Run connected accessibility, device, performance, and interaction checks.
4. Re-resolve the pinned graph and review upstream alpha changes deliberately.
5. Decide whether to ship the pinned alpha, update to a newer tested build, or
   exercise the documented rollback.

## Native alpha implementation decision

The optional compatibility spike became the selected implementation direction
after the user judged the stable-inspired pilot too subtle. The branch pins
Material 3 and adaptive navigation suite at `1.5.0-alpha27`, compiles against
warnings as errors, and uses the real public Expressive theme and components.

The source change required outside the intended UI slice was limited to the
alpha27 `ExposedDropdownMenu` extension import. This does not constitute release
approval: visual rendering, connected tests, accessibility, performance, and
upstream-alpha maintenance remain explicit gates.

## Main risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Alpha API and BOM churn | High | High | Pin exact overrides; isolate native APIs; review graph before merge/release |
| Visually undefined meaning of complete | High | High | Approve references, state inventory, and exception list first |
| Inconsistent partial migration | High | High | Central tokens, shared primitives, and per-surface completion tracking |
| Missing visual regressions | High | High | Establish screenshot or golden baselines before broad changes |
| Reader pagination or readability changes | Medium | High | Keep reading typography and paginator outside the chrome migration |
| RSVP state, timing, or jank regression | Medium | Critical | Preserve runtime ownership; never animate frame-index content |
| Palette contrast regression | Medium-high | High | Test all roles and actual alpha-composited surfaces |
| Compact-landscape or large-text clipping | High | High | Verify compact widths, landscape, and 200 percent font scaling |
| RTL or TalkBack regression | Medium-high | High | Add explicit RTL, focus-order, semantics, and announcement coverage |
| Tutorial target drift | Medium | Medium-high | Reverify every target bound after geometry changes |
| Excessive token abstraction | Medium | Medium | Tokenise visual semantics only; retain domain geometry locally |

## Acceptance criteria

### Functional invariants

- No preference or database migration is introduced merely for styling.
- Reader position, progress, pagination, focus, links, selection, notes, and
  gestures remain behaviourally equivalent.
- RSVP frame content, order, timing, play/pause state, resume position, ORP
  placement, and gesture behaviour remain equivalent.
- Theme changes do not reset Reader or RSVP state.
- Back, dismiss, menu, sheet, dialog, and keyboard behaviour remains correct.

### Visual and accessibility criteria

- Every user-visible composable is marked migrated or documented as an
  intentional Kairo-specific exception.
- All primary normal, loading, empty, error, dialog, sheet, and overlay states
  have approved baselines.
- Light, darkest, and at least one chromatic theme receive full screenshot
  coverage; all 12 themes receive algorithmic colour and contrast checks.
- Reading text retains its enhanced contrast target; controls meet WCAG AA
  after alpha compositing.
- Touch targets remain at least 48 dp unless a documented focused-reading
  exception is approved.
- Portrait, compact landscape, and a wider layout are verified.
- RTL, 200 percent font scaling, keyboard input, TalkBack focus order, and
  reduced motion are verified.
- Tutorial targets still align with the controls they explain.

### Engineering and release criteria

- Existing qualityCheck and qualityGate pass on the exact final commit.
- Android test sources compile and connected UI tests pass on the selected
  device matrix.
- Screenshot or golden verification passes.
- Reader and RSVP receive manual device verification.
- UI jank and frame time do not regress beyond an agreed threshold.
- Experimental or alpha APIs are confined to the theme and shared-component
  boundary.
- The dependency graph is reproducible and the rollback path is documented.
- No unrelated files are included in the migration commits.

## Stop/go gates

1. Definition gate: do not begin broad work without reference screens, motion
   policy, state inventory, and domain exceptions.
2. Dependency gate: an alpha must resolve, compile, test, and render cleanly.
   Failure means fix or deliberately exercise the documented rollback.
3. Foundation gate: palette, contrast, typography, shape, and baseline checks
   must pass before wide screen migration.
4. Pilot gate: review Settings and Library and recalibrate effort.
5. Reader gate: stop on pagination, position, gesture, selection, focus, or
   readability regression.
6. RSVP gate: stop on ORP, playback, state restoration, timing, gesture, or
   performance regression.
7. Release gate: complete the visual inventory, goldens, accessibility,
   connected tests, manual verification, and performance checks.
8. Alpha shipping gate: branch-level alpha acceptance is recorded; shipping
   still requires an explicit release decision after qualification.

## Decisions to make when revisiting

- Which reference screen defines the final Kairo visual direction?
- Should Android dynamic colour become an optional mode, or should Kairo's
  palettes remain exclusive?
- Do the retained 12 themes require bespoke Expressive tuning after visual
  review?
- Which current Reader and RSVP surfaces are explicit Kairo-specific
  exceptions?
- Which screenshot or golden tool should be adopted?
- What reduced-motion behaviour and performance threshold are required?
- Which Android API levels, screen classes, locales, and devices make up the
  release matrix?
- Is alpha27 approved for release after qualification, or should the branch be
  updated or rolled back before merge?

## Revisit checklist

- [ ] Refresh the implementation baseline from the intended parent branch.
- [ ] Re-read this document and mark stale findings.
- [x] Resolve and record Material 3, adaptive navigation, UI, Foundation, and
  Runtime versions.
- [ ] Check the latest stable, beta, alpha, and Compose BOM guidance.
- [ ] Re-inventory theme, UI, custom shapes, motion, and test coverage.
- [ ] Approve reference designs and explicit reading-mode exceptions.
- [ ] Select screenshot tooling and capture current baselines.
- [x] Adopt native Expressive foundation on the isolated alpha branch.
- [x] Implement the broad Settings and Library source slice.
- [ ] Review the rendered slice before expanding Reader and RSVP chrome.

## Investigation boundary

The original assessment used static repository inspection, official Android
documentation, local Material 3 source artifacts, and Gradle dependency
insight. This branch now includes the native theme and broad application-source
slice recorded above. Automated check results are recorded in Validation
status; visual quality, accessibility behaviour, connected interaction tests,
and emulator/physical-device rendering remain outstanding until the branch is
rendered and reviewed.
