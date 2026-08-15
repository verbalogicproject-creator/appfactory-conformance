package com.verbalogix.conformance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.verbalogix.conformance.ui.theme.ConformanceTheme

/**
 * Build provenance, rendered on screen.
 *
 * This is not decoration. "Is the APK on my phone the one I just built?" is otherwise
 * unanswerable, and a stale artifact looks exactly like a fresh one. That mistake was
 * made once in this lineage and caught only because two uploads had byte-identical
 * sizes. Here, a screenshot is evidence.
 *
 * Every value is a PARAMETER. No literal from a design mockup lives inside a
 * composable -- fabricated sample numbers shipping as if they were real measurements
 * is its own failure class, so mockup values belong in @Preview providers only.
 */
@Composable
fun HomeScreen(
    versionName: String,
    versionCode: Int,
    gitSha: String,
    itemCount: Int,
    notificationsGranted: Boolean?,
    serializationOk: Boolean?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("CONFORMANCE", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "appfactory pipeline self-test",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            ProvenanceCard(versionName, versionCode, gitSha)

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("SUBSYSTEMS", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    // Reaching this screen at all proves Hilt built its graph: the
                    // host Activity is @AndroidEntryPoint and the view model is
                    // @HiltViewModel, so a missing @HiltAndroidApp would have thrown
                    // before any of this composed.
                    KeyValue("hilt", "graph built")
                    KeyValue("room", "$itemCount row(s)")
                    // On a release build this line is R8 evidence: reaching it at
                    // all means minification did not strip the serializer. If it
                    // had, startup would have thrown before composing.
                    KeyValue(
                        "serialization",
                        when (serializationOk) {
                            null -> "checking"
                            true -> "round-trip ok"
                            false -> "FAILED"
                        },
                    )
                    KeyValue(
                        "notifications",
                        when (notificationsGranted) {
                            null -> "not requested"
                            true -> "granted"
                            false -> "denied — app still works"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProvenanceCard(versionName: String, versionCode: Int, gitSha: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("BUILD", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            KeyValue("version", versionName)
            KeyValue("code", versionCode.toString())
            KeyValue("commit", gitSha)
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

// Sample values live here and nowhere else.
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ConformanceTheme(darkTheme = true) {
        HomeScreen(
            versionName = "0.0.1",
            versionCode = 1,
            gitSha = "abc1234",
            itemCount = 3,
            notificationsGranted = true,
            serializationOk = true,
        )
    }
}
