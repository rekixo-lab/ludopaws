package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.data.audio.LudoPawsAudioAssetPlayer
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsProceduralVocal
import org.junit.Assert.assertEquals
import org.junit.Test

class LudoPawsPackagedAudioFallbackTest {
    @Test
    fun packagedFallbackNamesRemainStableAndDesignerReplaceable() {
        val expected =
            mapOf(
                LudoPawsProceduralVocal.DOG_YIP to "lp_vocal_dog_yip",
                LudoPawsProceduralVocal.DOG_WHINE to "lp_vocal_dog_whine",
                LudoPawsProceduralVocal.DOG_RUFF to "lp_vocal_dog_ruff",
                LudoPawsProceduralVocal.GOAT_BLEAT to "lp_vocal_goat_bleat",
                LudoPawsProceduralVocal.GOAT_SOFT_BLEAT to "lp_vocal_goat_soft_bleat",
                LudoPawsProceduralVocal.DUCK_QUACK to "lp_vocal_duck_quack",
                LudoPawsProceduralVocal.DUCK_SOFT_QUACK to "lp_vocal_duck_soft_quack",
                LudoPawsProceduralVocal.CAT_CHIRP to "lp_vocal_cat_chirp",
                LudoPawsProceduralVocal.CAT_MEW to "lp_vocal_cat_mew",
                LudoPawsProceduralVocal.CAT_PURR to "lp_vocal_cat_purr",
                LudoPawsProceduralVocal.CAT_HUFF to "lp_vocal_cat_huff",
                LudoPawsProceduralVocal.COCK_CROW to "lp_vocal_cock_crow",
                LudoPawsProceduralVocal.COCK_CLUCK to "lp_vocal_cock_cluck",
                LudoPawsProceduralVocal.PIG_OINK to "lp_vocal_pig_oink",
                LudoPawsProceduralVocal.PIG_SQUEAL to "lp_vocal_pig_squeal",
                LudoPawsProceduralVocal.ELEPHANT_TRUMPET to "lp_vocal_elephant_trumpet",
                LudoPawsProceduralVocal.ELEPHANT_RUMBLE to "lp_vocal_elephant_rumble",
                LudoPawsProceduralVocal.HORSE_NEIGH to "lp_vocal_horse_neigh",
                LudoPawsProceduralVocal.HORSE_SNORT to "lp_vocal_horse_snort",
                LudoPawsProceduralVocal.RABBIT_SNUFFLE to "lp_vocal_rabbit_snuffle",
            )

        assertEquals(LudoPawsProceduralVocal.entries.toSet(), expected.keys)
        expected.forEach { (preset, resourceName) ->
            assertEquals(
                resourceName,
                LudoPawsAudioAssetPlayer.packagedFallbackResourceName(preset),
            )
        }
    }
}
