# Release Gate 2.0

## Gate Status Summary

الحالة الحالية للإصدار 2.0 تقسم إلى ثلاث فئات:

### Ready

- توحيد build system وإضافة Gradle Wrapper
- توحيد إعدادات Kotlin / AGP / KSP / Compose / serialization
- تحويل `mlc4j` إلى Kotlin DSL
- تنظيف lifecycle / cancellation / thread-safety / backpressure في طبقة البث
- تحديث التوثيق الأساسي: `README`, `docs/mlc-integration.md`, `RELEASE_2_0_ANALYSIS.md`, `HANDOVER_2_0.md`
- إضافة CI workflow للتحقق الآلي:
  - [android-validation.yml](/data/data/com.termux/files/home/local-llm-chat-android/.github/workflows/android-validation.yml)

### Conditionally Ready

- جاهزية المستودع للتسليم بين المطورين
- جاهزية التحقق الآلي بمجرد تشغيل GitHub Actions على بيئة تحتوي Android SDK
- جاهزية التحقق المحلي إذا تم توفير Android SDK وضبط `local.properties` أو `ANDROID_HOME` / `ANDROID_SDK_ROOT`

### Not Yet Proven

- نجاح `test`, `assembleDebug`, و`lint` في بيئة Android مكتملة
- نجاح CI فعليًا على GitHub
- runtime verification على جهاز أو محاكي Android
- smoke test حقيقي لمسار فتح التطبيق وبدء inference أو أقرب مسار صالح

## What Was Completed

- تم توحيد البنية الأساسية للمشروع.
- تم تقليل الغموض حول backend الفعلي: `MLCInferenceEngine` هو binding الفعلي عبر Hilt.
- تم تحسين طبقة البث لتستخدم cancellation فعليًا مع `abort`.
- تم تقليل خطر التسريب وعدم الأمان الخيطي في `mlc4j`.
- تم تجهيز ملف handover ووثيقة تحليل للإصدار 2.0.
- تم تجهيز workflow آلي يمكنه إثبات build/test/lint على بيئة غير Termux.

## What Was Proven

- `./gradlew --no-daemon help --console=plain` يعمل
- `./gradlew --no-daemon tasks --console=plain` يعمل
- المستودع يمر بمرحلة configuration بنجاح
- ملفات runtime الأساسية داخل `mlc4j/output` موجودة:
  - `tvm4j_core.jar`
  - `arm64-v8a/libtvm4j_runtime_packed.so`

## What Has Not Been Proven Yet

- `./gradlew --no-daemon test --console=plain`
- `./gradlew --no-daemon assembleDebug --console=plain`
- `./gradlew --no-daemon lint --console=plain`
- نجاح GitHub Actions فعليًا
- تشغيل التطبيق على جهاز/محاكي Android
- نجاح تهيئة الـengine في runtime الفعلي

## Conditions Required For Full 2.0 Readiness

لا يمكن إعلان جاهزية 2.0 بالكامل إلا إذا تحققت الشروط التالية:

1. نجاح CI workflow الحالي على GitHub دون فشل.
2. نجاح `test`, `assembleDebug`, و`lint` في بيئة تحتوي Android SDK.
3. تنفيذ runtime smoke test على جهاز أو محاكي Android.
4. عدم ظهور crash مبكر عند بدء التطبيق أو عند بدء جلسة inference.
5. عدم اكتشاف مشكلة حرجة في ملفات runtime أو تحميل النموذج.

## Final Execution Checklist

1. ادفع الفرع الحالي إلى GitHub.
2. شغّل أو راقب workflow:
   - [android-validation.yml](/data/data/com.termux/files/home/local-llm-chat-android/.github/workflows/android-validation.yml)
3. تأكد أن المهام التالية نجحت داخل CI:
   - `help`
   - `tasks`
   - `test`
   - `assembleDebug`
   - `lint`
4. جهّز جهاز Android أو محاكيًا صالحًا.
5. ثبّت نسخة `debug` الناتجة أو ابنِها محليًا على بيئة Android مكتملة.
6. نفّذ smoke test runtime الأدنى.
7. إذا لم تظهر مشاكل حرجة، ارفع قرار gate إلى `GO`.

## Runtime Verification Checklist

### Minimum smoke test

1. فتح التطبيق بنجاح.
2. عدم حدوث crash عند الإقلاع.
3. الوصول إلى شاشة الدردشة.
4. وجود model/runtime assets بالشكل المتوقع.
5. محاولة بدء جلسة inference أو تحميل نموذج.
6. التحقق من عدم حدوث crash مباشر أثناء تهيئة `MLCInferenceEngine`.

### What to monitor

- crashes أو ANRs
- أخطاء تحميل `mlc4j` أو الـnative runtime
- فشل تحميل النموذج من المسار المحلي
- فشل مبكر في streaming أو إلغاء الطلب
- رسائل خطأ متكررة في logs مرتبطة بـFFI أو abort أو reset

### Success criteria

- التطبيق يبدأ بنجاح
- لا يوجد crash مبكر
- تهيئة engine لا تفشل مباشرة
- يمكن بدء جلسة inference أو الوصول إلى أقرب نقطة صالحة قبل التوليد
- لا تظهر مشكلة blocking أو تسريب واضح في أول smoke run

### Failure criteria

- crash عند الإقلاع
- crash أو exception غير متحكم بها عند تهيئة engine
- فشل تحميل runtime native
- فشل تحميل النموذج أو الوصول إلى assets المطلوبة
- فشل streaming أو الإلغاء بشكل يترك التطبيق في حالة غير صالحة

## Stop / Block Conditions

يجب حجب إعلان جاهزية 2.0 إذا ظهر أي مما يلي:

- فشل CI في `test`, `assembleDebug`, أو `lint`
- crash runtime مبكر
- فشل تحميل مكتبات `mlc4j/output`
- فشل واضح في تهيئة `MLCInferenceEngine`
- خطأ حرج في lifecycle أو cancellation يسبب تعليقًا أو تسريبًا ظاهرًا

## Release Decision

- الوضع الحالي: `Conditional Go`
- السبب:
  - الأساس الهندسي والتوثيقي جاهز
  - التحقق الآلي أصبح ممكنًا
  - لكن build الكامل وruntime verification لم يُثبتا بعد على بيئة Android مكتملة
