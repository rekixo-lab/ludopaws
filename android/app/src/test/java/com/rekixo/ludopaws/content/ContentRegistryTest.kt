package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.LudoPaws3DReactionMotion
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.boards.rendering.BoardRendererRegistry
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.dice.rendering.DiceRendererRegistry
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import org.junit.Assert.*
import org.junit.Test

class ContentRegistryTest {
    @Test
    fun everyCatalogCharacterHasOneRendererAndTheRightPack() {
        val ids = LudoPawsCharacterCatalog.characters.map { it.id }.toSet()
        assertEquals(ids, CharacterRegistry.all.map { it.id }.toSet())
        assertEquals(ids.size, CharacterRegistry.all.size)
        for (definition in CharacterDefinitions.all) {
            assertTrue(requireNotNull(LudoPawsCharacterCatalog.pack(definition.packId))
                .characterIds.contains(definition.character.id))
            assertSame(definition, CharacterRegistry.resolve(definition.character.id).definition)
        }
        assertEquals(
            CharacterDefinitions.all.filter { it.packId == "starter_paws" }.map { it.character.id },
            LudoPawsCharacterCatalog.pack("starter_paws")!!.characterIds,
        )
    }

    @Test
    fun legacyAndUnknownSelectionsKeepSafeCompatibleFallbacks() {
        assertEquals("goat", CharacterRegistry.resolve("hedgehog").id)
        assertEquals("cat", CharacterRegistry.resolve("sheep").id)
        assertEquals("dog", CharacterRegistry.resolve("squirrel").id)
        assertNull(CharacterRegistry.find("not_shipped"))
        assertEquals("dog", CharacterRegistry.resolve("not_shipped").id)
        assertEquals("board_classic", BoardRegistry.resolve("not_shipped").id)
        assertEquals("dice_classic", DiceRegistry.resolve("not_shipped").id)
        assertNull(BoardRegistry.find("not_shipped"))
        assertNull(DiceRegistry.find("not_shipped"))
    }

    @Test
    fun allCharacterMotionsRemainFiniteAndHopsFinishOnTheGround() {
        for (character in CharacterRegistry.all) {
            for (motion in CharacterMotion.entries) {
                for (step in 0..100) {
                    val pose = character.renderer.sample(motion, step / 100f)
                    val values = listOf(pose.liftY, pose.bodyYawDegrees, pose.headTiltDegrees,
                        pose.primaryAppendageDegrees, pose.secondaryAppendageDegrees,
                        pose.tertiaryAppendageDegrees, pose.legSwingDegrees, pose.legTuckDegrees, pose.legPaddleDegrees,
                        pose.bodyPitchDegrees, pose.bodyRollDegrees, pose.neckPitchDegrees, pose.bodyCrouch, pose.jawOpen)
                    assertTrue("${character.id} $motion step $step", values.all { it.isFinite() })
                }
            }
            assertEquals(0f, character.renderer.sample(CharacterMotion.HOP, 0f).liftY, .0001f)
            assertEquals(0f, character.renderer.sample(CharacterMotion.HOP, 1f).liftY, .0001f)
            for (cue in AnimationCue.entries) {
                for (step in 0..20) {
                    val pose = LudoPaws3DReactionMotion.sample(character.id, cue, step / 20f)
                    assertTrue(pose.scale.isFinite() && pose.scale > 0f)
                    assertTrue(listOf(pose.liftY, pose.bodyYawDegrees, pose.headTiltDegrees,
                        pose.primaryAppendageDegrees, pose.secondaryAppendageDegrees,
                        pose.tertiaryAppendageDegrees, pose.bodyPitchDegrees, pose.neckPitchDegrees, pose.jawOpen).all { it.isFinite() })
                }
                assertTrue(LudoPaws3DReactionMotion.durationMillis(character.id, cue) > 0L)
            }
        }
    }

    @Test
    fun boardAndDiceOffersHaveMatchingLocalDefinitionsAndRenderers() {
        assertEquals(StoreCosmeticCatalog.boards.map { it.id }.toSet(), BoardRegistry.all.map { it.id }.toSet())
        assertEquals(StoreCosmeticCatalog.dice.map { it.id }.toSet(), DiceRegistry.all.map { it.id }.toSet())
        for (board in BoardRegistry.all) {
            assertEquals(CosmeticCategory.BOARD, board.offer.category)
            assertTrue(BoardRendererRegistry.supports(board.rendererKey))
        }
        for (dice in DiceRegistry.all) {
            assertEquals(CosmeticCategory.DICE, dice.offer.category)
            assertTrue(DiceRendererRegistry.supports(dice.rendererKey))
        }
        assertEquals(setOf("board_classic", "board_autumn_forest", "board_blossom_garden", "board_winter_haven", "board_monsoon_mire", "board_cloud_lagoon", "board_astral"), BoardRegistry.all.filter { it.offer.contentAvailable }.map { it.id }.toSet())
        assertEquals(setOf("dice_classic", "dice_pearl_paw"), DiceRegistry.all.filter { it.offer.contentAvailable }.map { it.id }.toSet())
    }

    @Test
    fun dicePreviewsCannotMutateOtherViewsOrEquippedTeamBorders() {
        val definition = DiceRegistry.resolve("dice_diwali")
        val first = definition.palette()
        val original = first.faceColors.copyOf()
        first.faceColors[0] = 0
        assertArrayEquals(original, definition.palette().faceColors)
        assertEquals(0xFF123456.toInt(), definition.palette(0xFF123456.toInt()).borderColor)
        assertArrayEquals(original, definition.palette(0xFF123456.toInt()).faceColors)
    }
}
