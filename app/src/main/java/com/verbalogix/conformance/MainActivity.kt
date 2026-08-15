package com.verbalogix.conformance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verbalogix.conformance.ui.HomeScreen
import com.verbalogix.conformance.ui.HomeViewModel
import com.verbalogix.conformance.ui.theme.ConformanceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var notificationsGranted by mutableStateOf<Boolean?>(null)

    // Declared as a field so it is registered before onCreate returns, which the
    // Activity Result API requires.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsGranted = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // POST_NOTIFICATIONS is runtime-requestable from API 33. Every permission
        // needs a stated behaviour on denial; an unhandled denial is a crash waiting
        // for the first user who taps "Deny". Here the app degrades silently and
        // says so on screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val already = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (already) {
                notificationsGranted = true
            } else {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ConformanceTheme {
                val vm: HomeViewModel = hiltViewModel()
                val count by vm.itemCount.collectAsStateWithLifecycle()
                val granted = remember(notificationsGranted) { notificationsGranted }

                HomeScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    gitSha = BuildConfig.GIT_SHA,
                    itemCount = count,
                    notificationsGranted = granted,
                )
            }
        }
    }
}
