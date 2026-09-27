package com.openai.pixelcleaner;

import android.graphics.Bitmap;

import com.openai.pixelcleaner.core.PixelCleanerCore;

public final class ImageProcessor {
    private ImageProcessor() {}

    public static Bitmap process(Bitmap input, int noise, int sharpen, int fringe,
                                 PixelCleanerCore.ProgressListener progress) {
        if (input == null || input.isRecycled()) {
            throw new IllegalArgumentException("Invalid input bitmap");
        }

        int w = input.getWidth();
        int h = input.getHeight();
        int count = Math.multiplyExact(w, h);
        int[] pixels = new int[count];
        input.getPixels(pixels, 0, w, 0, 0, w, h);

        // pixels is a private working buffer, so the memory-reduced owned variant is safe here.
        int[] cleaned = PixelCleanerCore.cleanOwned(
                pixels, w, h,
                new PixelCleanerCore.Params(noise, sharpen, fringe),
                progress
        );

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setHasAlpha(true);
        out.setPremultiplied(true);
        out.setPixels(cleaned, 0, w, 0, 0, w, h);
        return out;
    }
}
