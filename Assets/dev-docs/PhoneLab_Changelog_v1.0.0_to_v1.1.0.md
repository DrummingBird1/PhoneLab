# PhoneLab — Changelog
## v1.0.0 → v1.1.0

A Hebrew version of this document is in [`PhoneLab_Changelog_v1.0.0_to_v1.1.0.he.md`](PhoneLab_Changelog_v1.0.0_to_v1.1.0.he.md).

---

## 📡 Sensors

### New sensors (10 → 17)

| Sensor | What it measures |
|--------|------------------|
| **Step Counter** | The device's cumulative step count, plus a separate per-session count |
| **Significant Motion** | One-shot detector that fires when the device goes from rest to motion |
| **Gyroscope Uncalibrated** | Raw, uncalibrated gyroscope, including the drift estimate |
| **Proximity** | Distance from the sensor (Near/Far), used to detect the phone at the ear |
| **Relative Humidity** | Relative humidity of the air, in percent |
| **Ambient Temperature** | Ambient temperature (with unit conversion) |
| **Heart Rate** | Heart rate in BPM with zones (Low/Normal/Elevated/High) |

### Improved sensors

| Sensor | Change |
|--------|--------|
| **Magnetometer** | Added an accurate compass heading (16 directions: N, NNE, NE…) |
| **Barometer** | Added altitude from the standard pressure formula, plus a weather description |
| **Accelerometer** | Added g-force and a state description (Still/Normal/Moving/High-G) |
| **Gyroscope** | Added conversion to °/s in addition to rad/s |
| **GPS Speed** | Added unit conversion (m/s → km/h / mph per the settings) |
| **GPS Altitude** | **New** — GPS altitude did not exist in v1.0.0 |
| **Gravity** | Added the tilt angle from vertical |
| **Step Detector** | Separated from Step Counter into its own sensor |

---

## 🧭 Live visual compass (all new)

New class: `CompassView.java` (208 lines)

- **Canvas drawing** — a full compass rose with 16 directions
- **Rotating needle** — red for north, blue for south
- **Smooth rotation** — interpolation (lerp) to avoid jumps
- **Fixed triangle indicator** at the top of the view
- **Degrees and direction name** in the center (for example: NNE 22°)
- Shown in Visual mode as a wide card above the sensors

---

## 🎤 Sound level meter (all new)

- Uses `AudioRecord` (not `MediaRecorder`) for real-time sampling
- Computes RMS and converts it to decibels (30–120 dB)
- Description: 🤫 Silent / 🔈 Quiet / 🔉 Normal / 🔊 Loud / 📢 Very loud!
- Visual progress bar
- Runs in the background on a separate thread with `daemon=true`
- Shown in both modes (textual and visual)

---

## 📐 Unit system (all new)

Did not exist in v1.0.0. Added from scratch:

| Unit | Metric | Imperial |
|------|--------|----------|
| Temperature | °C | °F |
| Speed | km/h | mph |
| Altitude / distance | m | ft |
| Pressure | hPa | inHg |

**Technical changes:**
- `AppPrefs.java` — added `KEY_UNIT_SYSTEM`, `UNITS_METRIC`, `UNITS_IMPERIAL`
- 10 new conversion functions: `temp()`, `tempUnit()`, `speed()`, `speedUnit()`, `altitude()`, `altUnit()`, `pressure()`, `pressureUnit()`, `distance()`, `distUnit()`
- All sensors and temperatures use these functions consistently

---

## 🌡️ Temperature monitoring (major upgrade)

### v1.0.0
- 4 zones: Battery, CPU, GPU, Skin
- Basic reading from `/sys/class/thermal`

### v1.1.0
- **8 zones:** Battery, CPU, GPU, Skin, Modem/5G, WiFi/BT, Charger/USB, NPU/AI
- **Improved detection logic** — each zone is matched by several keywords (for example CPU: `cpu`, `cpu0`, `soc_thermal`, `mtktscpu`, `cpu_thermal`, `tsens_tz_sensor0`)
- **Automatic averaging** when several sensors are found for the same category
- **Unit conversion** — all temperatures go through `AppPrefs.temp()` according to the user's setting

