package com.rekixo.ludopaws.feature.characters.domain.catalog

import com.rekixo.ludopaws.feature.characters.domain.model.AnimalCharacter
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalPack
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationSet
import com.rekixo.ludopaws.feature.characters.domain.model.CharacterSelection
import com.rekixo.ludopaws.feature.characters.domain.model.VoiceSet

/** Catalog assembled from registered character and pack definitions. */
object LudoPawsCharacterCatalog {
    const val STARTER_PACK_ID = "starter_paws"
    const val DEFAULT_CHARACTER_ID = "dog"

    /**
     * Compatibility only for installs/matches persisted before the 3D roster.
     * Old ids never appear in the production catalog or UI after normalization.
     */
    private val legacyCharacterAliases =
        mapOf(
            "squirrel" to "dog",
            "hedgehog" to "goat",
            "sheep" to "cat",
            "lion" to "donkey",
        )

    private val voiceSetList = CharacterDefinitions.all.map { it.voiceSet }
    private val animationSetList = CharacterDefinitions.all.map { it.animationSet }
    private val characterList = CharacterDefinitions.all.map { it.character }

    private val packList = CharacterPackDefinitions.all.map { pack ->
        AnimalPack(
            id = pack.id, displayName = pack.displayName, starter = pack.starter,
            characterIds = CharacterDefinitions.all.filter { it.packId == pack.id }.map { it.character.id },
        )
    }

    private val charactersById = characterList.associateBy(AnimalCharacter::id)
    private val packsById = packList.associateBy(AnimalPack::id)
    private val voiceSetsById = voiceSetList.associateBy(VoiceSet::id)
    private val animationSetsById = animationSetList.associateBy(AnimationSet::id)

    val defaultSelection: CharacterSelection =
        CharacterSelection(
            packId = STARTER_PACK_ID,
            characterId = DEFAULT_CHARACTER_ID,
        )

    init {
        require(CharacterDefinitions.all.all { definition -> definition.packId in packsById }) {
            "Character definition references an unregistered pack"
        }
        validateCatalog()
    }

    val characters: List<AnimalCharacter>
        get() = characterList

    val packs: List<AnimalPack>
        get() = packList

    val voiceSets: List<VoiceSet>
        get() = voiceSetList

    val animationSets: List<AnimationSet>
        get() = animationSetList

    fun canonicalCharacterId(id: String?): String? {
        val safe = id?.takeIf(String::isNotBlank) ?: return null
        return legacyCharacterAliases[safe] ?: safe
    }

    fun character(id: String): AnimalCharacter? =
        canonicalCharacterId(id)
            ?.let(charactersById::get)

    fun pack(id: String): AnimalPack? = packsById[id]

    fun voiceSet(id: String): VoiceSet? = voiceSetsById[id]

    fun animationSet(id: String): AnimationSet? = animationSetsById[id]

    fun charactersForPack(packId: String): List<AnimalCharacter> =
        packsById[packId]
            ?.characterIds
            ?.mapNotNull(charactersById::get)
            .orEmpty()

    fun isValidSelection(selection: CharacterSelection): Boolean {
        val pack = packsById[selection.packId] ?: return false
        val characterId = canonicalCharacterId(selection.characterId) ?: return false
        return characterId in pack.characterIds &&
            charactersById.containsKey(characterId)
    }

    fun resolveSelection(
        packId: String?,
        characterId: String?,
    ): CharacterSelection {
        if (packId.isNullOrBlank() && characterId.isNullOrBlank()) {
            return defaultSelection
        }

        val pack = packId?.let(packsById::get) ?: return defaultSelection
        val canonicalId = canonicalCharacterId(characterId)

        if (canonicalId.isNullOrBlank()) {
            val firstCharacter = pack.characterIds.firstOrNull() ?: return defaultSelection
            return CharacterSelection(
                packId = pack.id,
                characterId = firstCharacter,
            )
        }

        val requested =
            CharacterSelection(
                packId = pack.id,
                characterId = canonicalId,
            )
        return if (isValidSelection(requested)) requested else defaultSelection
    }

    private fun validateCatalog() {
        require(charactersById.size == characterList.size) {
            "Duplicate Ludo Paws character id"
        }
        require(packsById.size == packList.size) {
            "Duplicate Ludo Paws pack id"
        }
        require(voiceSetsById.size == voiceSetList.size) {
            "Duplicate Ludo Paws voice-set id"
        }
        require(animationSetsById.size == animationSetList.size) {
            "Duplicate Ludo Paws animation-set id"
        }
        require(packList.count(AnimalPack::starter) == 1) {
            "Exactly one starter character pack is required"
        }
        require(packsById[STARTER_PACK_ID]?.starter == true) {
            "Starter Paws must remain the starter pack"
        }
        require(charactersById.containsKey(DEFAULT_CHARACTER_ID)) {
            "Default Ludo Paws character is missing"
        }

        for (pack in packList) {
            require(pack.characterIds.all(charactersById::containsKey)) {
                "Pack ${pack.id} references an unknown character"
            }
        }

        for (character in characterList) {
            val voiceSet = voiceSetsById[character.voiceSetId]
            require(voiceSet != null) {
                "Character ${character.id} references an unknown voice set"
            }
            require(voiceSet.species == character.species) {
                "Voice set ${voiceSet.id} belongs to the wrong species"
            }
            require(animationSetsById.containsKey(character.animationSetId)) {
                "Character ${character.id} references an unknown animation set"
            }
        }

        require(isValidSelection(defaultSelection)) {
            "Default Ludo Paws selection must be valid"
        }
    }
}
