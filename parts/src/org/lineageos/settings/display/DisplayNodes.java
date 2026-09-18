/*
 * Copyright (C) 2022 The CipherOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.display;

public final class DisplayNodes {
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/msm_fb_ea_enable";
    private static final String HBM_ENABLE_KEY = "hbm_mode";
    private static final String AUTO_HBM_ENABLE_KEY = "auto_hbm";
    private static final String AUTO_HBM_THRESHOLD_KEY = "auto_hbm_threshold";
    private static final String AUTO_HBM_DISABLE_TIME_KEY = "hbm_disable_time";
    private static final String HBM_NODE =
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm";
    private static final String BACKLIGHT = "/sys/class/backlight/panel0-backlight/brightness";
    private static final String BACKLIGHT_MAX =
            "/sys/class/backlight/panel0-backlight/max_brightness";

    public static final int AUTO_HBM_THRESHOLD_DEFAULT = 7000;
    public static final int AUTO_HBM_DISABLE_TIME_DEFAULT = 1;

    private DisplayNodes() {}

    public static String getDcDimmingEnableKey() {
        return DC_DIMMING_ENABLE_KEY;
    }

    public static String getDcDimmingNode() {
        return DC_DIMMING_NODE;
    }

    public static String getHbmEnableKey() {
        return HBM_ENABLE_KEY;
    }

    public static String getAutoHbmEnableKey() {
        return AUTO_HBM_ENABLE_KEY;
    }

    public static String getAutoHbmThresholdKey() {
        return AUTO_HBM_THRESHOLD_KEY;
    }

    public static String getAutoHbmDisableTimeKey() {
        return AUTO_HBM_DISABLE_TIME_KEY;
    }

    public static String getHbmNode() {
        return HBM_NODE;
    }

    public static String getBacklight() {
        return BACKLIGHT;
    }

    public static String getBacklightMax() {
        return BACKLIGHT_MAX;
    }
}
