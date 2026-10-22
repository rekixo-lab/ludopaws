package com.rekixo.ludopaws.rendering.meshes

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Context-local buffers/uniform bindings for any immutable procedural mesh. */
internal class CharacterMesh(
    geometry: MeshGeometry,
    private val uMvpLocation: Int,
    private val uColorLocation: Int,
    private val uLightDirectionLocation: Int,
    private val uNormalMatrixLocation: Int,
) {
    private val vertexBuffer = ByteBuffer.allocateDirect(geometry.vertices.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(geometry.vertices); position(0) }
    private val indexBuffer = ByteBuffer.allocateDirect(geometry.indices.size * Short.SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(geometry.indices); position(0) }
    private val indexCount = geometry.indices.size

        fun draw(
            normalMatrix: FloatArray,
            mvp: FloatArray,
            color: FloatArray,
        ) {
            GLES30.glUniformMatrix3fv(
                uNormalMatrixLocation,
                1,
                false,
                normalMatrix,
                0,
            )
            GLES30.glUniformMatrix4fv(
                uMvpLocation,
                1,
                false,
                mvp,
                0,
            )
            GLES30.glUniform4fv(
                uColorLocation,
                1,
                color,
                0,
            )
            GLES30.glUniform3f(
                uLightDirectionLocation,
                0.40f,
                -1.0f,
                -0.65f,
            )

            vertexBuffer.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(
                0,
                3,
                GLES30.GL_FLOAT,
                false,
                6 * Float.SIZE_BYTES,
                vertexBuffer,
            )
            vertexBuffer.position(3)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(
                1,
                3,
                GLES30.GL_FLOAT,
                false,
                6 * Float.SIZE_BYTES,
                vertexBuffer,
            )
            indexBuffer.position(0)
            GLES30.glDrawElements(
                GLES30.GL_TRIANGLES,
                indexCount,
                GLES30.GL_UNSIGNED_SHORT,
                indexBuffer,
            )
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
        }
}
