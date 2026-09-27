package com.openai.pixelcleaner;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public final class CheckerboardDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int cell;

    public CheckerboardDrawable(int cellPx) {
        this.cell = Math.max(8, cellPx);
    }

    @Override
    public void draw(Canvas canvas) {
        int w = getBounds().width();
        int h = getBounds().height();
        canvas.drawColor(Color.rgb(242, 242, 242));
        paint.setColor(Color.rgb(218, 218, 218));
        for (int y = 0; y < h; y += cell) {
            for (int x = 0; x < w; x += cell) {
                if (((x / cell) + (y / cell)) % 2 == 0) {
                    canvas.drawRect(x, y, Math.min(x + cell, w), Math.min(y + cell, h), paint);
                }
            }
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
