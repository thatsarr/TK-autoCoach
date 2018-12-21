package com.tkmephi.automaticcoach;

import android.app.Application;
import android.os.Environment;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
        mailTo = "dimon1997@list.ru",
        mode = ReportingInteractionMode.TOAST,
        resToastText = R.string.crash_toast_text
)
public class AutoCoach extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        ACRA.init(this);
    }
}
