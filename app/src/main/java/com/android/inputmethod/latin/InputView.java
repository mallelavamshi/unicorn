/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.keyboard.MainKeyboardView;
import com.android.inputmethod.latin.suggestions.MoreSuggestionsView;
import com.android.inputmethod.latin.suggestions.SuggestionStripView;

public final class InputView extends FrameLayout {
    private final Rect mInputViewRect = new Rect();
    private MainKeyboardView mMainKeyboardView;
    private KeyboardTopPaddingForwarder mKeyboardTopPaddingForwarder;
    private MoreSuggestionsViewCanceler mMoreSuggestionsViewCanceler;
    private MotionEventForwarder<?, ?> mActiveForwarder;
    // Unicorn: a touch that started beside narrowed keys, going to the outer key (edge taps)
    private boolean mForwardingEdgeTap;
    // Unicorn: from Android 15 the navigation bar is drawn over the keyboard window. The keys are
    // kept above it and the strip behind its buttons is painted in the keyboard theme's color.
    private final Paint mNavigationBarPaint = new Paint();
    private final int[] mLocationInWindow = new int[2];
    private final int[] mBarLocationInWindow = new int[2];
    private int mImeNavigationBarId;

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
        mNavigationBarPaint.setColor(
                ai.unicorn.keyboard.KeyboardStyle.paletteFor(context).getBackgroundEnd());
        setWillNotDraw(false);
    }

    @Override
    public WindowInsets onApplyWindowInsets(final WindowInsets insets) {
        requestLayout();
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top,
            final int right, final int bottom) {
        final int overlap = getNavigationBarOverlap();
        if (overlap != getPaddingBottom()) {
            // Children are placed with the new padding in this pass; the extra layout it requests
            // finds the same overlap and stops there.
            setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), overlap);
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    /** How far the navigation bar reaches into this view, or 0 when the system keeps it below. */
    private int getNavigationBarOverlap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return 0;
        }
        final WindowInsets insets = getRootWindowInsets();
        if (insets == null) {
            return 0;
        }
        getLocationInWindow(mLocationInWindow);
        // Measured against the window's bottom, not past it: while the window is still being sized
        // (rotation, the landscape text box) this view can briefly overhang, and counting the
        // overhang made the padding grow on every pass until no keys were left.
        final int windowHeight = getRootView().getHeight();
        final int bottomInWindow = Math.min(mLocationInWindow[1] + getHeight(), windowHeight);
        final int navigationBar = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        final int belowView = windowHeight - bottomInWindow;
        return Math.max(Math.max(0, navigationBar - belowView), getImeNavigationBarOverlap(bottomInWindow));
    }

    /**
     * Unicorn: in landscape the navigation bar can move to the side while Android draws the keyboard
     * window's own back and switch buttons along the bottom. That bar is not reported as an inset, so
     * find it and keep the keys above it as well.
     */
    private int getImeNavigationBarOverlap(final int bottomInWindow) {
        if (mImeNavigationBarId == 0) {
            mImeNavigationBarId = android.content.res.Resources.getSystem().getIdentifier(
                    "input_method_navigation_bar_view", "id", "android");
            if (mImeNavigationBarId == 0) {
                mImeNavigationBarId = View.NO_ID;
            }
        }
        if (mImeNavigationBarId == View.NO_ID) {
            return 0;
        }
        final View bar = getRootView().findViewById(mImeNavigationBarId);
        if (bar == null || !bar.isShown() || bar.getHeight() <= 0) {
            return 0;
        }
        // Never more than the bar itself: its position can still be the one from before a rotation
        bar.getLocationInWindow(mBarLocationInWindow);
        return Math.min(bar.getHeight(), Math.max(0, bottomInWindow - mBarLocationInWindow[1]));
    }

    @Override
    protected void dispatchDraw(final Canvas canvas) {
        // Unicorn: the empty sides and the lifted space of a resized key area, in the theme's colour
        ai.unicorn.keyboard.KeyArea.drawBehind(this, canvas);
        super.dispatchDraw(canvas);
        final int strip = getPaddingBottom();
        if (strip > 0) {
            canvas.drawRect(0, getHeight() - strip, getWidth(), getHeight(), mNavigationBarPaint);
        }
    }

    @Override
    protected void onFinishInflate() {
        final SuggestionStripView suggestionStripView =
                (SuggestionStripView)findViewById(R.id.suggestion_strip_view);
        mMainKeyboardView = (MainKeyboardView)findViewById(R.id.keyboard_view);
        mKeyboardTopPaddingForwarder = new KeyboardTopPaddingForwarder(
                mMainKeyboardView, suggestionStripView);
        mMoreSuggestionsViewCanceler = new MoreSuggestionsViewCanceler(
                mMainKeyboardView, suggestionStripView);
        // Unicorn: side buttons for one-handed use and the resize handles
        ai.unicorn.keyboard.KeyArea.attach(this);
    }

    public void setKeyboardTopPadding(final int keyboardTopPadding) {
        mKeyboardTopPaddingForwarder.setKeyboardTopPadding(keyboardTopPadding);
    }

    @Override
    protected boolean dispatchHoverEvent(final MotionEvent event) {
        if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()
                && mMainKeyboardView.isShowingMoreKeysPanel()) {
            // With accessibility mode on, discard hover events while a more keys keyboard is shown.
            // The {@link MoreKeysKeyboard} receives hover events directly from the platform.
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        // Unicorn: the resize handles get every touch; a touch beside narrowed keys goes to the
        // outer key of that row
        mForwardingEdgeTap = false;
        if (ai.unicorn.keyboard.KeyArea.isEditing()) {
            mActiveForwarder = null;
            return false;
        }
        if (ai.unicorn.keyboard.KeyArea.interceptEdgeTap(this, me)) {
            mActiveForwarder = null;
            mForwardingEdgeTap = true;
            return true;
        }

        final Rect rect = mInputViewRect;
        getGlobalVisibleRect(rect);
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index) + rect.left;
        final int y = (int)me.getY(index) + rect.top;

        // The touch events that hit the top padding of keyboard should be forwarded to
        // {@link SuggestionStripView}.
        if (mKeyboardTopPaddingForwarder.onInterceptTouchEvent(x, y, me)) {
            mActiveForwarder = mKeyboardTopPaddingForwarder;
            return true;
        }

        // To cancel {@link MoreSuggestionsView}, we should intercept a touch event to
        // {@link MainKeyboardView} and dismiss the {@link MoreSuggestionsView}.
        if (mMoreSuggestionsViewCanceler.onInterceptTouchEvent(x, y, me)) {
            mActiveForwarder = mMoreSuggestionsViewCanceler;
            return true;
        }

        mActiveForwarder = null;
        return false;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (mForwardingEdgeTap) {
            return ai.unicorn.keyboard.KeyArea.forwardEdgeTap(this, me);
        }
        if (mActiveForwarder == null) {
            return super.onTouchEvent(me);
        }

        final Rect rect = mInputViewRect;
        getGlobalVisibleRect(rect);
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index) + rect.left;
        final int y = (int)me.getY(index) + rect.top;
        return mActiveForwarder.onTouchEvent(x, y, me);
    }

    /**
     * This class forwards series of {@link MotionEvent}s from <code>SenderView</code> to
     * <code>ReceiverView</code>.
     *
     * @param <SenderView> a {@link View} that may send a {@link MotionEvent} to <ReceiverView>.
     * @param <ReceiverView> a {@link View} that receives forwarded {@link MotionEvent} from
     *     <SenderView>.
     */
    private static abstract class
            MotionEventForwarder<SenderView extends View, ReceiverView extends View> {
        protected final SenderView mSenderView;
        protected final ReceiverView mReceiverView;

        protected final Rect mEventSendingRect = new Rect();
        protected final Rect mEventReceivingRect = new Rect();

        public MotionEventForwarder(final SenderView senderView, final ReceiverView receiverView) {
            mSenderView = senderView;
            mReceiverView = receiverView;
        }

        // Return true if a touch event of global coordinate x, y needs to be forwarded.
        protected abstract boolean needsToForward(final int x, final int y);

        // Translate global x-coordinate to <code>ReceiverView</code> local coordinate.
        protected int translateX(final int x) {
            return x - mEventReceivingRect.left;
        }

        // Translate global y-coordinate to <code>ReceiverView</code> local coordinate.
        protected int translateY(final int y) {
            return y - mEventReceivingRect.top;
        }

        /**
         * Callback when a {@link MotionEvent} is forwarded.
         * @param me the motion event to be forwarded.
         */
        protected void onForwardingEvent(final MotionEvent me) {}

        // Returns true if a {@link MotionEvent} is needed to be forwarded to
        // <code>ReceiverView</code>. Otherwise returns false.
        public boolean onInterceptTouchEvent(final int x, final int y, final MotionEvent me) {
            // Forwards a {link MotionEvent} only if both <code>SenderView</code> and
            // <code>ReceiverView</code> are visible.
            if (mSenderView.getVisibility() != View.VISIBLE ||
                    mReceiverView.getVisibility() != View.VISIBLE) {
                return false;
            }
            mSenderView.getGlobalVisibleRect(mEventSendingRect);
            if (!mEventSendingRect.contains(x, y)) {
                return false;
            }

            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                // If the down event happens in the forwarding area, successive
                // {@link MotionEvent}s should be forwarded to <code>ReceiverView</code>.
                if (needsToForward(x, y)) {
                    return true;
                }
            }

            return false;
        }

        // Returns true if a {@link MotionEvent} is forwarded to <code>ReceiverView</code>.
        // Otherwise returns false.
        public boolean onTouchEvent(final int x, final int y, final MotionEvent me) {
            mReceiverView.getGlobalVisibleRect(mEventReceivingRect);
            // Translate global coordinates to <code>ReceiverView</code> local coordinates.
            me.setLocation(translateX(x), translateY(y));
            mReceiverView.dispatchTouchEvent(me);
            onForwardingEvent(me);
            return true;
        }
    }

    /**
     * This class forwards {@link MotionEvent}s happened in the top padding of
     * {@link MainKeyboardView} to {@link SuggestionStripView}.
     */
    private static class KeyboardTopPaddingForwarder
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        private int mKeyboardTopPadding;

        public KeyboardTopPaddingForwarder(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        public void setKeyboardTopPadding(final int keyboardTopPadding) {
            mKeyboardTopPadding = keyboardTopPadding;
        }

        private boolean isInKeyboardTopPadding(final int y) {
            return y < mEventSendingRect.top + mKeyboardTopPadding;
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            // Forwarding an event only when {@link MainKeyboardView} is visible.
            // Because the visibility of {@link MainKeyboardView} is controlled by its parent
            // view in {@link KeyboardSwitcher#setMainKeyboardFrame()}, we should check the
            // visibility of the parent view.
            final View mainKeyboardFrame = (View)mSenderView.getParent();
            return mainKeyboardFrame.getVisibility() == View.VISIBLE && isInKeyboardTopPadding(y);
        }

        @Override
        protected int translateY(final int y) {
            final int translatedY = super.translateY(y);
            if (isInKeyboardTopPadding(y)) {
                // The forwarded event should have coordinates that are inside of the target.
                return Math.min(translatedY, mEventReceivingRect.height() - 1);
            }
            return translatedY;
        }
    }

    /**
     * This class forwards {@link MotionEvent}s happened in the {@link MainKeyboardView} to
     * {@link SuggestionStripView} when the {@link MoreSuggestionsView} is showing.
     * {@link SuggestionStripView} dismisses {@link MoreSuggestionsView} when it receives any event
     * outside of it.
     */
    private static class MoreSuggestionsViewCanceler
            extends MotionEventForwarder<MainKeyboardView, SuggestionStripView> {
        public MoreSuggestionsViewCanceler(final MainKeyboardView mainKeyboardView,
                final SuggestionStripView suggestionStripView) {
            super(mainKeyboardView, suggestionStripView);
        }

        @Override
        protected boolean needsToForward(final int x, final int y) {
            return mReceiverView.isShowingMoreSuggestionPanel() && mEventSendingRect.contains(x, y);
        }

        @Override
        protected void onForwardingEvent(final MotionEvent me) {
            if (me.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mReceiverView.dismissMoreSuggestionsPanel();
            }
        }
    }
}
