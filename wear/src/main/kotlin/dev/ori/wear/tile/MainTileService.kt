package dev.ori.wear.tile

import androidx.compose.ui.graphics.toArgb
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.wear.sync.WearState
import dev.ori.wear.ui.theme.OriDevWearColors
import javax.inject.Inject

/**
 * Quick-glance Wear OS Tile for Ori:Dev.
 *
 * Shows active connection and transfer counts from [WearState]. The tile is
 * refreshed every [FRESHNESS_INTERVAL_MILLIS] ms and supports tile preview for
 * the tile carousel editor.
 *
 * Phase 11 P3.2 (T2c) — the tile palette is sourced from [OriDevWearColors]
 * via [androidx.compose.ui.graphics.Color.toArgb] so the protolayout-rendered
 * tile and the Compose-rendered Wear screens stay in sync. The tile itself is
 * NOT a Compose composable (it's a `TileService` that serializes a protobuf
 * layout), so we can't use `MaterialTheme.colorScheme.*` here — instead we
 * read the Color objects directly and unwrap them into ARGB ints for
 * [ColorBuilders.argb].
 */
@AndroidEntryPoint
class MainTileService : TileService() {

    @Inject
    lateinit var wearState: WearState

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(createTile())
            TILE_REQUEST_TAG
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .build(),
            )
            RESOURCES_REQUEST_TAG
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        // No-op. Tile added to the carousel.
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) {
        // No-op. Tile removed from the carousel.
    }

    private fun createTile(): TileBuilders.Tile {
        val connectionCount = wearState.connections.value.size
        val transferCount = wearState.transfers.value.size

        // Palette — resolved once per tile build from OriDevWearColors so
        // any future palette tweak flows through a single file.
        val backgroundArgb = OriDevWearColors.Background.toArgb()
        val onBackgroundArgb = OriDevWearColors.OnBackground.toArgb()
        val mutedArgb = OriDevWearColors.OnSurfaceVariant.toArgb()
        val primaryArgb = OriDevWearColors.Primary.toArgb()

        val titleStyle = LayoutElementBuilders.FontStyle.Builder()
            .setColor(ColorBuilders.argb(primaryArgb))
            .build()
        val primaryLineStyle = LayoutElementBuilders.FontStyle.Builder()
            .setColor(ColorBuilders.argb(onBackgroundArgb))
            .build()
        val mutedLineStyle = LayoutElementBuilders.FontStyle.Builder()
            .setColor(ColorBuilders.argb(mutedArgb))
            .build()

        val background = ModifiersBuilders.Modifiers.Builder()
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(backgroundArgb))
                    .build(),
            )
            .build()

        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(
                LayoutElementBuilders.Box.Builder()
                    .setModifiers(background)
                    .addContent(
                        LayoutElementBuilders.Column.Builder()
                            .addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("Ori:Dev")
                                    .setFontStyle(titleStyle)
                                    .build(),
                            )
                            .addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("$connectionCount connected")
                                    .setFontStyle(primaryLineStyle)
                                    .build(),
                            )
                            .addContent(
                                LayoutElementBuilders.Text.Builder()
                                    .setText("$transferCount transfers")
                                    .setFontStyle(mutedLineStyle)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(layout)
                    .build(),
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val FRESHNESS_INTERVAL_MILLIS = 60_000L
        const val TILE_REQUEST_TAG = "MainTileService#onTileRequest"
        const val RESOURCES_REQUEST_TAG = "MainTileService#onTileResourcesRequest"
    }
}
