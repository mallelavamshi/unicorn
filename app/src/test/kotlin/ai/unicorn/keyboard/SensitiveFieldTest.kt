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

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveFieldTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private val chat = text or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    private fun sensitive(inputType: Int, packageName: String = "com.whatsapp", imeOptions: Int = 0, hint: String? = null) =
        SensitiveField.isSensitive(packageName, inputType, imeOptions, hint)

    @Test
    fun ordinaryChatAndSearchFieldsAreNotSensitive() {
        assertFalse(sensitive(chat, hint = "Message"))
        assertFalse(sensitive(text, "com.google.android.gm", hint = "Compose email"))
        assertFalse(sensitive(text, hint = "Search spinach recipes"), "pin inside a word")
    }

    @Test
    fun secretsNumbersAndAddressesAreSensitive() {
        assertTrue(sensitive(text or InputType.TYPE_TEXT_VARIATION_PASSWORD), "password")
        assertTrue(sensitive(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD), "visible password")
        assertTrue(sensitive(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD), "web password")
        assertTrue(sensitive(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD), "PIN")
        assertTrue(sensitive(InputType.TYPE_CLASS_NUMBER), "OTP and card numbers")
        assertTrue(sensitive(InputType.TYPE_CLASS_PHONE), "phone")
        assertTrue(sensitive(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS), "email address")
        assertTrue(sensitive(text, hint = "Enter OTP"), "OTP hint")
        assertTrue(sensitive(text, hint = "Card number"), "card hint")
        assertTrue(sensitive(text, hint = "UPI PIN"), "UPI hint")
    }

    @Test
    fun incognitoFieldsAndPaymentAppsAreSensitive() {
        assertTrue(sensitive(chat, imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING), "incognito")
        assertTrue(sensitive(chat, "com.phonepe.app"), "PhonePe")
        assertTrue(sensitive(chat, "com.google.android.apps.nbu.paisa.user"), "Google Pay")
        assertTrue(sensitive(chat, "com.sbi.lotusintouch"), "YONO SBI")
        assertTrue(sensitive(chat, "com.chase.sig.android"), "Chase")
        assertTrue(sensitive(chat, "com.x8bit.bitwarden"), "Bitwarden")
    }

    @Test
    fun usSecretsInHintsAreSensitive() {
        assertTrue(sensitive(text, hint = "Routing number"), "routing number")
        assertTrue(sensitive(text, hint = "Last 4 of your Social Security number"), "SSN")
        assertTrue(sensitive(text, hint = "Driver license number"), "license")
        assertFalse(sensitive(text, hint = "Search the account settings"), "account alone is fine")
    }
}
