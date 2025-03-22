package org.davidrevolt.artmoney

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import artmoney.composeapp.generated.resources.Res
import artmoney.composeapp.generated.resources.app_window_icon
import org.davidrevolt.artmoney.di.coreModule
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.KoinApplication


fun main() = application {
    val icon = painterResource(Res.drawable.app_window_icon)
    Window(
        onCloseRequest = ::exitApplication,
        title = "ArtMoney",
        icon = icon
    ) {
        KoinApplication(
            application = {
                modules(coreModule)
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                App()
            }
        }
    }
}
