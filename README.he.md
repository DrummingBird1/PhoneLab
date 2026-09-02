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

בלי פרסומות. בלי חשבון משתמש. בלי שירותי רקע כשלא משתמשים באפליקציה. האפליקציה רק קוראת חיישנים וקבצי מערכת שכל אפליקציית Android יכולה לגשת אליהם ממילא — היא **אף פעם לא מתחברת לאינטרנט**. הפרטים המלאים ב[מדיניות הפרטיות](https://drummingbird1.github.io/phonelab-privacy/).

## צילומי מסך

<div align="center">
<img src="Assets/screenshots/PhoneLab_screenshot_1_sensors.png" width="200" alt="טאב חיישנים">
<img src="Assets/screenshots/PhoneLab_screenshot_2_system.png" width="200" alt="טאב מערכת">
<img src="Assets/screenshots/PhoneLab_screenshot_3_hardware.png" width="200" alt="טאב חומרה">
<img src="Assets/screenshots/PhoneLab_screenshot_4_about.png" width="200" alt="טאב אודות">
<img src="Assets/screenshots/PhoneLab_screenshot_5_settings.png" width="200" alt="טאב הגדרות">
</div>

## תכונות עיקריות

**📡 טאב חיישנים** — קריאות חיות מ-24 חיישנים (מד תאוצה, ג'ירוסקופ, מגנטומטר, כוח כבידה, וקטורי סיבוב, ברומטר, תאורה, קרבה, לחות, טמפרטורת סביבה, דופק, מונה צעדים, גלאי הטיה ותנועה ועוד), בנוסף למהירות GPS ומד רמת קול חי. זמינות החיישנים תלויה במכשיר — האפליקציה מסמנת בבירור מה לא קיים בחומרה שלכם.

**⚙️ טאב מערכת** — דגם המכשיר, גרסת Android, מעבד/זיכרון/אחסון, טמפרטורות אזורי חום (מעבד/GPU/סוללה/מארז ועוד) עם התראות צבעוניות, בנצ'מרק ביצועים ב-4 שלבים, הקלטת סשן ל-CSV שממשיכה לרוץ ברקע דרך שירות חזית (foreground service), וייצוא מפרט לקובץ טקסט.

**🔧 טאב חומרה** — תדירות מעבד חיה לכל ליבה, נתוני סוללה מפורטים, יכולות תצוגה (קצב רענון, HDR), ודגלי יכולת חומרה (NFC, Bluetooth, מספר מצלמות, רישום ביומטרי).

**🏠 ווידג'ט למסך הבית ואריח בהגדרות המהירות** — בדקו את טמפרטורת המעבד בלי לפתוח את האפליקציה.

**🔔 התראות חום** — התראה אופציונלית כשטמפרטורת המעבד חוצה סף מסוים, עם היסטרזיס כדי שלא תציף אתכם בהתראות.

**🎨 שני מצבי תצוגה** — טקסטואלי (מספרים גולמיים, ידידותי למפתחים) או ויזואלי (אייקונים, מדי-עוצמה, פסי התקדמות) — מעבר בלחיצה אחת.

**🌙 ערכת נושא כהה/בהירה**, **🌐 4 שפות** (אנגלית, עברית, ספרדית, ערבית, עם תמיכה מלאה ב-RTL), **📐 יחידות מטריות/אימפריאליות**.

## PhoneLab Web

לוח מחוונים דפדפני נלווה נמצא בתיקיית [`Web/`](Web/) בריפו הזה ורץ בכתובת **[sensolab-web.vercel.app](https://sensolab-web.vercel.app)** — בלי צורך בהתקנה. הוא משקף את מה שפלטפורמת הווב חושפת בדפדפן/במכשיר הנוכחי שלכם: חיישני תנועה וכיוון, GPS, תאורת סביבה, בדיקת מהירות אינטרנט, וייצוא ל-CSV/PNG של כל מה שהוא קורא. הכול רץ בצד הלקוח — שום דבר לא נשלח לשום מקום.

## הורדה

הורידו את ה-APK החתום העדכני מ**[עמוד ה-Releases](https://github.com/DrummingBird1/PhoneLab/releases)** — כל גרסה מפרטת מה השתנה בשפה פשוטה ומצורף אליה APK מוכן להתקנה. הפצה דרך Google Play מתוכננת; בינתיים ה-APK היא הדרך המהירה ביותר לקבל את הגרסה העדכנית.

## בנייה מהמקור

דרישות: **JDK 21**, Android SDK 35, ו-Gradle wrapper (מגיע מוכן בריפו — אין צורך להתקין Gradle בנפרד).

```bash
cd App
./gradlew assembleDebug      # APK debug לא חתום → app/build/outputs/apk/debug/
./gradlew test                # בדיקות יחידה JUnit
```

בנייה חתומה (release) דורשת מפתח חתימה: העתיקו את `App/key.properties.template` ל-`App/key.properties` ומלאו את פרטי ה-keystore שלכם, ואז הריצו `./gradlew bundleRelease` או `./gradlew assembleRelease`. בלי `key.properties`, בנייה release עדיין תעבוד — רק לא חתומה.

ראו את [CLAUDE.md](CLAUDE.md) לסקירה ארכיטקטונית מלאה (Fragments, מחלקות עזר, מודל ההרשאות, פרטים לא-מובנים-מאליהם) — זה אותו מסמך אוריינטציה שמשמש לפיתוח בעזרת בינה מלאכותית בריפו הזה, והוא משמש גם כתיעוד טכני חי.

## ערימת טכנולוגיות

Java, Views קלאסיים של Android + Material Components (בלי Compose), `ViewPager2` + `TabLayout`, `WorkManager` לבדיקות חום ברקע, `Service` בחזית להקלטת CSV, `TileService` לאריח בהגדרות המהירות, ו-`AppWidgetProvider` לווידג'ט מסך הבית. לוח המחוונים הדפדפני בנוי ב-Vite + TypeScript, בלי framework.

## מבנה הפרויקט

```
PhoneLab/
├── App/            פרויקט ה-Gradle — פתחו את זה ב-Android Studio
├── Web/            לוח המחוונים הדפדפני (Vite + TypeScript)
├── Assets/         תוכן לחנות, אייקונים, צילומי מסך, Changelogs
├── Distribution/   פלט בנייה מקומי (לא נכלל בגיט)
└── Archive/        תמונות קפואות של גרסאות ישנות יותר
```

## רישיון

בשלב זה לא ניתן רישיון קוד פתוח — הקוד ציבורי לשקיפות, אך כל הזכויות שמורות. פתחו Issue אם תרצו לדבר על שימוש חוזר.

</div>
