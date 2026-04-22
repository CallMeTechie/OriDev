package dev.ori.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.app.ui.OriDevApp
import dev.ori.core.ui.theme.OriDevTheme
import dev.ori.domain.repository.SessionRegistry
import dev.ori.feature.onboarding.OnboardingFlow
import dev.ori.feature.onboarding.data.OnboardingPreferences
import dev.ori.feature.settings.data.AppPreferences
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var sessionRegistry: SessionRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val completed by onboardingPreferences.completed
                .collectAsStateWithLifecycle(initialValue = null)
            // The window gets FLAG_SECURE either during onboarding (the
            // existing behaviour) OR when the user enabled
            // "Screenshots blockieren" in Security settings AND at
            // least one SSH session is live. Combining both predicates
            // into a single derived Boolean keeps the LaunchedEffect
            // below linear.
            val blockScreenshotsPref by appPreferences.blockScreenshotsWhileConnected
                .collectAsStateWithLifecycle(initialValue = false)
            val openSessions by sessionRegistry.openSessions
                .collectAsStateWithLifecycle()
            val shouldSecure = completed == false ||
                (blockScreenshotsPref && openSessions.isNotEmpty())

            LaunchedEffect(shouldSecure) {
                if (shouldSecure) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            when (completed) {
                null -> OriDevTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
                false -> OriDevTheme {
                    // OnboardingFlow persists the completion flag via OnboardingViewModel
                    // before invoking onFinish, so nothing else needs to happen here —
                    // collectAsStateWithLifecycle will re-render with completed == true.
                    OnboardingFlow(onFinish = {})
                }
                true -> OriDevApp()
            }
        }
    }
}
