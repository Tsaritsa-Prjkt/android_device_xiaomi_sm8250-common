// SPDX-License-Identifier: Apache-2.0
package org.lineageos.settings.kcal;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One complete KCAL interface. Never combine attributes from different drivers. */
final class KcalNodes {
    static final int[] DEFAULT = {256, 256, 256, 255, 255, 255, 0};
    static final int[] MIN = {0, 0, 0, 128, 128, 128, 0};
    static final int[] MAX = {256, 256, 256, 383, 383, 383, 1536};
    private static final String[] NAMES = {
            "kcal_red", "kcal_green", "kcal_blue", "kcal_sat", "kcal_val",
            "kcal_cont", "kcal_hue"
    };

    private final Path[] channels;
    private final Path enable;
    private final boolean combined;
    private final boolean msmDrm;

    private KcalNodes(Path[] channels, Path enable, boolean combined, boolean msmDrm) {
        this.channels = channels;
        this.enable = enable;
        this.combined = combined;
        this.msmDrm = msmDrm;
    }

    int minimum(int channel) {
        // sde_setup_dspp_pccv4 clamps the three module parameters to 20.
        return msmDrm && channel < 3 ? 20 : MIN[channel];
    }

    private static boolean accessible(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path) && Files.isWritable(path);
    }

    private static KcalNodes probe(Path dir) {
        Path[] channels = new Path[NAMES.length];
        for (int i = 0; i < channels.length; i++) channels[i] = dir.resolve(NAMES[i]);
        boolean combined = !(accessible(channels[0]) && accessible(channels[1])
                && accessible(channels[2]));
        if (combined) {
            Path rgb = dir.resolve("kcal");
            if (!accessible(rgb)) return null;
            Arrays.fill(channels, 0, 3, rgb);
        }
        if (!Files.exists(channels[5])) channels[5] = dir.resolve("kcal_contrast");
        for (int i = 3; i < channels.length; i++) {
            if (!accessible(channels[i])) return null;
        }
        Path enable = dir.resolve("kcal_enable");
        if (Files.exists(enable) && !accessible(enable)) return null;
        KcalNodes nodes = new KcalNodes(channels, accessible(enable) ? enable : null, combined,
                !combined && dir.endsWith("module/msm_drm/parameters"));
        try {
            nodes.snapshot();
            return nodes;
        } catch (IOException e) {
            return null;
        }
    }

    static KcalNodes discover(Path sys) {
        Path platform = sys.resolve("devices/platform");
        KcalNodes found = probe(sys.resolve("module/msm_drm/parameters"));
        if (found != null) return found;
        found = probe(platform.resolve("kcal_ctrl.0"));
        if (found != null) return found;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(platform)) {
            for (Path child : children) {
                found = probe(child.resolve("kcal_ctrl.0"));
                if (found != null) return found;
            }
        } catch (IOException ignored) {
            // A restricted subtree does not rule out the class interfaces.
        }
        for (String dir : new String[]{"class/display", "class/graphics/fb0"}) {
            found = probe(sys.resolve(dir));
            if (found != null) return found;
        }
        for (Path root : new Path[]{sys.resolve("module"), platform}) {
            KcalNodes[] match = new KcalNodes[1];
            try {
                Files.walkFileTree(root, Collections.emptySet(), 6, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (name.equals("kcal_red") || name.equals("kcal")) {
                            match[0] = probe(file.getParent());
                        }
                        return match[0] == null ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
                    }
                    @Override
                    public FileVisitResult visitFileFailed(Path path, IOException error) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
                // Try the next root.
            }
            if (match[0] != null) return match[0];
        }
        return null;
    }

    private static String read(Path path, int count) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            String line = reader.readLine();
            String[] values = line == null ? new String[0] : line.trim().split("\\s+");
            if (values.length != count) throw new IOException("Invalid KCAL attribute: " + path);
            for (String value : values) Integer.parseInt(value);
            return String.join(" ", values);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid KCAL value at " + path, e);
        }
    }

    private Map<Path, String> snapshot() throws IOException {
        Map<Path, String> state = new LinkedHashMap<>();
        for (int i = 0; i < channels.length; i++) {
            if (!state.containsKey(channels[i])) {
                state.put(channels[i], read(channels[i], combined && i == 0 ? 3 : 1));
            }
        }
        if (enable != null) state.put(enable, read(enable, 1));
        return state;
    }

    private static void write(Path path, String value) throws IOException {
        Files.write(path, (value + "\n").getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    void apply(boolean enabled, int[] profile) throws IOException {
        Map<Path, String> before = snapshot();
        int[] target = enabled ? profile : DEFAULT;
        Map<Path, String> desired = new LinkedHashMap<>();
        if (combined) desired.put(channels[0], target[0] + " " + target[1] + " " + target[2]);
        for (int i = combined ? 3 : 0; i < channels.length; i++) {
            desired.put(channels[i], Integer.toString(target[i]));
        }
        if (enable != null) desired.put(enable, enabled ? "1" : "0");
        Map<Path, String> undo = new LinkedHashMap<>();
        try {
            for (Map.Entry<Path, String> item : desired.entrySet()) {
                Path path = item.getKey();
                if (item.getValue().equals(before.get(path))) continue;
                undo.put(path, before.get(path));
                write(path, item.getValue());
            }
            if (!snapshot().equals(desired)) throw new IOException("KCAL readback mismatch");
        } catch (IOException error) {
            for (Map.Entry<Path, String> item : undo.entrySet()) {
                try {
                    write(item.getKey(), item.getValue());
                } catch (IOException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            throw error;
        }
    }
}
