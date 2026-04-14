package dev.ori.feature.filemanager.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.ori.feature.filemanager.ui.FileManagerScreen

const val FILE_MANAGER_ROUTE = "filemanager"
const val FILE_MANAGER_WITH_PROFILE_ROUTE = "filemanager/{profileId}"

fun NavGraphBuilder.fileManagerScreen(
    onNavigateToEditor: (filePath: String, isRemote: Boolean) -> Unit = { _, _ -> },
) {
    composable(route = FILE_MANAGER_ROUTE) {
        FileManagerScreen(onNavigateToEditor = onNavigateToEditor)
    }
    composable(
        route = FILE_MANAGER_WITH_PROFILE_ROUTE,
        arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val profileId = backStackEntry.arguments?.getLong("profileId") ?: return@composable
        FileManagerScreen(
            initialProfileId = profileId,
            onNavigateToEditor = onNavigateToEditor,
        )
    }
}

fun NavController.navigateToFileManager(
    profileId: Long? = null,
    navOptions: NavOptions? = null,
) {
    val route = if (profileId != null) "filemanager/$profileId" else FILE_MANAGER_ROUTE
    navigate(route, navOptions)
}
