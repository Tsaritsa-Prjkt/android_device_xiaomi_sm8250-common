/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

import org.lineageos.settings.utils.FileUtils;

/** Centralized display sysfs paths/ABIs used by XiaomiParts. */
public final class DisplayNodes {
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String HBM_ENABLE_KEY = "hbm";

    /*
     * Keep the original alioth/sm8250 XiaomiParts ABI first. These nodes are the
     * paths used by the existing kernel/device-tree implementation and are the
     * safest contract for POCO F3 / Mi 11X / Redmi K40.
     */
    private static final String DC_DIMMING_LEGACY_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/msm_fb_ea_enable";
    private static final String HBM_DIRECT_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm";

    /* Optional fallbacks used by kernels exposing the newer display interfaces. */
    private static final String DC_DIMMING_ALT_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";
    private static final String HBM_COMMAND_NODE =
            "/sys/class/drm/card0/card0-DSI-1/disp_param";

    private static final String BACKLIGHT =
            "/sys/class/backlight/panel0-backlight/brightness";
    private static final String BACKLIGHT_MAX =
            "/sys/class/backlight/panel0-backlight/max_brightness";

    private DisplayNodes() {}

    public static String getDcDimmingEnableKey() {
        return DC_DIMMING_ENABLE_KEY;
    }

    public static String getHbmEnableKey() {
        return HBM_ENABLE_KEY;
    }

    /**
     * Prefer a node XiaomiParts can actually write. Merely existing is not enough:
     * some DRM nodes are visible to apps but intentionally not writable from the
     * system-app domain, which previously made the UI report a false unsupported state.
     */
    public static String getDcDimmingNode() {
        return firstWritableOrExisting(DC_DIMMING_LEGACY_NODE, DC_DIMMING_ALT_NODE);
    }

    public static String getHbmNode() {
        return firstWritableOrExisting(HBM_DIRECT_NODE, HBM_COMMAND_NODE);
    }

    public static boolean usesHbmCommandAbi() {
        return HBM_COMMAND_NODE.equals(getHbmNode());
    }

    public static String getHbmEnableValue() {
        return usesHbmCommandAbi() ? "0x10000" : "1";
    }

    public static String getHbmDisableValue() {
        return usesHbmCommandAbi() ? "0xF0000" : "0";
    }

    public static String getBacklight() {
        return BACKLIGHT;
    }

    public static String getBacklightMax() {
        return BACKLIGHT_MAX;
    }

    private static String firstWritableOrExisting(String... candidates) {
        for (String candidate : candidates) {
            if (FileUtils.isFileWritable(candidate)) return candidate;
        }
        for (String candidate : candidates) {
            if (FileUtils.fileExists(candidate)) return candidate;
        }
        // Return the canonical path for deterministic diagnostics on unsupported kernels.
        return candidates[0];
    }
}
