"""One-off adaptation of the AOSP LatinIME manifest and identifiers for the Unicorn app (applicationId ai.unicorn.app)."""
import re
from pathlib import Path

MAIN = Path(r"V:\nidi\unicorn\app\src\main")


def patch(rel, pairs, regex=False):
    p = MAIN / rel
    s = p.read_text(encoding="utf-8")
    for old, new in pairs:
        if regex:
            s, n = re.subn(old, new, s, flags=re.S)
        else:
            n = s.count(old)
            s = s.replace(old, new)
        assert n >= 1, (rel, old[:60])
    p.write_text(s, encoding="utf-8", newline="\n")


# Manifest: AGP 8 takes package/sdk from Gradle; drop permissions Unicorn does not use; add INTERNET for AI tools.
dropped = ["DOWNLOAD_WITHOUT_NOTIFICATION", "GET_ACCOUNTS", "READ_CONTACTS", "READ_PROFILE", "READ_SYNC_SETTINGS",
           "READ_SYNC_STATS", "USE_CREDENTIALS", "WRITE_EXTERNAL_STORAGE", "WRITE_SYNC_SETTINGS"]
patch("AndroidManifest.xml", [
    (r'\s*coreApp="true"\s*package="com.android.inputmethod.latin"\s*android:versionCode="30">', ">"),
    (r'\s*<uses-sdk[^>]*/>', ""),
] + [(rf'\s*<uses-permission android:name="android.permission.{p}"/>', "") for p in dropped] + [
    (r'(<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>)',
     r'\1\n    <uses-permission android:name="android.permission.INTERNET"/>'),
    (r'<permission android:name="com.android.inputmethod.latin.HIDE_SOFT_INPUT"',
     '<permission android:name="ai.unicorn.app.HIDE_SOFT_INPUT"'),
], regex=True)

# Identifiers that would collide with an installed AOSP keyboard (duplicate permission / provider authority).
patch(r"java\com\android\inputmethod\latin\LatinIME.java", [
    ('"com.android.inputmethod.latin.HIDE_SOFT_INPUT";\n\n    /**', '"ai.unicorn.app.HIDE_SOFT_INPUT";\n\n    /**'),
])
patch(r"java\com\android\inputmethod\latin\LatinIME.java", [
    ('PERMISSION_HIDE_SOFT_INPUT =\n            "com.android.inputmethod.latin.HIDE_SOFT_INPUT";',
     'PERMISSION_HIDE_SOFT_INPUT =\n            "ai.unicorn.app.HIDE_SOFT_INPUT";'),
])
patch(r"res\values\dictionary-pack.xml", [
    (">com.android.inputmethod.dictionarypack.aosp<", ">ai.unicorn.app.dictionarypack<"),
])
print("patched")
