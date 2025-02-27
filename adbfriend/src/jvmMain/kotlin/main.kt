import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mikepenz.adbfriend.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AdbFriend",
    ) {
        App()
    }
}