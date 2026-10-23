package com.rekixo.ludopaws.rendering.meshes

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Context-local buffers/uniform bindings for any immutable procedural mesh. */
internal class CharacterMesh(
    geometry: MeshGeometry,
    private val uMvpLocation: Int,
    private val uColorLocation: Int,
    private val uNormalMatrixLocation: Int,
) {
    private val vertexArray = IntArray(1)
    private val buffers = IntArray(2)
    private val indexCount = geometry.indices.size

    init {
        val local = geometry.localPositions
        val stride = if (local == null) 6 else 9
        val upload = if (local == null) geometry.vertices else FloatArray(geometry.vertices.size / 6 * stride).apply {
            repeat(geometry.vertices.size / 6) { vertex ->
                System.arraycopy(geometry.vertices, vertex * 6, this, vertex * stride, 6)
                System.arraycopy(local, vertex * 3, this, vertex * stride + 6, 3)
            }
        }
        val vertices = ByteBuffer.allocateDirect(upload.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(upload); position(0) }
        val indices = ByteBuffer.allocateDirect(geometry.indices.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(geometry.indices); position(0) }
        GLES30.glGenVertexArrays(1, vertexArray, 0)
        GLES30.glGenBuffers(2, buffers, 0)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.capacity() * Float.SIZE_BYTES,
            vertices, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.capacity() * Short.SIZE_BYTES,
            indices, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride * Float.SIZE_BYTES, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride * Float.SIZE_BYTES,
            if (local == null) 0 else 6 * Float.SIZE_BYTES)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(normalMatrix: FloatArray, mvp: FloatArray, color: FloatArray) {
        GLES30.glUniformMatrix3fv(uNormalMatrixLocation, 1, false, normalMatrix, 0)
        GLES30.glUniformMatrix4fv(uMvpLocation, 1, false, mvp, 0)
        GLES30.glUniform4fv(uColorLocation, 1, color, 0)
        GLES30.glBindVertexArray(vertexArray[0])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    /** Must be called with the owning EGL context current. */
    fun release() {
        GLES30.glDeleteBuffers(2, buffers, 0)
        GLES30.glDeleteVertexArrays(1, vertexArray, 0)
    }
}
