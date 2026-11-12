import com.rekixo.ludopaws.feature.characters.rendering.CharacterRenderer
import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionProfile
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry

/** Species wired into the harness. Add one entry per ported species. */
internal class Species(val id: String, val renderer: CharacterRenderer, val reactions: CharacterReactionProfile, val meshes: Map<String, MeshGeometry>) {
    companion object {
        val all = listOf(
            Species("dog", com.rekixo.ludopaws.feature.characters.content.dog.DogRenderer,
                com.rekixo.ludopaws.feature.characters.content.dog.DogReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.dog.DogGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "muzzle" to muzzle, "jaw" to jaw,
                        "ear" to ear, "limb" to limb, "paw" to paw, "tail" to tail)
                }),
            Species("goat", com.rekixo.ludopaws.feature.characters.content.goat.GoatRenderer,
                com.rekixo.ludopaws.feature.characters.content.goat.GoatReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.goat.GoatGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "muzzle" to muzzle, "jaw" to jaw, "ear" to ear,
                        "horn" to horn, "shin" to shin, "thigh" to thigh, "hoof" to hoof, "beard" to beard, "tuft" to tuft, "tail" to tail)
                }),
            Species("duck", com.rekixo.ludopaws.feature.characters.content.duck.DuckRenderer,
                com.rekixo.ludopaws.feature.characters.content.duck.DuckReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.duck.DuckGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "bill" to bill, "wing" to wing, "feather" to feather,
                        "tuft" to tuft, "tail" to tail, "leg" to leg, "foot" to foot)
                }),
            Species("cat", com.rekixo.ludopaws.feature.characters.content.cat.CatRenderer,
                com.rekixo.ludopaws.feature.characters.content.cat.CatReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.cat.CatGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "muzzle" to muzzle, "jaw" to jaw, "ear" to ear,
                        "nose" to nose, "limb" to limb, "paw" to paw, "tail" to tail, "tailTip" to tailTip)
                }),
            Species("donkey", com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyRenderer,
                com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "muzzle" to muzzle, "jaw" to jaw, "ear" to ear,
                        "leg" to leg, "hoof" to hoof, "tail" to tail, "tailTuft" to tailTuft, "mane" to mane, "forelock" to forelock)
                }),
            Species("goat_lumi", com.rekixo.ludopaws.feature.characters.content.lumi.LumiGoatRenderer,
                com.rekixo.ludopaws.feature.characters.content.lumi.LumiGoatReactionMotion,
                com.rekixo.ludopaws.feature.characters.content.lumi.LumiGoatGeometry.batches.entries
                    .flatMap { (rig, list) -> list.map { "${rig.name}/${it.tint.name}" to it.mesh } }.toMap()),
            Species("cock", com.rekixo.ludopaws.feature.characters.content.cock.CockRenderer,
                com.rekixo.ludopaws.feature.characters.content.cock.CockReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.cock.CockGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "wing" to wing, "feather" to feather, "beak" to beak)
                }),
            Species("pig", com.rekixo.ludopaws.feature.characters.content.pig.PigRenderer,
                com.rekixo.ludopaws.feature.characters.content.pig.PigReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.pig.PigGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "ear" to ear, "snout" to snout, "leg" to leg,
                        "hoof" to hoof, "curledTail" to curledTail)
                }),
            Species("elephant", com.rekixo.ludopaws.feature.characters.content.elephant.ElephantRenderer,
                com.rekixo.ludopaws.feature.characters.content.elephant.ElephantReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.elephant.ElephantGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "ear" to ear, "leg" to leg, "foot" to foot,
                        "trunk" to trunk, "tusk" to tusk)
                }),
            Species("horse", com.rekixo.ludopaws.feature.characters.content.horse.HorseRenderer,
                com.rekixo.ludopaws.feature.characters.content.horse.HorseReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.horse.HorseGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "muzzle" to muzzle, "jaw" to jaw, "ear" to ear,
                        "mane" to mane, "limb" to limb, "hoof" to hoof, "tail" to tail, "forelock" to forelock)
                }),
            Species("rabbit", com.rekixo.ludopaws.feature.characters.content.rabbit.RabbitRenderer,
                com.rekixo.ludopaws.feature.characters.content.rabbit.RabbitReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.rabbit.RabbitGeometry) {
                    mapOf("body" to body, "neck" to neck, "head" to head, "ear" to ear, "arm" to arm, "hindPaw" to hindPaw, "forePaw" to forePaw)
                }),
            Species("panda", com.rekixo.ludopaws.feature.characters.content.panda.PandaRenderer,
                com.rekixo.ludopaws.feature.characters.content.panda.PandaReactionMotion, emptyMap()),
            Species("monkey", com.rekixo.ludopaws.feature.characters.content.monkey.MonkeyRenderer,
                com.rekixo.ludopaws.feature.characters.content.monkey.MonkeyReactionMotion, emptyMap()),
            Species("giraffe", com.rekixo.ludopaws.feature.characters.content.giraffe.GiraffeRenderer,
                com.rekixo.ludopaws.feature.characters.content.giraffe.GiraffeReactionMotion,
                with(com.rekixo.ludopaws.feature.characters.content.giraffe.GiraffeGeometry) {
                    mapOf("body_gold" to body.gold, "body_patches" to body.patches, "neck_gold" to neck.gold,
                        "neck_patches" to neck.patches, "mane" to mane, "head" to head, "ear" to ear, "hoof" to hoof)
                }),
        )
    }
}
