package android.util

object Log {
    @JvmStatic fun e(tag: String, msg: String, t: Throwable? = null) = 0
    @JvmStatic fun w(tag: String, msg: String, t: Throwable? = null) = 0
    @JvmStatic fun d(tag: String, msg: String) = 0
}

open class LruCache<K, V>(val max: Int) {
    private val map = LinkedHashMap<K, V>()
    fun get(k: K): V? = map[k]
    fun put(k: K, v: V): V? = map.put(k, v)
    protected open fun sizeOf(k: K, v: V): Int = 1
}

class DisplayMetrics { @JvmField var density: Float = 2f }
