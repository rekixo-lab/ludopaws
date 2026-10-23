package com.rekixo.ludopaws.feature.characters.rendering

/** Small material palette; zero texture kind preserves the existing characters' matte shader. */
internal enum class CharacterSurface(val kind: Float, val gloss: Float) {
    MATTE(0f, .055f), SOFT_FUR(6f, .025f), COAT(1f, .045f), BODY_COAT(4f, .045f), HORN(2f, .11f), HOOF(3f, .13f), EYE(0f, .38f), NOSE(0f, .17f),
    HOUSE_WALL(7f, .025f), HOUSE_ROOF(8f, .055f),
    LUMI_FUR(15f, .035f), LUMI_GLOSS(16f, .15f), LUMI_EYE(17f, .45f),
    FINISH_METAL(12f, .65f), PARTY_BULB(13f, .85f), PARTY_GLOW(14f, 0f),
    GARDEN_GROUND(9f, .015f), GARDEN_STONE(10f, .045f), GARDEN_LEAF(11f, .08f),
}
