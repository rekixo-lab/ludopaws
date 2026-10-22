package com.rekixo.ludopaws.feature.characters.domain.catalog

import com.rekixo.ludopaws.feature.characters.content.dog.DogDefinition
import com.rekixo.ludopaws.feature.characters.content.goat.GoatDefinition
import com.rekixo.ludopaws.feature.characters.content.duck.DuckDefinition
import com.rekixo.ludopaws.feature.characters.content.cat.CatDefinition
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyDefinition

/** Add new shipped character definitions here; UI/catalog entries derive from this list. */
object CharacterDefinitions {
    val all = listOf(DogDefinition.content, GoatDefinition.content, DuckDefinition.content, CatDefinition.content, DonkeyDefinition.content)
    init {
        require(all.map { it.character.id }.distinct().size == all.size) { "Duplicate character definition" }
    }
}
