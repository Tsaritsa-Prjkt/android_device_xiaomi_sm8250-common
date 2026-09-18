/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

final class HBMControllerCore {
    static final int MAX_SCREEN_BRIGHTNESS = 255;

    interface Backend {
        boolean hasSnapshot();
        int readScreenBrightness();
        String readBacklightBrightness();
        String readMaxBacklightBrightness();
        void saveSnapshot(int screenBrightness, String backlightBrightness);
        int getSavedScreenBrightness();
        String getSavedBacklightBrightness();
        void clearSnapshot();
        boolean writeHbm(boolean enabled);
        boolean writeBacklight(String value);
        boolean writeScreenBrightness(int value);
    }

    private HBMControllerCore() {}

    static boolean enable(Backend backend) {
        String maxBacklight = sanitizeUnsignedInt(backend.readMaxBacklightBrightness());
        if (maxBacklight == null) {
            return false;
        }

        if (!backend.hasSnapshot()) {
            int screenBrightness = backend.readScreenBrightness();
            String backlightBrightness = sanitizeUnsignedInt(backend.readBacklightBrightness());
            if (screenBrightness >= 0 || backlightBrightness != null) {
                backend.saveSnapshot(screenBrightness, backlightBrightness);
            }
        }

        // Some sysfs implementations report an I/O failure even though the value is applied.
        // Keep the write best-effort so the UI state does not regress on those kernels.
        backend.writeHbm(true);
        backend.writeBacklight(maxBacklight);
        backend.writeScreenBrightness(MAX_SCREEN_BRIGHTNESS);
        return true;
    }

    static boolean disable(Backend backend) {
        // Same best-effort rule as enable(). Always attempt brightness restoration.
        backend.writeHbm(false);

        if (!backend.hasSnapshot()) {
            return true;
        }

        boolean restored = true;
        int screenBrightness = backend.getSavedScreenBrightness();
        String backlightBrightness = sanitizeUnsignedInt(backend.getSavedBacklightBrightness());

        if (screenBrightness >= 0) {
            restored &= backend.writeScreenBrightness(screenBrightness);
        }
        if (backlightBrightness != null) {
            restored &= backend.writeBacklight(backlightBrightness);
        }

        // Keep the snapshot when restoration failed, so a later disable/boot can retry.
        if (restored) {
            backend.clearSnapshot();
        }
        return true;
    }

    private static String sanitizeUnsignedInt(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) return null;
        }
        return trimmed;
    }
}
