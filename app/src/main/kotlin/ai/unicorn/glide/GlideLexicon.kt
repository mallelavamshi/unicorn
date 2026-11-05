/*
 * Copyright (C) 2026 Unicorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.unicorn.glide

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * The words glide typing can produce, packed into a few arrays (the English dictionary has 165,000 words, and String
 * objects for all of them would cost the keys' process megabytes). For each word it keeps the letter keys its path
 * visits, with repeats dropped ("hello" visits h, e, l, o), and indexes the words by their first and last key, which
 * is how a swipe's candidates are found.
 */
class GlideLexicon private constructor(
    val size: Int,
    private val text: CharArray,
    private val textStart: IntArray,
    private val path: ByteArray,
    private val pathStart: IntArray,
    /** 0 to 255, the engine's frequency scale (about 13 steps per doubling of use). */
    private val frequency: ByteArray,
    private val offensive: BooleanArray,
    /** Two or more capitals ("USA", "CPAs"): abbreviations, rarely what a swipe means. */
    private val capitals: BooleanArray,
    /** The first word with the same lower-case spelling ("us" for "US"), for context that ignores case. */
    private val group: IntArray,
    private val bucketStart: IntArray,
    private val bucketWords: IntArray,
    /** Word ids sorted by lower-case spelling, for lookups by text. */
    private val byText: IntArray,
) {
    class Entry(val word: String, val frequency: Int, val offensive: Boolean = false)

    fun word(id: Int): String = String(text, textStart[id], textStart[id + 1] - textStart[id])
    fun frequency(id: Int): Int = frequency[id].toInt() and 0xFF
    fun isOffensive(id: Int): Boolean = offensive[id]
    fun isAbbreviation(id: Int): Boolean = capitals[id]
    fun group(id: Int): Int = group[id]
    fun pathStart(id: Int): Int = pathStart[id]
    fun pathEnd(id: Int): Int = pathStart[id + 1]
    fun pathKey(index: Int): Int = path[index].toInt()
    fun pathLength(id: Int): Int = pathStart[id + 1] - pathStart[id]

    /**
     * Words whose path starts on one key and ends on another (0 = a ... 25 = z), most frequent first, are
     * bucketWord(bucketFrom(first, last)) until bucketWord(bucketTo(first, last)).
     */
    fun bucketFrom(firstKey: Int, lastKey: Int): Int = bucketStart[firstKey * KEYS + lastKey]
    fun bucketTo(firstKey: Int, lastKey: Int): Int = bucketStart[firstKey * KEYS + lastKey + 1]
    fun bucketWord(index: Int): Int = bucketWords[index]

    /** All ids spelled like [word] ignoring case, most frequent first; empty if unknown. */
    fun find(word: String): IntArray {
        val key = word.lowercase(Locale.ROOT)
        var lo = 0
        var hi = size - 1
        var hit = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compareLower(byText[mid], key)
            if (c < 0) lo = mid + 1 else if (c > 0) hi = mid - 1 else { hit = mid; hi = mid - 1 }
        }
        if (hit < 0) return IntArray(0)
        var end = hit
        while (end + 1 < size && compareLower(byText[end + 1], key) == 0) end++
        // The text index lists equal spellings most frequent first
        return IntArray(end - hit + 1) { byText[hit + it] }
    }

    private fun compareLower(id: Int, key: String): Int {
        val start = textStart[id]
        val length = textStart[id + 1] - start
        val n = minOf(length, key.length)
        for (i in 0 until n) {
            val a = lower(text[start + i])
            val b = key[i]
            if (a != b) return a - b
        }
        return length - key.length
    }

    /**
     * Saves the packed arrays, so the next start of the keys reads them instead of rebuilding them from the
     * dictionary (milliseconds instead of seconds on a busy phone). [read] loads them back.
     */
    fun write(file: File) {
        val n = size
        val bytes = 4 * 4 + text.size * 2 + path.size + n * 2 +
            4 * (textStart.size + pathStart.size + group.size + bucketStart.size + bucketWords.size + byText.size)
        val buffer = ByteBuffer.allocate(bytes)
        buffer.putInt(CACHE_MAGIC).putInt(n).putInt(text.size).putInt(path.size)
        buffer.asCharBuffer().put(text)
        buffer.position(buffer.position() + text.size * 2)
        for (array in arrayOf(textStart, pathStart, group, bucketStart, bucketWords, byText)) {
            buffer.asIntBuffer().put(array)
            buffer.position(buffer.position() + array.size * 4)
        }
        buffer.put(path).put(frequency)
        for (i in 0 until n) buffer.put(((if (offensive[i]) 1 else 0) or (if (capitals[i]) 2 else 0)).toByte())
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        FileOutputStream(temporary).use { it.write(buffer.array()) }
        if (!temporary.renameTo(file) && !(file.delete() && temporary.renameTo(file))) temporary.delete()
    }

    companion object {
        const val KEYS = 26
        private const val CACHE_MAGIC = 0x55474C31 // "UGL1"

        /** Arrays saved by [write]; throws if the file isn't one. */
        fun read(file: File): GlideLexicon {
            val buffer = FileInputStream(file).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
            require(buffer.int == CACHE_MAGIC) { "Not a glide cache" }
            val n = buffer.int
            val text = CharArray(buffer.int)
            val path = ByteArray(buffer.int)
            buffer.asCharBuffer().get(text)
            buffer.position(buffer.position() + text.size * 2)
            fun ints(count: Int) = IntArray(count).also {
                buffer.asIntBuffer().get(it)
                buffer.position(buffer.position() + count * 4)
            }
            val textStart = ints(n + 1)
            val pathStart = ints(n + 1)
            val group = ints(n)
            val bucketStart = ints(KEYS * KEYS + 1)
            val bucketWords = ints(n)
            val byText = ints(n)
            buffer.get(path)
            val frequency = ByteArray(n).also { buffer.get(it) }
            val flags = ByteArray(n).also { buffer.get(it) }
            return GlideLexicon(
                n, text, textStart, path, pathStart, frequency, BooleanArray(n) { flags[it].toInt() and 1 != 0 },
                BooleanArray(n) { flags[it].toInt() and 2 != 0 }, group, bucketStart, bucketWords, byText,
            )
        }

        /** The letter key a character is typed with, or -1 for one glide typing can't produce. */
        fun keyOf(c: Char): Int {
            val l = fold(lower(c))
            return if (l in 'a'..'z') l - 'a' else -1
        }

        /** Characters a path skips: they aren't on the letter keys, and people don't swipe through them. */
        fun isSkipped(c: Char) = c == '\'' || c == '’' || c == '-'

        private fun lower(c: Char) = if (c in 'A'..'Z') c + 32 else if (c < 'À') c else c.lowercaseChar()

        private const val ACCENTED = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿ"
        private const val PLAIN = "aaaaaaceeeeiiiinooooouuuuyy"
        private fun fold(c: Char): Char {
            if (c < 'à') return c
            val i = ACCENTED.indexOf(c)
            return if (i >= 0) PLAIN[i] else c
        }

        fun build(entries: List<Entry>): GlideLexicon {
            val builder = Builder(entries.size)
            for (entry in entries) builder.add(entry.word, entry.frequency, entry.offensive)
            return builder.build()
        }

        /**
         * Collects words straight into the packed arrays, keeping those made only of letter keys (plus apostrophes and
         * hyphens) with a path of at least two keys; a single key ("a", "I") is a tap, not a swipe. No object per word:
         * this runs when the keys start, often on a phone that is busy starting everything else.
         */
        class Builder(capacity: Int) : DictReader.WordSink {
            private var size = 0
            private var text = CharArray(maxOf(16, capacity * 9))
            private var textStart = IntArray(capacity + 2)
            private var path = ByteArray(maxOf(16, capacity * 8))
            private var pathStart = IntArray(capacity + 2)
            private var frequency = ByteArray(capacity + 1)
            private var offensive = BooleanArray(capacity + 1)
            private var capitals = BooleanArray(capacity + 1)
            private val scratch = ByteArray(64)

            override fun word(chars: CharSequence, frequency: Int, offensive: Boolean) = add(chars, frequency, offensive)

            fun add(word: CharSequence, frequency: Int, offensive: Boolean) {
                if (word.isEmpty() || word.length > 48) return
                var n = 0
                var upper = 0
                for (i in word.indices) {
                    val c = word[i]
                    if (isSkipped(c)) continue
                    val key = keyOf(c)
                    if (key < 0) return
                    if (c.isUpperCase()) upper++
                    if (n == 0 || scratch[n - 1].toInt() != key) scratch[n++] = key.toByte()
                }
                if (n < 2) return
                if (size + 1 >= textStart.size) {
                    val grown = textStart.size * 2
                    textStart = textStart.copyOf(grown)
                    pathStart = pathStart.copyOf(grown)
                    this.frequency = this.frequency.copyOf(grown)
                    this.offensive = this.offensive.copyOf(grown)
                    capitals = capitals.copyOf(grown)
                }
                val t = textStart[size]
                if (t + word.length > text.size) text = text.copyOf(maxOf(text.size * 2, t + word.length))
                for (i in word.indices) text[t + i] = word[i]
                val p = pathStart[size]
                if (p + n > path.size) path = path.copyOf(maxOf(path.size * 2, p + n))
                scratch.copyInto(path, p, 0, n)
                this.frequency[size] = frequency.coerceIn(0, 255).toByte()
                this.offensive[size] = offensive
                capitals[size] = upper >= 2
                size++
                textStart[size] = t + word.length
                pathStart[size] = p + n
            }

            fun build(): GlideLexicon {
                val n = size
                // Buckets by first and last key, most frequent first: a counting sort, frequencies being 0 to 255
                val order = IntArray(n)
                val perFrequency = IntArray(257)
                for (i in 0 until n) perFrequency[255 - (frequency[i].toInt() and 0xFF) + 1]++
                for (f in 0 until 256) perFrequency[f + 1] += perFrequency[f]
                for (i in 0 until n) order[perFrequency[255 - (frequency[i].toInt() and 0xFF)]++] = i
                val bucketStart = IntArray(KEYS * KEYS + 1)
                for (i in 0 until n) bucketStart[bucketOf(path, pathStart, i) + 1]++
                for (b in 0 until KEYS * KEYS) bucketStart[b + 1] += bucketStart[b]
                val fill = bucketStart.copyOf()
                val bucketWords = IntArray(n)
                for (i in order) bucketWords[fill[bucketOf(path, pathStart, i)]++] = i
                // Text index and case groups
                val byText = IntArray(n) { it }
                sortByText(byText, text, textStart, frequency)
                val group = IntArray(n)
                var i = 0
                while (i < n) {
                    var j = i
                    while (j + 1 < n && compareLower(text, textStart, byText[j + 1], byText[i]) == 0) j++
                    for (k in i..j) group[byText[k]] = byText[i]
                    i = j + 1
                }
                return GlideLexicon(
                    n, text.copyOf(textStart[n]), textStart.copyOf(n + 1), path.copyOf(pathStart[n]), pathStart.copyOf(n + 1),
                    frequency.copyOf(n), offensive.copyOf(n), capitals.copyOf(n), group, bucketStart, bucketWords, byText,
                )
            }
        }

        /** Two words compared by lower-case spelling. */
        private fun compareLower(text: CharArray, textStart: IntArray, a: Int, b: Int): Int {
            val sa = textStart[a]
            val la = textStart[a + 1] - sa
            val sb = textStart[b]
            val lb = textStart[b + 1] - sb
            for (i in 0 until minOf(la, lb)) {
                val ca = lower(text[sa + i])
                val cb = lower(text[sb + i])
                if (ca != cb) return ca - cb
            }
            return la - lb
        }

        /**
         * Sorts word ids by lower-case spelling, then most frequent first. A bottom-up merge sort on primitives that
         * skips merging runs already in order: the dictionary lists its words almost sorted, so this is nearly linear.
         */
        private fun sortByText(ids: IntArray, text: CharArray, textStart: IntArray, frequency: ByteArray) {
            fun before(a: Int, b: Int): Boolean {
                val c = compareLower(text, textStart, a, b)
                return c < 0 || c == 0 && (frequency[a].toInt() and 0xFF) >= (frequency[b].toInt() and 0xFF)
            }
            val n = ids.size
            var from = ids
            var to = IntArray(n)
            var width = 1
            while (width < n) {
                var start = 0
                while (start < n) {
                    val mid = minOf(start + width, n)
                    val end = minOf(start + 2 * width, n)
                    if (mid >= end || before(from[mid - 1], from[mid])) {
                        from.copyInto(to, start, start, end)
                    } else {
                        var i = start
                        var j = mid
                        var k = start
                        while (i < mid && j < end) to[k++] = if (before(from[i], from[j])) from[i++] else from[j++]
                        while (i < mid) to[k++] = from[i++]
                        while (j < end) to[k++] = from[j++]
                    }
                    start = end
                }
                val swap = from
                from = to
                to = swap
                width *= 2
            }
            if (from !== ids) from.copyInto(ids)
        }

        private fun bucketOf(path: ByteArray, pathStart: IntArray, id: Int) =
            path[pathStart[id]] * KEYS + path[pathStart[id + 1] - 1]

        /** The engine dictionary's words, from the bytes of a version 2 .dict file. */
        fun fromDictionary(bytes: ByteArray): GlideLexicon {
            val builder = Builder(170_000)
            DictReader.read(bytes, builder)
            return builder.build()
        }
    }
}
