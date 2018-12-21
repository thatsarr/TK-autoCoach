package com.tkmephi.automaticcoach;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private int min_cmds_period = 0; // in mseconds
    private int max_cmds_period = 2000; // in mseconds
    public static final String Broadcast_PLAY =
            "com.tkmephi.automaticcoach.Play";
    public static final String Broadcast_STOP =
            "com.tkmephi.automaticcoach.Stop";
    public static final String theme_filename_label_const =
            "Theme file: ";
    private Uri themeFile = null;
    Button playButton;
    Button resetButton;
    SeekBar speed_bar;
    SeekBar volume_bar;
    TextView timeout_val;
    TextView theme_filename_label;
    private PlayerService player = null;
    boolean serviceBound = false;
    boolean playback_started;

    AlertDialog.Builder builder;

    SpannableString about_message_gen(){
        final SpannableString s = new SpannableString(
                getResources().getString(R.string.new_in_version)
        );
        Linkify.addLinks(s, Linkify.ALL);
        return (s);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playback_started = false;
        playButton = (Button) findViewById(R.id.button_start);
        resetButton = (Button) findViewById(R.id.button_reset);
        resetButton.setVisibility(View.INVISIBLE);
        speed_bar  = (SeekBar) findViewById(R.id.speed_bar);
        speed_bar.setMax((max_cmds_period-min_cmds_period)/100);
        speed_bar.setProgress(speed_bar.getMax()/2);
        volume_bar = (SeekBar) findViewById(R.id.theme_volume_bar);
        timeout_val = (TextView) findViewById(R.id.timeout_value);
        theme_filename_label = (TextView) findViewById(R.id.theme_file_label);
        update_timeout_label();

        themeFile = resourceToUri(this, R.raw.theme);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
        } else {
            builder = new AlertDialog.Builder(this);
        }
        builder .setTitle("Info")
                .setMessage(about_message_gen())
                .setNeutralButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                }
                );

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
        Log.d("event", "MainActivity.playAudio()");
        //Check is service is active
        if (!serviceBound) {
            Log.d("event", "MainActivity.playAudio() !serviceBound branch");
            Intent intent = new Intent(this, PlayerService.class);
            intent.putExtra("timeout", getPeriod());
            intent.putExtra("theme_uri", themeFile.toString());
            startService(intent);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            Log.d("event", "MainActivity.playAudio() serviceBound aka else branch");
            Intent broadcastIntent =
                    new Intent(Broadcast_PLAY);
            sendBroadcast(broadcastIntent);
        }
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

    public void start_clicked(View view) {
        Log.d ("event", "start_clicked()");
        if (playback_started) {
            Intent broadcastIntent =
                    new Intent(Broadcast_STOP);
            Log.d("event", "sendBroadcast(STOP)");
            sendBroadcast(broadcastIntent);
            playButton.setText(R.string.button_start_text);
        } else {
            playAudio();
            playButton.setText(R.string.button_stop_text);
        }
        playback_started = !playback_started;
    }

    public void change_theme_music_dialog(View view) {
        Intent theme_file_intent = new Intent()
                .setType("audio/*")
                .setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(theme_file_intent, "Select a file"), 123);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==123) {
            if (resultCode==RESULT_OK) {
                themeFile = data.getData(); //The uri with the location of the file
                change_theme_music(themeFile);
                resetButton.setVisibility(View.VISIBLE);
            } else if (resultCode==RESULT_CANCELED) {
                Toast.makeText(this, "Audio file open canceled", Toast.LENGTH_SHORT).show();
            } else {
                Log.e("AudioDialog", "File open failed");
            }

        }

    }

    protected void change_theme_music (Uri themeFile){
        if (serviceBound) {
            player.change_theme(themeFile);
        }
        theme_filename_label.setText(theme_filename_label_const.concat(getFileName(themeFile)));
        playButton.setText(R.string.button_start_text);
        playback_started = false;
        Toast.makeText(this, "Theme changed", Toast.LENGTH_SHORT).show();
    }

    public void on_theme_reset_click(View view) {
        change_theme_music(resourceToUri(this, R.raw.theme));
        resetButton.setVisibility(View.INVISIBLE);
        theme_filename_label.setText(R.string.theme_file_default_label);

    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public void show_app_info(View view) {
        Dialog dialog = builder.create();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.show();
        TextView about_text = (TextView)dialog.findViewById(android.R.id.message);
        about_text.setMovementMethod(LinkMovementMethod.getInstance());
        about_text.setTextSize(14);
        Toast.makeText(
                this,
                "Version: ".concat(getResources().getString(R.string.app_version)),
                Toast.LENGTH_LONG
        ).show();
    }

    public static Uri resourceToUri(Context context, int resID) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                context.getResources().getResourcePackageName(resID) + '/' +
                context.getResources().getResourceTypeName(resID) + '/' +
                context.getResources().getResourceEntryName(resID) );
    }
}
