# Handover 2.0

## Overview

المشروع عبارة عن تطبيق Android Native مبني بـJetpack Compose لتشغيل نماذج لغوية محلية على الجهاز. المعمارية الحالية تفصل بين:

- طبقة التطبيق داخل `app`
- طبقة الربط مع MLC داخل `mlc4j`
- وثائق التشغيل والتحليل داخل `docs` وملفات الجذر

حالة المشروع الحالية مناسبة للتسليم بين المطورين، لكنها ليست `release-verified` بالكامل داخل هذه البيئة بسبب غياب Android SDK المحلي.

## Core Modules

### `:app`

مسؤول عن:

- نقطة الدخول الرئيسية للتطبيق
- واجهات Compose والتنقل
- ViewModels وإدارة الحالة
- Room وDataStore وrepositories
- واجهة `InferenceEngine` وتكامل التطبيق مع `MLCInferenceEngine`

### `:mlc4j`

مسؤول عن:

- طبقة JSON FFI مع runtime الخاص بـMLC/TVM
- إدارة طلبات `chat.completions`
- tracking للطلبات النشطة
- lifecycle للبث، cancellation، وcleanup
- نماذج `OpenAI-compatible protocol`

## Main Entry Point

- نقطة دخول Android الرئيسية هي:
  - [app/src/main/java/com/example/localllm/MainActivity.kt](/data/data/com.termux/files/home/local-llm-chat-android/app/src/main/java/com/example/localllm/MainActivity.kt)
- التنقل الرئيسي في:
  - [app/src/main/java/com/example/localllm/ui/navigation/AppNavigation.kt](/data/data/com.termux/files/home/local-llm-chat-android/app/src/main/java/com/example/localllm/ui/navigation/AppNavigation.kt)

## Engine Integration

- العقدة العامة داخل التطبيق هي:
  - [app/src/main/java/com/example/localllm/engine/InferenceEngine.kt](/data/data/com.termux/files/home/local-llm-chat-android/app/src/main/java/com/example/localllm/engine/InferenceEngine.kt)
- الـbackend المربوط فعليًا عبر Hilt هو:
  - [app/src/main/java/com/example/localllm/engine/MLCInferenceEngine.kt](/data/data/com.termux/files/home/local-llm-chat-android/app/src/main/java/com/example/localllm/engine/MLCInferenceEngine.kt)
- الـbinding الفعلي موجود في:
  - [app/src/main/java/com/example/localllm/di/AppModule.kt](/data/data/com.termux/files/home/local-llm-chat-android/app/src/main/java/com/example/localllm/di/AppModule.kt)

السلوك الحالي:

- `MLCInferenceEngine` يحمّل النموذج من المسار المحلي.
- يبني request بصيغة `chat.completions`.
- يحول stream الاستجابات إلى `Flow<GenerationResponse>`.
- عند الإلغاء أو إغلاق الـflow يتم cancel للـresponse channel، ما يؤدي إلى `abort(requestId)` في الطبقة الأدنى بدل الاكتفاء بإلغاء coroutine محليًا.

## mlc4j and FFI Layer

الملفات الأساسية:

- [mlc4j/src/main/java/ai/mlc/mlcllm/MLCEngine.kt](/data/data/com.termux/files/home/local-llm-chat-android/mlc4j/src/main/java/ai/mlc/mlcllm/MLCEngine.kt)
- [mlc4j/src/main/java/ai/mlc/mlcllm/StreamingState.kt](/data/data/com.termux/files/home/local-llm-chat-android/mlc4j/src/main/java/ai/mlc/mlcllm/StreamingState.kt)
- [mlc4j/src/main/java/ai/mlc/mlcllm/JSONFFIEngine.java](/data/data/com.termux/files/home/local-llm-chat-android/mlc4j/src/main/java/ai/mlc/mlcllm/JSONFFIEngine.java)
- [mlc4j/src/main/java/ai/mlc/mlcllm/OpenAIProtocol.kt](/data/data/com.termux/files/home/local-llm-chat-android/mlc4j/src/main/java/ai/mlc/mlcllm/OpenAIProtocol.kt)

كيف تعمل الطبقة باختصار:

- `JSONFFIEngine` ينادي وظائف الـnative runtime.
- `MLCEngine` يهيّئ الـbackground loops ويعرض API أعلى للتطبيق.
- `StreamingState` يدير state الطلبات النشطة، الإلغاء، الإغلاق، وforwarding للرسائل.
- `OpenAIProtocol` يحتوي data classes وserializers الخاصة بطلبات واستجابات `chat.completions`.

## What Is Still Work In Progress

- التحقق الكامل لبناء Android غير مثبت هنا بسبب غياب Android SDK المحلي.
- التحقق runtime الفعلي على جهاز Android مع نموذج MLC صالح ما زال مطلوبًا.
- بعض أجزاء `OpenAI-compatible protocol` ما زالت قابلة للتوسيع إذا كان الهدف دعم مزودات متعددة بشكل أوسع.
- `FakeInferenceEngine` ما زال موجودًا كخيار تطويري/اختباري، لكنه ليس backend التشغيل الفعلي.

## Local Run Requirements

لتشغيل المشروع محليًا يحتاج المطور إلى:

- JDK 17 أو أحدث
- Android SDK API 35
- Gradle Wrapper الموجود في المستودع
- ملفات runtime الخاصة بـ`mlc4j/output`

تعريف Android SDK يتم بإحدى الطريقتين:

```properties
# local.properties
sdk.dir=/absolute/path/to/android-sdk
```

أو:

```bash
export ANDROID_HOME=/absolute/path/to/android-sdk
export ANDROID_SDK_ROOT=/absolute/path/to/android-sdk
```

## Local Validation Commands

```bash
./gradlew --no-daemon help --console=plain
./gradlew --no-daemon tasks --console=plain
./gradlew --no-daemon test --console=plain
./gradlew --no-daemon assembleDebug --console=plain
./gradlew --no-daemon lint --console=plain
```

## Current Risks and Constraints

- بدون Android SDK لن تعمل مهام Android الأساسية.
- وجود `mlc4j` داخل المستودع لا يضمن وحده أن runtime native صالح أو متوافق مع الجهاز المستهدف.
- الأداء الفعلي وTTFT/TPS وسلوك الذاكرة ما زالت تحتاج تحققًا على جهاز حقيقي.
- لا يوجد حتى الآن إثبات CI دوري داخل هذا السياق بأن build/test/lint تعمل end-to-end.

## Recommended Next Priorities

1. ضبط Android SDK محليًا ثم إعادة تشغيل `test`, `assembleDebug`, و`lint`.
2. التحقق من اختبارات `mlc4j` المضافة لطبقة streaming والقناة والإلغاء.
3. تنفيذ smoke test فعلي على جهاز Android مع نموذج واحد معروف وصالح.
4. مراجعة `OpenAIProtocol.kt` إذا كان الهدف توسيع التوافق مع مزودات OpenAI-compatible إضافية.
5. تقليل أي غموض متبقٍ في runtime assets المطلوبة داخل `mlc4j/output`.
