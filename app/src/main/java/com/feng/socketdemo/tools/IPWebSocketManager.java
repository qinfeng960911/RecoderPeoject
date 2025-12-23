package com.feng.socketdemo.tools;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class IPWebSocketManager {

    private static final String TAG = "IPWebSocketManager";

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        SCANNING  // 正在扫描设备
    }

    // 配置参数
    public static class Config {
        public String ipAddress;                // 设备IP地址
        public int port = 8080;                 // WebSocket端口
        public String path = "/ws";            // WebSocket路径
        public int maxReconnectAttempts = 10;  // 最大重连次数
        public long reconnectDelay = 2000;     // 重连延迟(ms)
        public long pingInterval = 15000;      // 心跳间隔(ms)
        public int connectTimeout = 5000;      // 连接超时(ms)
        public boolean useWSS = false;         // 是否使用WSS
    }

    private final Config config;
    private OkHttpClient client;
    private WebSocket webSocket;
    private Request request;

    // 状态管理
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);
    private final AtomicLong lastReceivedTime = new AtomicLong(0);
    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;

    // 线程管理
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledExecutorService scanExecutor;
    private ExecutorService messageProcessor;

    // 回调接口
    public interface Listener {
        void onStateChanged(ConnectionState newState, ConnectionState oldState);

        void onMessageReceived(String message);

        void onError(String error);

        void onDeviceFound(String ip);  // 设备发现回调

        void onConnectionInfo(String ip, int port, String path);  // 连接信息
    }

    private Listener listener;

    // 消息队列
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(1000);

    public IPWebSocketManager(Config config) {
        this.config = config;
        initThreadPools();
    }

    // ====================== 线程管理 ======================

    private void initThreadPools() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WebSocket-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WebSocket-Reconnect");
            t.setDaemon(true);
            return t;
        });

        scanExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Device-Scanner");
            t.setDaemon(true);
            return t;
        });

        messageProcessor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "Message-Processor");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
    }

    // ====================== IP地址处理 ======================

    /**
     * 构建WebSocket URL
     */
    private String buildWebSocketUrl(String ip, int port, String path) {
        String protocol = config.useWSS ? "wss" : "ws";
        return String.format("%s://%s:%d%s", protocol, ip, port, path);
    }

    /**
     * 验证IP地址格式
     */
    public static boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    /**
     * 获取本地IP地址（局域网IP）
     */
    public static String getLocalIPAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    // ====================== 设备扫描 ======================

    /**
     * 扫描局域网设备
     */
    public void scanNetworkDevices(String baseIP, int startPort, int endPort) {
        updateState(ConnectionState.SCANNING);

        scanExecutor.submit(() -> {
            String localIP = getLocalIPAddress();
            if (localIP == null) {
                notifyError("无法获取本地IP地址");
                return;
            }

            // 获取网段，如 192.168.1
            String networkSegment = getNetworkSegment(localIP);
            Log.d(TAG, "开始扫描网段: " + networkSegment);

            // 扫描端口范围
            for (int port = startPort; port <= endPort; port++) {
                for (int i = 1; i <= 255; i++) {
                    final String ip = String.format("%s.%d", networkSegment, i);
                    final int currentPort = port;

                    // 跳过本机
                    if (ip.equals(localIP)) {
                        continue;
                    }

                    // 异步测试连接
                    scanExecutor.submit(() -> {
                        if (testDeviceConnection(ip, currentPort)) {
                            Log.d(TAG, "发现设备: " + ip + ":" + currentPort);
                            if (listener != null) {
                                mainHandler.post(() -> listener.onDeviceFound(ip));
                            }
                        }
                    });

                    // 避免过快
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            updateState(ConnectionState.DISCONNECTED);
        });
    }

    private String getNetworkSegment(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return "192.168.1";  // 默认网段
    }

    private boolean testDeviceConnection(String ip, int port) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isReachable(1000);  // 1秒超时
        } catch (Exception e) {
            return false;
        }
    }

    // ====================== 连接管理 ======================

    /**
     * 连接到指定IP设备
     */
    public void connectToDevice(String ip) {
        if (!isValidIPAddress(ip)) {
            notifyError("无效的IP地址格式: " + ip);
            return;
        }

        config.ipAddress = ip;
        connect();
    }

    /**
     * 连接到设备（使用配置中的IP）
     */
    public synchronized void connect() {
        if (config.ipAddress == null || config.ipAddress.isEmpty()) {
            notifyError("未设置设备IP地址");
            return;
        }

        if (isConnecting.get()) {
            Log.d(TAG, "正在连接中，忽略重复请求");
            return;
        }

        if (currentState == ConnectionState.CONNECTED) {
            Log.d(TAG, "已经连接，无需重新连接");
            return;
        }

        Log.d(TAG, "开始连接设备: " + config.ipAddress + ":" + config.port);
        isConnecting.set(true);
        updateState(ConnectionState.CONNECTING);

        // 通知连接信息
        if (listener != null) {
            mainHandler.post(() ->
                    listener.onConnectionInfo(config.ipAddress, config.port, config.path));
        }

        // 停止之前的重连任务
        reconnectExecutor.shutdownNow();
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

        // 初始化OkHttpClient
        initOkHttpClient();

        // 在新线程中建立连接
        new Thread(() -> {
            try {
                doConnect();
            } catch (Exception e) {
                Log.e(TAG, "连接异常", e);
                handleConnectionFailure(e);
            }
        }, "Device-Connector").start();
    }

    private void initOkHttpClient() {
        String url = buildWebSocketUrl(config.ipAddress, config.port, config.path);
        Log.d(TAG, "WebSocket URL: " + url);

        client = new OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(30000, TimeUnit.MILLISECONDS)
                .writeTimeout(30000, TimeUnit.MILLISECONDS)
                .pingInterval(config.pingInterval, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(new Interceptor() {
                    @NotNull
                    @Override
                    public Response intercept(@NotNull Chain chain) throws java.io.IOException {
                        Request request = chain.request();
                        Log.d(TAG, "发送请求到: " + request.url());
                        return chain.proceed(request);
                    }
                })
                .build();

        request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Android-Device-Controller")
                .addHeader("Device-Type", "Android")
                .addHeader("Connection", "Upgrade")
                .addHeader("Upgrade", "websocket")
                .build();
    }

    private void doConnect() {
        try {
            Log.d(TAG, "正在建立WebSocket连接...");

            WebSocketListener listener = new WebSocketListener() {
                @Override
                public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                    Log.d(TAG, "设备连接成功: " + config.ipAddress);
                    IPWebSocketManager.this.webSocket = webSocket;
                    isConnecting.set(false);
                    reconnectCount.set(0);
                    lastReceivedTime.set(System.currentTimeMillis());

                    updateState(ConnectionState.CONNECTED);
                    startHeartbeat();
                    processMessageQueue();

                    // 发送设备识别消息
                    sendDeviceIdentify();
                }

                @Override
                public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                    Log.v(TAG, "收到设备消息: " + text);
                    lastReceivedTime.set(System.currentTimeMillis());

                    // 处理心跳响应
                    if ("pong".equals(text) || text.contains("heartbeat")) {
                        Log.v(TAG, "收到心跳响应");
                        return;
                    }

                    // 异步处理消息
                    messageProcessor.submit(() -> processDeviceMessage(text));
                }

                @Override
                public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                    Log.v(TAG, "收到二进制数据，长度: " + bytes.size());
                    lastReceivedTime.set(System.currentTimeMillis());

                    // 处理二进制数据
                    processBinaryMessage(bytes);
                }

                @Override
                public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                    Log.d(TAG, "设备连接正在关闭: " + reason);
                }

                @Override
                public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                    Log.d(TAG, "设备连接已关闭: " + reason);
                    isConnecting.set(false);
                    stopHeartbeat();

                    if (code != 1000 && code != 1001) {
                        scheduleReconnect();
                    } else {
                        updateState(ConnectionState.DISCONNECTED);
                    }
                }

                @Override
                public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t,
                                      Response response) {
                    Log.e(TAG, "设备连接失败: " + t.getMessage());
                    isConnecting.set(false);
                    stopHeartbeat();
                    handleConnectionFailure(t);
                }
            };

            client.newWebSocket(request, listener);

        } catch (Exception e) {
            throw new RuntimeException("连接设备失败", e);
        }
    }

    private void sendDeviceIdentify() {
        try {
            JSONObject identify = new JSONObject();
            identify.put("type", "identify");
            identify.put("client", "android");
            identify.put("timestamp", System.currentTimeMillis());
            identify.put("command", "register");

            sendMessage(identify.toString());
        } catch (Exception e) {
            Log.e(TAG, "发送设备识别消息失败", e);
        }
    }

    // ====================== 心跳机制 ======================

    private void startHeartbeat() {
        Log.d(TAG, "启动设备心跳检测");
        lastHeartbeatTime.set(System.currentTimeMillis());

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (currentState != ConnectionState.CONNECTED) {
                return;
            }

            long now = System.currentTimeMillis();
            long lastReceived = lastReceivedTime.get();

            // 检查是否长时间未收到消息
            if (now - lastReceived > config.pingInterval * 2) {
                Log.w(TAG, "设备长时间无响应，可能已断开");
                disconnect();
                scheduleReconnect();
                return;
            }

            // 发送心跳
            sendHeartbeat();

        }, config.pingInterval, config.pingInterval, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        if (currentState == ConnectionState.CONNECTED && webSocket != null) {
            try {
                JSONObject heartbeat = new JSONObject();
                heartbeat.put("type", "heartbeat");
                heartbeat.put("client", "android");
                heartbeat.put("timestamp", System.currentTimeMillis());

                boolean success = webSocket.send(heartbeat.toString());
                if (success) {
                    lastHeartbeatTime.set(System.currentTimeMillis());
                    Log.v(TAG, "心跳发送成功");
                } else {
                    Log.w(TAG, "心跳发送失败");
                }
            } catch (Exception e) {
                Log.e(TAG, "发送心跳异常", e);
            }
        }
    }

    private void stopHeartbeat() {
        Log.d(TAG, "停止设备心跳检测");
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        }
    }

    // ====================== 重连机制 ======================

    private void scheduleReconnect() {
        int currentAttempt = reconnectCount.incrementAndGet();

        if (currentAttempt > config.maxReconnectAttempts) {
            Log.w(TAG, "达到最大重连次数: " + config.maxReconnectAttempts);
            notifyError("无法连接到设备，请检查网络");
            updateState(ConnectionState.DISCONNECTED);
            return;
        }

        updateState(ConnectionState.RECONNECTING);

        // 指数退避
        long delay = (long) (config.reconnectDelay * Math.pow(2, currentAttempt - 1));
        delay = Math.min(delay, 30000L);  // 最大30秒

        Log.d(TAG, String.format("将在 %,dms 后重连设备，第 %d 次尝试", delay, currentAttempt));

        reconnectExecutor.schedule(() -> {
            if (currentState != ConnectionState.CONNECTED) {
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void handleConnectionFailure(Throwable t) {
        updateState(ConnectionState.DISCONNECTED);
        notifyError("连接失败: " + t.getMessage());

        // 自动重连
        scheduleReconnect();
    }

    // ====================== 消息处理 ======================

    private void processDeviceMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");
            String deviceId = json.optString("device_id", "");

            Log.d(TAG, "处理设备消息，类型: " + type + ", 设备: " + deviceId);

            switch (type) {
                case "identify_response":
                    handleIdentifyResponse(json);
                    break;
                case "status":
                    handleStatusUpdate(json);
                    break;
                case "sensor_data":
                    handleSensorData(json);
                    break;
                case "command_response":
                    handleCommandResponse(json);
                    break;
                case "error":
                    handleErrorMessage(json);
                    break;
                default:
                    handleOtherMessage(json);
                    break;
            }

            // 通知监听器
            if (this.listener != null) {
                String finalMessage = message;
                mainHandler.post(() -> this.listener.onMessageReceived(finalMessage));
            }

        } catch (Exception e) {
            Log.e(TAG, "处理设备消息异常: " + message, e);
        }
    }

    private void processBinaryMessage(ByteString bytes) {
        // 处理二进制消息（如图像、音频等）
        Log.d(TAG, "收到二进制数据，长度: " + bytes.size());

        // 这里可以根据需要处理二进制数据
        // 例如：bytes.toByteArray() 转换为字节数组
    }

    private void handleIdentifyResponse(JSONObject json) {
        String deviceName = json.optString("device_name", "未知设备");
        String firmware = json.optString("firmware_version", "未知版本");

        Log.d(TAG, String.format("设备识别成功: %s (固件: %s)", deviceName, firmware));
    }

    private void handleStatusUpdate(JSONObject json) {
        String status = json.optString("status", "unknown");
        Log.d(TAG, "设备状态更新: " + status);
    }

    private void handleSensorData(JSONObject json) {
        // 处理传感器数据
        double temperature = json.optDouble("temperature", 0.0);
        double humidity = json.optDouble("humidity", 0.0);

        Log.d(TAG, String.format("传感器数据 - 温度: %.1f°C, 湿度: %.1f%%",
                temperature, humidity));
    }

    private void handleCommandResponse(JSONObject json) {
        boolean success = json.optBoolean("success", false);
        String command = json.optString("command", "");
        String message = json.optString("message", "");

        Log.d(TAG, String.format("命令响应 - 命令: %s, 结果: %s, 消息: %s",
                command, success ? "成功" : "失败", message));
    }

    private void handleErrorMessage(JSONObject json) {
        String error = json.optString("error", "未知错误");
        Log.e(TAG, "设备报告错误: " + error);
    }

    private void handleOtherMessage(JSONObject json) {
        // 处理其他类型的消息
        Log.d(TAG, "收到其他消息: " + json.toString());
    }

    // ====================== 设备控制命令 ======================

    /**
     * 发送控制命令到设备
     */
    public void sendCommand(String command, JSONObject params) {
        if (currentState != ConnectionState.CONNECTED) {
            notifyError("设备未连接，无法发送命令");
            return;
        }

        try {
            JSONObject cmd = new JSONObject();
            cmd.put("type", "command");
            cmd.put("command", command);
            cmd.put("timestamp", System.currentTimeMillis());

            if (params != null) {
                cmd.put("params", params);
            }

            sendMessage(cmd.toString());

        } catch (Exception e) {
            Log.e(TAG, "构建命令失败", e);
            notifyError("构建命令失败: " + e.getMessage());
        }
    }

    /**
     * 发送原始消息
     */
    public void sendMessage(String message) {
        if (currentState == ConnectionState.CONNECTED && webSocket != null) {
            try {
                boolean success = webSocket.send(message);
                if (success) {
                    Log.v(TAG, "消息发送成功: " + message);
                } else {
                    Log.w(TAG, "消息发送失败，加入队列: " + message);
                    messageQueue.offer(message);
                }
            } catch (Exception e) {
                Log.e(TAG, "发送消息异常", e);
                messageQueue.offer(message);
            }
        } else {
            Log.w(TAG, "设备未连接，消息加入队列: " + message);
            messageQueue.offer(message);
        }
    }

    private void processMessageQueue() {
        new Thread(() -> {
            try {
                while (!messageQueue.isEmpty() &&
                        currentState == ConnectionState.CONNECTED) {
                    String message = messageQueue.poll();
                    if (message != null) {
                        sendMessage(message);
                        Thread.sleep(10);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "处理消息队列异常", e);
            }
        }).start();
    }

    // ====================== 状态管理 ======================

    private void updateState(ConnectionState newState) {
        ConnectionState oldState = this.currentState;
        this.currentState = newState;

        Log.d(TAG, "设备连接状态变更: " + oldState + " -> " + newState);

        if (listener != null) {
            mainHandler.post(() -> listener.onStateChanged(newState, oldState));
        }
    }

    private void notifyError(String error) {
        Log.e(TAG, "错误: " + error);

        if (listener != null) {
            mainHandler.post(() -> listener.onError(error));
        }
    }

    // ====================== 公共方法 ======================

    public void disconnect() {
        disconnect(1000, "用户主动断开");
    }

    public synchronized void disconnect(int code, String reason) {
        Log.d(TAG, "断开设备连接: " + reason);

        stopHeartbeat();
        isConnecting.set(false);
        reconnectCount.set(0);

        if (webSocket != null) {
            webSocket.close(code, reason);
            webSocket = null;
        }

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }

        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
        }

        if (scanExecutor != null) {
            scanExecutor.shutdownNow();
        }

        if (messageProcessor != null) {
            messageProcessor.shutdownNow();
        }

        messageQueue.clear();
        updateState(ConnectionState.DISCONNECTED);
    }

    public ConnectionState getCurrentState() {
        return currentState;
    }

    public boolean isConnected() {
        return currentState == ConnectionState.CONNECTED;
    }

    public String getConnectedIP() {
        return config.ipAddress;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Config getConfig() {
        return config;
    }
}
