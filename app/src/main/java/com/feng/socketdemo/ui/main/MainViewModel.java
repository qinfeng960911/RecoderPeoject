package com.feng.socketdemo.ui.main;


import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;

import com.feng.socketdemo.base.BaseViewModel;

public class MainViewModel extends BaseViewModel {

    private final MutableLiveData<String> title = new MutableLiveData<>("Hello DataBinding");
    private int clickCount = 0;

    public MainViewModel(Application application) {
        super(application);
    }

    public MutableLiveData<String> getTitle() {
        return title;
    }

    public void onButtonClick() {
        clickCount++;
        title.setValue("点击次数: " + clickCount);
        Log.e("MainActivity", "clickCount:" + clickCount);

    }

    @Override
    protected void onInit() {
        // 初始化逻辑
    }
}