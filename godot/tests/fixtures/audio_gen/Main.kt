import com.rekixo.ludopaws.feature.characters.data.audio.LudoPawsProceduralAudio
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsProceduralVocal
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsReactionAudioProfile
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalSpecies
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.domain.model.VoiceCue
import com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReaction

// Golden PCM summaries of the UNCHANGED live procedural synth (private sfxSamples/vocalSamples,
// called by reflection) for every preset at three playback rates.
fun main() {
    val target = LudoPawsProceduralAudio
    val sfx = target.javaClass.getDeclaredMethod("sfxSamples", LudoPawsProceduralAudio.Sfx::class.java, Float::class.javaPrimitiveType)
        .apply { isAccessible = true }
    val vocal = target.javaClass.getDeclaredMethod("vocalSamples", LudoPawsProceduralVocal::class.java, Float::class.javaPrimitiveType)
        .apply { isAccessible = true }
    val rows = mutableListOf<String>()
    fun add(kind: String, name: String, rate: Float, s: ShortArray) {
        var sum = 0L
        var abs = 0L
        for (v in s) { sum += v; abs += kotlin.math.abs(v.toInt()) }
        val picks = (0 until 16).map { s[(it * (s.size - 1)) / 15].toInt() }
        rows += "[\"$kind\",\"$name\",$rate,${s.size},$sum,$abs,[${picks.joinToString(",")}]]"
    }
    for (rate in listOf(1f, .8f, 1.3f)) {
        for (p in LudoPawsProceduralAudio.Sfx.values()) add("sfx", p.name, rate, sfx.invoke(target, p, rate) as ShortArray)
        for (p in LudoPawsProceduralVocal.values()) add("vocal", p.name, rate, vocal.invoke(target, p, rate) as ShortArray)
    }
    // Voice plans: every species x cue x moment, plus shouldPlay / variant for sample keys.
    val species = listOf("DOG", "GOAT", "DUCK", "CAT", "DONKEY", "COCK", "PIG", "ELEPHANT", "HORSE", "RABBIT", "PANDA", "MONKEY", "GIRAFFE", "SHEEP", "CHICK")
    val moments = listOf<GameMomentType?>(null) + GameMomentType.values().toList()
    for (sp in species) for (cue in VoiceCue.values()) for (moment in moments) {
        if (moment != null && cue != VoiceCue.FRUSTRATED && moment != GameMomentType.CAPTURE_MADE && moment != GameMomentType.TOKEN_CAPTURED) continue
        val r = LudoPawsReaction("p", 0, 0, cue, AnimationCue.IDLE, 1, "m", 7, moment, "m:7:${cue.name}:${moment}")
        val plan = LudoPawsReactionAudioProfile.plan("dog", AnimalSpecies.valueOf(sp), r)
        if (plan == null) { rows += "[\"plan\",\"$sp\",\"${cue.name}\",\"$moment\",null]"; continue }
        val play = (0 until 20).map { LudoPawsReactionAudioProfile.shouldPlay(plan, "key-$sp-${cue.name}-$it") }
        val variant = (0 until 5).map { LudoPawsReactionAudioProfile.deterministicVariantIndex("key-$sp-$it", 2) }
        rows += "[\"plan\",\"$sp\",\"${cue.name}\",\"$moment\",[\"${plan.fallback}\",${plan.volume},${plan.playbackRate},${plan.probabilityPercent},${plan.delayMillis},[${plan.rawResourceNames.joinToString(",") { "\"$it\"" }}],[${play.joinToString(",")}],[${variant.joinToString(",")}]]]"
    }
    println("[\n" + rows.joinToString(",\n") + "\n]")
}
