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
import com.rekixo.ludopaws.feature.characters.content.pig.*
import com.rekixo.ludopaws.feature.characters.content.elephant.*
import com.rekixo.ludopaws.feature.characters.content.horse.*
import com.rekixo.ludopaws.feature.characters.content.rabbit.*

internal data class RegisteredCharacter(val definition: CharacterDefinition, val renderer: CharacterRenderer, val avatar: CharacterAvatarRenderer) {
    val id: String get() = definition.character.id
}

/** Explicit, deterministic registration. No reflection, Activity or extra GL context per animal. */
internal object CharacterRegistry {
    private val preparedIds = mutableSetOf<String>()
    private val preparationWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "LudoPawsModelPrewarm").apply { isDaemon = true }
    }

    fun prewarm(characterIds: List<String>) {
        for (id in characterIds.distinct()) {
            val entry = find(id) ?: continue
            if (!synchronized(preparedIds) { preparedIds.add(entry.id) }) continue
            preparationWorker.execute {
                val startedAt = android.os.SystemClock.elapsedRealtime()
                val preparation = object : CharacterDrawContext() {
                    override fun drawGeometry(parent: FloatArray, geometry: com.rekixo.ludopaws.rendering.meshes.MeshGeometry,
                        translateX: Float, translateY: Float, translateZ: Float,
                        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
                        rotateX: Float, rotateY: Float, rotateZ: Float) = Unit
                    override fun drawPart(parent: FloatArray, translateX: Float, translateY: Float, translateZ: Float,
                        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
                        rotateX: Float, rotateY: Float, rotateZ: Float) = Unit
                }
                val identity = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
                val result = runCatching {
                    entry.renderer.draw(preparation, identity, CharacterPose(jawOpen = .3f), com.rekixo.ludopaws.LudoPawsTeamColor.BLUE)
                }
                result.onFailure {
                    synchronized(preparedIds) { preparedIds.remove(entry.id) }
                    android.util.Log.w("LudoPawsLoad", "Model prewarm failed for ${entry.id}", it)
                }
                android.util.Log.d("LudoPawsLoad", "model=${entry.id} prepare_ms=${android.os.SystemClock.elapsedRealtime() - startedAt} success=${result.isSuccess}")
            }
        }
    }

    val all = GeneratedCharacterEntries.all
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
