package com.peak.salut;

import android.app.Activity;

import com.peak.salut.Callbacks.SalutDataCallback;

/**
 * Created by markrjr on 6/3/15.
 */
public class SalutDataReceiver {

    protected SalutDataCallback dataCallback;
    protected Activity currentContext;

    public SalutDataReceiver(Activity activity, SalutDataCallback dataCallback)
    {
        this.dataCallback = dataCallback;
        this.currentContext = activity;
    }

}
