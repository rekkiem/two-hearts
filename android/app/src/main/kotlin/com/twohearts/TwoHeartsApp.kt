package com.twohearts

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.twohearts.navigation.TwoHeartsNavHost
import com.twohearts.ui.theme.TwoHeartsTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TwoHeartsApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle deep link from magic link email
        val magicToken = intent?.data?.let { uri ->
            if (uri.scheme == "twohearts" && uri.host == "auth") {
                uri.getQueryParameter("token")
            } else null
        }

        setContent {
            TwoHeartsTheme {
                TwoHeartsNavHost(initialMagicToken = magicToken)
            }
        }
    }
}
