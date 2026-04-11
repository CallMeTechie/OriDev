package dev.ori.feature.filemanager.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.ori.feature.filemanager.ui.FileManagerScreen

const val FILE_MANAGER_ROUTE = "filemanager"

fun NavGraphBuilder.fileManagerScreen() {
    composable(route = FILE_MANAGER_ROUTE) {
        FileManagerScreen()
    }
}

fun NavController.navigateToFileManager(navOptions: NavOptions? = null) {
    navigate(FILE_MANAGER_ROUTE, navOptions)
}
