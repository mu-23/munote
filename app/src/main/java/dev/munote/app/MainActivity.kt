package dev.munote.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dev.munote.app.ui.MuNoteApp
import dev.munote.app.ui.theme.MuNoteTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.applySavedOrDefault(this)
        super.onCreate(savedInstanceState)
        setContent {
            MuNoteTheme {
                MuNoteApp(initialPdf = intent?.data)
            }
        }
    }
}
