package com.hanenashi.tomez;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.widget.EditText;

/** An EditText with a cursor that keeps blinking while Edit mode is active. */
public class TerminalEditText extends EditText {
    private static final long BLINK_MS = 530;
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean terminalCursorEnabled;
    private boolean cursorOn = true;
    private int cursorStyle = 40;
    private final Runnable blink = new Runnable() {
        @Override public void run() {
            if (!terminalCursorEnabled || !isAttachedToWindow() || !hasWindowFocus()) return;
            cursorOn = !cursorOn;
            invalidate();
            postDelayed(this, BLINK_MS);
        }
    };

    public TerminalEditText(Context context) {
        super(context);
        setCursorVisible(false);
        cursorPaint.setColor(Color.BLACK);
    }

    public void setTerminalCursorEnabled(boolean enabled) {
        terminalCursorEnabled = enabled;
        restartBlink();
    }

    public void setTerminalCursorStyle(int style) {
        cursorStyle = style;
        invalidate();
    }

    public void setTerminalCursorColor(int color) {
        cursorPaint.setColor(color);
        invalidate();
    }

    private void restartBlink() {
        removeCallbacks(blink);
        cursorOn = true;
        invalidate();
        if (terminalCursorEnabled && isAttachedToWindow() && hasWindowFocus())
            postDelayed(blink, BLINK_MS);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restartBlink();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(blink);
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        restartBlink();
    }

    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        cursorOn = true;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!terminalCursorEnabled || !cursorOn || !hasWindowFocus()
                || getSelectionStart() < 0 || getSelectionStart() != getSelectionEnd()) return;
        Layout layout = getLayout();
        if (layout == null) return;
        int line = layout.getLineForOffset(getSelectionStart());
        float x = getCompoundPaddingLeft() + layout.getPrimaryHorizontal(getSelectionStart())
                - getScrollX();
        float top = getCompoundPaddingTop() + layout.getLineTop(line) - getScrollY();
        float bottom = getCompoundPaddingTop() + layout.getLineBottom(line) - getScrollY();
        float cell = Math.max(dp(9), getPaint().measureText("M"));
        int originalAlpha = cursorPaint.getAlpha();
        switch (cursorStyle) {
            case 41: // Thick bar
                canvas.drawRect(x, top, x + dp(5), bottom, cursorPaint);
                break;
            case 42: // Block, translucent so the character remains legible
                cursorPaint.setAlpha(125);
                canvas.drawRect(x, top, x + cell, bottom, cursorPaint);
                break;
            case 43: // Underline
                canvas.drawRect(x, bottom - dp(3), x + cell, bottom, cursorPaint);
                break;
            default: // Thin bar
                canvas.drawRect(x, top, x + dp(2), bottom, cursorPaint);
                break;
        }
        cursorPaint.setAlpha(originalAlpha);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
