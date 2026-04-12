package com.example.localllm.di

import com.example.localllm.data.tools.GetBatteryStatusTool
import com.example.localllm.data.tools.GetClipboardTool
import com.example.localllm.data.tools.GetDeviceInfoTool
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Wires the Tool Calling layer.
 *
 * To add a new tool:
 *  1. Create a class in `data/tools/` that implements [Tool].
 *  2. Add a `@Provides @IntoSet` binding below — nothing else needs changing.
 */
@Module
@InstallIn(SingletonComponent::class)
object ToolsModule {

    @Provides
    @IntoSet
    fun provideDeviceInfoTool(tool: GetDeviceInfoTool): Tool = tool

    @Provides
    @IntoSet
    fun provideClipboardTool(tool: GetClipboardTool): Tool = tool

    @Provides
    @IntoSet
    fun provideBatteryStatusTool(tool: GetBatteryStatusTool): Tool = tool

    @Provides
    @Singleton
    fun provideToolRegistry(tools: Set<@JvmSuppressWildcards Tool>): ToolRegistry =
        ToolRegistry(tools)
}
