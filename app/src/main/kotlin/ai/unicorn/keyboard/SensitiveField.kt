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

/**
 * Fields Unicorn stays out of entirely: no AI bar, no voice, no word learning, no prompt saving, nothing kept for
 * undo. Passwords, PINs, one-time codes, card and number fields, email addresses, fields the app marks private, and
 * every field in banking and payment apps. The keys themselves still work as plain keys.
 */
object SensitiveField {
    /** Banking, payment and wallet apps (India first, then the largest elsewhere). */
    private val PrivateApps = setOf(
        "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
        "in.amazon.mShop.android.shopping.pay", "com.mobikwik_new", "com.freecharge.android", "com.sbi.lotusintouch",
        "com.sbi.SBIFreedomPlus", "com.snapwork.hdfc", "com.hdfcbank.payzapp", "com.csam.icici.bank.imobile",
        "com.axis.mobile", "com.msf.kbank.mobile", "com.bankofbaroda.mconnect", "com.infrasofttech.indianbank",
        "com.unionbankofindia.vyom", "com.pnb.mbanking", "com.idbibank.abhay_card", "com.fss.idfcpay",
        "com.yesbank.yesmobile", "com.dreamplug.androidapp", "com.slice", "com.jupiter.money", "com.fi.money",
        "com.zerodha.kite3", "com.nextbillion.groww", "com.upstox.pro",
        "com.paypal.android.p2pmobile", "com.venmo", "com.squareup.cash", "com.chase.sig.android",
        "com.wf.wellsfargomobile", "com.infonow.bofa", "com.konylabs.capitalone", "com.citi.citimobile",
        "com.usaa.mobile.android.usaa", "com.revolut.revolut", "com.transferwise.android", "com.coinbase.android",
        "com.binance.dev", "com.google.android.apps.walletnfcrel", "com.samsung.android.spay",
        // US banks, brokers and payments
        "com.americanexpress.android.acctsvcs.us", "com.discoverfinancial.mobile", "com.usbank.mobilebanking",
        "com.pnc.ecommerce.mobile", "com.tdbank", "com.ally.MobileBanking", "com.sofi.mobile", "com.onedebit.chime",
        "com.navyfederal.android", "com.robinhood.android", "com.fidelity.android", "com.schwab.mobile", "com.vanguard",
        "com.etrade.mobilepro.activity", "com.zellepay.zelle", "com.creditkarma.mobile",
        // Password managers and authenticators
        "com.x8bit.bitwarden", "com.onepassword.android", "com.lastpass.lpandroid", "com.dashlane",
        "com.google.android.apps.authenticator2", "com.authy.authy", "com.azure.authenticator",
    )

    /** Words in a field's hint that mean it holds a secret, as whole words ("pin", not "spinach"). */
    private val SecretHint = Regex(
        "\\b(pass(word|code)?|pin|m-?pin|otp|one[- ]?time|verification code|security code|cvv|cvc|card number|" +
            "upi|ssn|social security|routing( number)?|account number|tax id|itin|license number|aadhaar|pan number)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun of(editorInfo: EditorInfo?): Boolean = editorInfo != null && isSensitive(
        editorInfo.packageName, editorInfo.inputType, editorInfo.imeOptions, editorInfo.hintText,
    )

    fun isSensitive(packageName: String?, inputType: Int, imeOptions: Int, hint: CharSequence?): Boolean {
        if (packageName in PrivateApps) return true
        if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        // Numbers, phone numbers and dates: codes, PINs, card numbers, amounts
        if (inputClass != InputType.TYPE_CLASS_TEXT) return true
        when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            -> return true
        }
        return hint != null && SecretHint.containsMatchIn(hint)
    }
}
