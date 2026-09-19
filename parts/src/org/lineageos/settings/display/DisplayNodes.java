/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.display;

import org.lineageos.settings.utils.FileUtils;

/** Centralized display sysfs paths/ABIs used by XiaomiParts. */
public final class DisplayNodes {
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String HBM_ENABLE_KEY = "hbm";

    // Main sm8250-common/alioth paths used by this tree.
    private static final String DC_DIMMING_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";
    private static final String HBM_COMMAND_NODE =
            "/sys/class/drm/card0/card0-DSI-1/disp_param";

    // Fallbacks used by some sm8250 kernels while keeping the same UI implementation.
    private static final String DC_DIMMING_FALLBACK =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/msm_fb_ea_enable";
    private static final String HBM_DIRECT_FALLBACK =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm";

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

    public static String getDcDimmingNode() {
        if (FileUtils.fileExists(DC_DIMMING_NODE)) return DC_DIMMING_NODE;
        return DC_DIMMING_FALLBACK;
    }

    public static String getHbmNode() {
        if (FileUtils.fileExists(HBM_COMMAND_NODE)) return HBM_COMMAND_NODE;
        return HBM_DIRECT_FALLBACK;
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
}
