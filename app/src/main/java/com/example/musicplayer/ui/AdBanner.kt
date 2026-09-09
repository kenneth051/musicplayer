package com.example.musicplayer.ui

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

// Google's official test banner unit - always fills, safe to load on a dev device.
// Real ad units shouldn't be loaded/viewed on dev devices (AdMob invalid-traffic policy),
// and new ad units can have low/no fill for a while after creation anyway.
private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
private const val PRODUCTION_AD_UNIT_ID = "ca-app-pub-1223870447474307/6781470652"

/**
 * A banner ad sized with Google's current-orientation anchored adaptive size, so it fills the
 * device width (rather than the fixed 320x50dp of AdSize.BANNER) and reports its real height back
 * to the caller so the container isn't clipped or left with dead space.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    val adSize = remember(screenWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
    }
    val isDebuggable = remember {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(adSize.height.dp),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adSize)
                adUnitId = if (isDebuggable) TEST_AD_UNIT_ID else PRODUCTION_AD_UNIT_ID
                adListener = object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AdBanner", "Ad failed to load: ${error.code} ${error.message}")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { adView ->
            // Width can change without recreating the composable (foldable/split-screen resize),
            // so re-fit and reload whenever the computed adaptive size no longer matches.
            if (adView.adSize != adSize) {
                adView.setAdSize(adSize)
                adView.loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}
