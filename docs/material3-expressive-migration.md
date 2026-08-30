# Material 3 Expressive migration assessment

- Status: Settings Home pilot source implemented; visual/device verification outstanding
- Created: 30 August 2026
- Original audit baseline: b0dc6ca on chore/lint-enforcement
- Implementation baseline: 5f11a98 on main
- Implementation branch: feat/material3-expressive

## Purpose

This document records the repository investigation, accepted production
direction, and recommended migration strategy for giving Kairo a complete
Material 3 Expressive visual treatment. It remains the durable plan for later
phases while also recording the bounded production pilots completed against
it.

The migration should make Kairo feel more polished, tactile, coherent, and
adaptive without compromising the calm reading experience, Reader pagination,
RSVP timing, ORP geometry, gestures, accessibility, or saved state.

## Accepted production direction

The production migration will remain on stable Material 3 1.4.0. Kairo can
adopt the Expressive design language through its public stable colour,
typography, shape, surface, and component APIs without depending on internal or
alpha-only APIs.

The public native Expressive theme APIs are deferred until they reach a stable
Material 3 release. A separate alpha compatibility spike remains optional and
would be evidence-gathering only, not approval to ship an alpha dependency.

Settings Home is the first bounded production pilot. It establishes an
explicit theme seam through `KairoShapes = Shapes()`, preserving the stable
Material 3 1.4 default shape values, plus a prominent navigation-row
presentation. This is not yet a bespoke Kairo shape scale. The existing compact
row remains in place for Reader, RSVP, Info, and other callers. Library, Reader,
and RSVP migration are explicitly outside this pilot and must pass their own
later review gates.

## Executive summary

Kairo is already a Compose Material 3 application. This is not a Material 2 to
Material 3 rewrite and it is not primarily an import or dependency migration.
The work is a design-system and interaction migration across a UI that contains
many intentionally custom reading surfaces.

The central theme seam is favourable, so a convincing theme and standard-screen
pilot is moderate work. A genuinely complete, production-ready migration is a
large visual release because Reader and RSVP must be redesigned and verified
without changing their domain-specific behaviour.

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

## AndroidX status at the time of investigation

The application declares Compose BOM 2026.08.00 and a versionless Material 3
dependency. Gradle dependency insight resolved:

- androidx.compose.material3:material3:1.4.0
- androidx.compose.ui and foundation: 1.12.0

On 30 August 2026, AndroidX listed Material 3 version 1.4.0 as stable and
1.5.0-alpha27 as the latest alpha. The public MaterialExpressiveTheme API was
added in 1.5.0-alpha27. Kairo's resolved 1.4.0 source contains internal
Expressive motion and theme implementation details, but they are not public
application APIs.

Therefore:

- Applying much of the Expressive design language does not require an immediate
  alpha dependency.
- A literal adoption of the public MaterialExpressiveTheme API currently
  requires the 1.5 alpha line.
- Some individual Expressive components remain experimental even on that line.
- Android's Compose BOM guidance says alpha and beta BOMs are intended for
  testing upcoming features and are not intended for production use.
- These facts are time-sensitive and must be rechecked before implementation.

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
- At the original audit, KairoTheme supplied a ColorScheme and Typography but
  no explicit Shapes. The Settings Home pilot now passes `KairoShapes =
  Shapes()` through that seam while retaining Material 3 1.4 defaults.
- The global Typography overrides only bodyLarge and titleLarge.
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
- Eight Compose instrumentation test classes focused primarily on behaviour,
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

The foundation will likely need:

- A complete Kairo typography scale.
- Explicit Material Shapes, including increased expressive shapes when the
  selected AndroidX version supports them.
- A motion policy that distinguishes prominent transitions from recurring
  utility interactions.
- A small set of Kairo-specific semantic tokens for reading chrome needs that
  Material does not model directly.
- Shared component defaults for recurring Kairo cards, rows, grouped settings,
  icon controls, dialogs, and transient chrome.

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

Migrate Settings first, then Library, search, import, saved content, dialogs,
and sheets. This pilot should:

