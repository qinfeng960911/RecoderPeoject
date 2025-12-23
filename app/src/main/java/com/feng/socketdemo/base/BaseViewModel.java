package com.feng.socketdemo.base;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基础ViewModel
 * 提供通用的状态管理和生命周期控制
 */
public abstract class BaseViewModel extends AndroidViewModel {

    // 加载状态
    protected final MutableLiveData<Boolean> loadingState = new MutableLiveData<>(false);

    // 错误状态
    protected final MutableLiveData<String> errorState = new MutableLiveData<>();

    // 是否已初始化
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public BaseViewModel(@NonNull Application application) {
        super(application);
    }

    /**
     * 初始化ViewModel
     * 只会被调用一次
     */
    protected void initViewModel() {
        if (isInitialized.compareAndSet(false, true)) {
            onInit();
        }
    }

    /**
     * ViewModel初始化回调
     */
    protected void onInit() {
        // 子类可以重写
    }

    /**
     * 显示加载状态
     */
    public void showLoading() {
        loadingState.setValue(true);
    }

    /**
     * 隐藏加载状态
     */
    public void hideLoading() {
        loadingState.setValue(false);
    }

    /**
     * 显示错误
     */
    public void showError(String error) {
        errorState.setValue(error);
    }

    /**
     * 清除错误
     */
    public void clearError() {
        errorState.setValue(null);
    }

    /**
     * 获取加载状态
     */
    public LiveData<Boolean> getLoadingState() {
        return loadingState;
    }

    /**
     * 获取错误状态
     */
    public LiveData<String> getErrorState() {
        return errorState;
    }

    /**
     * ViewModel被销毁时调用
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        // 清理资源
    }
}
