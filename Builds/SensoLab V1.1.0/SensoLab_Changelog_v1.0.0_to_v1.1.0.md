# SensoLab — Changelog
## v1.0.0 → v1.1.0

---

## 📡 סנסורים

### סנסורים חדשים (10 → 17)

| סנסור | מה הוא מודד |
|-------|------------|
| **Step Counter** | מונה צעדים מצטבר של המכשיר + ספירת סשן נפרדת |
| **Significant Motion** | גלאי one-shot — מתעורר כשהמכשיר עובר מנוח לתנועה |
| **Gyroscope Uncalibrated** | ג'ירוסקופ גולמי ללא כיול, כולל אמידת ה-drift |
| **Proximity** | מרחק מהחיישן (Near/Far), בשימוש לזיהוי הצמדה לאוזן |
| **Relative Humidity** | אחוז לחות יחסי באוויר |
| **Ambient Temperature** | טמפרטורת הסביבה (עם המרת יחידות) |
| **Heart Rate** | דופק ב-BPM עם zones (Low/Normal/Elevated/High) |

### סנסורים ששופרו

| סנסור | שינוי |
|-------|-------|
| **Magnetometer** | הוסף חישוב כיוון מצפן מדויק (16 כיוונים: N, NNE, NE…) |
| **Barometer** | הוסף חישוב גובה בנוסחת הלחץ הסטנדרטית + תיאור מזג אוויר |
| **Accelerometer** | הוסף חישוב g-force + תיאור מצב (Still/Normal/Moving/High-G) |
| **Gyroscope** | הוסף המרה ל-°/s בנוסף ל-rad/s |
| **GPS Speed** | הוסף המרת יחידות (m/s → km/h / mph לפי הגדרות) |
| **GPS Altitude** | **חדש** — גובה GPS לא היה קיים ב-v1.0.0 |
| **Gravity** | הוסף חישוב זווית הטיה מהאנך |
| **Step Detector** | הפרדה מ-Step Counter לסנסורים נפרדים |

---

## 🧭 מצפן ויזואלי חי (חדש לחלוטין)

קלאס חדש: `CompassView.java` (208 שורות קוד)

- **ציור על Canvas** — רוז מצפן מלאה עם 16 כיוונים
- **מחוג מסתובב** — אדום לצפון, כחול לדרום
- **Smooth rotation** — אינטרפולציה (lerp) למניעת קפיצות
- **טריאנגל מחוון** קבוע בראש המסך
- **תצוגת מעלות + שם כיוון** במרכז (לדוגמה: NNE 22°)
- מופיע במצב Visual בכרטיס רחב בראש הסנסורים

---

## 🎤 מד רמת קול (חדש לחלוטין)

- שימוש ב-`AudioRecord` (לא `MediaRecorder`) לדיגום בזמן אמת
- חישוב RMS → המרה לדציבלים (30–120 dB)
- תיאור: 🤫 Silent / 🔈 Quiet / 🔉 Normal / 🔊 Loud / 📢 Very loud!
- Progress bar ויזואלי
- עובד ברקע בthread נפרד עם `daemon=true`
- מוצג בשני המצבים (טקסטואלי + ויזואלי)

---

## 📐 מערכת יחידות (חדש לחלוטין)

לא היה קיים ב-v1.0.0. נוסף לחלוטין:

| יחידה | מטרי | אמריקאי |
|-------|------|---------|
| טמפרטורה | °C | °F |
| מהירות | km/h | mph |
| גובה / מרחק | m | ft |
| לחץ | hPa | inHg |

**שינויים טכניים:**
- `AppPrefs.java` — נוספו `KEY_UNIT_SYSTEM`, `UNITS_METRIC`, `UNITS_IMPERIAL`
- 10 פונקציות המרה חדשות: `temp()`, `tempUnit()`, `speed()`, `speedUnit()`, `altitude()`, `altUnit()`, `pressure()`, `pressureUnit()`, `distance()`, `distUnit()`
- כל הסנסורים והטמפרטורות משתמשים בפונקציות אלו באופן אחיד

---

## 🌡️ מוניטורינג טמפרטורות (שדרוג משמעותי)

### v1.0.0
- 4 אזורים: Battery, CPU, GPU, Skin
- קריאה בסיסית מ-`/sys/class/thermal`

### v1.1.0
- **8 אזורים:** Battery, CPU, GPU, Skin, Modem/5G, WiFi/BT, Charger/USB, NPU/AI
- **לוגיקת זיהוי משופרת** — כל אזור מחפש לפי מספר מילות מפתח (למשל CPU: `cpu`, `cpu0`, `soc_thermal`, `mtktscpu`, `cpu_thermal`, `tsens_tz_sensor0`)
- **ממוצע אוטומטי** כשמספר מעבדים נמצאו לאותה קטגוריה
- **המרת יחידות** — כל הטמפרטורות עוברות דרך `AppPrefs.temp()` לפי הגדרת המשתמש