---

## 📤 System spec export (all new)

A `📤 Export System Specs to Text File` button in the System tab:

**What is exported to the .txt file:**
- Device name, Android version, CPU, RAM, storage, screen, ABI
- All temperatures at the time of export
- A timestamp (`PhoneLab_Specs_YYYYMMDD_HHMMSS.txt`)

**Technical:**
- Saved to `getExternalFilesDir(DIRECTORY_DOCUMENTS)` — no Storage permission needed
- Shared through `FileProvider` + `Intent.ACTION_SEND`
- Added a `<provider>` to `AndroidManifest.xml` and the file `res/xml/file_paths.xml`

---

## 🎨 Light mode fix (critical bug)

### The bug in v1.0.0
`values/colors.xml` (the default/light set) contained **dark colors**:
```
background: #0D1117  ← black
surface:    #161B22  ← very dark
onSurface:  #E6EDF3  ← white
```
As a result, tapping "Switch to Light Mode" **changed nothing** — the UI stayed dark.

### The fix in v1.1.0
`values/colors.xml` now holds real light colors:
```
background: #F0F4F8  ← light blue-gray
surface:    #FFFFFF  ← white
onSurface:  #1A1A2E  ← almost black
primary:    #1565C0  ← dark blue
```
`values-night/colors.xml` keeps the dark colors as before.

Also added `colorSurfaceVariant` and `colorOnSurfaceVariant`, which were missing entirely in v1.0.0 (they caused XML errors in styles).

---

## ⚖️ Visual mode — tiles per display mode

### v1.0.0 — Visual tiles (12 cards)
`accel, gyro, mag, light, pressure, gravity, lin_accel, rot, step, tilt, speed, (game_rot)`

### v1.1.0 — Visual tiles (16 cards)
Everything that was there, plus 6 new: `proximity, humidity, ambient_temp, heart_rate, sig_motion, sound`

**Removed an inconsistency:** in v1.0.0 the textual mode showed sensors that did not appear in visual mode.

---

## 🔒 Permissions

| Permission | v1.0.0 | v1.1.0 |
|------------|--------|--------|
| ACCESS_FINE_LOCATION | ✅ | ✅ |
| ACCESS_COARSE_LOCATION | ✅ | ✅ |
| ACTIVITY_RECOGNITION | ✅ | ✅ |
| BODY_SENSORS | ❌ | ✅ (heart rate) |
| RECORD_AUDIO | ❌ | ✅ (sound meter) |

---

## 🗂️ File changes

| File | v1.0.0 | v1.1.0 | Change |
|------|--------|--------|--------|
| `CompassView.java` | ❌ does not exist | ✅ 208 lines | **New** |
| `SensorsFragment.java` | 348 lines | 548 lines | +200 |
| `SystemFragment.java` | 359 lines | 426 lines | +67 |
| `MainActivity.java` | 53 lines | 82 lines | +29 |
| `AppPrefs.java` | 30 lines | 43 lines | +13 |
| `SettingsFragment.java` | 68 lines | 79 lines | +11 |
| `AboutFragment.java` | 70 lines | 70 lines | unchanged |
| `res/xml/file_paths.xml` | ❌ does not exist | ✅ | **New** (FileProvider) |
| `values/colors.xml` | light mode bug | fixed | **Critical fix** |
| `values-night/colors.xml` | ✅ | ✅ improved | Update |
| `values/themes.xml` | missing variants | completed | Fix |
| `build.gradle` | versionCode 1, "1.0" | versionCode 2, "1.1.0" | Version |

---

## 📊 Summary in numbers

| Metric | v1.0.0 | v1.1.0 |
|--------|--------|--------|
| Java files | 6 | 7 (+1) |
| Sensors | 10 | 17 (+7) |
| Temperature zones | 4 | 8 (+4) |
| Visual tiles | 12 | 16 (+4) |
| Permissions | 3 | 5 (+2) |
| Lines of code (total) | ~928 | ~1,448 (+56%) |
