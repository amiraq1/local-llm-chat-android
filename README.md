# LocalLLM — Android On-device LLM Chat App

مشروع Android Native كامل لتشغيل نماذج لغوية محلية على الجهاز.

## المتطلبات

- Android Studio Hedgehog (2023.1.1) أو أحدث
- JDK 17
- Android SDK API 35
- Gradle 8.5+

## بنية المشروع

```
app/src/main/java/com/example/localllm/
├── LLMApplication.kt              ← Application class (Hilt)
├── MainActivity.kt                ← Single Activity entry point
│
├── engine/                        ← طبقة الاستدلال (Abstraction)
│   ├── InferenceEngine.kt         ← Interface + Request/Response models
│   └── FakeInferenceEngine.kt     ← Mock يحاكي streaming tokens (للتطوير)
│
├── domain/model/
│   └── AppModels.kt               ← Domain models: Conversation, Message, LLMModel, AppSettings
│
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt         ← Room Database
│   │   ├── entity/Entities.kt     ← ConversationEntity, MessageEntity, InstalledModelEntity, BenchmarkResultEntity
│   │   └── dao/                   ← ConversationDao, MessageDao, ModelDao, BenchmarkDao
│   ├── datastore/
│   │   └── SettingsDataStore.kt   ← DataStore<Preferences> للإعدادات
│   └── repository/
│       ├── ConversationRepository.kt
│       └── ModelRepository.kt     ← فهرس النماذج + فحص التوافق
│
├── di/
│   └── AppModule.kt               ← Hilt modules: DatabaseModule + EngineModule
│
└── ui/
    ├── theme/Theme.kt             ← Material3 Dark/Light themes
    ├── navigation/AppNavigation.kt ← Bottom nav + NavHost
    ├── chat/
    │   ├── ChatViewModel.kt        ← Streaming inference + conversation management
    │   └── ChatScreen.kt          ← Chat UI با streaming bubbles
    ├── models/
    │   ├── ModelsViewModel.kt
    │   └── ModelsScreen.kt        ← Model catalog with compatibility check
    ├── history/
    │   ├── HistoryViewModel.kt
    │   └── HistoryScreen.kt       ← Conversation history + search + delete
    ├── benchmark/
    │   ├── BenchmarkViewModel.kt   ← TTFT + Tokens/sec measurement
    │   └── BenchmarkScreen.kt
    └── settings/
        ├── SettingsViewModel.kt
        └── SettingsScreen.kt      ← Temperature, Top-P, Max Tokens, Wi-Fi toggle
```

## خطوات التشغيل

```bash
# 1. افتح المشروع في Android Studio
#    File → Open → android-llm-chat/

# 2. انتظر Gradle sync

# 3. شغّل على جهاز حقيقي أو Emulator (API 28+)
#    Run → Run 'app'
```

## ربط MLC LLM الحقيقي

الـ `FakeInferenceEngine` هو mock للتطوير. لتوصيل MLC LLM الحقيقي:

1. أضف الـ AAR إلى `app/libs/`:
   ```kotlin
   // app/build.gradle.kts
   implementation(files("libs/mlc-llm-android.aar"))
   ```

2. نفّذ `MLCInferenceEngine : InferenceEngine`:
   ```kotlin
   class MLCInferenceEngine @Inject constructor() : InferenceEngine {
       private val engine = MLCEngine()
       override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<ModelSession> {
           engine.load(modelPath)
           return Result.success(MLCModelSession(engine))
       }
       // ...
   }
   ```

3. في `AppModule.kt`، غيّر الـ binding:
   ```kotlin
   @Binds abstract fun bindInferenceEngine(mlc: MLCInferenceEngine): InferenceEngine
   ```

## الـ Stack

| الطبقة | التقنية |
|--------|---------|
| UI | Jetpack Compose + Material3 |
| State | ViewModel + StateFlow + collectAsState |
| DI | Hilt (Dagger2) |
| Local DB | Room 2.6 (SQLite) |
| Settings | DataStore Preferences |
| Navigation | Navigation Compose |
| Background | WorkManager (جاهز للتوسع) |
| Logging | Timber |
| Engine | InferenceEngine interface → FakeInferenceEngine / MLCInferenceEngine |