---

## 📤 ייצוא מפרט מערכת (חדש לחלוטין)

כפתור `📤 Export System Specs to Text File` בטאב System:

**מה מיוצא לקובץ .txt:**
- שם המכשיר, גרסת Android, CPU, RAM, Storage, מסך, ABI
- כל הטמפרטורות בזמן הייצוא
- חותמת זמן (`SensoLab_Specs_YYYYMMDD_HHMMSS.txt`)

**טכנית:**
- שמירה ב-`getExternalFilesDir(DIRECTORY_DOCUMENTS)` — ללא צורך בהרשאת Storage
- שיתוף דרך `FileProvider` + `Intent.ACTION_SEND`
- נוסף `<provider>` ל-`AndroidManifest.xml` + קובץ `res/xml/file_paths.xml`

---

## 🎨 תיקון Light Mode (באג קריטי)

### הבאג ב-v1.0.0
`values/colors.xml` (שמוגדר כ-Default/Light) הכיל **צבעים כהים**:
```
background: #0D1117  ← שחור
surface:    #161B22  ← כהה מאוד
onSurface:  #E6EDF3  ← לבן
```
כתוצאה מכך, לחיצה על "Switch to Light Mode" **לא שינתה דבר** — הממשק נשאר כהה.

### התיקון ב-v1.1.0
`values/colors.xml` מחזיק עכשיו צבעים בהירים אמיתיים:
```
background: #F0F4F8  ← אפור-כחלחל בהיר
surface:    #FFFFFF  ← לבן
onSurface:  #1A1A2E  ← כמעט שחור
primary:    #1565C0  ← כחול כהה
```
`values-night/colors.xml` מחזיק את הצבעים הכהים כמקודם.

נוסף גם `colorSurfaceVariant` ו-`colorOnSurfaceVariant` שחסרו לחלוטין ב-v1.0.0 (גרמו לשגיאות XML בסגנונות).

---

## ⚖️ מצב Visual — פריטי במצבי תצוגה

### v1.0.0 — Visual tiles (12 כרטיסים)
`accel, gyro, mag, light, pressure, gravity, lin_accel, rot, step, tilt, speed, (game_rot)`

### v1.1.0 — Visual tiles (16 כרטיסים)
כל מה שהיה + 6 חדשים: `proximity, humidity, ambient_temp, heart_rate, sig_motion, sound`

**הוסרה חוסר עקביות:** ב-v1.0.0 המצב הטקסטואלי הציג סנסורים שלא הופיעו ב-visual.

---

## 🔒 הרשאות

| הרשאה | v1.0.0 | v1.1.0 |
|--------|--------|--------|
| ACCESS_FINE_LOCATION | ✅ | ✅ |
| ACCESS_COARSE_LOCATION | ✅ | ✅ |
| ACTIVITY_RECOGNITION | ✅ | ✅ |
| BODY_SENSORS | ❌ | ✅ (heart rate) |
| RECORD_AUDIO | ❌ | ✅ (sound meter) |

---

## 🗂️ שינויי קבצים

| קובץ | v1.0.0 | v1.1.0 | שינוי |
|------|--------|--------|-------|
| `CompassView.java` | ❌ לא קיים | ✅ 208 שורות | **חדש** |
| `SensorsFragment.java` | 348 שורות | 548 שורות | +200 |
| `SystemFragment.java` | 359 שורות | 426 שורות | +67 |
| `MainActivity.java` | 53 שורות | 82 שורות | +29 |
| `AppPrefs.java` | 30 שורות | 43 שורות | +13 |
| `SettingsFragment.java` | 68 שורות | 79 שורות | +11 |
| `AboutFragment.java` | 70 שורות | 70 שורות | ללא שינוי |
| `res/xml/file_paths.xml` | ❌ לא קיים | ✅ | **חדש** (FileProvider) |
| `values/colors.xml` | באג light mode | תוקן | **תיקון קריטי** |
| `values-night/colors.xml` | ✅ | ✅ שופר | עדכון |
| `values/themes.xml` | חסרו variants | הושלם | תיקון |
| `build.gradle` | versionCode 1, "1.0" | versionCode 2, "1.1.0" | גרסה |

---

## 📊 סיכום מספרי

| מדד | v1.0.0 | v1.1.0 |
|-----|--------|--------|
| קבצי Java | 6 | 7 (+1) |
| סנסורים | 10 | 17 (+7) |
| אזורי טמפרטורה | 4 | 8 (+4) |
| Visual tiles | 12 | 16 (+4) |
| הרשאות | 3 | 5 (+2) |
| שורות קוד (סה"כ) | ~928 | ~1,448 (+56%) |
