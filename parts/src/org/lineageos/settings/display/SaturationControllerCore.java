/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.display;

final class SaturationControllerCore {

    private SaturationControllerCore() { }

    static float toSurfaceFlingerValue(int seekBarValue) {
        return seekBarValue == 100 ? 1.001f : seekBarValue / 100.0f;
    }
}
