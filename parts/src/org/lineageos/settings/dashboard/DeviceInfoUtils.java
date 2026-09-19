/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.dashboard;

import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;

import java.util.Locale;

/** Resolves Xiaomi/POCO/Redmi identity from the active product properties. */
public final class DeviceInfoUtils {
    private DeviceInfoUtils() {}

    public static String getCodename() {
        String device = firstNonEmpty(
                SystemProperties.get("ro.product.device"),
                SystemProperties.get("ro.product.vendor.device"),
                SystemProperties.get("ro.product.product.device"),
                SystemProperties.get("ro.product.system.device"),
                SystemProperties.get("ro.product.system_ext.device"),
                SystemProperties.get("ro.product.odm.device"),
                SystemProperties.get("ro.build.product"),
                Build.DEVICE,
                Build.PRODUCT);
        return TextUtils.isEmpty(device) ? "unknown" : normalizeCodename(device);
    }

    public static String getMarketingName() {
        final String codename = getCodename();
        switch (codename) {
            case "alioth":
            case "aliothin":
                return "POCO F3 / Mi 11X / Redmi K40";
            case "apollo":
            case "apollopro":
                return "Mi 10T / Mi 10T Pro / Redmi K30S Ultra";
            case "lmi":
            case "lmipro":
                return "POCO F2 Pro / Redmi K30 Pro";
            case "umi":
                return "Mi 10";
            case "cmi":
                return "Mi 10 Pro";
            case "cas":
                return "Mi 10 Ultra";
            case "thyme":
                return "Mi 10S";
            case "munch":
                return "POCO F4 / Redmi K40S";
            case "psyche":
                return "Xiaomi 12X";
            case "elish":
                return "Xiaomi Pad 5 Pro";
            case "enuma":
                return "Xiaomi Pad 5 Pro 5G";
            default:
                String marketName = firstNonEmpty(
                        SystemProperties.get("ro.product.marketname"),
                        SystemProperties.get("ro.product.vendor.marketname"),
                        SystemProperties.get("ro.product.product.marketname"),
                        SystemProperties.get("ro.product.system.marketname"),
                        SystemProperties.get("ro.product.system_ext.marketname"),
                        SystemProperties.get("ro.product.model"),
                        Build.MODEL);
                return TextUtils.isEmpty(marketName) ? "Xiaomi device" : marketName;
        }
    }

    private static String normalizeCodename(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        // Some device trees append region suffixes to the base codename.
        int separator = normalized.indexOf('_');
        if (separator > 0) {
            String base = normalized.substring(0, separator);
            if (isKnownCodename(base)) return base;
        }
        return normalized;
    }

    private static boolean isKnownCodename(String value) {
        switch (value) {
            case "alioth": case "aliothin": case "apollo": case "apollopro":
            case "lmi": case "lmipro": case "umi": case "cmi": case "cas":
            case "thyme": case "munch": case "psyche": case "elish": case "enuma":
                return true;
            default:
                return false;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value.trim();
        }
        return "";
    }
}
