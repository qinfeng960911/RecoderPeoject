package com.feng.socketdemo.ui.main;

import android.util.Log;

import com.feng.socketdemo.R;
import com.feng.socketdemo.base.BaseBindingActivity;
import com.feng.socketdemo.databinding.ActivityMainBinding;

public class MainActivity extends BaseBindingActivity<ActivityMainBinding, MainViewModel> {

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void initView() {
        // 可以在这里获取View引用
        // 但通过DataBinding，通常不需要

        binding.button.setOnClickListener(v -> {
            viewModel.getTitle().setValue("点击次数: " );
        });
    }

    @Override
    protected void initData() {
        // 初始化数据
    }

    @Override
    protected void initObserver() {
        // 观察ViewModel的变化
        Log.e("MainActivity", "viewModel:" + viewModel);

        if (viewModel != null) {
            // 观察加载状态
            viewModel.getLoadingState().observe(this, isLoading -> {
                onLoadingStateChanged(isLoading);
            });

            // 观察错误状态
            viewModel.getErrorState().observe(this, error -> {
                onErrorStateChanged(error);
            });

            // 观察标题变化
            viewModel.getTitle().observe(this, title -> {
                // 可以通过binding直接访问View
                binding.title.setText(title);
            });
        }
    }

    @Override
    protected void onLoadingStateChanged(boolean isLoading) {
        if (isLoading) {
            // 显示加载
        } else {
            // 隐藏加载
        }
    }
}
