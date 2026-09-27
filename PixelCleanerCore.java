package com.openai.pixelcleaner.core;

import java.util.Arrays;

/**
 * Pure-Java, size-preserving pixel cleaner.
 *
 * Design goals:
 * - Never rescale, crop, rotate, or regenerate the image.
 * - Preserve alpha exactly except for deliberately removed faint, isolated fringe pixels.
 * - Reduce low-amplitude AI/compression-like mottling only inside similar-color neighborhoods.
 * - Apply a subtle unsharp-mask pass after denoising.
 */
public final class PixelCleanerCore {
    private PixelCleanerCore() {}

    public static final class Params {
        public final int noiseReduction; // 0..100
        public final int sharpening;     // 0..100
        public final int fringeCleanup;  // 0..100

        public Params(int noiseReduction, int sharpening, int fringeCleanup) {
            this.noiseReduction = clamp100(noiseReduction);
            this.sharpening = clamp100(sharpening);
            this.fringeCleanup = clamp100(fringeCleanup);
        }
    }

    public interface ProgressListener {
        void onProgress(String stage, int percent);
    }

    public static int[] clean(int[] argb, int width, int height, Params params) {
        return clean(argb, width, height, params, null);
    }

    public static int[] clean(int[] argb, int width, int height, Params params,
                              ProgressListener listener) {
        validate(argb, width, height);
        return cleanOwned(Arrays.copyOf(argb, argb.length), width, height, params, listener);
    }

    /**
     * Memory-reduced variant for a caller-owned working array. The supplied array may be
     * overwritten and must not be reused by the caller after this method starts.
     */
    public static int[] cleanOwned(int[] ownedArgb, int width, int height, Params params,
                                   ProgressListener listener) {
        validate(ownedArgb, width, height);
        if (params == null) params = new Params(15, 15, 35);

        int[] a = ownedArgb;
        int[] b = new int[ownedArgb.length];

        progress(listener, "외곽 픽셀 정리", 5);
        cleanupFringe(a, b, width, height, params.fringeCleanup);
        int[] tmp = a; a = b; b = tmp;

        progress(listener, "노이즈 제거", 35);
        denoise(a, b, width, height, params.noiseReduction);
        tmp = a; a = b; b = tmp;

        progress(listener, "샤프닝", 75);
        sharpen(a, b, width, height, params.sharpening);

        progress(listener, "완료", 100);
        return b;
    }

    private static void validate(int[] argb, int width, int height) {
        if (argb == null) throw new IllegalArgumentException("pixels == null");
        long expected = (long) width * (long) height;
        if (width <= 0 || height <= 0 || expected != argb.length) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
    }

    /** Remove only very faint alpha speckles that are not attached to a real opaque edge. */
    static void cleanupFringe(int[] src, int[] dst, int w, int h, int strength) {
        if (strength <= 0) {
            System.arraycopy(src, 0, dst, 0, src.length);
            return;
        }

        // At the default 35 this gives roughly alpha 16 / 40 cutoffs.
        final int lowCut = 4 + Math.round(strength * 0.34f);
        final int midCut = 10 + Math.round(strength * 0.86f);

        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - 1);
            int y1 = Math.min(h - 1, y + 1);
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int p = src[idx];
                int alpha = (p >>> 24) & 0xFF;
                if (alpha == 0) {
                    dst[idx] = 0;
                    continue;
                }
                if (alpha > midCut) {
                    dst[idx] = p;
                    continue;
                }

                int strong = 0;
                int medium = 0;
                int maxAlpha = 0;
                int x0 = Math.max(0, x - 1);
                int x1 = Math.min(w - 1, x + 1);
                for (int yy = y0; yy <= y1; yy++) {
                    int row = yy * w;
                    for (int xx = x0; xx <= x1; xx++) {
                        if (xx == x && yy == y) continue;
                        int na = (src[row + xx] >>> 24) & 0xFF;
                        if (na > maxAlpha) maxAlpha = na;
                        if (na >= 128) strong++;
                        if (na >= 48) medium++;
                    }
                }

                boolean remove = false;
                if (alpha <= lowCut && strong == 0) {
                    remove = true;
                } else if (alpha <= midCut && strong == 0 && medium <= 1 && maxAlpha < 96) {
                    remove = true;
                }

