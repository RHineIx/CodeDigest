package com.codedigest.ix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.codedigest.ix.data.PreferencesManager
import com.codedigest.ix.ui.MainScreen
import com.codedigest.ix.ui.theme.CodeDigestTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = PreferencesManager(this)
        val factory = MainViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            CodeDigestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}
