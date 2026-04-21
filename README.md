# LocalLLM — Android On-device LLM Chat App

مشروع Android Native لتشغيل نماذج لغوية محلية على الجهاز مع واجهة Compose وطبقة تكامل MLC/`mlc4j`.

## المتطلبات

- JDK 17+
- Android SDK API 35
- Android Studio حديث أو بيئة Gradle تدعم Android

## حالة المشروع الحالية

- الـ `InferenceEngine` المربوط عبر Hilt هو `FallbackInferenceEngine`، الذي يجرّب `MLCInferenceEngine` أولًا ثم يتراجع إلى `FakeInferenceEngine` عند فشل المكتبات الأصلية أو الـGPU.
- `FakeInferenceEngine` يعمل كـbackend احتياطي وللاختبارات والتطوير المعزول.
- مشروع `mlc4j` مضمن داخل المستودع ومربوط مباشرة مع التطبيق.
- يوجد Gradle Wrapper داخل المستودع، والإصدار الحالي هو Gradle 8.7.
- التحقق الكامل (`assembleDebug` / `test` / `lint`) يحتاج Android SDK محليًا مضبوطًا عبر `ANDROID_HOME` أو `ANDROID_SDK_ROOT` أو `local.properties`.

## Stack

| الطبقة | التقنية |
|--------|---------|
| UI | Jetpack Compose + Material3 |
| State | ViewModel + StateFlow |
| DI | Hilt |
| Local DB | Room |
| Settings | DataStore Preferences |
| Navigation | Navigation Compose |
| Logging | Timber |
| Engine | `InferenceEngine` → `MLCInferenceEngine` / `FakeInferenceEngine` |
| Native Bridge | `mlc4j` + TVM runtime |

## بنية مختصرة

```text
app/
  src/main/java/com/example/localllm/
    engine/          طبقة التطبيق للاستدلال
    ui/              شاشات Compose وViewModels
    data/            Room + DataStore + repositories
    di/              Hilt modules
mlc4j/
  src/main/java/ai/mlc/mlcllm/
    MLCEngine.kt     إدارة الطلبات والبث فوق JSON FFI
    OpenAIProtocol.kt
    JSONFFIEngine.java
docs/
  mlc-integration.md
```

## التشغيل

```bash
./gradlew --no-daemon help --console=plain
./gradlew --no-daemon tasks --console=plain
```

للتجميع أو الاختبارات على جهازك يجب أولًا تعريف Android SDK لGradle بإحدى الطرق التالية:

```bash
# 1) عبر local.properties في جذر المشروع:
# sdk.dir=/absolute/path/to/android-sdk

# أو 2) عبر متغيرات البيئة:
# export ANDROID_HOME=/absolute/path/to/android-sdk
# export ANDROID_SDK_ROOT=/absolute/path/to/android-sdk

# بعد ذلك:
./gradlew --no-daemon assembleDebug --console=plain
./gradlew --no-daemon test --console=plain
./gradlew --no-daemon lint --console=plain
```

## ملاحظات MLC

- التطبيق يفترض وجود مكتبات `mlc4j/output` والـassets المرتبطة بنماذج MLC.
- طبقة البث الحالية تستخدم cancellation فعليًا عبر `abort` في طبقة FFI بدل الاكتفاء بإلغاء coroutine محليًا.
- ما زالت بعض أجزاء التكامل تعتبر scaffold عمليًا، خصوصًا ما يعتمد على البيئة المحلية والـnative toolchain.

راجع [docs/mlc-integration.md](/data/data/com.termux/files/home/local-llm-chat-android/docs/mlc-integration.md) و[RELEASE_2_0_ANALYSIS.md](/data/data/com.termux/files/home/local-llm-chat-android/RELEASE_2_0_ANALYSIS.md) للتفاصيل.
