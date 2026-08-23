package skul9x.example.makesound.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/**
 * Minimal NPZ / NPY parser for NumPy float32 tensors.
 * Extracts embeddings and projection matrices from `vieneu_v3_heads.npz`.
 */
class NpzReader private constructor(
    private val arrays: Map<String, NpyArray>
) {
    data class NpyArray(
        val shape: IntArray,
        val data: FloatArray
    ) {
        val size: Int get() = data.size

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as NpyArray
            if (!shape.contentEquals(other.shape)) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = shape.contentHashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    val keys: Set<String> get() = arrays.keys

    fun has(key: String): Boolean = arrays.containsKey(key)

    fun getArray(key: String): NpyArray? = arrays[key]

    fun getFloatArray(key: String): FloatArray? = arrays[key]?.data

    fun getShape(key: String): IntArray? = arrays[key]?.shape

    fun getScalarFloat(key: String): Float? {
        val arr = arrays[key] ?: return null
        return if (arr.data.isNotEmpty()) arr.data[0] else null
    }

    fun get2D(key: String): Array<FloatArray>? {
        val arr = arrays[key] ?: return null
        val shape = arr.shape
        if (shape.size != 2) return null
        val rows = shape[0]
        val cols = shape[1]
        val result = Array(rows) { FloatArray(cols) }
        var offset = 0
        for (r in 0 until rows) {
            System.arraycopy(arr.data, offset, result[r], 0, cols)
            offset += cols
        }
        return result
    }

    fun get3D(key: String): Array<Array<FloatArray>>? {
        val arr = arrays[key] ?: return null
        val shape = arr.shape
        if (shape.size != 3) return null
        val d0 = shape[0]
        val d1 = shape[1]
        val d2 = shape[2]
        val result = Array(d0) { Array(d1) { FloatArray(d2) } }
        var offset = 0
        for (i in 0 until d0) {
            for (j in 0 until d1) {
                System.arraycopy(arr.data, offset, result[i][j], 0, d2)
                offset += d2
            }
        }
        return result
    }

    companion object {
        private val SHAPE_PATTERN = Pattern.compile("""'shape':\s*\((.*?)\)""")

        fun read(inputStream: InputStream): NpzReader {
            val map = HashMap<String, NpyArray>()
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val key = if (name.endsWith(".npy")) name.substring(0, name.length - 4) else name
                    val bytes = readAllBytes(zis, entry.size)
                    if (bytes.isNotEmpty()) {
                        val npy = parseNpy(bytes)
                        if (npy != null) {
                            map[key] = npy
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return NpzReader(map)
        }

        fun read(file: File): NpzReader {
            return FileInputStream(file).use { read(it) }
        }

        fun parseNpy(bytes: ByteArray): NpyArray? {
            if (bytes.size < 10) return null
            // Check magic \x93NUMPY
            if (bytes[0] != 0x93.toByte() ||
                bytes[1] != 'N'.code.toByte() ||
                bytes[2] != 'U'.code.toByte() ||
                bytes[3] != 'M'.code.toByte() ||
                bytes[4] != 'P'.code.toByte() ||
                bytes[5] != 'Y'.code.toByte()
            ) {
                return null
            }

            val major = bytes[6].toInt() and 0xFF
            val headerLen: Int
            val dataOffset: Int

            if (major == 1) {
                headerLen = ByteBuffer.wrap(bytes, 8, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                dataOffset = 10 + headerLen
            } else {
                headerLen = ByteBuffer.wrap(bytes, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
                dataOffset = 12 + headerLen
            }

            if (dataOffset > bytes.size) return null

            val headerStr = String(bytes, if (major == 1) 10 else 12, headerLen, Charsets.US_ASCII)
            val shape = parseShape(headerStr)

            val rawDataLen = bytes.size - dataOffset
            val floatCount = rawDataLen / 4
            val floatArray = FloatArray(floatCount)

            val byteBuffer = ByteBuffer.wrap(bytes, dataOffset, rawDataLen).order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asFloatBuffer().get(floatArray)

            return NpyArray(shape, floatArray)
        }

        private fun parseShape(header: String): IntArray {
            val matcher = SHAPE_PATTERN.matcher(header)
            if (!matcher.find()) return intArrayOf()
            val inside = matcher.group(1)?.trim() ?: return intArrayOf()
            if (inside.isEmpty()) return intArrayOf()

            val parts = inside.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            return parts.map { it.toInt() }.toIntArray()
        }

        fun readAllBytes(inputStream: InputStream, expectedSize: Long = -1L): ByteArray {
            if (expectedSize > 0 && expectedSize <= Int.MAX_VALUE) {
                val size = expectedSize.toInt()
                val bytes = ByteArray(size)
                var totalRead = 0
                while (totalRead < size) {
                    val read = inputStream.read(bytes, totalRead, size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                return if (totalRead == size) bytes else bytes.copyOf(totalRead)
            }
            val buffer = ByteArray(8192)
            val baos = ByteArrayOutputStream()
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }
            return baos.toByteArray()
        }
    }
}
