package dev.ori.wear.tile

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.ori.wear.sync.WearState
import javax.inject.Inject

/**
 * Quick-glance Wear OS Tile for Ori:Dev.
 *
 * Shows active connection and transfer counts from [WearState]. The tile is
 * refreshed every [FRESHNESS_INTERVAL_MILLIS] ms and supports tile preview for
 * the tile carousel editor.
 */
@AndroidEntryPoint
class MainTileService : TileService() {

    @Inject
    lateinit var wearState: WearState

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val future = ResolvableFuture.create<TileBuilders.Tile>()
        future.set(createTile())
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val future = ResolvableFuture.create<ResourceBuilders.Resources>()
        future.set(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )
        return future
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

        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("Ori:Dev")
                            .build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("$connectionCount connected")
                            .build(),
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("$transferCount transfers")
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
    }
}
