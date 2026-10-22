package com.rekixo.ludopaws.rendering.shaders

import android.opengl.GLES30

internal object GlProgram {
    fun createProgram(
        vertexSource: String,
        fragmentSource: String,
    ): Int {
        val vertex =
            compileShader(
                GLES30.GL_VERTEX_SHADER,
                normalizeShaderSource(vertexSource),
            )
        val fragment =
            compileShader(
                GLES30.GL_FRAGMENT_SHADER,
                normalizeShaderSource(fragmentSource),
            )
        return GLES30.glCreateProgram().also { created ->
            GLES30.glAttachShader(created, vertex)
            GLES30.glAttachShader(created, fragment)
            GLES30.glLinkProgram(created)
            val status = IntArray(1)
            GLES30.glGetProgramiv(
                created,
                GLES30.GL_LINK_STATUS,
                status,
                0,
            )
            check(status[0] == GLES30.GL_TRUE) {
                "3D pawn program link failed: ${GLES30.glGetProgramInfoLog(created)}"
            }
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
        }
    }

    private fun normalizeShaderSource(source: String): String {
        // Some GLES drivers reject any empty/indented prefix before #version.
        // Find the directive explicitly instead of relying on multiline-string
        // indentation behavior, and make it the first character passed to GLES.
        val versionIndex = source.indexOf("#version")
        check(versionIndex >= 0) { "3D pawn shader is missing its #version directive" }
        return source.substring(versionIndex).trimStart()
    }

    private fun compileShader(
        type: Int,
        source: String,
    ): Int =
        GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(
                shader,
                GLES30.GL_COMPILE_STATUS,
                status,
                0,
            )
            check(status[0] == GLES30.GL_TRUE) {
                "3D pawn shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}"
            }
        }


}
