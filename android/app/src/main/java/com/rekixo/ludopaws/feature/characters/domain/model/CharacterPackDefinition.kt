package com.rekixo.ludopaws.feature.characters.domain.model

/** Pack identity is explicit so adding a paid animal never grants it through Starter Paws. */
data class CharacterPackDefinition(val id: String, val displayName: String, val starter: Boolean = false)