                // Full transparent black avoids hidden RGB garbage in PNGs.
                dst[idx] = remove ? 0 : p;
            }
        }
    }

    /** Small edge-aware local smoother. Alpha is never blurred. */
    static void denoise(int[] src, int[] dst, int w, int h, int strength) {
        if (strength <= 0) {
            System.arraycopy(src, 0, dst, 0, src.length);
            return;
        }

        final int range = 6 + Math.round(strength * 0.95f); // default 15 -> ~20 RGB levels
        final float mix = Math.min(0.82f, 0.10f + strength * 0.0072f); // default -> ~0.21

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int center = src[idx];
                int a = (center >>> 24) & 0xFF;
                if (a == 0) {
                    dst[idx] = 0;
                    continue;
                }
                int cr = (center >>> 16) & 0xFF;
                int cg = (center >>> 8) & 0xFF;
                int cb = center & 0xFF;

                long sumR = 0, sumG = 0, sumB = 0, sumW = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    int row = yy * w;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        int q = src[row + xx];
                        int qa = (q >>> 24) & 0xFF;
                        if (qa == 0) continue;

                        // Do not pull colors across a transparency boundary.
                        if (Math.abs(a - qa) > 56 && Math.min(a, qa) < 224) continue;

                        int qr = (q >>> 16) & 0xFF;
                        int qg = (q >>> 8) & 0xFF;
                        int qb = q & 0xFF;
                        int diff = (Math.abs(cr - qr) + Math.abs(cg - qg) + Math.abs(cb - qb)) / 3;
                        if (diff > range) continue;

                        int spatial;
                        if (dx == 0 && dy == 0) spatial = 8;
                        else if (dx == 0 || dy == 0) spatial = 4;
                        else spatial = 2;
                        int rw = range - diff + 1;
                        int weight = spatial * rw;
                        sumR += (long) qr * weight;
                        sumG += (long) qg * weight;
                        sumB += (long) qb * weight;
                        sumW += weight;
                    }
                }

                int fr = sumW == 0 ? cr : (int) (sumR / sumW);
                int fg = sumW == 0 ? cg : (int) (sumG / sumW);
                int fb = sumW == 0 ? cb : (int) (sumB / sumW);

                int r = clamp255(Math.round(cr + (fr - cr) * mix));
                int g = clamp255(Math.round(cg + (fg - cg) * mix));
                int b = clamp255(Math.round(cb + (fb - cb) * mix));
                dst[idx] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    /** Mild 3x3 Gaussian unsharp mask, constrained by alpha boundaries. */
    static void sharpen(int[] src, int[] dst, int w, int h, int strength) {
        if (strength <= 0) {
            System.arraycopy(src, 0, dst, 0, src.length);
            return;
        }

        final float amount = Math.min(0.90f, strength * 0.0085f); // default 15 -> 0.1275

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int center = src[idx];
                int a = (center >>> 24) & 0xFF;
                if (a == 0) {
                    dst[idx] = 0;
                    continue;
                }
                int cr = (center >>> 16) & 0xFF;
                int cg = (center >>> 8) & 0xFF;
                int cb = center & 0xFF;

                long sumR = 0, sumG = 0, sumB = 0, sumW = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    int row = yy * w;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        int q = src[row + xx];
                        int qa = (q >>> 24) & 0xFF;
                        if (qa == 0) continue;
                        if (Math.abs(a - qa) > 48 && Math.min(a, qa) < 232) continue;

                        int weight;
                        if (dx == 0 && dy == 0) weight = 4;
                        else if (dx == 0 || dy == 0) weight = 2;
                        else weight = 1;
                        sumR += ((q >>> 16) & 0xFF) * (long) weight;
                        sumG += ((q >>> 8) & 0xFF) * (long) weight;
                        sumB += (q & 0xFF) * (long) weight;
                        sumW += weight;
                    }
                }

                int br = sumW == 0 ? cr : (int) (sumR / sumW);
                int bg = sumW == 0 ? cg : (int) (sumG / sumW);
                int bb = sumW == 0 ? cb : (int) (sumB / sumW);

                int r = clamp255(Math.round(cr + (cr - br) * amount));
                int g = clamp255(Math.round(cg + (cg - bg) * amount));
                int b = clamp255(Math.round(cb + (cb - bb) * amount));
                dst[idx] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int clamp100(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void progress(ProgressListener listener, String stage, int percent) {
        if (listener != null) listener.onProgress(stage, percent);
    }
}
