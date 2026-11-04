package com.mounsokdara.zyphvert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mounsokdara.zyphvert.ui.ZyphvertShell
import com.mounsokdara.zyphvert.ui.theme.ZyphvertTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZyphvertTheme {
                ZyphvertShell()
            }
        }
    }
}
