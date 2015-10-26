package com.peak.salut;

import android.app.Activity;
import android.content.Context;

import com.peak.salut.Callbacks.SalutDataCallback;

/**
 * Created by markrjr on 6/3/15.
 */
public class SalutDataReceiver {

    protected SalutDataCallback dataCallback;
    protected Context context;
    protected Activity activity;

    public SalutDataReceiver(Activity activity, SalutDataCallback dataCallback)
    {
        this.dataCallback = dataCallback;
        this.context = activity.getApplicationContext();
        this.activity = activity;
    }

}
