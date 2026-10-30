package com.streamtv.app

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.streamtv.app.navigation.AppNavigation
import com.streamtv.app.provider.CompositeMetadataProvider
import com.streamtv.app.provider.CompositeStreamProvider
import com.streamtv.app.ui.theme.StreamTvTheme
import com.streamtv.core.model.ContentType
import com.streamtv.providers.m3u.M3uStreamProvider
import com.streamtv.providers.tmdb.TmdbConfig
import com.streamtv.providers.tmdb.TmdbMetadataProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val tmdbConfig = TmdbConfig()
    private val tmdbProvider = TmdbMetadataProvider(config = tmdbConfig)
    private val m3uProvider = M3uStreamProvider()

    private val metadataProvider = CompositeMetadataProvider(
        tmdbProvider = tmdbProvider,
        m3uProvider = m3uProvider,
        tmdbConfig = tmdbConfig
    )

    private val streamProvider = CompositeStreamProvider(
        m3uProvider = m3uProvider
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tmdbConfig.apiKey = getSharedPreferences("streamtv_settings", MODE_PRIVATE)
            .getString("tmdb_api_key", null)

        setContent {
            StreamTvTheme {
                AppNavigation(
                    metadataProvider = metadataProvider,
                    streamProvider = streamProvider,
                    tmdbConfig = tmdbConfig,
                    m3uProvider = m3uProvider
                )
            }
        }

        if (!tmdbConfig.isConfigured) {
            showTmdbKeyDialog()
        }
    }

    private fun showTmdbKeyDialog() {
        val input = EditText(this).apply {
            hint = "TMDB API key (v3)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Set up TMDB")
            .setMessage("Enter your TMDB API key (v3). TMDB provides information, not video streams.")
            .setView(input)
            .setNegativeButton("Later", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val key = input.text.toString().trim()
                if (key.isBlank()) {
                    input.error = "Enter your TMDB API key"
                } else {
                    input.isEnabled = false
                    saveButton.isEnabled = false
                    saveButton.text = "Checking..."
                    val candidate = TmdbMetadataProvider(config = TmdbConfig(apiKey = key))
                    lifecycleScope.launch {
                        val result = candidate.getTrending(ContentType.MOVIE)
                        if (!dialog.isShowing) return@launch
                        if (result.isSuccess) {
                            getSharedPreferences("streamtv_settings", MODE_PRIVATE)
                                .edit().putString("tmdb_api_key", key).apply()
                            dialog.dismiss()
                            recreate()
                        } else {
                            input.isEnabled = true
                            saveButton.isEnabled = true
                            saveButton.text = "Save"
                            input.error = "Check your key and internet connection"
                            input.requestFocus()
                        }
                    }
                }
            }
        }
        dialog.show()
    }
}