- validate the theme and component abstraction;
- establish real migration velocity;
- expose large-text and narrow-width issues early;
- provide product feedback before Reader and RSVP work;
- create reference screenshot coverage.

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

### Phase 7: native Expressive API and release hardening

When Material 3 1.5 is stable, or alpha risk is explicitly accepted:

1. Adopt the native theme through the isolated KairoTheme boundary.
2. Pin and record the exact dependency graph.
3. Resolve component API differences at shared adapters.
4. Complete the visual-state inventory and documented exception list.
5. Run accessibility, device, performance, and screenshot qualification.

## Alpha compatibility spike

Before committing the product migration to an alpha dependency, run a separate
one-to-two-day spike:

1. Pin Material 3 1.5.0-alpha27 or the then-current exact version without
   broadly changing the Compose BOM unless dependency alignment requires it.
2. Confirm dependency resolution and inspect transitive changes.
3. Compile production and Android test sources with warnings as errors.
4. Run qualityGate and the relevant connected tests.
5. Render a small Settings or Library reference screen under
   MaterialExpressiveTheme.
6. Check system bars, edge-to-edge, sheets, menus, fields, tabs, and current
   experimental opt-ins.
7. Record all source changes required by the alpha.

The spike is evidence, not automatic approval to ship. If the alpha introduces
unacceptable churn, the stable design-language migration should continue
without it.

## Main risks

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Alpha API and BOM churn | High | High | Stay stable initially; pin spike versions; isolate native APIs |
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
   Failure means continue on stable Material 3.
3. Foundation gate: palette, contrast, typography, shape, and baseline checks
   must pass before wide screen migration.
4. Pilot gate: review Settings and Library and recalibrate effort.
5. Reader gate: stop on pagination, position, gesture, selection, focus, or
   readability regression.
6. RSVP gate: stop on ORP, playback, state restoration, timing, gesture, or
   performance regression.
7. Release gate: complete the visual inventory, goldens, accessibility,
   connected tests, manual verification, and performance checks.
8. Alpha shipping gate: require explicit product acceptance, exact version
   pinning, and a rollback plan; otherwise wait for stable AndroidX support.

## Decisions to make when revisiting

- Is the goal an Expressive-inspired stable design or mandatory use of the
  native MaterialExpressiveTheme API?
- Must the implementation ship before Material 3 1.5 is stable?
- Which reference screen defines the final Kairo visual direction?
- Should Android dynamic colour become an optional mode, or should Kairo's
  palettes remain exclusive?
- Should all 12 themes remain, and must every theme receive bespoke tuning?
- Which current Reader and RSVP surfaces are explicit Kairo-specific
  exceptions?
- Does navigation need an adaptive navigation suite, or is the current route
  model preferable?
- Which screenshot or golden tool should be adopted?
- What reduced-motion behaviour and performance threshold are required?
- Which Android API levels, screen classes, locales, and devices make up the
  release matrix?

## Revisit checklist

- [ ] Refresh the implementation baseline from the intended parent branch.
- [ ] Re-read this document and mark stale findings.
- [ ] Re-run Gradle dependency insight for Material 3.
- [ ] Check the latest stable, beta, alpha, and Compose BOM guidance.
- [ ] Re-inventory theme, UI, custom shapes, motion, and test coverage.
- [ ] Approve reference designs and explicit reading-mode exceptions.
- [ ] Select screenshot tooling and capture current baselines.
- [ ] Run the dependency compatibility spike.
- [ ] Implement the Settings and Library pilot.
- [ ] Review the pilot before authorising Reader and RSVP migration.

## Investigation boundary

The original assessment used static repository inspection, official Android
documentation, local Material 3 source artifacts, and Gradle dependency
insight. At that historical boundary no application source had been changed
and no full test suite or emulator/physical-device rendering was required. The
Settings Home pilot now changes application source and adds deterministic
coverage, but its visual quality, accessibility behaviour, and physical-device
rendering remain outstanding until the pilot is rendered and reviewed.
