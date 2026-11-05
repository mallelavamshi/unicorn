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

package ai.unicorn.memory

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryImporterTest {
    private val chatGptExport = """
        [{
          "title": "SUV shopping",
          "create_time": 1700000000.5,
          "update_time": 1700000600.0,
          "conversation_id": "conv-1",
          "current_node": "a2",
          "mapping": {
            "root": {"message": null, "parent": null},
            "sys": {"message": {"author": {"role": "system"}, "content": {"content_type": "text", "parts": [""]}}, "parent": "root"},
            "u1": {"message": {"author": {"role": "user"}, "create_time": 1700000001.0,
                   "content": {"content_type": "text", "parts": ["best suv for a family of 5"]}}, "parent": "sys"},
            "a1-old": {"message": {"author": {"role": "assistant"}, "create_time": 1700000002.0,
                   "content": {"content_type": "text", "parts": ["regenerated away"]}}, "parent": "u1"},
            "a1": {"message": {"author": {"role": "assistant"}, "create_time": 1700000003.0,
                   "content": {"content_type": "text", "parts": ["Consider the Highlander."]}}, "parent": "u1"},
            "u2": {"message": {"author": {"role": "user"}, "create_time": 1700000004.0,
                   "content": {"content_type": "multimodal_text", "parts": [{"asset_pointer": "file-1"}, "is it reliable?"]}}, "parent": "a1"},
            "t1": {"message": {"author": {"role": "tool"}, "content": {"content_type": "text", "parts": ["tool output"]}}, "parent": "u2"},
            "a2": {"message": {"author": {"role": "assistant"}, "create_time": 1700000005.0,
                   "content": {"content_type": "text", "parts": ["Yes, very."]}}, "parent": "t1"}
          }
        }]
    """.trimIndent()

    private val claudeExport = """
        [{
          "uuid": "claude-1",
          "name": "Salary talk",
          "created_at": "2025-03-05T10:12:13.123456Z",
          "updated_at": "2025-03-05T10:20:00Z",
          "chat_messages": [
            {"sender": "human", "text": "", "created_at": "2025-03-05T10:12:14Z",
             "content": [{"type": "text", "text": "How do I negotiate my salary?"}]},
            {"sender": "assistant", "text": "Start with research.", "created_at": "2025-03-05T10:12:20Z", "content": []}
          ]
        }]
    """.trimIndent()

    private val geminiActivity = """
        [
          {"header": "Gemini Apps", "title": "Prompted plan a 10 day Japan trip", "time": "2025-04-01T10:00:00.000Z",
           "products": ["Gemini Apps"], "safeHtmlItem": [{"html": "<p>Day 1: Tokyo</p>"}]},
          {"header": "Gemini Apps", "title": "Prompted is the JR pass worth it?", "time": "2025-04-01T10:10:00.000Z",
           "products": ["Gemini Apps"]},
          {"header": "Gemini Apps", "title": "Used Gemini Apps", "time": "2025-04-01T10:11:00.000Z", "products": ["Gemini Apps"]},
          {"header": "Gemini Apps", "title": "Prompted best laptop for coding", "time": "2025-04-03T09:00:00.000Z",
           "products": ["Gemini Apps"]}
        ]
    """.trimIndent()

    @Test
    fun `chatgpt export follows the current branch and keeps only text turns`() {
        val (origin, conversation) = MemoryImporter.parse("conversations.json", chatGptExport.byteInputStream()).single()
        assertEquals(MemoryOrigin.CHATGPT_EXPORT, origin)
        assertEquals("conv-1", conversation.externalId)
        assertEquals(
            listOf(
                MemoryRole.USER to "best suv for a family of 5",
                MemoryRole.ASSISTANT to "Consider the Highlander.",
                MemoryRole.USER to "is it reliable?",
                MemoryRole.ASSISTANT to "Yes, very.",
            ),
            conversation.messages.map { it.role to it.text },
        )
        assertEquals(1700000001000L, conversation.messages.first().createdAt)
    }

    @Test
    fun `claude export inside a zip is detected and parsed`() {
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("users.json")); out.write("[{\"uuid\":\"u\"}]".toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("conversations.json")); out.write(claudeExport.toByteArray()); out.closeEntry()
            }
        }.toByteArray()
        val (origin, conversation) = MemoryImporter.parse("export.zip", zip.inputStream()).single()
        assertEquals(MemoryOrigin.CLAUDE_EXPORT, origin)
        assertEquals("Salary talk", conversation.title)
        assertEquals(
            listOf(MemoryRole.USER to "How do I negotiate my salary?", MemoryRole.ASSISTANT to "Start with research."),
            conversation.messages.map { it.role to it.text },
        )
    }

    @Test
    fun `gemini takeout prompts are grouped into sessions`() {
        val parsed = MemoryImporter.parse("Takeout/My Activity/Gemini Apps/MyActivity.json", geminiActivity.byteInputStream()).toList()
        assertEquals(2, parsed.size)
        val first = parsed[0].second
        assertEquals(
            listOf(
                MemoryRole.USER to "plan a 10 day Japan trip",
                MemoryRole.ASSISTANT to "Day 1: Tokyo",
                MemoryRole.USER to "is the JR pass worth it?",
            ),
            first.messages.map { it.role to it.text },
        )
        assertEquals("best laptop for coding", parsed[1].second.messages.single().text)
    }

    @Test
    fun `markdown thread splits questions and answers`() {
        val md = "# best crm for startups\nHubSpot is popular.\n\n# is it free?\nThere is a free tier.\n"
        val conversation = MemoryImporter.parseMarkdownThread(md, "thread.md", now = 0)!!
        assertEquals(
            listOf(MemoryRole.USER, MemoryRole.ASSISTANT, MemoryRole.USER, MemoryRole.ASSISTANT),
            conversation.messages.map { it.role },
        )
        assertEquals(CaptureSource.PERPLEXITY.id, conversation.sourceId)
    }

    @Test
    fun `shared text detects the source from links`() {
        val shared = MemoryImporter.fromSharedText("Look at this https://chatgpt.com/share/abc", null, senderSourceId = null, now = 0)!!
        assertEquals(CaptureSource.CHATGPT.id, shared.sourceId)
        val unknown = MemoryImporter.fromSharedText("just some text", null, senderSourceId = null, now = 0)!!
        assertEquals(OTHER_SOURCE_ID, unknown.sourceId)
    }
}
