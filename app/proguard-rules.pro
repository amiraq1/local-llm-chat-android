<<<<<<< HEAD
<<<<<<< HEAD
# ─── LocalLLM ProGuard Rules ─────────────────────────────────────────────────

# ── Room ──
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Hilt / Dagger ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# ── Kotlin Serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Timber ──
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ── Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── General ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
=======
# Module-specific ProGuard rules.
# Intentionally minimal for now to keep release builds configurable without
# introducing behavior changes during the build-system upgrade phase.
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
=======
# Module-specific ProGuard rules.
# Intentionally minimal for now to keep release builds configurable without
# introducing behavior changes during the build-system upgrade phase.
>>>>>>> 050ce6414e57d683a82e894e3da65e4ca8aa1ae5
