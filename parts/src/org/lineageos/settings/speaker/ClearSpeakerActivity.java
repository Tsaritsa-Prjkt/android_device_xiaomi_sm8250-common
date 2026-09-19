/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.settings.speaker;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.lineageos.settings.R;

import java.io.IOException;

/** Screenshot-styled clear-speaker page with lifecycle-safe 30-second playback. */
public class ClearSpeakerActivity extends Activity {
    private static final String TAG = "ClearSpeakerActivity";
    private static final long PLAY_DURATION_MS = 30_000L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer mPlayer;
    private ToggleButton mToggle;
    private TextView mStatus;
    private long mStartedAt;

    private final Runnable mTick = new Runnable() {
        @Override public void run() {
            if (mPlayer == null || mStatus == null) return;
            long elapsed = Math.max(0L, System.currentTimeMillis() - mStartedAt);
            long remaining = Math.max(0L, PLAY_DURATION_MS - elapsed);
            if (remaining == 0L) {
                stopPlaying();
                return;
            }
            mStatus.setText("Playing • " + ((remaining + 999L) / 1000L) + "s");
            mHandler.postDelayed(this, 500L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clear_speaker);
        findViewById(R.id.xp_back).setOnClickListener(v -> finish());
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mToggle = findViewById(R.id.clear_speaker_toggle);
        mStatus = findViewById(R.id.clear_speaker_status);
        mToggle.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                if (!startPlaying()) setToggleChecked(false);
            } else {
                stopPlaying();
            }
        });
    }

    @Override protected void onStop() {
        stopPlaying();
        super.onStop();
    }

    private boolean startPlaying() {
        releasePlayer();
        MediaPlayer player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setLooping(true);
        try (AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.clear_speaker_sound)) {
            player.setDataSource(afd);
            player.setVolume(1.0f, 1.0f);
            player.prepare();
            player.start();
            mPlayer = player;
            mStartedAt = System.currentTimeMillis();
            mHandler.removeCallbacksAndMessages(null);
            mHandler.post(mTick);
            mHandler.postDelayed(this::stopPlaying, PLAY_DURATION_MS);
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Failed to play clear-speaker sample", e);
            try { player.release(); } catch (RuntimeException ignored) { }
            mStatus.setText(R.string.parts_apply_failed);
            return false;
        }
    }

    private void stopPlaying() {
        mHandler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (mStatus != null) mStatus.setText("Ready");
        setToggleChecked(false);
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            try { mPlayer.release(); } catch (RuntimeException ignored) { }
            mPlayer = null;
        }
    }

    private void setToggleChecked(boolean checked) {
        if (mToggle == null || mToggle.isChecked() == checked) return;
        mToggle.setOnCheckedChangeListener(null);
        mToggle.setChecked(checked);
        mToggle.setOnCheckedChangeListener((button, value) -> {
            if (value) {
                if (!startPlaying()) setToggleChecked(false);
            } else {
                stopPlaying();
            }
        });
    }
}
