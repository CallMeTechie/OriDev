package dev.ori.core.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

/**
 * Helper to build a GMS native ad loader. Isolated to avoid name clash between
 * [dev.ori.core.ads.AdLoader] and [com.google.android.gms.ads.AdLoader].
 */
internal fun buildGmsNativeAdLoader(
    context: Context,
    adUnitId: String,
    onLoaded: (NativeAd) -> Unit,
    onFailed: (LoadAdError) -> Unit,
): com.google.android.gms.ads.AdLoader =
    com.google.android.gms.ads.AdLoader.Builder(context, adUnitId)
        .forNativeAd(onLoaded)
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                onFailed(error)
            }
        })
        .withNativeAdOptions(NativeAdOptions.Builder().build())
        .build()
