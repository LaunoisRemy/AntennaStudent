package de.danoeh.antennapod.core.service.playback;

import static de.danoeh.antennapod.core.service.playback.PlaybackService.TAG;
import static de.danoeh.antennapod.core.service.playback.PlaybackService.isCasting;

import android.bluetooth.BluetoothA2dp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;

import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.playback.base.PlayerStatus;

public class UnPauseClass {
    /**
     * Is true if the service was running, but paused due to headphone disconnect
     */
    private static boolean transientPause = false;

    private PlaybackService playbackService;

    public UnPauseClass(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }


    /**
     * Pauses playback when the headset is disconnected and the preference is
     * set
     */
    public final BroadcastReceiver headsetDisconnected = new BroadcastReceiver() {
        private static final String TAG = "headsetDisconnected";
        private static final int UNPLUGGED = 0;
        private static final int PLUGGED = 1;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (isInitialStickyBroadcast()) {
                // Don't pause playback after we just started, just because the receiver
                // delivers the current headset state (instead of a change)
                return;
            }

            if (TextUtils.equals(intent.getAction(), Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                Log.d(TAG, "Headset plug event. State is " + state);
                if (state != -1) {
                    if (state == UNPLUGGED) {
                        Log.d(TAG, "Headset was unplugged during playback.");
                    } else if (state == PLUGGED) {
                        Log.d(TAG, "Headset was plugged in during playback.");
                        unpauseIfPauseOnDisconnect(false);
                    }
                } else {
                    Log.e(TAG, "Received invalid ACTION_HEADSET_PLUG intent");
                }
            }
        }
    };

    public final BroadcastReceiver bluetoothStateUpdated = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1);
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    Log.d(TAG, "Received bluetooth connection intent");
                    unpauseIfPauseOnDisconnect(true);
                }
            }
        }
    };

    public final BroadcastReceiver audioBecomingNoisy = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // sound is about to change, eg. bluetooth -> speaker
            Log.d(TAG, "Pausing playback because audio is becoming noisy");
            pauseIfPauseOnDisconnect();
        }
    };

    /**
     * Pauses playback if PREF_PAUSE_ON_HEADSET_DISCONNECT was set to true.
     */
    void pauseIfPauseOnDisconnect() {
        Log.d(TAG, "pauseIfPauseOnDisconnect()");
        transientPause = (playbackService.getMediaPlayer().getPlayerStatus() == PlayerStatus.PLAYING);
        if (UserPreferences.isPauseOnHeadsetDisconnect() && !isCasting()) {
            playbackService.getMediaPlayer().pause(!UserPreferences.isPersistNotify(), false);
        }
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    void unpauseIfPauseOnDisconnect(boolean bluetooth) {
        if (playbackService.getMediaPlayer().isAudioChannelInUse()) {
            Log.d(TAG, "unpauseIfPauseOnDisconnect() audio is in use");
            return;
        }
        if (transientPause) {
            transientPause = false;
            if (!bluetooth && UserPreferences.isUnpauseOnHeadsetReconnect()) {
                playbackService.getMediaPlayer().resume();
            } else if (bluetooth && UserPreferences.isUnpauseOnBluetoothReconnect()) {
                // let the user know we've started playback again...
                Vibrator v = (Vibrator) playbackService.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(500);
                }
                playbackService.getMediaPlayer().resume();
            }
        }
    }



}
