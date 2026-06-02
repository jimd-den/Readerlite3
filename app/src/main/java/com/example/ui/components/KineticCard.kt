package com.example.ui.components

import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AnimationChoreography
import com.example.domain.model.MixProfile
import com.example.domain.model.SentenceComposition
import com.example.ui.util.CompositionSampler
import com.example.ui.util.SignalExtractor
import kotlinx.coroutines.delay

@Composable
fun SimpleFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        val spacing = 8.dp.roundToPx()

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + spacing
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        var totalHeight = 0
        val rowHeights = rows.map { row ->
            val maxH = row.maxOfOrNull { it.height } ?: 0
            totalHeight += maxH + spacing
            maxH
        }
        totalHeight = (totalHeight - spacing).coerceAtLeast(0)

        layout(constraints.maxWidth, totalHeight) {
            var currentY = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowHeight = rowHeights[rowIndex]
                var currentX = when (horizontalArrangement) {
                    Arrangement.End -> constraints.maxWidth - row.sumOf { it.width } - (row.size - 1) * spacing
                    Arrangement.Center -> (constraints.maxWidth - row.sumOf { it.width } - (row.size - 1) * spacing) / 2
                    else -> 0
                }
                row.forEach { placeable ->
                    placeable.placeRelative(currentX, currentY + (rowHeight - placeable.height) / 2)
                    currentX += placeable.width + spacing
                }
                currentY += rowHeight + spacing
            }
        }
    }
}

@Composable
fun KineticCard(
    sentenceId: String,
    sentenceText: String,
    subheading: String?,
    index: Int,
    profile: MixProfile,
    userFontSize: Float,
    customFontFamily: FontFamily?,
    modifier: Modifier = Modifier,
    forceCodeView: Boolean = false
) {
    val signals = remember(sentenceText) { SignalExtractor.extract(sentenceText) }
    val composition = remember(sentenceId, profile.id, profile.chaosLevel, userFontSize) {
        CompositionSampler.getOrPut(sentenceId, sentenceText, profile, userFontSize)
    }

    val isCodeBlock = forceCodeView || signals.isCode

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isCodeBlock) {
                        listOf(Color(0xFF1E1E1E), Color(0xFF121212))
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    }
                )
            )
            .border(
                width = 1.dp,
                color = if (isCodeBlock) Color.DarkGray else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isCodeBlock) Alignment.Start else Alignment.CenterHorizontally
        ) {
            // Label / Subheading Box
            val labelText = if (isCodeBlock) "CODE SYNTAX BLOCK" else (subheading?.uppercase() ?: "INTRO")
            Text(
                text = labelText,
                color = if (isCodeBlock) Color(0xFFFFCC00) else MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .background(
                        color = (if (isCodeBlock) Color(0xFFFFCC00) else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            if (isCodeBlock) {
                // Highlighting layout for code entries
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = sentenceText,
                        color = Color(0xFFE5C07B), // warm yellow-orange soft tone
                        fontFamily = FontFamily.Monospace,
                        fontSize = (userFontSize * 0.9f).coerceAtLeast(12f).sp,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // Typography Engine layout using simple phrases flow wrapping list
                SimpleFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = when (composition.alignmentMode) {
                        "center" -> Arrangement.Center
                        "right" -> Arrangement.End
                        else -> Arrangement.Start
                    }
                ) {
                    var wordCounter = 0
                    composition.phrases.forEachIndexed { pIdx, phraseText ->
                        val startWordGlobalIdx = wordCounter
                        val wordsComp = phraseText.split(" ").filter { it.isNotEmpty() }
                        wordCounter += wordsComp.size

                        var isAnimated by remember(sentenceId) { mutableStateOf(false) }
                        LaunchedEffect(sentenceId) {
                            if (profile.reduceMotion) {
                                isAnimated = true
                            } else {
                                delay(composition.phraseDelays.getOrElse(pIdx) { 0L })
                                isAnimated = true
                            }
                        }

                        val duration = if (composition.animationChoreography == AnimationChoreography.SLOW_EMERGE) 600 else 300
                        val alphaAnim by animateFloatAsState(
                            targetValue = if (isAnimated) 1f else 0f,
                            animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                            label = "p_alpha_${sentenceId}_$pIdx"
                        )
                        
                        val startTranslateY = composition.phraseTranslateYStart.getOrElse(pIdx) { 0f }
                        val translateYAnim by animateFloatAsState(
                            targetValue = if (isAnimated) 0f else startTranslateY,
                            animationSpec = if (composition.animationChoreography == AnimationChoreography.GRAVITY_DROP) {
                                androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                )
                            } else {
                                tween(durationMillis = 350, easing = EaseOutQuad)
                            },
                            label = "p_transY_${sentenceId}_$pIdx"
                        )

                        val scaleAnim by animateFloatAsState(
                            targetValue = if (isAnimated) 1f else if (composition.animationChoreography == AnimationChoreography.WEIGHT_PULSE) 0.94f else 1f,
                            animationSpec = tween(durationMillis = 250, easing = EaseOutBack),
                            label = "p_scale_${sentenceId}_$pIdx"
                        )

                        // Wrap words of this phrase in a single horizontal row so they stay together as a structural "readable section"
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = alphaAnim
                                    translationY = translateYAnim * density
                                    scaleX = scaleAnim
                                    scaleY = scaleAnim
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            wordsComp.forEachIndexed { wIdx, word ->
                                val globalWordIdx = startWordGlobalIdx + wIdx
                                val weightVal = composition.wordWeights.getOrElse(globalWordIdx) { 400 }
                                val sizeVal = composition.wordSizes.getOrElse(globalWordIdx) { userFontSize }
                                val opacityVal = composition.wordOpacities.getOrElse(globalWordIdx) { 1.0f }
                                val trackingVal = composition.wordTrackings.getOrElse(globalWordIdx) { 0.0f }

                                Text(
                                    text = if (wIdx == wordsComp.lastIndex) word else "$word ",
                                    fontSize = sizeVal.sp,
                                    fontWeight = FontWeight(weightVal),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = opacityVal),
                                    letterSpacing = trackingVal.sp,
                                    fontFamily = customFontFamily ?: when (composition.layoutMode) {
                                        "display" -> FontFamily.SansSerif
                                        "poster" -> FontFamily.Serif
                                        else -> FontFamily.Default
                                    },
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Footer metadata showing citation context
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SENTENCE ${index + 1}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
