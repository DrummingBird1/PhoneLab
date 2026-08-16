# PhoneLab — Material 3 + Card-Redesign Migration Plan

Grounded in the current tree (v1.1.0 active dev). This is the "biggest visual lever"
mini-project: move off Material 2, give the readouts a real type hierarchy, and make
"Visual Mode" actually show gauges instead of slightly-bigger monospace text.

Estimated effort: ~1 week with on-device iteration. Not a flag flip.

---

## 0. Prerequisite gate (do this first)

The documented `.\gradlew assembleDebug` **cannot run** — there is no `gradlew`/`gradlew.bat`
and no `gradle-wrapper.jar` in the tree, only `gradle/wrapper/gradle-wrapper.properties`
(pins Gradle 8.9). Today the project only builds inside Android Studio.

**Before any of the phases below**, restore a headless build so each phase can be verified:

```powershell
# from Builds/PhoneLab V1.1.0/PhoneLab/ , with a system Gradle 8.9 available
gradle wrapper --gradle-version 8.9
.\gradlew assembleDebug     # must go green once
```

Do not start UI changes until `assembleDebug` produces an APK from the CLI. Every phase
ends with "build green + eyeball on device."

---

## 1. Material 2 → Material 3 theme

**Current:** `res/values/themes.xml` → `Theme.MaterialComponents.DayNight.NoActionBar`
(Material 2, 2018). Custom `colorPrimary/Surface/...` mapped to `@color/*`.

**Target:** `Theme.Material3.DayNight.NoActionBar`.

Steps:
1. Bump the Material dep if needed (already on `com.google.android.material:material:1.12.0`,
   which supports M3 — no change required).
2. Swap the parent in `themes.xml` (and any `values-night/themes.xml`).
3. Rename the M2 attrs to their M3 equivalents (this is the real work — a straight
   parent swap will drop colors):
   - `colorPrimaryVariant` → `colorPrimaryContainer` / `colorOnPrimaryContainer`
   - `colorSurface` stays, but add `colorSurfaceContainer*` tiers and `colorSurfaceVariant`
   - `colorOnSurfaceVariant` stays
   - add `colorSecondary` / `colorTertiary` (M3 expects the fuller palette)
4. Rebuild the `@color/*` set as an M3 tonal palette (or generate one and keep the brand hue).
5. Optional but recommended: **dynamic color** on Android 12+
   (`DynamicColors.applyToActivitiesIfAvailable(this)` in a custom `Application`, gated so
   older devices fall back to the static palette).

Risks / watch-outs:
- Every `?attr/colorX` reference in the 6 layouts + `CompassView`. `CompassView` colors are
  intentionally theme-independent (`compass_*` in `colors.xml`) — leave those alone.
- `CardView` (`androidx.cardview`) is the M2-era card. M3 wants `MaterialCardView`. Migrating
  the card widget is part of Phase 2 anyway.
- Test light **and** dark, and the 3-way theme setting (`AppPrefs.getNightMode`).

Exit: app launches, all 5 tabs render, light/dark/system all correct.

---

## 2. Type scale + a real card component

**Current pain (measured):** 51 `TextView`s with `textSize="11sp"` + `fontFamily="monospace"`.
Each sensor is one mono `TextView` with no hierarchy (see `fragment_sensors.xml`,
`fragment_system.xml`, `fragment_hardware.xml`).

**Target:** a reusable card layout with a 3-level type scale:
- label — 12sp, `?attr/colorOnSurfaceVariant`, weight 500
- value — 24–28sp, `?attr/colorOnSurface`, weight 500
- unit  — 13sp, muted

Steps:
1. Add M3 type tokens (or just per-view sizes to start).
2. Create `res/layout/item_sensor_card.xml` = `MaterialCardView` → (icon, label, value, unit,
   optional meter). One include, reused everywhere.
3. Replace the hand-rolled `CardView`+mono `TextView` pairs with `<include>` + a small
   binder. This is the natural point to introduce **View Binding** (kills the 109
   `findViewById` calls) — do it here so the card wiring is typed from day one.
4. Keep the scientific format strings (`X:%+.3f Y:%+.3f Z:%+.3f`, `m/s²`, `rad/s`, …) — those
   are intentionally untranslated and belong in the value slot as-is.

Exit: textual mode reads as cards with hierarchy, not a terminal dump.

---

## 3. Real gauges in Visual Mode

**Current:** except `CompassView`, "Visual Mode" = emoji + slightly larger mono text. The
Play listing sells "gauges at a glance"; this phase delivers that.

`ModeAwareFragment` already toggles textual/visual `LinearLayout`s via `bindModeLayouts` +
`applyMode()`, so the plumbing exists — this phase only fills the visual layout.

Steps:
1. Build 2–3 small custom `View`s in the `CompassView` style (canvas draw, internal lerp
   smoothing, updated on the main thread — **not** a worker thread, matching the CompassView
   contract):
   - `BarMeterView` — horizontal fill for bounded scalars (light lux, pressure hPa, humidity %).
   - `RadialGaugeView` — arc gauge for accel/gyro magnitude, sound dB.
   - reuse `CompassView` for orientation.
2. Feed them from the same values the textual cards use — route through `AppPrefs`
   unit helpers / `Formatters`, never format inline.
3. Give unbounded/unsupported sensors a graceful "—" state (many sensors are absent on a
   given device; `<uses-feature required="false">` means fragments already fall back).

Exit: toggling Display mode → Visual shows meters, and it matches the store copy.

---

## 4. Icons: emoji → vector set

**Current:** emoji are the entire icon system (📳🌀⚡💡…) — render differently per OEM, can't
be tinted, don't scale crisply.

Steps:
1. Add Material Symbols (or Tabler) as vector drawables (`vectorDrawables.useSupportLibrary`
   is already `true`).
2. Replace emoji in tab titles and card headers with `?attr/colorOnSurfaceVariant`-tinted
   vectors.
3. Tab labels: use `tab.setIcon(...)` instead of emoji in the string.

Exit: consistent, tintable icons across devices.

---

## 5. Tab overflow

**Current:** 5 fixed tabs with emoji+text will crowd/truncate on narrow phones now that
Hardware was added.

Options (pick one):
- `app:tabMode="scrollable"` on the `TabLayout`, or
- icon-only tabs (pairs with Phase 4), or
- swap to `BottomNavigationView` (bigger change, better ergonomics for 5 destinations).

Exit: no truncation at 360dp width.

---

## Rollout order & why

1. **Restore the wrapper** — nothing is verifiable without it.
2. **Phase 1 (theme)** — everything else renders on top of it; do it first so you re-QA once.
3. **Phase 2 (cards + View Binding)** — the structural readability win, and it de-risks 3/4.
4. **Phase 3 (gauges)** — the headline feature that matches the store promise.
5. **Phase 4 (icons)** and **Phase 5 (tabs)** — cheap polish, do last, can ship independently.

## Out of scope here (separate technical track)

- `ViewModel` hoisting of sensor/recording/benchmark state (rotation + theme-change resilience)
- Splitting `SensorsFragment` (~700 lines) into `SoundMeter` / `GpsTracker` / recording controller
- Lint baseline + CI (`.\gradlew lintRelease`)

These are worth doing but are refactors, not the visual payoff — keep them off the critical
path for this redesign.
