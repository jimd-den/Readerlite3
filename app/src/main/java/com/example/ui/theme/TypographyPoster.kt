package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.example.domain.model.MixProfile
import com.example.ui.components.KineticCard

@Composable
fun TypographyPoster(
    sentence: String,
    subheading: String?,
    index: Int,
    modifier: Modifier = Modifier
) {
    KineticCard(
        sentenceId = "poster_$index",
        sentenceText = sentence,
        subheading = subheading ?: "",
        index = index,
        profile = MixProfile.BUILT_IN_PROFILES[0],
        userFontSize = 24f,
        customFontFamily = null,
        modifier = modifier
    )
}
