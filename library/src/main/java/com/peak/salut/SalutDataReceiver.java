package com.peak.salut;

import android.content.Context;

import com.peak.salut.Callbacks.SalutDataCallback;

public class SalutDataReceiver {

    protected SalutDataCallback dataCallback;
    protected Context context;

    public SalutDataReceiver(Context applicationContext, SalutDataCallback dataCallback) {
        this.dataCallback = dataCallback;
        this.context = applicationContext;
    }
}
