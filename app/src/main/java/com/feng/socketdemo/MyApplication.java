package com.feng.socketdemo;

import android.app.Application;
import android.content.Context;

import com.feng.socketdemo.utils.LanguageUtils;

public class MyApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        // 初始化语言设置
        Context context = LanguageUtils.updateResources(base, LanguageUtils.getAppLanguage(base));
        super.attachBaseContext(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 应用语言设置
        LanguageUtils.applyLanguage(this);
    }
}
