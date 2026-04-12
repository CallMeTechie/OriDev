package dev.ori.feature.editor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.ori.feature.editor.ui.CodeEditorScreen

const val EDITOR_ROUTE_BASE = "editor"
const val EDITOR_ROUTE = "$EDITOR_ROUTE_BASE/{isRemote}/{filePath}"

fun NavGraphBuilder.editorScreen(onNavigateBack: () -> Unit = {}) {
    composable(
        route = EDITOR_ROUTE,
        arguments = listOf(
            navArgument("isRemote") { type = NavType.BoolType },
            navArgument("filePath") { type = NavType.StringType },
        ),
    ) {
        CodeEditorScreen(onNavigateBack = onNavigateBack)
    }
}

fun NavController.navigateToEditor(filePath: String, isRemote: Boolean) {
    val encoded = java.net.URLEncoder.encode(filePath, "UTF-8")
    navigate("$EDITOR_ROUTE_BASE/$isRemote/$encoded")
}
