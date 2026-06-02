package com.example.ui.util

data class MixProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val chaosLevel: Float,       // 0.0–1.0
    val tempoScale: Float,       // 0.5–2.0 (multiplies all animation delays)
    val sizeScale: Float,        // 0.7–1.5 (multiplies all font sizes)
    val weightContrast: Float,   // 0.0–1.0 (spread between min and max word weight)
    val opacityDepth: Float,     // 0.0–1.0 (how much opacity curve dips)
    val alignmentBias: String,   // "left" | "center" | "mixed"
    val allowRightAlign: Boolean,
    val animationSet: List<String>, // List of animations allowed, e.g., ["all"]
    val reduceMotion: Boolean = false
) {
    companion object {
        val BUILT_IN_PROFILES = listOf(
            MixProfile(
                id = "calm-focus",
                name = "Calm Focus",
                isBuiltIn = true,
                chaosLevel = 0.1f,
                tempoScale = 1.1f,
                sizeScale = 1.0f,
                weightContrast = 0.1f,
                opacityDepth = 0.3f,
                alignmentBias = "left",
                allowRightAlign = false,
                animationSet = listOf("cascade-up", "opacity-bloom", "slow-emerge"),
                reduceMotion = false
            ),
            MixProfile(
                id = "editorial",
                name = "Editorial",
                isBuiltIn = true,
                chaosLevel = 0.3f,
                tempoScale = 0.9f,
                sizeScale = 1.1f,
                weightContrast = 0.4f,
                opacityDepth = 0.5f,
                alignmentBias = "mixed",
                allowRightAlign = false,
                animationSet = listOf("cascade-up", "cascade-down", "opacity-bloom"),
                reduceMotion = false
            ),
            MixProfile(
                id = "electric",
                name = "Electric",
                isBuiltIn = true,
                chaosLevel = 0.6f,
                tempoScale = 0.7f,
                sizeScale = 1.2f,
                weightContrast = 0.7f,
                opacityDepth = 0.8f,
                alignmentBias = "mixed",
                allowRightAlign = true,
                animationSet = listOf("all"),
                reduceMotion = false
            ),
            MixProfile(
                id = "poster",
                name = "Poster",
                isBuiltIn = true,
                chaosLevel = 0.5f,
                tempoScale = 0.8f,
                sizeScale = 1.4f,
                weightContrast = 0.8f,
                opacityDepth = 0.6f,
                alignmentBias = "center",
                allowRightAlign = true,
                animationSet = listOf("focal-snap", "burst-out", "gravity-drop", "weight-pulse"),
                reduceMotion = false
            ),
            MixProfile(
                id = "ghost",
                name = "Ghost",
                isBuiltIn = true,
                chaosLevel = 0.4f,
                tempoScale = 1.2f,
                sizeScale = 0.95f,
                weightContrast = 0.3f,
                opacityDepth = 0.9f,
                alignmentBias = "left",
                allowRightAlign = false,
                animationSet = listOf("slow-emerge", "opacity-bloom"),
                reduceMotion = false
            ),
            MixProfile(
                id = "max-chaos",
                name = "Maximum",
                isBuiltIn = true,
                chaosLevel = 1.0f,
                tempoScale = 0.6f,
                sizeScale = 1.3f,
                weightContrast = 1.0f,
                opacityDepth = 1.0f,
                alignmentBias = "mixed",
                allowRightAlign = true,
                animationSet = listOf("all"),
                reduceMotion = false
            )
        )
    }
}
