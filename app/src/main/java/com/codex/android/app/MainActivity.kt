package com.codex.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.codex.android.app.ui.MainScreen
import com.codex.android.app.ui.MainViewModel
import com.codex.android.app.ui.theme.CodexAndroidTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodexAndroidTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

