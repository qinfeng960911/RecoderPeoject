package com.feng.socketdemo.base;


import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.feng.socketdemo.R;
import com.feng.socketdemo.utils.LanguageUtils;

import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;

/**
 * 修正后的 BaseActivity
 * 解决了 getVariableId() 方法不存在的问题
 */
public abstract class BaseActivity<B extends ViewDataBinding, V extends BaseViewModel>
        extends AppCompatActivity implements LifecycleOwner {

    public B binding;
    public V viewModel;

    private ViewModelProvider viewModelProvider;
    private final Map<Class<? extends ViewModel>, ViewModel> viewModelMap = new HashMap<>();
    public boolean isDestroyed = false;

    @LayoutRes
    protected abstract int getLayoutId();

    // 抽象方法，子类必须实现
    protected abstract void initView();

    protected abstract void initData();

    protected abstract void initObserver();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 在super.onCreate之前设置语言
        LanguageUtils.applyLanguage(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        super.onCreate(savedInstanceState);

        // 隐藏标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // 设置状态栏样式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        }

        // 初始化DataBinding
        initDataBinding();

        // 初始化ViewModel
        initViewModel();

        // 调用初始化方法
        initView();
        initData();
        initObserver();

        // 初始化ViewModel
        if (viewModel != null) {
            viewModel.initViewModel();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // 在attachBaseContext时设置语言
        Context context = LanguageUtils.updateResources(newBase, LanguageUtils.getAppLanguage(newBase));
        super.attachBaseContext(context);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 处理配置变化
        LanguageUtils.applyLanguage(this);
        // 阻止屏幕旋转到其他方向，保持横屏
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); // 或者根据需要选择其他行为，如提醒用户或保持横屏等。
        }
    }



    /**
     * 初始化DataBinding
     */
    private void initDataBinding() {
        int layoutId = getLayoutId();
        if (layoutId <= 0) {
            throw new IllegalArgumentException("布局ID必须大于0");
        }

        binding = DataBindingUtil.inflate(
                LayoutInflater.from(this),
                layoutId,
                null,
                false
        );

        setContentView(binding.getRoot());
        binding.setLifecycleOwner(this);
    }

    /**
     * 初始化ViewModel
     */
    private void initViewModel() {
        viewModelProvider = new ViewModelProvider(this);
        Class<V> viewModelClass = getViewModelClass();
        if (viewModelClass != null) {
            viewModel = viewModelProvider.get(viewModelClass);
            viewModelMap.put(viewModelClass, viewModel);
        }
    }

    /**
     * 获取ViewModel的Class对象
     */
    @SuppressWarnings("unchecked")
    protected Class<V> getViewModelClass() {
        try {
            // 获取泛型参数类型
            java.lang.reflect.Type type = getClass().getGenericSuperclass();

            if (type instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.ParameterizedType paramType = (java.lang.reflect.ParameterizedType) type;
                java.lang.reflect.Type[] typeArguments = paramType.getActualTypeArguments();

                if (typeArguments.length > 1) {
                    return (Class<V>) typeArguments[1];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        throw new IllegalStateException("请在泛型参数中指定 ViewModel 类");
    }

    public V getViewModel() {
        if (viewModel == null) {
            Class<V> modelclass = (Class<V>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
            viewModel = (V) new ViewModelProvider(this).get(modelclass);
            return viewModel;
        }

        return viewModel;
    }


    // 以下是具体实现示例
    protected void setupStatusBar() {
        // 状态栏设置
    }


    protected void startActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }

    protected void startActivity(Class<?> cls, Bundle bundle) {
        Intent intent = new Intent(this, cls);
        if (bundle != null) {
            intent.putExtras(bundle);
        }
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;

        if (binding != null) {
            binding.unbind();
            binding = null;
        }

        viewModelMap.clear();
        viewModel = null;
        viewModelProvider = null;
    }

}