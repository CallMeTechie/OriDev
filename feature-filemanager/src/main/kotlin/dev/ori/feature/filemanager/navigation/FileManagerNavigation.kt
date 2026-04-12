package dev.ori.feature.filemanager.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.ori.feature.filemanager.ui.FileManagerScreen

const val FILE_MANAGER_ROUTE = "filemanager"

fun NavGraphBuilder.fileManagerScreen(
    onNavigateToEditor: (filePath: String, isRemote: Boolean) -> Unit = { _, _ -> },
) {
    composable(route = FILE_MANAGER_ROUTE) {
        FileManagerScreen(onNavigateToEditor = onNavigateToEditor)
    }
}

fun NavController.navigateToFileManager(navOptions: NavOptions? = null) {
    navigate(FILE_MANAGER_ROUTE, navOptions)
}
