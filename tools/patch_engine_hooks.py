"""Inserts Unicorn's prompt-saving hooks into the AOSP LatinIME engine. Idempotent."""
from pathlib import Path

LATIN = Path(r"V:\nidi\unicorn\app\src\main\java\com\android\inputmethod\latin")


def patch(rel, old, new):
    p = LATIN / rel
    s = p.read_text(encoding="utf-8")
    if new in s:
        return
    assert s.count(old) == 1, (rel, old[:60], s.count(old))
    p.write_text(s.replace(old, new), encoding="utf-8", newline="\n")


patch("LatinIME.java",
      "        super.onStartInputView(editorInfo, restarting);\n\n        mDictionaryFacilitator.onStartInput();",
      "        super.onStartInputView(editorInfo, restarting);\n        // Unicorn: prompt saving for allow-listed AI apps\n"
      "        ai.unicorn.Unicorn.onStartInputView(this, editorInfo);\n\n        mDictionaryFacilitator.onStartInput();")
patch("LatinIME.java",
      "    void onFinishInputInternal() {\n        super.onFinishInput();\n",
      "    void onFinishInputInternal() {\n        super.onFinishInput();\n        ai.unicorn.Unicorn.onFinishInput(this);\n")
patch("LatinIME.java",
      "        super.onFinishInputView(finishingInput);\n        cleanupInternalStateForFinishInput();",
      "        super.onFinishInputView(finishingInput);\n        ai.unicorn.Unicorn.onFinishInput(this);\n"
      "        cleanupInternalStateForFinishInput();")
patch("LatinIME.java",
      "        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,\n                composingSpanStart, composingSpanEnd);\n",
      "        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,\n                composingSpanStart, composingSpanEnd);\n"
      "        ai.unicorn.Unicorn.onSelectionUpdate(this);\n")
patch("inputlogic/InputLogic.java",
      "    private void performEditorAction(final int actionId) {\n        mConnection.performEditorAction(actionId);",
      "    private void performEditorAction(final int actionId) {\n        ai.unicorn.Unicorn.onEditorAction(mLatinIME);\n"
      "        mConnection.performEditorAction(actionId);")
patch("inputlogic/InputLogic.java",
      "            final int currentKeyboardScriptId) {\n        mSpaceState = SpaceState.NONE;\n        mDeleteCount++;",
      "            final int currentKeyboardScriptId) {\n        ai.unicorn.Unicorn.onLocalDelete(mLatinIME);\n"
      "        mSpaceState = SpaceState.NONE;\n        mDeleteCount++;")
print("hooks in place")
