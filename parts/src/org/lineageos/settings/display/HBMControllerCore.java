/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.display;

final class HBMControllerCore {

    static final String MAX_BACKLIGHT = "2047";
    static final int MAX_SCREEN_BRIGHTNESS = 255;

    interface Backend {
        boolean hasSnapshot();
        int readScreenBrightness();
        String readBacklightBrightness();
        void saveSnapshot(int screenBrightness, String backlightBrightness);
        int getSavedScreenBrightness();
        String getSavedBacklightBrightness();
        void clearSnapshot();
        boolean writeHbm(boolean enabled);
        boolean writeBacklight(String value);
        boolean writeScreenBrightness(int value);
    }

    private HBMControllerCore() { }

    static boolean enable(Backend backend) {
        if (!backend.hasSnapshot()) {
            int screenBrightness = backend.readScreenBrightness();
            String backlightBrightness = backend.readBacklightBrightness();
            if (screenBrightness >= 0 || isValidBacklight(backlightBrightness)) {
                backend.saveSnapshot(screenBrightness, backlightBrightness);
            }
        }

        // Keep the original Tsaritsa behavior: sysfs writes are best-effort.
        // Do not reject the preference change based on FileUtils.writeLine().
        backend.writeHbm(true);

        backend.writeBacklight(MAX_BACKLIGHT);
        backend.writeScreenBrightness(MAX_SCREEN_BRIGHTNESS);
        return true;
    }

    static boolean disable(Backend backend) {
        // Same as enable(): do not make the UI state depend on the return
        // value of a sysfs write. Restore the saved brightness regardless.
        backend.writeHbm(false);

        if (!backend.hasSnapshot()) {
            return true;
        }

        int screenBrightness = backend.getSavedScreenBrightness();
        String backlightBrightness = backend.getSavedBacklightBrightness();

        if (screenBrightness >= 0) {
            backend.writeScreenBrightness(screenBrightness);
        }
        if (isValidBacklight(backlightBrightness)) {
            backend.writeBacklight(backlightBrightness.trim());
        }

        backend.clearSnapshot();
        return true;
    }

    private static boolean isValidBacklight(String value) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
