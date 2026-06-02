package com.example.ui.util

import com.example.domain.model.AnimationChoreography
import com.example.domain.model.MixProfile
import com.example.domain.model.SentenceComposition
import com.example.domain.model.TypographySignals
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI

object CompositionSampler {
    private val cache = LinkedHashMap<String, SentenceComposition>(20, 0.75f, true)
    private const val CACHE_MAX = 20

    private class Mulberry32(private var seed: Long) {
        fun nextFloat(): Float {
            seed = (seed + 0x6D2B79F5L) and 0xFFFFFFFFL
            var t = (seed xor (seed ushr 15)) * (1 or seed.toInt())
            t = t and 0xFFFFFFFFL
            t = (t + ((t xor (t ushr 7)) * (61 or t.toInt()))) xor t
            t = t and 0xFFFFFFFFL
            val rv = (t xor (t ushr 14)) and 0xFFFFFFFFL
            return rv.toFloat() / 4294967296f
        }
    }

    private fun hashString(str: String): Long {
        var hash = 5381L
        for (char in str) {
            hash = ((hash shl 5) + hash) + char.toLong()
            hash = hash and 0xFFFFFFFFL
        }
        return hash
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + fraction * (end - start)
    }

    @Synchronized
    fun getOrPut(sentenceId: String, text: String, profile: MixProfile, userFontSize: Float): SentenceComposition {
        val key = "$sentenceId:${profile.id}:${profile.chaosLevel}:${userFontSize}"
        val existing = cache[key]
        if (existing != null) {
            return existing
        }

        val generated = generate(sentenceId, text, profile, userFontSize)
        
        if (cache.size >= CACHE_MAX) {
            val eldestKey = cache.keys.firstOrNull()
            if (eldestKey != null) {
                cache.remove(eldestKey)
            }
        }
        cache[key] = generated
        return generated
    }

