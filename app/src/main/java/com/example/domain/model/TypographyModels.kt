package com.example.domain.model

enum class AnimationChoreography {
    CASCADE_UP,      // rise from +12dp to 0
    CASCADE_DOWN,    // fall from -12dp to 0
    OPACITY_BLOOM,   // opacity 0 to target, no translate
    FOCAL_SNAP,      // snap visible simultaneously
    WAVE_ROLL,       // sine-wave stagger
    DRIFT_SETTLE,    // translateX +/- 8dp + translateY +6dp to 0
    GRAVITY_DROP,    // spring drop from above (-20dp to 0)
    BURST_OUT,       // stagger from center outward
    TYPEWRITER,      // left to right rapid reveals
    WEIGHT_PULSE,    // scale + opacity pulse
    SLOW_EMERGE,     // slow opacity bloom
    NONE             // immediate, no animation
}

data class TypographySignals(
    val wordCount: Int,
    val isShortForm: Boolean,
    val isPosterCandidate: Boolean,
    val hasExclamation: Boolean,
    val hasQuestion: Boolean,
    val capsRatio: Float,
    val longestWordLength: Int,
    val punctuationDensity: Float,
    val isCode: Boolean,
    val isHeading: Boolean,
    val isBlockquote: Boolean
)

data class SentenceComposition(
    val layoutMode: String, // "prose" | "display" | "code" | "poster"
    val alignmentMode: String, // "left" | "center" | "right"
    val animationChoreography: AnimationChoreography,
    val tempoMs: Long,
    val wordTimingCurve: String, // "linear" | "ease-in" | "ease-out" | "burst" | "wave"
    
    // Per-word style (set statically at render time — NOT animated)
    val wordWeights: List<Int>,         // fontWeight per word [300–800]
    val wordSizes: List<Float>,          // fontSize modifier per word
    val wordOpacities: List<Float>,       // opacity per word [0.7–1.0]
    val wordTrackings: List<Float>,       // letterSpacing per word (sp multiplier)
    
    // Per-phrase animation (phrase = 3–5 words grouped together)
    val phraseTranslateYStart: List<Float>, // initial offset (dp)
    val phraseDelays: List<Long>,          // delay per phrase (ms)
    val phrases: List<String>              // chunked phrase texts
)

data class MixProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val chaosLevel: Float,       // 0.0–1.0
    val tempoScale: Float,       // 0.5–2.0
    val sizeScale: Float,        // 0.7–1.5
    val weightContrast: Float,   // 0.0–1.0 (spread between min/max weight)
    val opacityDepth: Float,     // 0.0–1.0 (how much opacity dips)
    val alignmentBias: String,   // "left" | "center" | "mixed"
    val allowRightAlign: Boolean,
    val animationSet: List<AnimationChoreography>,
    val reduceMotion: Boolean
) {
    companion object {
        val ALL_CHOREOGRAPHIES = AnimationChoreography.values().toList()
        val ACTIVE_CHOREOGRAPHIES = ALL_CHOREOGRAPHIES.filter { it != AnimationChoreography.NONE }
        
        val BUILT_IN_PROFILES = listOf(
            MixProfile("calm-focus", "Calm Focus", true, 0.1f, 1.1f, 1.0f, 0.1f, 0.1f, "left", false, listOf(AnimationChoreography.CASCADE_UP, AnimationChoreography.OPACITY_BLOOM, AnimationChoreography.SLOW_EMERGE), false),
            MixProfile("editorial", "Editorial", true, 0.3f, 0.9f, 1.1f, 0.4f, 0.4f, "mixed", false, listOf(AnimationChoreography.CASCADE_UP, AnimationChoreography.OPACITY_BLOOM, AnimationChoreography.DRIFT_SETTLE, AnimationChoreography.TYPEWRITER), false),
            MixProfile("electric", "Electric", true, 0.6f, 0.7f, 1.2f, 0.7f, 0.7f, "mixed", true, ACTIVE_CHOREOGRAPHIES, false),
            MixProfile("poster", "Poster", true, 0.5f, 0.8f, 1.4f, 0.8f, 0.5f, "center", true, listOf(AnimationChoreography.FOCAL_SNAP, AnimationChoreography.BURST_OUT, AnimationChoreography.GRAVITY_DROP, AnimationChoreography.WEIGHT_PULSE), false),
            MixProfile("ghost", "Ghost", true, 0.4f, 1.2f, 0.95f, 0.3f, 0.8f, "left", false, listOf(AnimationChoreography.OPACITY_BLOOM, AnimationChoreography.SLOW_EMERGE), false),
            MixProfile("max-chaos", "Maximum", true, 1.0f, 0.6f, 1.3f, 1.0f, 1.0f, "mixed", true, ALL_CHOREOGRAPHIES, false)
        )
    }
}

enum class ColorThemeOption(val displayName: String) {
    COSMIC_SLATE("Cosmic Slate"),
    LIGHT_MINIMAL("Light Minimal"),
    SEPIA_VINTAGE("Sepia Vintage"),
    WARM_SUNSET("Warm Sunset"),
    FOREST_INK("Forest Ink"),
    OBSIDIAN_OLED("Obsidian OLED")
}
