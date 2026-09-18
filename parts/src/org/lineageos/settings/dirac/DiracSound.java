/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.dirac;

import android.media.audiofx.AudioEffect;

import java.util.UUID;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class DiracSound extends AudioEffect {

    public static final int EQ_BAND_COUNT = 10;
    private static final int MISOUND_PARAM_ENABLE = 25;
    private static final int DIRACSOUND_PARAM_HEADSET_TYPE = 1;
    private static final int DIRACSOUND_PARAM_EQ_LEVEL = 2;
    private static final int DIRACSOUND_PARAM_MUSIC = 4;
    private static final int DIRACSOUND_PARAM_HIFI = 8;
    private static final int DIRACSOUND_PARAM_SCENE = 15;

    private static final UUID EFFECT_TYPE_DIRACSOUND =
            UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b");
    private static final String TAG = "DiracSound";

    public DiracSound(int priority, int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_DIRACSOUND, priority, audioSession);
    }

    /** MiSound has a native enable gate separate from music mode and AudioEffect state. */
    @Override
    public int setEnabled(boolean enabled) {
        // Stop Android processing first when disabling, even if the vendor write fails.
        if (!enabled) checkStatus(super.setEnabled(false));
        checkStatus(setParameter(MISOUND_PARAM_ENABLE, enabled ? 1 : 0));
        if (!enabled) return SUCCESS;
        int status = super.setEnabled(true);
        if (status != SUCCESS) {
            // Do not leave the native gate enabled after a failed framework enable.
            checkStatus(setParameter(MISOUND_PARAM_ENABLE, 0));
        }
        return status;
    }

    @Override
    public boolean getEnabled() {
        int[] value = new int[1];
        checkStatus(getParameter(MISOUND_PARAM_ENABLE, value));
        return value[0] == 1 && super.getEnabled();
    }

    /** Stock parameter 19 returns a little-endian count followed by headset IDs. */
    public int[] getHeadsetList() {
        byte[] reply = new byte[300];
        int size = getParameter(19, reply);
        checkStatus(size);
        if (size < 4) return new int[0];
        ByteBuffer buffer = ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN);
        int count = buffer.getInt();
        if (count < 0 || count > (Math.min(size, reply.length) - 4) / 4) {
            // Some legacy backends return a truncated count-plus-ID payload.
            // Do not infer capabilities from the zero-filled buffer tail.
            return new int[0];
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = buffer.getInt();
        return ids;
    }

    public int getMusic() throws IllegalStateException,
            IllegalArgumentException, UnsupportedOperationException,
            RuntimeException {
        int[] value = new int[1];
        checkStatus(getParameter(DIRACSOUND_PARAM_MUSIC, value));
        return value[0];
    }

    public void setMusic(int enable) throws IllegalStateException,
            IllegalArgumentException, UnsupportedOperationException,
            RuntimeException {
        checkStatus(setParameter(DIRACSOUND_PARAM_MUSIC, enable));
    }

    public void setHeadsetType(int type) throws IllegalStateException,
            IllegalArgumentException, UnsupportedOperationException,
            RuntimeException {
        checkStatus(setParameter(DIRACSOUND_PARAM_HEADSET_TYPE, type));
    }

    public void setLevel(int band, float level) throws IllegalStateException,
            IllegalArgumentException, UnsupportedOperationException,
            RuntimeException {
        if (band < 0 || band >= EQ_BAND_COUNT) throw new IllegalArgumentException("Invalid EQ band");
        if (!Float.isFinite(level)) throw new IllegalArgumentException("Non-finite EQ level");
        checkStatus(setParameter(new int[]{DIRACSOUND_PARAM_EQ_LEVEL, band},
                String.valueOf(level).getBytes(StandardCharsets.US_ASCII)));
    }

    public void setHifiMode(int mode) throws IllegalStateException,
            IllegalArgumentException, UnsupportedOperationException,
            RuntimeException {
        if (mode < 0 || mode > 1) throw new IllegalArgumentException("Invalid Hi-Fi mode");
        checkStatus(setParameter(DIRACSOUND_PARAM_HIFI, mode));
    }

    public void setScenario(int scene) throws IllegalStateException,
            IllegalArgumentException, UnsupportedOperationException,
            RuntimeException {
        if (scene < 0 || scene > 4) throw new IllegalArgumentException("Invalid scenario");
        checkStatus(setParameter(DIRACSOUND_PARAM_SCENE, scene));
    }
}
