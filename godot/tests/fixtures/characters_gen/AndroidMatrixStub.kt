package android.opengl

/** Pure-JVM stand-in for android.opengl.Matrix (column-major, same semantics as AOSP). */
object Matrix {
    fun setIdentityM(m: FloatArray, offset: Int) {
        for (i in 0 until 16) m[offset + i] = 0f
        for (i in 0 until 16 step 5) m[offset + i] = 1f
    }
    fun translateM(m: FloatArray, offset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            val mi = offset + i
            m[12 + mi] += m[mi] * x + m[4 + mi] * y + m[8 + mi] * z
        }
    }
    fun scaleM(m: FloatArray, offset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            val mi = offset + i
            m[mi] *= x; m[4 + mi] *= y; m[8 + mi] *= z
        }
    }
    fun multiplyMM(result: FloatArray, resultOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val tmp = FloatArray(16)
        for (i in 0 until 4) for (j in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += lhs[lhsOffset + k * 4 + j] * rhs[rhsOffset + i * 4 + k]
            tmp[i * 4 + j] = s
        }
        System.arraycopy(tmp, 0, result, resultOffset, 16)
    }
    fun setRotateM(rm: FloatArray, rmOffset: Int, a: Float, x0: Float, y0: Float, z0: Float) {
        var x = x0; var y = y0; var z = z0
        rm[rmOffset + 3] = 0f; rm[rmOffset + 7] = 0f; rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f; rm[rmOffset + 13] = 0f; rm[rmOffset + 14] = 0f; rm[rmOffset + 15] = 1f
        val rad = a * (Math.PI.toFloat() / 180f)
        val s = kotlin.math.sin(rad); val c = kotlin.math.cos(rad)
        if (1f == x && 0f == y && 0f == z) {
            rm[rmOffset + 5] = c; rm[rmOffset + 10] = c; rm[rmOffset + 6] = s; rm[rmOffset + 9] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 2] = 0f; rm[rmOffset + 4] = 0f; rm[rmOffset + 8] = 0f; rm[rmOffset] = 1f
        } else if (0f == x && 1f == y && 0f == z) {
            rm[rmOffset] = c; rm[rmOffset + 10] = c; rm[rmOffset + 8] = s; rm[rmOffset + 2] = -s
            rm[rmOffset + 1] = 0f; rm[rmOffset + 4] = 0f; rm[rmOffset + 6] = 0f; rm[rmOffset + 9] = 0f; rm[rmOffset + 5] = 1f
        } else if (0f == x && 0f == y && 1f == z) {
            rm[rmOffset] = c; rm[rmOffset + 5] = c; rm[rmOffset + 1] = s; rm[rmOffset + 4] = -s
            rm[rmOffset + 2] = 0f; rm[rmOffset + 6] = 0f; rm[rmOffset + 8] = 0f; rm[rmOffset + 9] = 0f; rm[rmOffset + 10] = 1f
        } else {
            val len = kotlin.math.sqrt(x * x + y * y + z * z)
            if (1f != len) { val r = 1f / len; x *= r; y *= r; z *= r }
            val nc = 1f - c
            val xy = x * y; val yz = y * z; val zx = z * x
            val xs = x * s; val ys = y * s; val zs = z * s
            rm[rmOffset] = x * x * nc + c; rm[rmOffset + 4] = xy * nc - zs; rm[rmOffset + 8] = zx * nc + ys
            rm[rmOffset + 1] = xy * nc + zs; rm[rmOffset + 5] = y * y * nc + c; rm[rmOffset + 9] = yz * nc - xs
            rm[rmOffset + 2] = zx * nc - ys; rm[rmOffset + 6] = yz * nc + xs; rm[rmOffset + 10] = z * z * nc + c
        }
    }
    fun rotateM(m: FloatArray, mOffset: Int, a: Float, x: Float, y: Float, z: Float) {
        val r = FloatArray(16); setRotateM(r, 0, a, x, y, z)
        val t = FloatArray(16); multiplyMM(t, 0, m, mOffset, r, 0)
        System.arraycopy(t, 0, m, mOffset, 16)
    }
    /** General 4x4 inverse (cofactors), like AOSP Matrix.invertM. */
    fun invertM(mInv: FloatArray, mInvOffset: Int, m: FloatArray, mOffset: Int): Boolean {
        val a = DoubleArray(16) { m[mOffset + it].toDouble() }
        val inv = DoubleArray(16)
        inv[0] = a[5]*a[10]*a[15]-a[5]*a[11]*a[14]-a[9]*a[6]*a[15]+a[9]*a[7]*a[14]+a[13]*a[6]*a[11]-a[13]*a[7]*a[10]
        inv[4] = -a[4]*a[10]*a[15]+a[4]*a[11]*a[14]+a[8]*a[6]*a[15]-a[8]*a[7]*a[14]-a[12]*a[6]*a[11]+a[12]*a[7]*a[10]
        inv[8] = a[4]*a[9]*a[15]-a[4]*a[11]*a[13]-a[8]*a[5]*a[15]+a[8]*a[7]*a[13]+a[12]*a[5]*a[11]-a[12]*a[7]*a[9]
        inv[12] = -a[4]*a[9]*a[14]+a[4]*a[10]*a[13]+a[8]*a[5]*a[14]-a[8]*a[6]*a[13]-a[12]*a[5]*a[10]+a[12]*a[6]*a[9]
        inv[1] = -a[1]*a[10]*a[15]+a[1]*a[11]*a[14]+a[9]*a[2]*a[15]-a[9]*a[3]*a[14]-a[13]*a[2]*a[11]+a[13]*a[3]*a[10]
        inv[5] = a[0]*a[10]*a[15]-a[0]*a[11]*a[14]-a[8]*a[2]*a[15]+a[8]*a[3]*a[14]+a[12]*a[2]*a[11]-a[12]*a[3]*a[10]
        inv[9] = -a[0]*a[9]*a[15]+a[0]*a[11]*a[13]+a[8]*a[1]*a[15]-a[8]*a[3]*a[13]-a[12]*a[1]*a[11]+a[12]*a[3]*a[9]
        inv[13] = a[0]*a[9]*a[14]-a[0]*a[10]*a[13]-a[8]*a[1]*a[14]+a[8]*a[2]*a[13]+a[12]*a[1]*a[10]-a[12]*a[2]*a[9]
        inv[2] = a[1]*a[6]*a[15]-a[1]*a[7]*a[14]-a[5]*a[2]*a[15]+a[5]*a[3]*a[14]+a[13]*a[2]*a[7]-a[13]*a[3]*a[6]
        inv[6] = -a[0]*a[6]*a[15]+a[0]*a[7]*a[14]+a[4]*a[2]*a[15]-a[4]*a[3]*a[14]-a[12]*a[2]*a[7]+a[12]*a[3]*a[6]
        inv[10] = a[0]*a[5]*a[15]-a[0]*a[7]*a[13]-a[4]*a[1]*a[15]+a[4]*a[3]*a[13]+a[12]*a[1]*a[7]-a[12]*a[3]*a[5]
        inv[14] = -a[0]*a[5]*a[14]+a[0]*a[6]*a[13]+a[4]*a[1]*a[14]-a[4]*a[2]*a[13]-a[12]*a[1]*a[6]+a[12]*a[2]*a[5]
        inv[3] = -a[1]*a[6]*a[11]+a[1]*a[7]*a[10]+a[5]*a[2]*a[11]-a[5]*a[3]*a[10]-a[9]*a[2]*a[7]+a[9]*a[3]*a[6]
        inv[7] = a[0]*a[6]*a[11]-a[0]*a[7]*a[10]-a[4]*a[2]*a[11]+a[4]*a[3]*a[10]+a[8]*a[2]*a[7]-a[8]*a[3]*a[6]
        inv[11] = -a[0]*a[5]*a[11]+a[0]*a[7]*a[9]+a[4]*a[1]*a[11]-a[4]*a[3]*a[9]-a[8]*a[1]*a[7]+a[8]*a[3]*a[5]
        inv[15] = a[0]*a[5]*a[10]-a[0]*a[6]*a[9]-a[4]*a[1]*a[10]+a[4]*a[2]*a[9]+a[8]*a[1]*a[6]-a[8]*a[2]*a[5]
        val det = a[0]*inv[0]+a[1]*inv[4]+a[2]*inv[8]+a[3]*inv[12]
        if (det == 0.0) return false
        for (i in 0 until 16) mInv[mInvOffset + i] = (inv[i] / det).toFloat()
        return true
    }
}
