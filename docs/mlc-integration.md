# MLC LLM Android Integration Guide

## Current Status

المستودع لم يعد في حالة `FakeInferenceEngine`-only. الحالة الحالية كالتالي:

- `MLCInferenceEngine` هو الـbackend المربوط فعليًا عبر Hilt.
- module `mlc4j` مضمّن داخل المشروع ومربوط مع `app`.
- طبقة البث تمر عبر `MLCEngine` و`JSONFFIEngine` وواجهة `OpenAIProtocol`.
- تم تشديد lifecycle الخاص بالبث بحيث يدعم:
  - إلغاء الطلب عبر `abort`
  - تنظيف state عند إغلاق الـstream
  - تقليل مخاطر التسريب وعدم الأمان الخيطي في تتبع الطلبات

هذا لا يعني أن المشروع `runtime-verified` بالكامل في هذه البيئة. التحقق الكامل ما زال متوقفًا على توفر Android SDK محليًا، وعلى صلاحية ملفات الـnative runtime الموجودة ضمن `mlc4j/output`.

## Architecture Notes

### App layer

- `app/.../engine/MLCInferenceEngine.kt`
  يحمّل النموذج ويحوّل stream الاستجابات إلى `Flow<GenerationResponse>`.

### Native bridge layer

- `mlc4j/.../MLCEngine.kt`
  يدير request lifecycle فوق JSON FFI.
- `mlc4j/.../JSONFFIEngine.java`
  الجسر المباشر إلى وظائف TVM/MLC.
- `mlc4j/.../OpenAIProtocol.kt`
  نماذج protocol المستعملة في `chat.completions`.

## What Is Wired vs What Is Still Pending

### Wired now

- DI binding على `MLCInferenceEngine`
- وجود `mlc4j` داخل البناء
- request abort path من طبقة التطبيق إلى طبقة FFI
- bounded channel strategy بدل النمو غير المحدود للذاكرة

### Still environment-dependent

- نجاح `assembleDebug` و`lint` و`test` يتطلب Android SDK محليًا مضبوطًا
- التشغيل الفعلي يعتمد على صلاحية المكتبات الأصلية داخل `mlc4j/output`
- الأداء الفعلي وTTFT/TPS ما زالا يحتاجان تحققًا على جهاز Android مناسب

## Local Validation Requirements

قبل التحقق الكامل محليًا:

1. اضبط Android SDK عبر `ANDROID_HOME` أو `ANDROID_SDK_ROOT` أو `local.properties`.
2. تأكد أن ملفات `mlc4j/output/*.jar` و`mlc4j/output/arm64-v8a/*.so` موجودة وصالحة.
3. شغّل:

```bash
./gradlew --no-daemon help --console=plain
./gradlew --no-daemon tasks --console=plain
./gradlew --no-daemon assembleDebug --console=plain
./gradlew --no-daemon test --console=plain
./gradlew --no-daemon lint --console=plain
```

## Scope Boundary

هذه الوثيقة لا تحاول وصف بناء مكتبات MLC الأصلية من الصفر. إذا احتجت إعادة توليد `mlc4j` أو الـruntime native artifacts، فذلك ما يزال عملًا خارجيًا عن هذا المستودع ويحتاج toolchain Android/NDK كاملة.
