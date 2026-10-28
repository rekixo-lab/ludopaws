package com.rekixo.ludopaws.feature.characters.domain.catalog

import com.rekixo.ludopaws.feature.characters.content.dog.DogDefinition
import com.rekixo.ludopaws.feature.characters.content.goat.GoatDefinition
import com.rekixo.ludopaws.feature.characters.content.lumi.LumiGoatDefinition
import com.rekixo.ludopaws.feature.characters.content.duck.DuckDefinition
import com.rekixo.ludopaws.feature.characters.content.cat.CatDefinition
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyDefinition
import com.rekixo.ludopaws.feature.characters.content.cock.CockDefinition
import com.rekixo.ludopaws.feature.characters.content.pig.PigDefinition
import com.rekixo.ludopaws.feature.characters.content.elephant.ElephantDefinition
import com.rekixo.ludopaws.feature.characters.content.horse.HorseDefinition
import com.rekixo.ludopaws.feature.characters.content.rabbit.RabbitDefinition

/** Add new shipped character definitions here; UI/catalog entries derive from this list. */
object CharacterDefinitions {
    val all = GeneratedCharacterDefinitions.all
    init {
        require(all.map { it.character.id }.distinct().size == all.size) { "Duplicate character definition" }
    }
}
