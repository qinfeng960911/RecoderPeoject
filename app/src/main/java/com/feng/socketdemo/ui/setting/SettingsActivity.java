package com.feng.socketdemo.ui.setting;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.feng.socketdemo.R;

public class SettingsActivity extends AppCompatActivity {

    // 循环录制相关按钮
    private Button btn1Min, btn3Min, btn5Min;

    // 视频分辨率相关按钮
    private Button btn1440P, btn1080P, btn720P;

    // 灵敏度相关按钮
    private Button btnOff, btnLow, btnMedium, btnHigh;

    // 开关和格式化按钮
    private Switch switchParkingGuard;
    private Button btnFormat;
    private TextView tvSdCardSpace;

    // 存储当前设置状态
    private int currentLoopTime = 3; // 3分钟
    private int currentResolution = 1440; // 1440P
    private String currentSensitivity = "中"; // 中
    private boolean isParkingGuardEnabled = true;

    // SD卡信息
    private String sdCardSpace = "214.63M";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化视图
        initViews();

        // 设置点击监听器
        setupClickListeners();

        // 设置初始状态
        updateUI();
    }

    private void initViews() {
        // 返回按钮
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        // 循环录制按钮
        btn1Min = findViewById(R.id.btn_1min);
        btn3Min = findViewById(R.id.btn_3min);
        btn5Min = findViewById(R.id.btn_5min);

        // 分辨率按钮
        btn1440P = findViewById(R.id.btn_1440p);
        btn1080P = findViewById(R.id.btn_1080p);
        btn720P = findViewById(R.id.btn_720p);

        // 灵敏度按钮
        btnOff = findViewById(R.id.btn_off);
        btnLow = findViewById(R.id.btn_low);
        btnMedium = findViewById(R.id.btn_medium);
        btnHigh = findViewById(R.id.btn_high);

        // 开关和格式化按钮
        switchParkingGuard = findViewById(R.id.switch_parking_guard);
        btnFormat = findViewById(R.id.btn_format);
        tvSdCardSpace = findViewById(R.id.tv_sd_card_space);
    }

    private void setupClickListeners() {
        // 循环录制按钮点击监听
        btn1Min.setOnClickListener(v -> setLoopTime(1));
        btn3Min.setOnClickListener(v -> setLoopTime(3));
        btn5Min.setOnClickListener(v -> setLoopTime(5));

        // 分辨率按钮点击监听
        btn1440P.setOnClickListener(v -> setResolution(1440));
        btn1080P.setOnClickListener(v -> setResolution(1080));
        btn720P.setOnClickListener(v -> setResolution(720));

        // 灵敏度按钮点击监听
        btnOff.setOnClickListener(v -> setSensitivity("关闭"));
        btnLow.setOnClickListener(v -> setSensitivity("低"));
        btnMedium.setOnClickListener(v -> setSensitivity("中"));
        btnHigh.setOnClickListener(v -> setSensitivity("高"));

        // 停车守卫开关监听
        switchParkingGuard.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isParkingGuardEnabled = isChecked;
            Toast.makeText(SettingsActivity.this,
                    "停车守卫: " + (isChecked ? "开启" : "关闭"),
                    Toast.LENGTH_SHORT).show();
        });

        // 格式化按钮点击监听
        btnFormat.setOnClickListener(v -> {
            // 显示确认对话框
            new android.app.AlertDialog.Builder(this)
                    .setTitle("格式化SD卡")
                    .setMessage("确定要格式化SD卡吗？所有数据将会被清除。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        formatSDCard();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void setLoopTime(int minutes) {
        if (currentLoopTime == minutes) return;

        currentLoopTime = minutes;
        updateLoopTimeButtons();

        Toast.makeText(this, "循环录制时间设置为: " + minutes + "分钟",
                Toast.LENGTH_SHORT).show();
    }

    private void setResolution(int resolution) {
        if (currentResolution == resolution) return;

        currentResolution = resolution;
        updateResolutionButtons();

        String resolutionText = resolution + (resolution == 720 ? "P" : "P");
        Toast.makeText(this, "视频分辨率设置为: " + resolutionText,
                Toast.LENGTH_SHORT).show();
    }

    private void setSensitivity(String sensitivity) {
        if (currentSensitivity.equals(sensitivity)) return;

        currentSensitivity = sensitivity;
        updateSensitivityButtons();

        Toast.makeText(this, "紧急录像灵敏度设置为: " + sensitivity,
                Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        updateLoopTimeButtons();
        updateResolutionButtons();
        updateSensitivityButtons();

        // 设置开关状态
        switchParkingGuard.setChecked(isParkingGuardEnabled);
    }

    private void updateLoopTimeButtons() {
        // 重置所有按钮选中状态
        setButtonSelected(btn1Min, currentLoopTime == 1);
        setButtonSelected(btn3Min, currentLoopTime == 3);
        setButtonSelected(btn5Min, currentLoopTime == 5);
    }

    private void updateResolutionButtons() {
        // 重置所有按钮选中状态
        setButtonSelected(btn1440P, currentResolution == 1440);
        setButtonSelected(btn1080P, currentResolution == 1080);
        setButtonSelected(btn720P, currentResolution == 720);
    }

    private void updateSensitivityButtons() {
        // 重置所有按钮选中状态
        setButtonSelected(btnOff, "关闭".equals(currentSensitivity));
        setButtonSelected(btnLow, "低".equals(currentSensitivity));
        setButtonSelected(btnMedium, "中".equals(currentSensitivity));
        setButtonSelected(btnHigh, "高".equals(currentSensitivity));
    }

    private void setButtonSelected(Button button, boolean selected) {
        button.setSelected(selected);
        if (selected) {
            // 选中状态
            button.setBackgroundResource(R.drawable.btn_filled_selector);
            button.setTextColor(Color.WHITE);
        } else {
            // 未选中状态
            button.setBackgroundResource(R.drawable.btn_outline_selector);
            button.setTextColor(Color.WHITE);
        }
    }

    private void formatSDCard() {
        // 模拟格式化操作
        Toast.makeText(this, "正在格式化SD卡...", Toast.LENGTH_SHORT).show();

        // 模拟格式化完成后的回调
        new android.os.Handler().postDelayed(() -> {
            sdCardSpace = "0.00M"; // 格式化后容量为0
            tvSdCardSpace.setText("剩余容量" + sdCardSpace);
            Toast.makeText(SettingsActivity.this,
                    "SD卡格式化完成", Toast.LENGTH_SHORT).show();
        }, 2000);
    }

    // 保存设置到SharedPreferences
    private void saveSettings() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("DashCamSettings", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        editor.putInt("loop_time", currentLoopTime);
        editor.putInt("resolution", currentResolution);
        editor.putString("sensitivity", currentSensitivity);
        editor.putBoolean("parking_guard", isParkingGuardEnabled);

        editor.apply();
    }

    // 从SharedPreferences加载设置
    private void loadSettings() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("DashCamSettings", MODE_PRIVATE);

        currentLoopTime = prefs.getInt("loop_time", 3);
        currentResolution = prefs.getInt("resolution", 1440);
        currentSensitivity = prefs.getString("sensitivity", "中");
        isParkingGuardEnabled = prefs.getBoolean("parking_guard", true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettings();
        updateUI();
    }
}