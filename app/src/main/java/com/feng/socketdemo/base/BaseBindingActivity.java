package com.feng.socketdemo.base;


import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.feng.socketdemo.R;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 修正后的 BaseBindingActivity
 * 解决了 getVariableId() 方法不存在的问题
 */
public abstract class BaseBindingActivity<B extends ViewDataBinding, V extends BaseViewModel>
        extends AppCompatActivity implements LifecycleOwner {

    protected B binding;
    protected V viewModel;

    private ViewModelProvider viewModelProvider;
    private final Map<Class<? extends ViewModel>, ViewModel> viewModelMap = new HashMap<>();
    private boolean isDestroyed = false;

    @LayoutRes
    protected abstract int getLayoutId();

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

    /**
     * 获取ViewModel在布局中的变量名
     * 默认是 "viewModel"
     */
    protected String getViewModelVariableName() {
        return "viewModel";
    }

    /**
     * 获取ViewModel的变量ID
     * 子类可以重写此方法来返回特定的变量ID
     */
    protected int getViewModelVariableId() {
        return 0; // 0 表示没有设置
    }

    /**
     * 通过反射获取变量的ID
     */
    protected int getVariableId(String variableName) {
        if (binding == null || variableName == null || variableName.isEmpty()) {
            return 0;
        }

        try {
            // 通过反射调用生成的 getVariableId 方法
            String methodName = "get" + capitalize(variableName) + "VariableId";
            Method method = binding.getClass().getMethod(methodName);
            Object result = method.invoke(binding);

            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception e) {
            // 方法不存在，返回0
        }

        return 0;
    }

    /**
     * 字符串首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
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

            // 绑定ViewModel到DataBinding
            bindViewModelToDataBinding();
        }
    }

    /**
     * 将ViewModel绑定到DataBinding
     */
    private void bindViewModelToDataBinding() {
        if (binding == null || viewModel == null) {
            return;
        }

        // 方法1：先尝试通过重写的 getViewModelVariableId() 获取
        int variableId = getViewModelVariableId();

        // 方法2：如果方法1返回0，尝试通过变量名获取
        if (variableId == 0) {
            String variableName = getViewModelVariableName();
            variableId = getVariableId(variableName);
        }

        // 方法3：如果前两种方法都失败，尝试通过BR类获取
        if (variableId == 0) {
            variableId = getVariableIdFromBR(getViewModelVariableName());
        }

        // 设置变量
        if (variableId != 0) {
            binding.setVariable(variableId, viewModel);
        } else {
            // 如果还是找不到，可以尝试通过反射设置默认的变量
            trySetDefaultVariables();
        }
    }

    /**
     * 从BR类获取变量ID
     */
    private int getVariableIdFromBR(String variableName) {
        try {
            // 获取当前包名下的BR类
            String packageName = getPackageName();
            String className = packageName + ".BR";

            Class<?> brClass = Class.forName(className);
            java.lang.reflect.Field field = brClass.getField(variableName);
            return field.getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 尝试设置默认的变量
     */
    private void trySetDefaultVariables() {
        // 尝试一些常见的变量名
        String[] commonVariableNames = {
                "viewModel", "vm", "model", "data", "state"
        };

        for (String varName : commonVariableNames) {
            int varId = getVariableIdFromBR(varName);
            if (varId != 0) {
                binding.setVariable(varId, viewModel);
                return;
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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

    /**
     * 获取指定类型的ViewModel（支持多ViewModel）
     */
    @SuppressWarnings("unchecked")
    protected <T extends ViewModel> T getViewModel(@NonNull Class<T> modelClass) {
        if (viewModelMap.containsKey(modelClass)) {
            return (T) viewModelMap.get(modelClass);
        }

        T vm = viewModelProvider.get(modelClass);
        viewModelMap.put(modelClass, vm);
        return vm;
    }

    /**
     * 设置DataBinding变量
     */
    protected void setBindingVariable(String variableName, Object value) {
        if (binding == null) {
            return;
        }

        int variableId = getVariableId(variableName);
        if (variableId == 0) {
            variableId = getVariableIdFromBR(variableName);
        }

        if (variableId != 0) {
            binding.setVariable(variableId, value);
        }
    }

    /**
     * 设置DataBinding变量（通过变量ID）
     */
    protected void setBindingVariable(int variableId, Object value) {
        if (binding != null && variableId != 0) {
            binding.setVariable(variableId, value);
        }
    }

    // 抽象方法，子类必须实现
    protected abstract void initView();

    protected abstract void initData();

    protected abstract void initObserver();

    // 以下是具体实现示例
    protected void setupStatusBar() {
        // 状态栏设置
    }

    protected void onLoadingStateChanged(boolean isLoading) {
        // 加载状态变化处理
    }

    protected void onErrorStateChanged(String error) {
        if (error != null && !error.trim().isEmpty()) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        }
    }

    protected void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

    protected void startActivityAndFinish(Class<?> cls) {
        startActivity(cls);
        finish();
    }

    public void finishActivity() {
        finish();
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

    public boolean isActivityDestroyed() {
        return isDestroyed;
    }

    public Lifecycle.State getCurrentLifecycleState() {
        return getLifecycle().getCurrentState();
    }

    public boolean isAtLeastState(Lifecycle.State state) {
        return getCurrentLifecycleState().isAtLeast(state);
    }

    public boolean isActive() {
        return isAtLeastState(Lifecycle.State.STARTED);
    }

    public B getBinding() {
        return binding;
    }

    public V getViewModel() {
        return viewModel;
    }
}