// SPDX-License-Identifier: Apache-2.0
package org.lineageos.settings.kcal;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Boot and the settings screen serialize hardware changes and profile updates. */
public final class KcalSettings {
    static final Executor IO = Executors.newSingleThreadExecutor();
    private static final String ENABLED = "enabled";
    private final SharedPreferences preferences;
    KcalNodes nodes;

    KcalSettings(Context context) {
        preferences = context.getSharedPreferences("kcal", Context.MODE_PRIVATE);
    }

    void discover() {
        nodes = KcalNodes.discover(Paths.get("/sys"));
    }

    boolean enabled() {
        return preferences.getBoolean(ENABLED, false);
    }

    int[] profile() {
        int[] values = KcalNodes.DEFAULT.clone();
        for (int i = 0; i < values.length; i++) {
            int min = nodes == null ? KcalNodes.MIN[i] : nodes.minimum(i);
            values[i] = Math.max(min, Math.min(KcalNodes.MAX[i],
                    preferences.getInt("channel_" + i, values[i])));
        }
        return values;
    }

    void change(boolean enabled, int[] profile) throws IOException {
        if (nodes == null) throw new IOException("KCAL unavailable");
        nodes.apply(enabled, profile);
        SharedPreferences.Editor edit = preferences.edit().putBoolean(ENABLED, enabled);
        for (int i = 0; i < profile.length; i++) edit.putInt("channel_" + i, profile[i]);
        edit.apply();
    }

    public static void restore(Context context, Runnable finished) {
        KcalSettings settings = new KcalSettings(context);
        IO.execute(() -> {
            try {
                if (!settings.preferences.contains(ENABLED)) return;
                settings.discover();
                if (settings.nodes != null) {
                    settings.nodes.apply(settings.enabled(), settings.profile());
                }
            } catch (IOException e) {
                Log.e("XiaomiParts", "Could not restore KCAL", e);
            } finally {
                finished.run();
            }
        });
    }
}
