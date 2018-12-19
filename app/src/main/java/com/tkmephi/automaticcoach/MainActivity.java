package com.tkmephi.automaticcoach;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private int min_cmds_period = 0; // in mseconds
    private int max_cmds_period = 2000; // in mseconds
    public static final String Broadcast_PLAY =
            "com.tkmephi.automaticcoach.Play";
    public static final String Broadcast_STOP =
            "com.tkmephi.automaticcoach.Stop";
    Button playButton;
    SeekBar speed_bar;
    SeekBar volume_bar;
    TextView timeout_val;
    TextView volume_val;
    private PlayerService player = null;
    boolean serviceBound = false;
//    Intent intent_playerService;

    boolean playback_started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playback_started = false;
        playButton = (Button) findViewById(R.id.button_start);
        speed_bar  = (SeekBar) findViewById(R.id.speed_bar);
        volume_bar = (SeekBar) findViewById(R.id.theme_volume_bar);
        timeout_val = (TextView) findViewById(R.id.timeout_value);
        update_timeout_label();
        speed_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                update_timeout_label();
                Log.i("period", "changed: " + Integer.toString(getPeriod()));
                if (player != null)
                    player.set_timeout(getPeriod());
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        volume_bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.i("theme volume", "changed: " +
                        Float.toString(Math.round(get_theme_volume())));
                if (player != null)
                    player.set_theme_volume(get_theme_volume());
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        speed_bar.setMax((max_cmds_period-min_cmds_period)/100);
    }

    //Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
            player = binder.getService();
            if (player != null)
                player.set_theme_volume(get_theme_volume());
            serviceBound = true;

//            Toast.makeText(MainActivity.this, "Service Bound", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("ServiceState", serviceBound);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("ServiceState");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            //service is active
            player.stopSelf();
        }
    }

    private void playAudio() {
        //Check is service is active
        if (!serviceBound) {
            Intent intent = new Intent(this, PlayerService.class);
//            Toast.makeText(MainActivity.this,
//                    "timeout set:"
//                            + Integer.toString(getPeriod()) + "ms", Toast.LENGTH_SHORT).show();
            intent.putExtra("timeout", getPeriod());
            startService(intent);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            Intent broadcastIntent =
                    new Intent(Broadcast_PLAY);
            sendBroadcast(broadcastIntent);
        }
    }

    public void start_clicked(View view) {
        if (playback_started) {
            Intent broadcastIntent =
                    new Intent(Broadcast_STOP);
            sendBroadcast(broadcastIntent);
            playButton.setText(R.string.button_start_text);
        } else {
            playAudio();
            playButton.setText(R.string.button_stop_text);
        }
        playback_started = !playback_started;
    }

    protected int getPeriod (){
        return ( Math.round(
                ((max_cmds_period-min_cmds_period)/speed_bar.getMax() * (speed_bar.getMax()-speed_bar.getProgress()))
                    + min_cmds_period )
        );
    }
    protected float get_theme_volume(){
        return ( (float) (1 - (Math.log(volume_bar.getMax() - volume_bar.getProgress()) /
                Math.log(volume_bar.getMax()))));
    }

    private void update_timeout_label(){
        timeout_val.setText(Double.toString((getPeriod() + PlayerService.cmd_duration)/1000.0  ) + " sec");
    }

}
