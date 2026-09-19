/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.corecontrol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/** State-safe helpers for CPU online/offline sysfs nodes. */
public final class CoreControlUtils {
    public static final int NUM_CORES = 8;
    private static final int LAST_LITTLE_CORE = 5;

    public boolean isCoreOnline(int core) {
        File node = node(core);
        // Linux commonly omits cpu0/online because CPU0 cannot be offlined.
        if (!node.exists()) return core == 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(node))) {
            return "1".equals(reader.readLine());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public boolean setCoreOnline(int core, boolean online) {
        if (core < 0 || core >= NUM_CORES) return false;
        if (!online && !canOffline(core)) return false;
        File node = node(core);
        if (!node.exists()) return core == 0 && online;
        if (!node.canWrite()) return false;
        try (FileWriter writer = new FileWriter(node)) {
            writer.write(online ? "1" : "0");
            writer.flush();
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return isCoreOnline(core) == online;
    }

    public boolean canOffline(int core) {
        if (core == 0) return false;
        if (core > LAST_LITTLE_CORE) return true;
        int remaining = 0;
        for (int i = 0; i <= LAST_LITTLE_CORE; i++) {
            if (i != core && isCoreOnline(i)) remaining++;
        }
        return remaining >= 2;
    }

    private File node(int core) {
        return new File("/sys/devices/system/cpu/cpu" + core + "/online");
    }
}
