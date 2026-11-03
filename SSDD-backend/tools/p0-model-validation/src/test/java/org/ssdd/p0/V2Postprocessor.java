package org.ssdd.p0;

import java.util.Arrays;

/** Diagnostic implementation of the supplied Python V2 postprocess contract. */
final class V2Postprocessor {
    static final int[] MIN_AREA = {600, 600, 900, 2000};
    static final int MIN_COMPONENT = 150;

    record Masks(byte[][] channels, int[] pixels, int[] components, int unionPixels, int overlapPixels) {}

    static double sigmoid(float value) {
        return value >= 0 ? 1.0 / (1.0 + Math.exp(-value))
                : Math.exp(value) / (1.0 + Math.exp(value));
    }

    static Masks process(float[] logits, int width, int height, int minComponent, int[] minArea) {
        int size = Math.multiplyExact(width, height);
        if (logits.length != 4 * size || minArea.length != 4) {
            throw new IllegalArgumentException("Expected four NCHW channels");
        }
        byte[][] masks = new byte[4][size];
        int[] pixels = new int[4];
        int[] components = new int[4];
        int[] queue = new int[size];
        boolean[] visited = new boolean[size];
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i < size; i++) {
                float logit = logits[c * size + i];
                if (!Float.isFinite(logit)) throw new IllegalArgumentException("Non-finite logit");
                masks[c][i] = (byte) (sigmoid(logit) >= 0.5 ? 1 : 0);
            }
            Arrays.fill(visited, false);
            for (int start = 0; start < size; start++) {
                if (masks[c][start] == 0 || visited[start]) continue;
                int head = 0, tail = 1;
                queue[0] = start;
                visited[start] = true;
                while (head < tail) {
                    int p = queue[head++], x = p % width, y = p / width;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                            int neighbor = ny * width + nx;
                            if (!visited[neighbor] && masks[c][neighbor] != 0) {
                                visited[neighbor] = true;
                                queue[tail++] = neighbor;
                            }
                        }
                    }
                }
                if (tail < minComponent) {
                    for (int k = 0; k < tail; k++) masks[c][queue[k]] = 0;
                } else {
                    pixels[c] += tail;
                    components[c]++;
                }
            }
            if (pixels[c] < minArea[c]) {
                Arrays.fill(masks[c], (byte) 0);
                pixels[c] = 0;
                components[c] = 0;
            }
        }
        int union = 0, overlap = 0;
        for (int i = 0; i < size; i++) {
            int positive = 0;
            for (byte[] mask : masks) positive += mask[i];
            if (positive > 0) union++;
            if (positive > 1) overlap++;
        }
        return new Masks(masks, pixels, components, union, overlap);
    }
}
