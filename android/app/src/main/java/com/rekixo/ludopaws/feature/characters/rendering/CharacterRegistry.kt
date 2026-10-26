package com.rekixo.ludopaws.feature.characters.rendering

import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.CharacterDefinition
import com.rekixo.ludopaws.feature.characters.content.dog.*
import com.rekixo.ludopaws.feature.characters.content.goat.*
import com.rekixo.ludopaws.feature.characters.content.lumi.*
import com.rekixo.ludopaws.feature.characters.content.duck.*
import com.rekixo.ludopaws.feature.characters.content.cat.*
import com.rekixo.ludopaws.feature.characters.content.donkey.*
import com.rekixo.ludopaws.feature.characters.content.cock.*

internal data class RegisteredCharacter(val definition: CharacterDefinition, val renderer: CharacterRenderer, val avatar: CharacterAvatarRenderer) {
    val id: String get() = definition.character.id
}

/** Explicit, deterministic registration. No reflection, Activity or extra GL context per animal. */
internal object CharacterRegistry {
    val all = listOf(
        RegisteredCharacter(DogDefinition.content, DogRenderer, DogAvatarRenderer),
        RegisteredCharacter(GoatDefinition.content, GoatRenderer, GoatAvatarRenderer),
        RegisteredCharacter(DuckDefinition.content, DuckRenderer, DuckAvatarRenderer),
        RegisteredCharacter(CatDefinition.content, CatRenderer, CatAvatarRenderer),
        RegisteredCharacter(DonkeyDefinition.content, DonkeyRenderer, DonkeyAvatarRenderer),
        RegisteredCharacter(LumiGoatDefinition.content, LumiGoatRenderer, LumiGoatAvatarRenderer),
        RegisteredCharacter(CockDefinition.content, CockRenderer, CockAvatarRenderer),
    )
    private val byId = all.associateBy { it.id }
    init {
        require(byId.size == all.size) { "Duplicate character renderer" }
        require(byId.keys == CharacterDefinitions.all.map { it.character.id }.toSet()) {
            "Every character definition must have exactly one renderer"
        }
    }
    fun find(id: String?): RegisteredCharacter? = LudoPawsCharacterCatalog.canonicalCharacterId(id)?.let(byId::get)
    fun resolve(id: String?): RegisteredCharacter = find(id) ?: byId.getValue(LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID)
}
