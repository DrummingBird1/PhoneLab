package com.sensolab.devicemonitor;

/**
 * Minimal in-place radix-2 FFT for the vibration tool. Pure Java, no allocation beyond the
 * caller's buffers, so it is cheap enough to run a few times per second on 256 samples.
 */
public final class Fft {
    private Fft() {}

    /** In-place complex FFT; {@code re.length} must be a power of two. */
    public static void transform(float[] re, float[] im) {
        int n = re.length;
        // Bit-reversal permutation
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wRe = (float) Math.cos(ang), wIm = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float curRe = 1f, curIm = 0f;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    float vRe = re[b] * curRe - im[b] * curIm;
                    float vIm = re[b] * curIm + im[b] * curRe;
                    re[b] = re[a] - vRe; im[b] = im[a] - vIm;
                    re[a] += vRe;        im[a] += vIm;
                    float nRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = nRe;
                }
            }
        }
    }

    /**
     * Strongest non-DC frequency (Hz) in a real signal sampled at {@code sampleRateHz}.
     * Applies a Hann window and removes the mean first. Returns 0 for a flat signal.
     * {@code samples.length} must be a power of two.
     */
    public static float dominantFrequency(float[] samples, float sampleRateHz) {
        int n = samples.length;
        float mean = 0f;
        for (float s : samples) mean += s;
        mean /= n;
        float[] re = new float[n], im = new float[n];
        for (int i = 0; i < n; i++) {
            float hann = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
            re[i] = (samples[i] - mean) * hann;
        }
        transform(re, im);
        int best = 0;
        float bestMag = 0f;
        for (int k = 1; k < n / 2; k++) {
            float mag = re[k] * re[k] + im[k] * im[k];
            if (mag > bestMag) { bestMag = mag; best = k; }
        }
        return bestMag < 1e-9f ? 0f : best * sampleRateHz / n;
    }
}
