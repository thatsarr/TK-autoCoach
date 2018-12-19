package com.tkmephi.automaticcoach;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Random;

public class PlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,

        AudioManager.OnAudioFocusChangeListener {

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private AudioManager audioManager;

    //mediaplayer stuff
    private MediaPlayer mediaPlayer;

    // soundpool stuff
    private SoundPool soundPool;
    boolean soundPool_allowed;
    int cur_stream;
    Context context = this;
    int samples_loaded_count;
    boolean samples_loaded;
    float commands_volume_coeff = 1.0f;
    float theme_volume_coeff = 0.8f;

    // logic-needed stuff
    private Handler timeout_handler;
    private Runnable runnable;
    private Random randomizer = new Random();

    private int cmds_timeout;
    private int cmds_number = 6;
    static public int cmd_duration = 1000; // in ms
    private int[] cmds_array;
//    private String theme_name = "theme.mp3";

    @Override
    public void onCreate() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        this creation might be an error on android 5 and 6
        soundPool = new SoundPool(6, AudioManager.STREAM_MUSIC, 100);
        timeout_handler = new Handler();
        runnable = new Runnable() {
            int cmd = cmds_number-1;
            public void run() {
                Log.i("commands", "index: " + Integer.toString(cmd));
                if (samples_loaded & soundPool_allowed) {
                    playSound(cmds_array[cmd], 1.0f);
                    cur_stream = cmd;
                    ++cmd;
                    if (cmd == cmds_number) {
                        commands_blend();
                        cmd = 0;
                    }
                    timeout_handler.postDelayed(this, cmds_timeout + cmd_duration);

                }
            }
        };
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool soundPool, int sampleId,int status) {
                samples_loaded_count += 1;
                if (samples_loaded_count == cmds_number)
                    samples_loaded = true;
                Log.i("samples", "loaded: " + Integer.toString(sampleId));

            }
        });
        register_playAudio();
        register_stopAudio();
        cmds_init();
    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        cmds_timeout = intent.getIntExtra("timeout", 1500);

        if (!requestAudioFocus()) {
            //Could not gain focus
            stopSelf();
        }
        initMediaPlayer();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer == null) initMediaPlayer();
                else if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        //Invoked when playback of a media source has completed.
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        //Invoked when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //Invoked to communicate some info.
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        //Invoked when the media source is ready for playback.
        playMedia();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        //Invoked indicating the completion of a seek operation.
    }

    public class LocalBinder extends Binder {
        PlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PlayerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (soundPool != null) {
            soundPool.stop(cur_stream);
            soundPool.release();
            soundPool = null;
        }
        removeAudioFocus();
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        float streamVolumeCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float streamVolumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volume = streamVolumeCurrent*theme_volume_coeff / streamVolumeMax;
        Log.i("mediaplayer", "curVolume" + Float.toString(volume));
        mediaPlayer.setVolume(volume, volume);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(
                    getApplicationContext(),
                    Uri.parse("android.resource://com.tkmephi.automaticcoach/" + R.raw.theme)
            );
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
            Log.d("event", "playMedia()");
            play_random_commands();
            soundPool_allowed = true;

        }
    }

    private void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        if (soundPool == null) return;
        soundPool_allowed = false;
    }

    private boolean requestAudioFocus() {
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus gained
            return true;
        }
        //Could not gain focus
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }

    public void playSound(int soundID, float fSpeed) {
        float streamVolumeCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float streamVolumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volume = streamVolumeCurrent*commands_volume_coeff / streamVolumeMax;
        Log.i("soundPool", "curVolume" + Float.toString(volume));
        soundPool.play(soundID, volume, volume, 1, 0, fSpeed);
    }

    private BroadcastReceiver playAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            playMedia();
            Log.i("media", "started");
        }
    };

    private void register_playAudio() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_PLAY);
        registerReceiver(playAudio, filter);
    }

    private BroadcastReceiver stopAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopMedia();
            Log.i("media", "stopped");
        }
    };

    private void register_stopAudio() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_STOP);
        registerReceiver(stopAudio, filter);
    }

    public void cmds_init(){
        // init comands array with all files in R.raw except "theme_name"
        cmds_array = new int[cmds_number];
        samples_loaded_count = 0;
        samples_loaded = false;
        cmds_array[0] = soundPool.load( context,R.raw.cmd_carabine,1);
        cmds_array[1] = soundPool.load( context,R.raw.cmd_catalka,1);
        cmds_array[2] = soundPool.load( context,R.raw.cmd_eight,1);
        cmds_array[3] = soundPool.load( context,R.raw.cmd_jumar,1);
        cmds_array[4] = soundPool.load( context,R.raw.cmd_left,1);
        cmds_array[5] = soundPool.load( context,R.raw.cmd_right,1);
//        Field[] fields=R.raw.class.getFields();
//        int cmds_cnt = 0;
//        for (Field field : fields) {
//            if (!field.getName().equals(theme_name)) {
//                try {
//                    cmds_array[cmds_cnt] = field.getInt(field);
//                } catch (IllegalAccessException e) {
//                    Log.e("Error", "Get int from Fields exception");
//                }
//            }
//            Log.i("Raw Asset: ", field.getName());
//        }
    }


    public void commands_blend(){
        int index;
        int tmp;
        Log.i("commands", "blending");
        for (int i = 0; i < cmds_number; ++i) {
            index = randomizer.nextInt(cmds_number);
            Log.i ("swapping", " " + Integer.toString(i) + " and " + Integer.toString(index));
            tmp = cmds_array[index];
            cmds_array[index] = cmds_array[i];
            cmds_array[i] = tmp;
        }

    }

    public void play_random_commands(){
        timeout_handler.postDelayed(runnable,cmds_timeout + cmd_duration);
    }

    public void set_timeout(int new_timeout){
        cmds_timeout = new_timeout;
    }
    public void set_theme_volume(float new_volume) {
        theme_volume_coeff = new_volume;
        Log.i("mediaplayer", "curVolume" + Float.toString(new_volume));
        mediaPlayer.setVolume(new_volume, new_volume);
    }
}