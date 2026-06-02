package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MixProfile

@Composable
fun TypographyPlayground(
    activeProfile: MixProfile,
    customProfiles: List<MixProfile>,
    onProfileSelected: (MixProfile) -> Unit,
    onSaveProfile: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onClose: () -> Unit,
    onChaosChanged: (Float) -> Unit,
    onTempoChanged: (Float) -> Unit,
    onSizeChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveProfileName by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙ ENGINE CALIBRATOR",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close panel")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Presets List
            Text(
                text = "Preset Profiles",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Built-in presets
                items(MixProfile.BUILT_IN_PROFILES) { preset ->
                    val isSelected = activeProfile.id == preset.id
                    PresetChip(
                        name = preset.name,
                        selected = isSelected,
                        onClick = { onProfileSelected(preset) }
                    )
                }

                // Custom presets
                items(customProfiles) { preset ->
                    val isSelected = activeProfile.id == preset.id
                    PresetChip(
                        name = preset.name + " (u)",
                        selected = isSelected,
                        onClick = { onProfileSelected(preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sliders Panel
            EngineSlider(
                label = "ENGINE CHAOS DIAL",
                value = activeProfile.chaosLevel,
                valueRange = 0.0f..1.0f,
                onValueChange = onChaosChanged,
                textLabel = "%.2f".format(activeProfile.chaosLevel)
            )

            EngineSlider(
                label = "TEMPO DELAY SCALE",
                value = activeProfile.tempoScale,
                valueRange = 0.5f..2.0f,
                onValueChange = onTempoChanged,
                textLabel = "%.2fx".format(activeProfile.tempoScale)
            )

            EngineSlider(
                label = "FONT SCALE INDEX",
                value = activeProfile.sizeScale,
                valueRange = 0.7f..1.5f,
                onValueChange = onSizeChanged,
                textLabel = "%.2fx".format(activeProfile.sizeScale)
            )

            EngineSlider(
                label = "WEIGHT VARIATION SPREAD",
                value = activeProfile.weightContrast,
                valueRange = 0.0f..1.0f,
                onValueChange = onWeightChanged,
                textLabel = "%.2f".format(activeProfile.weightContrast)
            )

            EngineSlider(
                label = "OPACITY HIGHLIGHT DEPTH",
                value = activeProfile.opacityDepth,
                valueRange = 0.0f..1.0f,
                onValueChange = onOpacityChanged,
                textLabel = "%.2f".format(activeProfile.opacityDepth)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Custom", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset-Vibe", fontSize = 12.sp)
                }
            }
        }
    }

    // Modal dialog to input profile name
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Vibe Preset", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = saveProfileName,
                    onValueChange = { saveProfileName = it },
                    label = { Text("Preset Name (e.g. Dreamy Spark)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveProfileName.isNotBlank()) {
                            onSaveProfile(saveProfileName.trim())
                            saveProfileName = ""
                            showSaveDialog = false
                        }
                    }
                ) {
                    Text("Save Preset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PresetChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EngineSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    textLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = textLabel,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}
