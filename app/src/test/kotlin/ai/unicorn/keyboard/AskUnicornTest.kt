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

package ai.unicorn.keyboard

import ai.unicorn.words.PersonalWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AskUnicornTest {
    @Test
    fun slashCommandsAndAliases() {
        assertEquals(ParsedPrompt(AskCommand.REPLY, "say yes"), ParsedPrompt.parse("/reply say yes"))
        assertEquals(ParsedPrompt(AskCommand.REPLY, ""), ParsedPrompt.parse("/read-thread"))
        assertEquals(ParsedPrompt(AskCommand.EMAIL, "ask for leave"), ParsedPrompt.parse("/Email ask for leave"))
        assertEquals(ParsedPrompt(AskCommand.FIX, ""), ParsedPrompt.parse("  /grammar "))
    }

    @Test
    fun plainTextAndUnknownCommandsAreFreeRequests() {
        assertEquals(ParsedPrompt(AskCommand.FREE, "write a birthday wish"), ParsedPrompt.parse("write a birthday wish"))
        assertEquals(ParsedPrompt(AskCommand.FREE, "/party tonight?"), ParsedPrompt.parse("/party tonight?"))
    }

    @Test
    fun onlyRealWordsAreLearned() {
        assertTrue(PersonalWords.isLearnable("Gachibowli"))
        assertTrue(PersonalWords.isLearnable("don't"))
        assertFalse(PersonalWords.isLearnable("482913"), "codes")
        assertFalse(PersonalWords.isLearnable("me@mail.com"), "emails")
        assertFalse(PersonalWords.isLearnable("On my way!"), "phrases")
        assertFalse(PersonalWords.isLearnable("a"), "single letters")
    }
}
