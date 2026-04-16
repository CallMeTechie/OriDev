package dev.ori.core.ads

import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun AdNativeCardView(
    handle: Any,
    modifier: Modifier = Modifier,
) {
    val nativeAd = handle as? NativeAd ?: return
    AndroidView(
        factory = { context ->
            NativeAdView(context).apply {
                val container = FrameLayout(context)
                addView(container)

                val headlineView = TextView(context).apply {
                    text = nativeAd.headline
                }
                container.addView(headlineView)
                this.headlineView = headlineView

                nativeAd.icon?.drawable?.let { drawable ->
                    val iconView = ImageView(context).apply {
                        setImageDrawable(drawable)
                    }
                    container.addView(iconView)
                    this.iconView = iconView
                }

                setNativeAd(nativeAd)
            }
        },
        modifier = modifier,
    )
}
