/*
 * Copyright (C) 2023 Paranoid Android
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

package org.lineageos.settings.speaker;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.lineageos.settings.R;

import java.io.IOException;

public class ClearSpeakerFragment extends Fragment {

    private static final String TAG = "ClearSpeakerFragment";
    private static final int PLAY_DURATION_MS = 30000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mMediaPlayer;
    private ImageButton mActionButton;
    private boolean mPlaying;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.clear_speaker_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mActionButton = view.findViewById(R.id.clear_speaker_action);
        mActionButton.setOnClickListener(v -> {
            if (mPlaying) {
                stopPlaying();
            } else if (startPlaying()) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(this::stopPlaying, PLAY_DURATION_MS);
            }
        });
        updateButton();
    }

    @Override
    public void onStop() {
        stopPlaying();
        super.onStop();
    }

    private boolean startPlaying() {
        requireActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        MediaPlayer player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setLooping(true);
        player.setOnErrorListener((mediaPlayer, what, extra) -> {
            stopPlaying();
            return true;
        });

        try (AssetFileDescriptor afd = getResources().openRawResourceFd(
                R.raw.clear_speaker_sound)) {
            player.setDataSource(afd);
            player.setVolume(1.0f, 1.0f);
            player.prepare();
            player.start();
            mMediaPlayer = player;
            mPlaying = true;
            updateButton();
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "Failed to play speaker clean sound", e);
            player.release();
            return false;
        }
    }

    private void stopPlaying() {
        mHandler.removeCallbacksAndMessages(null);
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to stop speaker clean sound", e);
            } finally {
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
        mPlaying = false;
        updateButton();
    }

    private void updateButton() {
        if (mActionButton == null) {
            return;
        }
        mActionButton.setImageResource(mPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        mActionButton.setContentDescription(getString(mPlaying
                ? R.string.clear_speaker_stop : R.string.clear_speaker_start));
    }
}
