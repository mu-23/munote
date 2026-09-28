package dev.munote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.munote.app.ui.MuNoteApp
import dev.munote.app.ui.theme.MuNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MuNoteTheme {
                MuNoteApp(initialPdf = intent?.data)
            }
        }
    }
}
