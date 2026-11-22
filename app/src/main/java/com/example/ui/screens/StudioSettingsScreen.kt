package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.video.ConnectionCheckResult
import com.example.data.video.OwnerBackendConfig
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.theme.AnamorphicCyan
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioRose
import com.example.ui.theme.StudioRoseDark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioSettingsScreen(
    config: OwnerBackendConfig,
    connectionCheck: ConnectionCheckResult?,
    isCheckingConnection: Boolean,
    onSaveConfig: (
        url: String,
        apiKey: String,
        modelId: String,
        authHeader: String,
        usesBearer: Boolean
    ) -> Unit,
    onClearConfig: () -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlInput by remember(config.backendBaseUrl) { mutableStateOf(config.backendBaseUrl) }
    var apiKeyInput by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var modelInput by remember(config.videoModelId) { mutableStateOf(config.videoModelId) }
    var headerNameInput by remember(config.authHeaderName) { mutableStateOf(config.authHeaderName) }
    var usesBearer by remember(config.usesBearerPrefix) { mutableStateOf(config.usesBearerPrefix) }
    var showApiKey by remember { mutableStateOf(false) }

    LaunchedEffect(config) {
        if (urlInput.isBlank() && config.backendBaseUrl.isNotBlank()) urlInput = config.backendBaseUrl
        if (apiKeyInput.isBlank() && config.apiKey.isNotBlank()) apiKeyInput = config.apiKey
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CinemaAmber)
                            Text(
                                text = "मालिक सुरक्षित वीडियो बैकएंड कॉन्फ़िगरेशन",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }

                        Text(
                            text = "समर्थित वीडियो जनरेशन API (जैसे Google Veo या आपका Cloud Run प्रॉक्सी बैकएंड) को सुरक्षित रूप से जोड़ें। आपकी API कुंजी Android Keystore (AES-256-GCM) से एन्क्रिप्ट होकर सेव होती है।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (config.fromBuildSecrets) {
                            Surface(
                                color = AnamorphicCyan.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "AI Studio Secrets पैनल (BuildConfig) से डिफ़ॉल्ट कॉन्फ़िगरेशन लोड किया गया है।",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AnamorphicCyan,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("सुरक्षित बैकएंड HTTPS URL") },
                            placeholder = { Text("https://your-video-backend.run.app") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backend_url_input")
                        )

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("मालिक API कुंजी / Bearer टोकन") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showApiKey) "कुंजी छिपाएं" else "कुंजी दिखाएं"
                                    )
                                }
                            },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backend_api_key_input")
                        )

                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("वीडियो मॉडल ID (उदा. veo-3.0-generate-preview)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backend_model_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = headerNameInput,
                                onValueChange = { headerNameInput = it },
                                label = { Text("ऑथ हेडर नाम") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Bearer उपसर्ग", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = usesBearer,
                                    onCheckedChange = { usesBearer = it }
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSaveConfig(
                                        urlInput,
                                        apiKeyInput,
                                        modelInput,
                                        headerNameInput,
                                        usesBearer
                                    )
                                },
                                modifier = Modifier.testTag("save_backend_config_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("एन्क्रिप्ट कर सेव करें")
                            }

                            OutlinedButton(
                                onClick = {
                                    onSaveConfig(
                                        urlInput,
                                        apiKeyInput,
                                        modelInput,
                                        headerNameInput,
                                        usesBearer
                                    )
                                    onTestConnection()
                                },
                                enabled = !isCheckingConnection,
                                modifier = Modifier.testTag("test_backend_connection_button")
                            ) {
                                if (isCheckingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                                }
                                Spacer(Modifier.size(6.dp))
                                Text("कनेक्शन जाँचें")
                            }

                            OutlinedButton(
                                onClick = {
                                    urlInput = ""
                                    apiKeyInput = ""
                                    onClearConfig()
                                },
                                modifier = Modifier.testTag("clear_backend_config_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("कुंजी हटाएं")
                            }
                        }

                        connectionCheck?.let { check ->
                            Surface(
                                color = if (check.success) EmeraldReady.copy(alpha = 0.14f) else StudioRoseDark,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (check.success) EmeraldReady.copy(alpha = 0.5f) else StudioRose.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (check.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (check.success) EmeraldReady else StudioRose
                                    )
                                    Text(
                                        text = check.detailHindi,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (check.success) EmeraldReady else StudioRose
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                FirebaseAiNoticeBanner()
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "बैकएंड API अनुबंध और पारदर्शिता नियम",
                            style = MaterialTheme.typography.titleMedium,
                            color = CinemaAmber
                        )
                        Text(
                            text = "• POST /v1/videos/generate — टेक्स्ट-टू-वीडियो और इमेज-टू-वीडियो (16:9, 9:16, 1:1) के लिए बैकएंड को अनुरोध भेजता है।\n" +
                                "• GET /v1/videos/operations/{jobId} — वास्तविक जनरेशन प्रगति और अंतिम MP4 URL प्राप्त करता है।\n" +
                                "• POST /v1/images/generate — हर सीन के लिए अलग AI रेफ़रेंस फ़ोटो जनरेट करता है।\n" +
                                "• POST /v1/audio/tts — सर्वर-साइड हिंदी AI वॉइस (WAV/MP3) जनरेट करता है (उपलब्ध न होने पर डिवाइस हिंदी TTS का उपयोग होता है)।\n" +
                                "• GET /health — बैकएंड कनेक्शन और प्रदाता की स्थिति जांचता है।\n" +
                                "• यह स्टूडियो कोई आंतरिक क्रेडिट शुल्क नहीं लेता, प्रदाता कोटा को बायपास नहीं करता और कभी भी नकली वीडियो प्रीव्यू नहीं दिखाता।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
