# Theme studio

Open **Settings → Theme & fonts**. Palette cards preview the built-in themes and the retained custom design. Changes stay in a saveable draft until **Apply theme** saves colours, the active theme, and all three font choices atomically. Leaving a changed draft offers **Keep editing** or **Discard**. Selecting a built-in palette preserves the custom design. **Start again** resets the draft only.

The compact preview stays above the controls on phones and beside them on wider screens. Palettes, Colours, and Fonts have separate tabs; each retains its own position. Select a colour swatch to edit it inline with hue/saturation/brightness sliders or a six-digit hex value. Valid edits update the preview immediately, without a confirmation dialog. Incomplete hex input cannot change the palette and disables Apply while that editor is visible. **Follow palette** removes that role's override. Editing a colour or harmony selects the custom theme automatically.

## Colour roles

| Role | Controls |
| --- | --- |
| Background | Page and reading background; the palette seed |
| Surfaces | Cards, sheets, and successive surface containers |
| Text | Reading text and content on surfaces |
| Primary | Main actions, selection, and RSVP focus colour |
| Secondary | Supporting actions and accents |
| Tertiary | Additional accents and links |

Tonal uses the background hue; analogous uses neighbouring hues; complementary uses opposing hues; triadic uses hues spaced 120 degrees apart. Neutral backgrounds use a stable warm seed hue. Accent saturation is reduced for supporting roles.

The generator preserves the opaque background exactly. It derives a common readable surface range and independently resolves text on filled controls, error colours, inverse surfaces, outlines, and fixed roles. It moves requested colours toward black or white only when necessary to achieve at least 4.5:1 for generated text/accent pairs and 3:1 for outlines. Stored overrides remain the user's requested colours, so changing the background can resolve them differently. This uses deterministic HSL hue relationships and sRGB luminance; it does not claim perceptually uniform colour distances.

The threshold follows [WCAG's minimum text contrast guidance](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html). The generator checks both calculated colours and their final 8-bit sRGB values. For custom themes, Reader and RSVP body text also resolve the requested brightness against the background and retain at least 4.5:1 contrast. Bionic text and fixation colours are corrected against the card and active-frame backgrounds. The stored brightness preference is preserved; dimming stops where further dimming would break the contrast threshold. These checks cover palette roles and these reading paths, not a claim of whole-app WCAG conformance: other overlays, secondary contextual text, focus indicators, and interactions still need a broader accessibility audit.

## Fonts and storage

Reader, interface, and RSVP/Bionic font choices are independent. Existing installations retain Merriweather for Reader, system sans for the interface, and their existing timed-reading font. Font preferences remain independent of the chosen palette.

The eight choices are Inter, Roboto, Merriweather, Lora, Lexend, system sans, system serif, and monospace. The five named fonts are bundled; system families use Android's installed fonts and glyph fallback. The new font files come from the Google Fonts [Lora](https://github.com/google/fonts/tree/main/ofl/lora) and [Lexend](https://github.com/google/fonts/tree/main/ofl/lexend) directories, retrieved on 2026-09-12. Their SIL Open Font Licences are packaged in `app/src/main/assets/licenses/`.

`custom_theme_v1` contains a versioned colour-role encoding. Missing or malformed fields fall back individually; unknown encoding versions fall back to the default custom design. Existing `reader_theme` and `rsvp_font_family` keys retain their meanings. The draft survives activity recreation without writing preferences.

## Verification

- Unit tests cover colour encoding, malformed values, neutral/extreme/saturated backgrounds, all four harmonies, pinned roles, and 2,000 deterministic generated/overridden palettes.
- Device tests cover stored preference mapping for every font, draft recreation, applying colour/font changes together, discard behaviour, settings search, large-text control access, fixed preview placement while dragging, landscape layout, reset synchronisation, and tab transitions.
- Run `./gradlew qualityGate` for deterministic checks. The theme device tests are `ThemeSettingsDeviceTest` and `ThemePreferencesDeviceTest`.
