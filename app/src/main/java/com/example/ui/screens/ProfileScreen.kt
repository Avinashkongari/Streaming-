package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.config.ApiConfig
import com.example.data.model.AppSessionMode
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AxionCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceCarbon
import com.example.ui.theme.SurfaceSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    sessionMode: AppSessionMode,
    favoritesCount: Int,
    watchedCount: Int,
    onLogout: () -> Unit
) {
    val auth = sessionMode as? AppSessionMode.Authenticated
    val isPreview = sessionMode is AppSessionMode.UiPreview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )

            // Account Status Card
            Surface(
                color = SurfaceCarbon,
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_account_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (isPreview) AmberWarning.copy(alpha = 0.16f) else AxionCrimson.copy(alpha = 0.18f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPreview) Icons.Outlined.Visibility else Icons.Filled.Person,
                                contentDescription = null,
                                tint = if (isPreview) AmberWarning else AxionCrimson,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    auth != null -> auth.user.displayName ?: "Private Member"
                                    isPreview -> "UI Preview Session"
                                    else -> "Not Signed In"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            StatusPill(
                                label = when {
                                    auth != null -> auth.user.status.uppercase()
                                    isPreview -> "UNAUTHENTICATED PREVIEW"
                                    else -> "INACTIVE"
                                },
                                color = if (auth != null) EmeraldActive else AmberWarning
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatMetric(label = "My List", value = favoritesCount.toString())
                        StatMetric(label = "In Progress", value = watchedCount.toString())
                        StatMetric(
                            label = "Device Lock",
                            value = if (auth?.user?.deviceBound == true) "Bound" else "None"
                        )
                    }
                }
            }

            // Access Code Status Card
            Surface(
                color = SurfaceCarbon,
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_access_code_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = AxionCrimson,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.profile_access_code_status),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    InfoRow(
                        label = "Code",
                        value = auth?.accessCode?.maskedCode ?: "No active code (UI Preview)",
                        mono = true
                    )
                    InfoRow(
                        label = "Status",
                        value = auth?.accessCode?.status?.replaceFirstChar { it.uppercase() } ?: "Not Activated"
                    )
                    InfoRow(
                        label = "Ownership",
                        value = if (auth?.accessCode?.singleUserLocked == true) {
                            "Single-user private lock"
                        } else {
                            "Requires invitation code"
                        }
                    )
                    if (!auth?.accessCode?.expiresAt.isNullOrBlank()) {
                        InfoRow(
                            label = "Valid Until",
                            value = auth?.accessCode?.expiresAt.orEmpty()
                        )
                    }
                }
            }

            // Backend & Supabase Integration Status
            Surface(
                color = SurfaceCarbon,
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.profile_backend_status),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    InfoRow(
                        label = "Streaming API",
                        value = if (ApiConfig.isBackendConfigured) {
                            ApiConfig.backendHost ?: "Configured"
                        } else {
                            "Not configured in Secrets"
                        },
                        mono = true
                    )
                    InfoRow(
                        label = "Supabase Metadata",
                        value = if (ApiConfig.isSupabaseConfigured) "Connected (RLS)" else "Pending configuration"
                    )
                    InfoRow(
                        label = "Stream Protocol",
                        value = "Progressive HTTP Range (≤200 MB)"
                    )
                }
            }

            // Privacy & Streaming Safeguards
            Surface(
                color = SurfaceCarbon,
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = EmeraldActive,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.profile_security_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }

                    SafeguardItem("Temporary RAM buffering only — zero full-file downloads")
                    SafeguardItem("Never writes or exports media to your device Gallery")
                    SafeguardItem("Zero Telegram bot tokens or channel secrets inside the app")
                    SafeguardItem("Session tokens encrypted on-device via Android Keystore")
                }
            }

            // Logout / Exit Preview CTA
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("profile_logout_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPreview) AxionCrimson else SurfaceSlate,
                    contentColor = TextPrimary
                ),
                border = if (isPreview) null else BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (isPreview) R.string.profile_exit_preview else R.string.profile_logout
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = TextPrimary
        )
    }
}

@Composable
private fun SafeguardItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = EmeraldActive,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
