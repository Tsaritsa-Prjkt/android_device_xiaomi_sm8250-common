/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

final class AutoHBMPolicy {
    private AutoHBMPolicy() {}

    static boolean shouldEnable(float lux, int threshold, boolean active, boolean blocked) {
        return lux >= threshold && !active && !blocked;
    }

    static boolean shouldScheduleDisable(float lux, int threshold, boolean active) {
        return lux < threshold && active;
    }

    static boolean shouldDisableNow(float lux, int threshold, boolean active) {
        return lux < threshold && active;
    }
}
