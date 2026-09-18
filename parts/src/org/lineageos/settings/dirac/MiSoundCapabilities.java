/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.dirac;

final class MiSoundCapabilities {

    private MiSoundCapabilities() { }

    static int chooseHeadset(int preferred, int[] supported) {
        if (supported == null || supported.length == 0) {
            // Empty means unavailable/malformed capability data. Keep the
            // configured value instead of inferring unsupported hardware.
            return preferred;
        }

        for (int id : supported) {
            if (id == preferred) {
                return preferred;
            }
        }
        return supported[0];
    }
}
