package org.ssdd.p0;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class V2PostprocessorTest {
    @Test
    void usesInclusiveThresholdEightNeighborsAndUnionForOverlaps() {
        float[] logits = new float[4 * 9];
        Arrays.fill(logits, -100);
        logits[0] = 0;
        logits[4] = 10;
        logits[9 + 4] = 10;
        var result = V2Postprocessor.process(logits, 3, 3, 0, new int[4]);
        assertArrayEquals(new int[]{2, 1, 0, 0}, result.pixels());
        assertArrayEquals(new int[]{1, 1, 0, 0}, result.components());
        assertEquals(2, result.unionPixels());
        assertEquals(1, result.overlapPixels());
    }

    @Test
    void filtersComponentsBeforeClassAreaAndRetainsExactBoundaries() {
        int width = 50, size = width * 50;
        float[] logits = new float[4 * size];
        Arrays.fill(logits, -100);
        // 149-pixel class 1 component disappears before its class-area check.
        for (int i = 0; i < 149; i++) logits[i] = 10;
        // Exactly 150 pixels survive the component filter.
        for (int i = 0; i < 150; i++) logits[size + i] = 10;
        // 200 + 200 pixels, both components survive, but class total is < 600.
        for (int y = 0; y < 10; y++) for (int x = 0; x < 20; x++) {
            logits[2 * size + y * width + x] = 10;
            logits[2 * size + (y + 20) * width + x] = 10;
        }
        // Exactly 600 pixels survive the class-area check.
        for (int i = 0; i < 600; i++) logits[3 * size + i] = 10;
        var result = V2Postprocessor.process(logits, width, 50, 150, new int[]{0, 0, 600, 600});
        assertArrayEquals(new int[]{0, 150, 0, 600}, result.pixels());
        assertArrayEquals(new int[]{0, 1, 0, 1}, result.components());
    }

    @Test
    void rejectsNonFiniteOutput() {
        assertThrows(IllegalArgumentException.class,
                () -> V2Postprocessor.process(new float[]{Float.NaN, 0, 0, 0}, 1, 1, 0, new int[4]));
    }
}
