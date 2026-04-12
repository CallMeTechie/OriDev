package dev.ori.feature.editor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.ori.feature.editor.ui.DiffDataHolder
import dev.ori.feature.editor.ui.DiffPayload
import dev.ori.feature.editor.ui.DiffViewerScreen
import java.util.UUID

const val DIFF_ROUTE_BASE = "diff"
const val DIFF_ROUTE = "$DIFF_ROUTE_BASE/{diffId}"

fun NavGraphBuilder.diffViewerScreen(onNavigateBack: () -> Unit = {}) {
    composable(
        route = DIFF_ROUTE,
        arguments = listOf(navArgument("diffId") { type = NavType.StringType }),
    ) {
        DiffViewerScreen(onNavigateBack = onNavigateBack)
    }
}

fun NavController.navigateToDiff(
    oldContent: String,
    newContent: String,
    oldTitle: String,
    newTitle: String,
) {
    val id = UUID.randomUUID().toString()
    DiffDataHolder.put(
        id,
        DiffPayload(
            oldContent = oldContent,
            newContent = newContent,
            oldTitle = oldTitle,
            newTitle = newTitle,
        ),
    )
    navigate("$DIFF_ROUTE_BASE/$id")
}
