/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.corecontrol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/** State-safe helpers for CPU online/offline sysfs nodes on SM8250. */
public final class CoreControlUtils {
    public static final int NUM_CORES = 8;
    private static final int LAST_EFFICIENCY_CORE = 3;
    private static final int MIN_EFFICIENCY_CORES_ONLINE = 2;
    private static final String CPU_BASE = "/sys/devices/system/cpu/cpu";

    public boolean isCoreOnline(int core) {
        if (core < 0 || core >= NUM_CORES) return false;
        File node = onlineNode(core);
        if (!node.exists()) {
            /* CPU0 and non-hotpluggable CPUs do not expose an online node and are always online. */
            return cpuDirectory(core).exists();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(node))) {
            return "1".equals(reader.readLine());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public boolean isCoreControllable(int core) {
        return core > 0 && core < NUM_CORES && onlineNode(core).exists();
    }

    public boolean setCoreOnline(int core, boolean online) {
        if (core < 0 || core >= NUM_CORES) return false;
        if (!isCoreControllable(core)) return isCoreOnline(core) == online;
        if (!online && !canOffline(core)) return false;

        File node = onlineNode(core);
        try (FileWriter writer = new FileWriter(node)) {
            writer.write(online ? "1" : "0");
            writer.flush();
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return isCoreOnline(core) == online;
    }

    public boolean canOffline(int core) {
        if (!isCoreControllable(core)) return false;
        if (core > LAST_EFFICIENCY_CORE) return true;

        int remaining = 0;
        for (int i = 0; i <= LAST_EFFICIENCY_CORE; i++) {
            if (i != core && isCoreOnline(i)) remaining++;
        }
        return remaining >= MIN_EFFICIENCY_CORES_ONLINE;
    }

    private File onlineNode(int core) {
        return new File(CPU_BASE + core + "/online");
    }

    private File cpuDirectory(int core) {
        return new File(CPU_BASE + core);
    }
}
