package com.mounsokdara.zyphvert.ui.files

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.mounsokdara.zyphvert.data.ThemeMode
import com.mounsokdara.zyphvert.tools.InKind
import com.mounsokdara.zyphvert.tools.PickedFile
import com.mounsokdara.zyphvert.ui.settings.SettingsViewModel
import com.mounsokdara.zyphvert.ui.theme.ZyphvertTheme
import dagger.hilt.android.AndroidEntryPoint

data class PickRequest(
    val kind: InKind, val multiple: Boolean, val title: String,
    val folder: Boolean = false,
    val startPath: String? = null,
)

sealed interface PickOutcome {
    data class Picked(val files: List<PickedFile>) : PickOutcome
    data object UseSystem : PickOutcome
    data object Cancelled : PickOutcome
}

private const val RESULT_USE_SYSTEM = Activity.RESULT_FIRST_USER
private const val X_KIND = "kind"
private const val X_MULTIPLE = "multiple"
private const val X_TITLE = "title"
private const val X_FOLDER = "folder"
private const val X_START = "start"
private const val X_URIS = "uris"
private const val X_NAMES = "names"
private const val X_COMPANIONS = "companions"
private const val X_LIVE = "live"

class PickFilesContract : ActivityResultContract<PickRequest, PickOutcome>() {
    override fun createIntent(context: Context, input: PickRequest): Intent =
        Intent(context, PickerActivity::class.java)
            .putExtra(X_KIND, input.kind.name)
            .putExtra(X_MULTIPLE, input.multiple)
            .putExtra(X_TITLE, input.title)
            .putExtra(X_FOLDER, input.folder)
            .putExtra(X_START, input.startPath)

    override fun parseResult(resultCode: Int, intent: Intent?): PickOutcome {
        if (resultCode == RESULT_USE_SYSTEM) return PickOutcome.UseSystem
        if (resultCode != Activity.RESULT_OK || intent == null) return PickOutcome.Cancelled
        val uris = intent.getStringArrayListExtra(X_URIS) ?: return PickOutcome.Cancelled
        val names = intent.getStringArrayListExtra(X_NAMES).orEmpty()
        val companions = intent.getStringArrayListExtra(X_COMPANIONS).orEmpty()
        val live = intent.getBooleanArrayExtra(X_LIVE) ?: BooleanArray(0)
        val files = uris.mapIndexed { i, u ->
            PickedFile(Uri.parse(u), names.getOrElse(i) { "" }, companions.getOrNull(i)?.ifEmpty { null }, live.getOrElse(i) { false })
        }
        return if (files.isEmpty()) PickOutcome.Cancelled else PickOutcome.Picked(files)
    }
}

@Composable
fun rememberFilePicker(onUseSystem: () -> Unit, onPicked: (List<PickedFile>) -> Unit): (PickRequest) -> Unit {
    val system by rememberUpdatedState(onUseSystem)
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(PickFilesContract()) { out ->
        when (out) {
            is PickOutcome.Picked -> picked(out.files)
            PickOutcome.UseSystem -> system()
            PickOutcome.Cancelled -> Unit
        }
    }
    return { request -> launcher.launch(request) }
}

@AndroidEntryPoint
class PickerActivity : ComponentActivity() {
    private val settingsVm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = runCatching { InKind.valueOf(intent.getStringExtra(X_KIND).orEmpty()) }.getOrDefault(InKind.Any)
        val multiple = intent.getBooleanExtra(X_MULTIPLE, false)
        val title = intent.getStringExtra(X_TITLE) ?: "Choose files"
        val folder = intent.getBooleanExtra(X_FOLDER, false)
        val startPath = intent.getStringExtra(X_START)
        setContent {
            val s by settingsVm.settings.collectAsState()
            val dark = when (s.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b)
                    ) { dark },
                )
                onDispose { }
            }
            ZyphvertTheme(darkTheme = dark, dynamicColor = s.dynamicColor, amoled = s.extraDark, seedColor = s.seedColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    FileManagerScreen(
                        pick = PickConfig(
                            kind = kind, multiple = multiple, title = title, folder = folder, startPath = startPath,
                            onDismiss = { finish() },
                            onUseSystem = { setResult(RESULT_USE_SYSTEM); finish() },
                            onPicked = { files ->
                                setResult(
                                    Activity.RESULT_OK,
                                    Intent()
                                        .putStringArrayListExtra(X_URIS, ArrayList(files.map { it.uri.toString() }))
                                        .putStringArrayListExtra(X_NAMES, ArrayList(files.map { it.name }))
                                        .putStringArrayListExtra(X_COMPANIONS, ArrayList(files.map { it.companion.orEmpty() }))
                                        .putExtra(X_LIVE, files.map { it.live }.toBooleanArray()),
                                )
                                finish()
                            },
                        ),
                    )
                }
            }
        }
    }
}
