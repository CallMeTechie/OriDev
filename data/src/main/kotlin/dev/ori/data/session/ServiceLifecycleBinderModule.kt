package dev.ori.data.session

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the real Android-Context-backed [SessionRegistryImpl.ServiceLifecycleBinder]
 * used at runtime. Kept in its own module so the registry impl doesn't
 * import `android.content.Context` directly — that keeps
 * `SessionRegistryImpl` itself trivially testable against a spy.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceLifecycleBinderModule {

    private const val CONNECTION_SERVICE_CLASS = "dev.ori.app.service.ConnectionService"

    @Provides
    @Singleton
    fun provide(
        @ApplicationContext context: Context,
    ): SessionRegistryImpl.ServiceLifecycleBinder =
        SessionRegistryImpl.ServiceLifecycleBinder { anyOpen ->
            val intent = Intent().apply {
                setClassName(context.packageName, CONNECTION_SERVICE_CLASS)
            }
            if (anyOpen) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
}
