# Release 2.0 Analysis

## Executive Summary

إصدار 2.0 يركز على تنظيف البنية الأساسية للمشروع بدل إضافة ميزات جديدة. النتيجة الأساسية هي أن نظام البناء صار أوضح وأكثر اتساقًا، وأن طبقة البث بين التطبيق و`mlc4j` أصبحت أقرب إلى سلوك production من ناحية lifecycle والإلغاء وإدارة الطلبات.

## Build System Changes

- إضافة Gradle Wrapper داخل المستودع.
- تثبيت Gradle على `8.7`.
- الإبقاء على:
  - AGP `8.5.2`
  - Kotlin `2.0.21`
  - KSP `2.0.21-1.0.27`
  - Compose BOM `2024.10.00`
- توحيد الإصدارات عبر `gradle/libs.versions.toml` قدر الإمكان.
- تحويل `mlc4j` من Groovy DSL إلى Kotlin DSL لتقليل التباين مع `app`.
- توحيد `Java 21` و`jvmTarget 21` واستخدام `compilerOptions`.

## Streaming / Engine Integration Changes

- ربط مسار الإلغاء في `MLCInferenceEngine` مع cancel فعلي للـresponse channel.
- ربط `abort` في `JSONFFIEngine` ضمن lifecycle الطلبات.
- إزالة `GlobalScope` في `mlc4j` واستبداله بـ`CoroutineScope` مملوك داخليًا.
- جعل request state مبنيًا على `ConcurrentHashMap` بدل mutable map غير محمية.
- إضافة cleanup أوضح عند إغلاق الطلب أو إلغاء الـstream.

## Lifecycle / Cancellation / Thread-Safety / Backpressure

### Lifecycle and cancellation

- لم يعد الإلغاء يعتمد على `generationJob.cancel()` فقط.
- عند إغلاق الـflow أو إلغاء الطلب، يتم إلغاء stream الاستجابات وربط ذلك بمسار `abort`.
- الهدف هو منع بقاء request native نشطًا بعد توقف collector.

### Thread-safety

- تتبع الطلبات النشطة أصبح thread-safe.
- تم تقليل مخاطر السباقات بين callback القادم من الطبقة الأصلية وبين coroutine consumer.

### Backpressure

- تم استبدال `Channel.UNLIMITED` باستراتيجية bounded أكثر أمانًا.
- هذا القرار يقلل احتمالية نمو الذاكرة بلا حد إذا أصبح المستهلك أبطأ من المنتج.
- ما زال throttling على مستوى UI مؤجلًا كتحسين لاحق.

## OpenAI-Compatible Protocol Improvements

- تصحيح حقل `logprobs` بدل التسمية الخاطئة السابقة.
- جعل `system_fingerprint` مرنًا عبر `nullable`.
- توسيع بعض الأنواع من `String` ضيقة إلى `JsonElement` حيث يلزم لتمثيل payloads الواقعية بشكل أفضل.
- جعل `ChatCompletionMessageContent` أكثر مرونة مع structured content المعتمد على JSON objects.

## What Remains Scaffold / Work In Progress

- التحقق الكامل لبناء Android ما زال معتمدًا على وجود Android SDK محليًا.
- runtime verification الحقيقي على جهاز Android لم يُثبت بعد في هذه البيئة.
- بعض أجزاء `OpenAI-compatible` protocol ما زالت تحتاج مراجعة إضافية إذا أُريد دعم أوسع لمزودات متعددة.
- اختبارات JVM المركزة على الطبقة الجديدة ما زالت محدودة بسبب اعتماد المشروع على Android plugin وغياب SDK محليًا أثناء التحقق.

## Current Constraints

- البيئة الحالية لا تحتوي Android SDK مضبوطًا لGradle.
- لذلك `assembleDebug` و`test` و`lint` الخاصة بـAndroid لا يمكن إثباتها هنا حتى النهاية.
- وجود `mlc4j` داخل المستودع لا يكفي وحده؛ يجب أيضًا أن تكون ملفات الـnative runtime صالحة ومناسبة للجهاز المستهدف.

## Local Validation Requirements

1. ضبط Android SDK عبر `local.properties` أو `ANDROID_HOME` أو `ANDROID_SDK_ROOT`.
2. التأكد من وجود ملفات `mlc4j/output` المطلوبة للـruntime.
3. تشغيل:
   - `./gradlew --no-daemon help --console=plain`
   - `./gradlew --no-daemon tasks --console=plain`
   - `./gradlew --no-daemon test --console=plain`
   - `./gradlew --no-daemon assembleDebug --console=plain`
   - `./gradlew --no-daemon lint --console=plain`

## Recommendations for Next Releases

1. استكمال اختبارات unit/integration قابلة للتشغيل آليًا لطبقة `mlc4j` و`MLCInferenceEngine`.
2. إضافة تحقق runtime على جهاز Android فعلي مع نموذج واحد معروف وصالح.
3. مراجعة `OpenAIProtocol.kt` بشكل أوسع لتحسين التوافق مع مزودات OpenAI-compatible.
4. إضافة throttling أو batching خفيف لتحديثات `streamingText` على مستوى UI إذا ظهر ضغط إعادة تركيب.
5. تثبيت إعداد CI يوفّر Android SDK ويشغّل `assembleDebug`, `test`, و`lint` بشكل منتظم.
