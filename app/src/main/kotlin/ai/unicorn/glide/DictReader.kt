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

/**
 * Reads the words and frequencies of a read-only engine dictionary (format version 2, as in res/raw/main_en.dict), so
 * glide typing decodes against exactly the words typing suggests. The layout is documented in
 * com.android.inputmethod.latin.makedict.FormatSpec. Shortcuts and bigrams are skipped; the English dictionary has none.
 */
object DictReader {
    private const val MAGIC = 0x9BC13AFE.toInt()
    private const val ADDRESS_TYPE_MASK = 0xC0
    private const val HAS_MULTIPLE_CHARS = 0x20
    private const val IS_TERMINAL = 0x10
    private const val HAS_SHORTCUTS = 0x08
    private const val HAS_BIGRAMS = 0x04
    private const val IS_NOT_A_WORD = 0x02
    private const val IS_POSSIBLY_OFFENSIVE = 0x01
    private const val CHARS_TERMINATOR = 0x1F
    private const val ATTRIBUTE_HAS_NEXT = 0x80
    private const val BIGRAM_ADDRESS_TYPE_MASK = 0x30

    /** Receives each word; [chars] is only valid during the call. */
    fun interface WordSink {
        fun word(chars: CharSequence, frequency: Int, offensive: Boolean)
    }

    fun read(bytes: ByteArray, sink: WordSink) {
        val buffer = Buffer(bytes)
        require(buffer.int(0) == MAGIC) { "Not a dictionary" }
        val version = buffer.u16(4)
        require(version in 2..202) { "Unsupported dictionary version $version" }
        val headerSize = buffer.int(8)
        require(!String(bytes, 12, headerSize - 12, Charsets.ISO_8859_1).contains("codePointTable")) {
            "Dictionaries with a code point table are not supported"
        }
        readArray(buffer, headerSize, StringBuilder(), sink)
    }

    private fun readArray(b: Buffer, start: Int, prefix: StringBuilder, sink: WordSink) {
        var pos = start
        val first = b.u8(pos++)
        val count = if (first and 0x80 != 0) ((first and 0x7F) shl 8) or b.u8(pos++) else first
        repeat(count) {
            val flags = b.u8(pos++)
            val length = prefix.length
            if (flags and HAS_MULTIPLE_CHARS != 0) {
                while (true) {
                    val c = b.u8(pos)
                    if (c == CHARS_TERMINATOR) { pos++; break }
                    pos = appendChar(b, pos, prefix)
                }
            } else {
                pos = appendChar(b, pos, prefix)
            }
            var frequency = -1
            if (flags and IS_TERMINAL != 0) frequency = b.u8(pos++)
            val addressSize = when (flags and ADDRESS_TYPE_MASK) {
                0x40 -> 1
                0x80 -> 2
                0xC0 -> 3
                else -> 0
            }
            val childrenAddress = when (addressSize) {
                1 -> pos + b.u8(pos)
                2 -> pos + b.u16(pos)
                3 -> pos + ((b.u8(pos) shl 16) or b.u16(pos + 1))
                else -> -1
            }
            pos += addressSize
            if (flags and HAS_SHORTCUTS != 0) pos += b.u16(pos)
            if (flags and HAS_BIGRAMS != 0) {
                while (true) {
                    val attribute = b.u8(pos++)
                    pos += when (attribute and BIGRAM_ADDRESS_TYPE_MASK) {
                        0x10 -> 1
                        0x20 -> 2
                        0x30 -> 3
                        else -> 0
                    }
                    if (attribute and ATTRIBUTE_HAS_NEXT == 0) break
                }
            }
            if (frequency >= 0 && flags and IS_NOT_A_WORD == 0) {
                sink.word(prefix, frequency, flags and IS_POSSIBLY_OFFENSIVE != 0)
            }
            if (childrenAddress >= 0) readArray(b, childrenAddress, prefix, sink)
            prefix.setLength(length)
        }
    }

    private fun appendChar(b: Buffer, pos: Int, out: StringBuilder): Int {
        val c = b.u8(pos)
        if (c >= 0x20) {
            out.append(c.toChar())
            return pos + 1
        }
        out.appendCodePoint(((c and 0x1F) shl 16) or b.u16(pos + 1))
        return pos + 3
    }

    private class Buffer(private val bytes: ByteArray) {
        fun u8(at: Int) = bytes[at].toInt() and 0xFF
        fun u16(at: Int) = (u8(at) shl 8) or u8(at + 1)
        fun int(at: Int) = (u16(at) shl 16) or u16(at + 2)
    }
}
