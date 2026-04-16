package dev.ori.core.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ori.domain.model.AdSlot
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AdMobAdLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) : AdLoader {

    private val adViews = ConcurrentHashMap<AdSlot, AdView>()
    private val nativeAds = ConcurrentHashMap<AdSlot, NativeAd>()

    private fun bannerUnitId(@Suppress("UNUSED_PARAMETER") slot: AdSlot): String =
        "ca-app-pub-3940256099942544/6300978111" // Google test banner

    private fun nativeUnitId(@Suppress("UNUSED_PARAMETER") slot: AdSlot): String =
        "ca-app-pub-3940256099942544/2247696110" // Google test native

    override suspend fun loadBanner(slot: AdSlot): AdLoadResult =
        suspendCancellableCoroutine { cont ->
            val adView = AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = bannerUnitId(slot)
            }
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    adViews[slot] = adView
                    if (cont.isActive) cont.resume(AdLoadResult.Loaded(adView))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (error.code == AdRequest.ERROR_CODE_NO_FILL) {
                        if (cont.isActive) cont.resume(AdLoadResult.NoFill)
                    } else {
                        if (cont.isActive) cont.resume(
                            AdLoadResult.Failed(error.code, error.message),
                        )
                    }
                }
            }
            adView.loadAd(AdRequest.Builder().build())

            cont.invokeOnCancellation {
                adView.destroy()
            }
        }

    override suspend fun loadNative(slot: AdSlot): AdLoadResult =
        suspendCancellableCoroutine { cont ->
            val loader = buildGmsNativeAdLoader(
                context = context,
                adUnitId = nativeUnitId(slot),
                onLoaded = { nativeAd ->
                    nativeAds[slot] = nativeAd
                    if (cont.isActive) cont.resume(AdLoadResult.Loaded(nativeAd))
                },
                onFailed = { error ->
                    if (error.code == AdRequest.ERROR_CODE_NO_FILL) {
                        if (cont.isActive) cont.resume(AdLoadResult.NoFill)
                    } else {
                        if (cont.isActive) cont.resume(
                            AdLoadResult.Failed(error.code, error.message),
                        )
                    }
                },
            )

            loader.loadAd(AdRequest.Builder().build())
        }

    override fun destroy(slot: AdSlot) {
        adViews.remove(slot)?.destroy()
        nativeAds.remove(slot)?.destroy()
    }
}
