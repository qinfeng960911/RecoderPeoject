package com.feng.socketdemo.ui.main;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.feng.socketdemo.R;
import com.feng.socketdemo.base.BaseActivity;
import com.feng.socketdemo.config.DeviceCmd;
import com.feng.socketdemo.data.VideoSource;
import com.feng.socketdemo.databinding.ActivityTcpSocketBinding;
import com.feng.socketdemo.service.SocketTcpClient;
import com.feng.socketdemo.tools.TcpSocketClient;
import com.feng.socketdemo.utils.NumberUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TcpSocketActivity extends BaseActivity<ActivityTcpSocketBinding, TcpSocketModel> {

    private static final String TAG = "TcpSocketActivity";

    private static final int REQUEST_PLAY = 1000;

    private TcpSocketClient tcpClient;

    // 消息编号计数器，每发送一条消息递增
    private int messageCode = 0;

    private SocketTcpClient client;

    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected int getLayoutId() {
        return R.layout.activity_tcp_socket;
    }

    @Override
    protected void initView() {

        initViews();
        initTcpClient();
        updateUI();
    }

    @Override
    protected void initData() {

    }

    @Override
    protected void initObserver() {

    }


    private void initSocketTcpClient() {

        // 获取客户端实例
        client = SocketTcpClient.getInstance();

//         设置事件监听器
        client.setEventListener(new SocketTcpClient.SocketEventListener() {
            @Override
            public void onConnect() {
                Log.w(TAG, "连接成功");
                // 连接成功后发送测试消息
            }

            @Override
            public void onDisconnect() {
                Log.w(TAG, "连接断开");
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "发生错误: " + error);
            }

            @Override
            public void onMessageReceived(String message) {
                Log.w(TAG, "收到消息: " + message);
            }

            @Override
            public void onHexMessageReceived(String hexMessage) {
                Log.w(TAG, "收到十六进制消息: " + hexMessage);
            }

            @Override
            public void onMessageSent(String message) {
                Log.w(TAG, "消息已发送: " + message);
            }

            @Override
            public void onHexMessageSent(String hexMessage) {
                Log.w(TAG, "十六进制消息已发送: " + hexMessage);
            }
        });

        new Thread(() -> {

            // 连接到服务器
            boolean connected = client.connect("192.168.42.1", 7878, 10);

            if (connected) {
                Log.w(TAG, "开始通信...");

                // 主线程等待一段时间后断开连接
//                try {
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

                // 断开连接
//                client.disconnect();
            }

            // 关闭客户端
//            client.shutdown();
        }).start();


    }

    private void initViews() {

        // 设置默认IP和端口
        binding.etIpAddress.setText("192.168.42.1");
        binding.etMessage.setText("257");
        binding.etPort.setText("7878");

        // 设置按钮点击监听
        binding.btnConnect.setOnClickListener(v -> connect());
        binding.btnDisconnect.setOnClickListener(v -> disconnect());
        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.btnSendHex.setOnClickListener(v -> {
            //跳转记录仪页面
//            ContentValues cv = new ContentValues();
//            cv.put(VideoSource.URL, DeviceCmd.RTSP_LIVE);
//            cv.put(VideoSource.TRANSPORT_MODE, VideoSource.TRANSPORT_MODE_TCP);//TCP
//            cv.put(VideoSource.SEND_OPTION, VideoSource.SEND_OPTION_TRUE );//发送活性包

            Intent i = new Intent(TcpSocketActivity.this, PlayActivity.class);
            i.putExtra("play_url", DeviceCmd.RTSP_LIVE);
            i.putExtra(VideoSource.TRANSPORT_MODE, VideoSource.TRANSPORT_MODE_TCP);
            i.putExtra(VideoSource.SEND_OPTION, VideoSource.SEND_OPTION_TRUE);

            ActivityCompat.startActivityForResult(this, i, REQUEST_PLAY, null);
        });

        binding.btnAlbum.setOnClickListener(v -> {
            Intent intent = new Intent(TcpSocketActivity.this, AlbumActivity.class);
            startActivity(intent);
        });


    }

    private void initTcpClient() {
        tcpClient = new TcpSocketClient();

        // 设置监听器
        tcpClient.setListener(new TcpSocketClient.Listener() {
            @Override
            public void onStateChanged(TcpSocketClient.ConnectionState newState,
                                       TcpSocketClient.ConnectionState oldState) {
                runOnUiThread(() -> {
                    updateStatus(newState);
                    updateUI();

                    if (newState == TcpSocketClient.ConnectionState.CONNECTED) {
                        showToast("连接成功");
                    } else if (newState == TcpSocketClient.ConnectionState.DISCONNECTED) {
                        showToast("连接断开");
                    }
                });
            }

            @Override
            public void onMessageReceived(String message) {
                runOnUiThread(() -> appendMessage("接收: " + message));
            }

            @Override
            public void onDataReceived(byte[] data) {
                runOnUiThread(() -> {
                    String hex = NumberUtil.bytesToHex(data);
                    appendMessage("接收数据: " + hex);
                });
            }

            @Override
            public void onError(Throwable cause) {
                runOnUiThread(() -> {
                    appendMessage("错误: " + cause.getMessage());
                    showToast("错误: " + cause.getMessage());
                });
            }

            @Override
            public void onConnected(String remoteAddress) {
                runOnUiThread(() -> {
                    appendMessage("已连接到: " + remoteAddress);
                });
            }
        });
    }

    private void connect() {
        String ip = binding.etIpAddress.getText().toString().trim();
        String portStr = binding.etPort.getText().toString().trim();

        if (ip.isEmpty()) {
            showToast("请输入IP地址");
            return;
        }

        if (portStr.isEmpty()) {
            showToast("请输入端口号");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);

            // 更新配置
            TcpSocketClient.Config config = tcpClient.getConfig();
            config.host = ip;
            config.port = port;

            // 连接
            tcpClient.connect();

        } catch (NumberFormatException e) {
            showToast("端口号格式错误");
        }
    }

    private void disconnect() {
        tcpClient.disconnect();
    }


    private void sendMessage() {
        new Thread(() -> {
            if (tcpClient != null) {
                String etCmd = binding.etMessage.getText().toString();
                tcpClient.sendData(Integer.parseInt(etCmd));
            }
        }).start();
    }

    private void sendHexData() {
        String hex = binding.etMessage.getText().toString().trim();
        if (hex.isEmpty()) {
            return;
        }

        try {
            String etCmd = binding.etMessage.getText().toString();
//            client.sendDataToSocket();
            tcpClient.sendData(Integer.parseInt(etCmd));
            appendMessage("发送数据: " + hex);
//            etMessage.setText("");
        } catch (Exception e) {
            showToast("十六进制格式错误");
        }
    }

    private void updateStatus(TcpSocketClient.ConnectionState state) {
        String statusText = "";
        int color = 0xFFF44336;  // 红色

        switch (state) {
            case DISCONNECTED:
                statusText = "已断开";
                color = 0xFFF44336;  // 红色
                break;
            case CONNECTING:
                statusText = "连接中...";
                color = 0xFFFFC107;  // 黄色
                break;
            case CONNECTED:
                statusText = "已连接";
                color = 0xFF4CAF50;  // 绿色
                break;
            case RECONNECTING:
                statusText = "重连中...";
                color = 0xFFFFC107;  // 黄色
                break;
        }

        binding.tvStatus.setText("状态: " + statusText);
        binding.tvStatus.setTextColor(color);
    }

    private void updateUI() {
        boolean isConnected = tcpClient.isConnected();

        binding.etIpAddress.setEnabled(!isConnected);
        binding.etPort.setEnabled(!isConnected);
        binding.btnConnect.setEnabled(!isConnected);
        binding.btnDisconnect.setEnabled(isConnected);
//        btnSend.setEnabled(isConnected);
        binding.btnSendHex.setEnabled(isConnected);
//        etMessage.setEnabled(isConnected);
    }

    private void appendMessage(String text) {
        runOnUiThread(() -> {
            String time = timeFormat.format(new Date());
            String current = binding.tvMessages.getText().toString();
            String newText = "[" + time + "] " + text;

            if (current.length() > 5000) {
                // 限制显示长度
                int cutIndex = current.indexOf('\n', 1000);
                if (cutIndex > 0) {
                    current = current.substring(cutIndex + 1);
                }
            }

            binding.tvMessages.setText(current + "\n" + newText);

            // 滚动到底部
            final ScrollView scrollView = findViewById(R.id.scroll_view);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(TcpSocketActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) {
            tcpClient.destroy();
        }
    }
}