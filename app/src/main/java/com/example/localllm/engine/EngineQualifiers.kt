package com.example.localllm.engine

import javax.inject.Qualifier

/**
 * Hilt qualifier marking the native MLC-backed [InferenceEngine] implementation.
 * Used by [FallbackInferenceEngine] to inject the primary engine without coupling
 * to the concrete class — keeping the fallback testable in pure JVM unit tests.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MlcEngine

/**
 * Hilt qualifier marking the deterministic fake [InferenceEngine] implementation
 * used as the safety fallback when the native runtime is unavailable or fails.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FakeEngine
