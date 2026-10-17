<div align="center">

<img src="Assets/PhoneLab_icon_512x512.png" width="96" alt="PhoneLab icon">

# PhoneLab

[![Android CI](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/DrummingBird1/PhoneLab)](https://github.com/DrummingBird1/PhoneLab/releases/latest)

**קרא בשפה אחרת:** [English](README.md) · עברית · [Español](README.es.md) · [العربية](README.ar.md)

[**⬇ הורדת ה-APK האחרון**](https://github.com/DrummingBird1/PhoneLab/releases/latest) · [אתר האפליקציה](https://drummingbird1.github.io/PhoneLab/) · [מדיניות פרטיות](https://drummingbird1.github.io/phonelab-privacy/)

</div>

<div dir="rtl" align="right">

---

## מה זה PhoneLab?

**PhoneLab** היא אפליקציית Android במסך אחד שמציגה בזמן אמת בדיוק מה החומרה של המכשיר שלכם עושה כרגע — כל חיישן, כל אזור טמפרטורה, כל פרט מערכת. מיועדת למפתחים שבודקים התנהגות חיישנים, לחובבי טכנולוגיה, לגיימרים שעוקבים אחרי חום המעבד, או לכל מי שסתם סקרן מה יש בתוך הטלפון.

בלי פרסומות. בלי חשבון משתמש. בלי שירותי רקע כשלא משתמשים באפליקציה. האפליקציה רק קוראת חיישנים וקבצי מערכת שכל אפליקציית Android יכולה לגשת אליהם ממילא — לגרסת GitHub / F-Droid **אין הרשאת אינטרנט בכלל** (גרסת Google Play מוסיפה את Google Play Billing עבור צנצנת הטיפים האופציונלית: Google מעבדת את הטיפים ומקבלת יומני אבחון מספריית החיוב, ואילו הקוד של PhoneLab עצמו אף פעם לא משתמש ברשת). הפרטים המלאים ב[מדיניות הפרטיות](https://drummingbird1.github.io/phonelab-privacy/).

## צילומי מסך

<div align="center">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_sensors.png" width="180" alt="לשונית חיישנים (מצב חזותי)">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_hardware.png" width="180" alt="לשונית חומרה">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_device_check.png" width="180" alt="בדיקת מכשיר">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_tools.png" width="180" alt="לשונית כלים">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_system.png" width="180" alt="לשונית מערכת (מצב חזותי)">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6_settings.png" width="180" alt="לשונית הגדרות">
</div>

## תכונות עיקריות

**📡 טאב חיישנים** — קריאות חיות מ-24 חיישנים (מד תאוצה, ג'ירוסקופ, מגנטומטר, כוח כבידה, וקטורי סיבוב, ברומטר, תאורה, קרבה, לחות, טמפרטורת סביבה, דופק, מונה צעדים, גלאי הטיה ותנועה ועוד), בנוסף למהירות GPS ומד רמת קול חי. זמינות החיישנים תלויה במכשיר — האפליקציה מסמנת בבירור מה לא קיים בחומרה שלכם.

**⚙️ טאב מערכת** — דגם המכשיר, גרסת Android, מעבד/זיכרון/אחסון, טמפרטורות אזורי חום (מעבד/GPU/סוללה/מארז ועוד) עם התראות צבעוניות, בנצ'מרק ביצועים ב-4 שלבים, הקלטת סשן ל-CSV שממשיכה לרוץ ברקע דרך שירות חזית (foreground service), וייצוא מפרט לקובץ טקסט.

**🔧 טאב חומרה** — תדירות מעבד חיה לכל ליבה, נתוני סוללה מפורטים, יכולות תצוגה (קצב רענון, HDR), ודגלי יכולת חומרה (NFC, Bluetooth, מספר מצלמות, רישום ביומטרי).

**🧰 טאב כלים** — פלס מים, כיול מצפן, גלאי מתכות מבוסס מגנטומטר, וסייסמוגרף רעידות עם ניתוח תדרים.

**✅ בדיקת מכשיר** — 11 בדיקות חומרה מודרכות (מסך מגע, ריבוי מגע, תצוגה, רמקול, מיקרופון, רטט, כפתורים, קרבה, פנס, חיישני תנועה, סוללה) עם דוח עבר/נכשל לשיתוף — שימושי כשקונים או מוכרים טלפון משומש.

**🔋 בריאות סוללה** — מצב הסוללה, הספק טעינה בוואט, קיבולת משוערת, מחזורי טעינה והיסטוריית רמה של 24 שעות.

**🏠 ווידג'ט למסך הבית ואריח בהגדרות המהירות** — בדקו את טמפרטורת המעבד בלי לפתוח את האפליקציה.

**🔔 התראות** — כללים מותאמים אישית לכל קריאה ("תתריע כש-X עולה מעל Y") וגם התראות טמפרטורת מעבד, כולן עם היסטרזיס כדי שלא יציפו אתכם.

**🎨 שני מצבי תצוגה** — טקסטואלי (מספרים גולמיים, ידידותי למפתחים) או ויזואלי (אייקונים, מדי-עוצמה, פסי התקדמות) — מעבר בלחיצה אחת.

**✨ Material 3** עם צבעי Material You אופציונליים, **🌙 ערכת נושא כהה/בהירה**, **🌐 4 שפות** (אנגלית, עברית, ספרדית, ערבית, עם תמיכה מלאה ב-RTL), **📐 יחידות מטריות/אימפריאליות**.

## PhoneLab Web

לוח מחוונים דפדפני נלווה נמצא בתיקיית [`Web/`](Web/) בריפו הזה ורץ בכתובת **[sensolab-web.vercel.app](https://sensolab-web.vercel.app)** — בלי צורך בהתקנה. הוא משקף את מה שפלטפורמת הווב חושפת בדפדפן/במכשיר הנוכחי שלכם: חיישני תנועה וכיוון, GPS, תאורת סביבה, בדיקת מהירות אינטרנט, וייצוא ל-CSV/PNG של כל מה שהוא קורא. הכול רץ בצד הלקוח — שום דבר לא נשלח לשום מקום.

## הורדה

- **APK:** הורידו את `PhoneLab-v<version>.apk` מ**[עמוד ה-Releases](https://github.com/DrummingBird1/PhoneLab/releases)** — כל גרסה מפרטת מה השתנה בשפה פשוטה.
- **Obtainium:** הוסיפו את `https://github.com/DrummingBird1/PhoneLab` כדי לקבל עדכונים ישירות מה-Releases.
- **F-Droid / IzzyOnDroid:** בתהליך הגשה.
- **Google Play:** בקרוב.

גרסאות GitHub / F-Droid (ה-flavor‏ `foss`) לא כוללות ספריות קנייניות ואין להן הרשאת אינטרנט. גרסת Google Play חתומה על ידי Google, ולכן מעבר בין Play להתקנת APK מחייב הסרה קודם.

## בנייה מהמקור

דרישות: **JDK 21**, Android SDK 36, ו-Gradle wrapper (מגיע מוכן בריפו — אין צורך להתקין Gradle בנפרד).

לאפליקציה שני flavors: ‏`foss` (‏GitHub, ‏F-Droid וחנויות נוספות — בלי ספריות קנייניות) ו-`play` (‏Google Play, מוסיף את צנצנת הטיפים של Play Billing).

```bash
cd App
./gradlew assembleFossDebug    # APK debug → app/build/outputs/apk/foss/debug/
./gradlew assembleFossRelease  # APK release → app/build/outputs/apk/foss/release/
./gradlew test                 # בדיקות יחידה JUnit
```

בנייה חתומה דורשת מפתח חתימה: העתיקו את `App/key.properties.template` ל-`App/key.properties` ומלאו את פרטי ה-keystore שלכם. בלי `key.properties`, בנייה release עדיין תעבוד — רק לא חתומה.

ראו את [CLAUDE.md](CLAUDE.md) לסקירה ארכיטקטונית מלאה (Fragments, מחלקות עזר, מודל ההרשאות, פרטים לא-מובנים-מאליהם) — זה אותו מסמך אוריינטציה שמשמש לפיתוח בעזרת בינה מלאכותית בריפו הזה, והוא משמש גם כתיעוד טכני חי.

## ערימת טכנולוגיות

Java, Views קלאסיים של Android + Material Components (בלי Compose), `ViewPager2` + `TabLayout`, `WorkManager` לבדיקות חום ברקע, `Service` בחזית להקלטת CSV, `TileService` לאריח בהגדרות המהירות, ו-`AppWidgetProvider` לווידג'ט מסך הבית. לוח המחוונים הדפדפני בנוי ב-Vite + TypeScript, בלי framework.

## מבנה הפרויקט

```
PhoneLab/
├── App/            פרויקט ה-Gradle — פתחו את זה ב-Android Studio
├── Web/            לוח המחוונים הדפדפני (Vite + TypeScript)
├── Assets/         תוכן לחנות, אייקונים, צילומי מסך, Changelogs
├── fastlane/       טקסט, אייקון וצילומי מסך לחנויות (F-Droid / IzzyOnDroid)
├── Distribution/   מתכון F-Droid ומדריך חנויות (בנייה ומפתחות לא נכללים בגיט)
└── Archive/        תמונות קפואות של גרסאות ישנות יותר
```

## רישיון

PhoneLab היא תוכנה חופשית ברישיון **[GNU General Public License v3.0](LICENSE)** ‏(GPL-3.0-only). מותר להשתמש בה, ללמוד אותה, לשתף ולשנות אותה; גרסאות משונות שמופצות חייבות להישאר באותו רישיון עם קוד מקור זמין.

</div>
