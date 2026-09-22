# ChikChik — Build کردن APK نهایی

نام برنامه: **ChikChik** · Package: `com.chikchik.posecamera` · minSdk 24 · targetSdk 35

## پیش‌نیاز
- Android Studio (نسخه‌ی جدید) **یا** JDK 17+ و Android SDK (Platform 35 + Build-Tools)
- اینترنت برای اولین Build (دانلود Gradle 8.9 و وابستگی‌ها)

## روش ۱ — Android Studio (ساده‌ترین)
1. `File ▸ Open` و پوشه‌ی پروژه را باز کنید و Sync کامل شود.
2. `Build ▸ Generate Signed App Bundle / APK ▸ APK` (یا از Terminal دستور پایین را بزنید).

## روش ۲ — خط فرمان
فایل `gradle/wrapper/gradle-wrapper.jar` در این بسته نیست (باینری است). یک‌بار بسازید:

```bash
gradle wrapper --gradle-version 8.9     # با هر Gradle نصب‌شده؛ یا اولین Sync در Android Studio
./gradlew assembleRelease               # Windows: gradlew.bat assembleRelease
```

خروجی:

```
app/build/outputs/apk/release/app-release.apk
```

APK یونیورسال است (بدون ABI/Density split و بدون کتابخانه‌ی native)، روی arm64-v8a، armeabi-v7a، x86 و x86_64 نصب می‌شود.

## امضا (Signing)
- بدون هیچ تنظیمی، APK نسخه‌ی Release با کلید **debug** امضا می‌شود → قابل نصب و تست، ولی برای Google Play مناسب نیست.
- برای کلید اختصاصی: `keystore.properties.example` را به `keystore.properties` کپی و پر کنید
  (کلید را با `keytool` بسازید؛ دستورش داخل همان فایل است). **کلید و رمز را گم نکنید.**

## نصب روی گوشی
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## چک‌لیست تست دستی روی دستگاه واقعی
- [ ] باز شدن دوربین · تعویض دوربین · زوم · Tap to Focus · فلش · تایمر (۳/۵/۱۰) و لغو با شاتر
- [ ] انتخاب عکس از گالری، لغو انتخاب، انتخاب فایل خراب/بسیار بزرگ (باید پیام ساده بیاید)
- [ ] Overlay: شفافیت، جابه‌جایی، Pinch، چرخش، Lock، Reset
- [ ] گرفتن عکس ← Retake / Save / Share ← دیدن عکس در Gallery (آلبوم `ChikChik`)
- [ ] رد کردن Permission دوربین (و «دیگر نپرس») ← دکمه باید به Settings ببرد
- [ ] چرخش صفحه (portrait/landscape) وسط کار: Overlay و تنظیمات نپرند
- [ ] رفتن به Background و برگشت (حتی وسط شمارش تایمر)
- [ ] در App Info: نام «ChikChik» و دسترسی‌های «دوربین» و «اینترنت» (اینترنت فقط برای Explore استفاده می‌شود)
- [ ] Explore: بدون کلید API در `secrets.properties` باید پیام «تنظیم نشده» را نشان دهد، نه کرش
- [ ] Explore با اینترنت خاموش: پیام خطای اتصال + دکمه «تلاش دوباره» باید کار کند

## Explore / Inspiration (Stage 16) — منبع آنلاین تصاویر
Explore از API رسمی Pexels (https://www.pexels.com/api/) استفاده می‌کند. برای فعال‌سازی:
1. یک حساب رایگان در https://www.pexels.com/api/ بسازید و یک API Key بگیرید.
2. فایل `secrets.properties.example` را در ریشه‌ی پروژه کپی کنید به `secrets.properties`
   (این فایل Git-ignore شده و هرگز نباید Commit شود).
3. مقدار `PEXELS_API_KEY` را در آن فایل پر کنید و پروژه را دوباره Sync/Build کنید.

بدون این فایل، Explore به‌طور کامل و بدون کرش کار می‌کند، فقط پیام «منبع آنلاین تصاویر هنوز
تنظیم نشده است» را نشان می‌دهد.
