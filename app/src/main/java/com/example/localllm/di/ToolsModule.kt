package com.example.localllm.di

import com.example.localllm.data.tools.BatteryInfoProvider
import com.example.localllm.data.tools.BuildDeviceInfoProvider
import com.example.localllm.data.tools.ClipboardReader
import com.example.localllm.data.tools.DeviceInfoProvider
import com.example.localllm.data.tools.GetBatteryStatusTool
import com.example.localllm.data.tools.GetClipboardTool
import com.example.localllm.data.tools.GetDeviceInfoTool
import com.example.localllm.data.tools.ReadScreenTool
import com.example.localllm.data.tools.SystemBatteryInfoProvider
import com.example.localllm.data.tools.SystemClipboardReader
import com.example.localllm.domain.tools.Tool
import com.example.localllm.domain.tools.ToolRegistry
import dagger.Binds
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
 *  1. Create a class in `data/tools/` implementing [Tool] and its provider abstraction if needed.
 *  2. Add a `@Provides @IntoSet` binding below — nothing else needs to change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ToolsModule {

    @Binds
    @Singleton
    abstract fun bindDeviceInfoProvider(impl: BuildDeviceInfoProvider): DeviceInfoProvider

    @Binds
    @Singleton
    abstract fun bindClipboardReader(impl: SystemClipboardReader): ClipboardReader

    @Binds
    @Singleton
    abstract fun bindBatteryInfoProvider(impl: SystemBatteryInfoProvider): BatteryInfoProvider

    companion object {

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
        @IntoSet
        fun provideReadScreenTool(tool: ReadScreenTool): Tool = tool

        @Provides
        @Singleton
        fun provideToolRegistry(tools: Set<@JvmSuppressWildcards Tool>): ToolRegistry =
            ToolRegistry(tools)
    }
}
