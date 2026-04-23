package dev.ori.app.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ori.data.session.ResumeCoordinator
import dev.ori.domain.preferences.SessionResumePreferences

/**
 * Hilt [EntryPoint] for app-level Composables that cannot take `@Inject`
 * constructor parameters (pure `@Composable` functions).
 *
 * Consumers grab the application-scoped Hilt component via
 * [dagger.hilt.android.EntryPointAccessors.fromApplication] and pull the
 * singletons they need. Keeps top-level Compose wiring (e.g.
 * [dev.ori.app.ui.OriDevApp]'s debounced route write and
 * [dev.ori.app.ui.SnackbarHostEffect]) decoupled from the Activity.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun sessionResumePrefs(): SessionResumePreferences
    fun resumeCoordinator(): ResumeCoordinator
}
