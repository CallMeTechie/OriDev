package dev.ori.feature.terminal.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dev.ori.feature.terminal.ui.DefaultTerminalEmulatorProvider
import dev.ori.feature.terminal.ui.TerminalEmulatorProvider

@Module
@InstallIn(ViewModelComponent::class)
abstract class TerminalModule {
    @Binds
    abstract fun bindTerminalEmulatorProvider(
        impl: DefaultTerminalEmulatorProvider,
    ): TerminalEmulatorProvider
}
