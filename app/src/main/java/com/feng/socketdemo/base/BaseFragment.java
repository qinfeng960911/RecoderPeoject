package com.feng.socketdemo.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;

/**
 * DataBinding Fragment基类
 */
public abstract class BaseFragment<B extends ViewDataBinding, V extends BaseViewModel>
        extends Fragment implements LifecycleOwner {

    protected B binding;
    protected V viewModel;

    private ViewModelProvider viewModelProvider;
    private final Map<Class<? extends ViewModel>, ViewModel> viewModelMap = new HashMap<>();

    @LayoutRes
    protected abstract int getLayoutId();

    @SuppressWarnings("unchecked")
    protected Class<V> getViewModelClass() {
        try {
            ParameterizedType type = (ParameterizedType) getClass().getGenericSuperclass();
            return (Class<V>) type.getActualTypeArguments()[1];
        } catch (Exception e) {
            throw new IllegalStateException("Please specify ViewModel class", e);
        }
    }

    protected String getViewModelVariableName() {
        return "vm";
    }

    protected boolean enableDataBinding() {
        return true;
    }

    protected boolean enableViewModelInit() {
        return true;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        int layoutId = getLayoutId();
        if (layoutId <= 0) {
            throw new IllegalArgumentException("Layout ID must be greater than 0");
        }

        binding = DataBindingUtil.inflate(inflater, layoutId, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 设置生命周期所有者
        if (binding != null) {
            binding.setLifecycleOwner(getViewLifecycleOwner());
        }

        // 初始化ViewModel
        initViewModel();

        // 初始化
        initView();
        initData();
        initObserver();

        if (viewModel != null && enableViewModelInit()) {
            viewModel.initViewModel();
        }
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
     * 获取ViewModel的变量ID
     * 子类可以重写此方法来返回特定的变量ID
     */
    protected int getViewModelVariableId() {
        return 0; // 0 表示没有设置
    }

    /**
     * 从BR类获取变量ID
     */
    private int getVariableIdFromBR(String variableName) {
        try {
            // 获取当前包名下的BR类
            String packageName = requireActivity().getPackageName();
            String className = packageName + ".BR";

            Class<?> brClass = Class.forName(className);
            java.lang.reflect.Field field = brClass.getField(variableName);
            return field.getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }


    @SuppressWarnings("unchecked")
    protected <T extends ViewModel> T getViewModel(@NonNull Class<T> modelClass) {
        if (viewModelMap.containsKey(modelClass)) {
            return (T) viewModelMap.get(modelClass);
        }

        T vm = viewModelProvider.get(modelClass);
        viewModelMap.put(modelClass, vm);
        return vm;
    }

    protected void setBindingVariable(int variableId, Object value) {
        if (binding != null) {
            binding.setVariable(variableId, value);
        }
    }

    protected void initView() {
        // 子类重写
    }

    protected void initData() {
        // 子类重写
    }

    protected void initObserver() {
        if (viewModel != null) {
            viewModel.getLoadingState().observe(getViewLifecycleOwner(), isLoading -> {
                if (isLoading != null) {
                    onLoadingStateChanged(isLoading);
                }
            });

            viewModel.getErrorState().observe(getViewLifecycleOwner(), error -> {
                if (error != null) {
                    onErrorStateChanged(error);
                }
            });
        }
    }

    protected void onLoadingStateChanged(boolean isLoading) {
        // 子类重写
    }

    protected void onErrorStateChanged(String error) {
        if (error != null && !error.trim().isEmpty() && getContext() != null) {
            Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
        }
    }

    protected void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (binding != null) {
            binding.unbind();
            binding = null;
        }

        viewModelMap.clear();
        viewModel = null;
        viewModelProvider = null;
    }
}
