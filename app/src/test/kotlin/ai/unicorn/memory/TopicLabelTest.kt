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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TopicLabelTest {
    private fun label(vararg texts: String) = MemoryInsightsEngine.topicLabel(texts.toList())

    @Test
    fun smallTalkIsNotATopic() {
        assertNull(label("See you at 5! I can't wait!! 🎉", "see you at 5"))
        assertNull(label("hey are you free tmrw", "hey you free tonight?"))
        assertNull(label("thanks so much!", "okay thank you"))
    }

    @Test
    fun sharedWordsNameTheTopicInWritingOrder() {
        assertEquals(
            "waterproof trekking",
            label("best waterproof trekking shoes under 100", "are trekking shoes waterproof enough for rain", "waterproof trekking boots for kids"),
        )
        assertEquals("tacos", label("best tacos near me", "where to get tacos for a birthday dinner"))
    }

    @Test
    fun sharedWordsWinOverOneOffs() {
        assertEquals(
            "car insurance",
            label("cheapest car insurance for teens", "is car insurance cheaper with a dashcam", "car insurance quote for a used Honda"),
        )
    }

    @Test
    fun paraphrasesAreNamedAfterTheNewest() {
        assertEquals("cheap flights", label("cheap flights to NYC in December", "budget airfare new york"))
    }
}
