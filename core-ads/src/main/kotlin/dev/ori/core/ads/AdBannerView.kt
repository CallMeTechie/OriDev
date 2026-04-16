package dev.ori.core.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdView

@Composable
fun AdBannerView(
    handle: Any,
    modifier: Modifier = Modifier,
) {
    val adView = handle as? AdView ?: return
    AndroidView(
        factory = { adView },
        modifier = modifier,
    )
}