    private fun generate(sentenceId: String, text: String, profile: MixProfile, userFontSize: Float): SentenceComposition {
        val signals = SignalExtractor.extract(text)
        val seed = hashString(sentenceId)
        val rng = Mulberry32(seed)

        // 1. Layout Mode Determine
        val layoutMode = when {
            signals.isCode -> "code"
            signals.isHeading -> "display"
            signals.isBlockquote -> "prose"
            signals.wordCount <= 5 && rng.nextFloat() < (0.4f + profile.chaosLevel * 0.4f) -> "poster"
            signals.wordCount <= 10 && rng.nextFloat() < (0.3f + profile.chaosLevel * 0.3f) -> "display"
            else -> "prose"
        }

        // 2. Alignment Mode Determine
        val alignmentMode = when (layoutMode) {
            "code" -> "left"
            "poster" -> {
                val rand = rng.nextFloat()
                if (rand < 0.6f) "center" else if (profile.allowRightAlign && rand < 0.8f) "right" else "left"
            }
            "display" -> {
                if (rng.nextFloat() < (0.5f + profile.chaosLevel * 0.3f)) "center" else "left"
            }
            else -> "left" // prose is always left
        }

        // 3. Animation Choreography Selection
        val animationChoreography = when {
            signals.isCode || signals.isHeading || signals.isBlockquote -> AnimationChoreography.NONE
            profile.reduceMotion -> AnimationChoreography.NONE
            else -> {
                val available = if (profile.animationSet.isEmpty()) {
                    MixProfile.ACTIVE_CHOREOGRAPHIES
                } else {
                    profile.animationSet.filter { it != AnimationChoreography.NONE }
                }

                if (available.isEmpty()) {
                    AnimationChoreography.NONE
                } else {
                    val index = (rng.nextFloat() * available.size).toInt().coerceIn(0, available.size - 1)
                    available[index]
                }
            }
        }

        // 4. Words split and profile characteristics scale maps
        val wordsRaw = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val words = if (wordsRaw.isEmpty()) listOf(" ") else wordsRaw
        
        val wordWeights = ArrayList<Int>(words.size)
        val wordSizes = ArrayList<Float>(words.size)
        val wordOpacities = ArrayList<Float>(words.size)
        val wordTrackings = ArrayList<Float>(words.size)

        val connectors = setOf("the", "a", "an", "and", "or", "but", "if", "then", "of", "in", "on", "at", "to", "for", "with", "by", "as", "is", "was", "are", "were", "be", "been", "has", "have", "had", "it", "its", "this", "that", "these", "those")

        for (i in words.indices) {
            val randWeight = rng.nextFloat()
            val cleanWord = words[i].lowercase().replace(Regex("[^a-z]"), "")
            
            // Differentiate word importance for kinetic emphasis mapping
            val importanceFactor = when {
                connectors.contains(cleanWord) -> 0.72f // small secondary grammatical tokens
                words[i].any { it.isUpperCase() } -> 1.38f // capitalized words emphasis
                cleanWord.length > 7 -> 1.25f // complex/long words emphasis
                else -> 1.0f
            }

            val importanceWeightOffset = when {
                connectors.contains(cleanWord) -> -160
                words[i].any { it.isUpperCase() } -> 220
                cleanWord.length > 7 -> 120
                else -> 0
            }

            val baseW = (lerp(400f, 700f, randWeight) + importanceWeightOffset * profile.weightContrast)
            val varianceW = profile.chaosLevel * profile.weightContrast * 200f
            val finalWeight = (baseW + (rng.nextFloat() - 0.5f) * varianceW).toInt().coerceIn(300, 900)
            wordWeights.add(finalWeight)

            val baseScaleFactor = when (layoutMode) {
                "poster" -> 1.95f
                "display" -> 1.45f
                "code" -> 0.82f
                else -> 1.08f
            }
            
            // Highly responsive and expressive size variance
            val scaleFactorFromImportance = lerp(1.0f, importanceFactor, profile.sizeScale * 0.9f)
            val baseSize = userFontSize * profile.sizeScale * baseScaleFactor * scaleFactorFromImportance
            
            val maxVarianceFactor = profile.chaosLevel * 0.48f
            val varianceS = maxVarianceFactor * baseSize
            val finalSize = (baseSize + (rng.nextFloat() - 0.5f) * varianceS).coerceIn(11f, 50f)
            wordSizes.add(finalSize)

            val lowerOpacityBound = lerp(0.85f, 0.7f, profile.opacityDepth * profile.chaosLevel)
            val finalOpacity = lerp(lowerOpacityBound, 1.0f, rng.nextFloat())
            wordOpacities.add(finalOpacity)

            val trackingVal = if (layoutMode == "poster" || layoutMode == "display") {
                lerp(-1.0f, 2.0f, rng.nextFloat()) * profile.chaosLevel
            } else {
                0.0f
            }
            wordTrackings.add(trackingVal)
        }

        // 5. Chunk words into Phrases (3-5 words each)
        val phrases = chunkIntoPhrases(words)
        val phraseCount = phrases.size

        // 6. Timing and Start Translate configs per phrase
        val tempoMs = (160 * profile.tempoScale).toLong()
        val wordTimingCurve = when {
            profile.chaosLevel < 0.2f -> "linear"
            profile.chaosLevel < 0.5f -> if (rng.nextFloat() < 0.5f) "ease-out" else "linear"
            else -> listOf("linear", "ease-out", "burst", "wave").random()
        }

        val phraseTranslateYStart = ArrayList<Float>(phraseCount)
        val phraseDelays = ArrayList<Long>(phraseCount)

        val defaultTranslateY = when (animationChoreography) {
            AnimationChoreography.CASCADE_UP -> 20f
            AnimationChoreography.CASCADE_DOWN -> -20f
            AnimationChoreography.GRAVITY_DROP -> -40f
            AnimationChoreography.DRIFT_SETTLE -> 10f
            AnimationChoreography.WAVE_ROLL -> 15f
            else -> 0f
        }

        for (p in 0 until phraseCount) {
            val offset = when (animationChoreography) {
                AnimationChoreography.WAVE_ROLL -> sin((p.toFloat() / phraseCount.toFloat()) * PI.toFloat()) * 15f
                else -> defaultTranslateY
            }
            phraseTranslateYStart.add(offset)

            // Ensure strictly sequential left-to-right reading order
            val delay = (p * tempoMs).toLong()
            phraseDelays.add(delay)
        }

        return SentenceComposition(
            layoutMode = layoutMode,
            alignmentMode = alignmentMode,
            animationChoreography = animationChoreography,
            tempoMs = tempoMs,
            wordTimingCurve = wordTimingCurve,
            wordWeights = wordWeights,
            wordSizes = wordSizes,
            wordOpacities = wordOpacities,
            wordTrackings = wordTrackings,
            phraseTranslateYStart = phraseTranslateYStart,
            phraseDelays = phraseDelays,
            phrases = phrases
        )
    }

    private fun chunkIntoPhrases(words: List<String>): List<String> {
        val phrases = ArrayList<String>()
        var currentPhrase = ArrayList<String>()
        for (word in words) {
            currentPhrase.add(word)
            // Group up to 4 words, or cut earlier if it ends with major punctuation
            if (currentPhrase.size >= 4 || (currentPhrase.size >= 3 && word.matches(Regex(".*[,.!?]$")))) {
                phrases.add(currentPhrase.joinToString(" "))
                currentPhrase = ArrayList()
            }
        }
        if (currentPhrase.isNotEmpty()) {
            phrases.add(currentPhrase.joinToString(" "))
        }
        return phrases
    }
}
