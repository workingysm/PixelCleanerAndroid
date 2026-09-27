import com.openai.pixelcleaner.core.PixelCleanerCore;

public class CoreSelfTest {
    public static void main(String[] args) {
        testUniformIsStable();
        testFaintIsolatedSpeckRemoved();
        testAntialiasNearOpaqueEdgeSurvives();
        testSizeIsPreserved();
        testOwnedVariantMatches();
        testOverflowDimensionsRejected();
        System.out.println("PixelCleanerCore self-test: PASS");
    }

    private static void testUniformIsStable() {
        int[] p = new int[25];
        for (int i = 0; i < p.length; i++) p[i] = 0xFF7A7A7A;
        int[] out = PixelCleanerCore.clean(p, 5, 5, new PixelCleanerCore.Params(15, 15, 35));
        for (int v : out) require(v == 0xFF7A7A7A, "uniform color changed");
    }

    private static void testFaintIsolatedSpeckRemoved() {
        int[] p = new int[25];
        p[12] = (10 << 24) | 0x00FFFFFF;
        int[] out = PixelCleanerCore.clean(p, 5, 5, new PixelCleanerCore.Params(0, 0, 35));
        require(out[12] == 0, "isolated faint speck was not removed");
    }

    private static void testAntialiasNearOpaqueEdgeSurvives() {
        int[] p = new int[25];
        p[12] = (12 << 24) | 0x00FFFFFF;
        p[13] = 0xFFFFFFFF;
        int[] out = PixelCleanerCore.clean(p, 5, 5, new PixelCleanerCore.Params(0, 0, 35));
        require(((out[12] >>> 24) & 0xFF) == 12, "valid antialias edge was removed");
    }

    private static void testSizeIsPreserved() {
        int[] p = new int[63];
        int[] out = PixelCleanerCore.clean(p, 9, 7, new PixelCleanerCore.Params(15, 15, 35));
        require(out.length == 63, "pixel count changed");
    }

    private static void testOwnedVariantMatches() {
        int[] p1 = new int[81];
        for (int i = 0; i < p1.length; i++) {
            int v = 90 + (i % 7);
            p1[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        int[] p2 = p1.clone();
        PixelCleanerCore.Params params = new PixelCleanerCore.Params(22, 17, 35);
        int[] a = PixelCleanerCore.clean(p1, 9, 9, params);
        int[] b = PixelCleanerCore.cleanOwned(p2, 9, 9, params, null);
        require(java.util.Arrays.equals(a, b), "owned processing differs");
    }

    private static void testOverflowDimensionsRejected() {
        boolean rejected = false;
        try {
            PixelCleanerCore.clean(new int[1], Integer.MAX_VALUE, 2, new PixelCleanerCore.Params(0, 0, 0));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "overflow dimensions were not rejected");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
