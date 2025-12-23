package com.feng.socketdemo.tools;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.feng.socketdemo.bean.DeviceInfoBean;
import com.feng.socketdemo.bean.LoginBean;
import com.feng.socketdemo.config.DeviceCmd;
import com.feng.socketdemo.utils.NumberUtil;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TcpSocketClient {
    private static final String TAG = "TcpSocketClient";

    private static final int MESSAGE_START_CODE = 0x0000ABBC; // 起始码固定
    private static final int DVR_MESSAGE_CODE = 0xFFFFFFFF;   // 从DVR发送的消息码

    // 连接状态
    public enum ConnectionState {
        DISCONNECTED,      // 已断开
        CONNECTING,        // 连接中
        CONNECTED,         // 已连接
        RECONNECTING       // 重连中
    }

    // 配置参数
    public static class Config {
        public String host = "192.168.42.1";  // 默认IP
        public int port = 7878;                // 默认端口
        int connectionTimeout = 5000;   // 连接超时(ms)
        int readTimeout = 30000;        // 读超时(ms)
        int writeTimeout = 10000;       // 写超时(ms)
        int maxReconnectAttempts = 5;   // 最大重连次数
        long reconnectInitialDelay = 1000;  // 初始重连延迟(ms)
        long reconnectMaxDelay = 30000;     // 最大重连延迟(ms)
        long heartbeatInterval = 2000;     // 心跳间隔(ms)
        int sendBufferSize = 8192;      // 发送缓冲区大小
        int receiveBufferSize = 8192;   // 接收缓冲区大小
        boolean tcpNoDelay = true;      // 禁用Nagle算法
        boolean keepAlive = true;       // 启用TCP keep-alive
        String charset = "UTF-8";       // 字符编码
        int token = 0;//token
    }

    private final Config config = new Config();

    // Socket 相关
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;

    // 状态管理
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);
    private final AtomicLong lastReceivedTime = new AtomicLong(0);
    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;
    private final ReentrantLock writeLock = new ReentrantLock(true);

    // 线程管理
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledExecutorService readTimeoutExecutor;
    private ExecutorService messageProcessor;
    private Thread receiveThread;
    private final AtomicBoolean isReceiving = new AtomicBoolean(false);

    // 消息编号计数器，每发送一条消息递增
    private int messageCode = 0;

    // 回调接口
    public interface Listener {
        void onStateChanged(ConnectionState newState, ConnectionState oldState);

        void onMessageReceived(String message);

        void onDataReceived(byte[] data);

        void onError(Throwable cause);

        void onConnected(String remoteAddress);
    }

    private Listener listener;

    // 消息队列
    private final BlockingQueue<Integer> messageQueue = new LinkedBlockingQueue<>(1000);
    private final BlockingQueue<Integer> dataQueue = new LinkedBlockingQueue<>(100);
    private final AtomicBoolean isProcessingQueue = new AtomicBoolean(false);

    public TcpSocketClient() {
        initThreadPools();
    }

    public TcpSocketClient(String host, int port) {
        config.host = host;
        config.port = port;
        initThreadPools();
    }

    // ====================== 线程管理 ======================

    private void initThreadPools() {
        // 心跳线程池
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TCP-Heartbeat");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });

        // 重连线程池
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TCP-Reconnect");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });

        // 读超时检测线程池
        readTimeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "TCP-ReadTimeout");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });

        // 消息处理线程池
        messageProcessor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "TCP-MessageProcessor");
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    }

    // ====================== 连接管理 ======================

    public synchronized void connect() {
        connect(config.host, config.port);
    }

    public synchronized void connect(String host, int port) {
        if (isConnecting.get()) {
            Log.d(TAG, "已经在连接中，忽略重复连接请求");
            return;
        }

        if (currentState == ConnectionState.CONNECTED) {
            Log.d(TAG, "已经连接，无需重新连接");
            return;
        }

        Log.d(TAG, "开始连接TCP Socket: " + host + ":" + port);
        isConnecting.set(true);
        updateState(ConnectionState.CONNECTING);

        // 在新线程中建立连接
        new Thread(() -> {
            try {
                doConnect(host, port);
            } catch (Exception e) {
                Log.e(TAG, "连接异常", e);
                handleConnectionFailure(e);
            }
        }, "TCP-Connector").start();
    }

    private void doConnect(String host, int port) throws IOException {
        Log.d(TAG, "正在建立TCP连接: " + host + ":" + port);

        // 关闭旧连接
        closeQuietly();

        try {
            // 创建Socket
            socket = new Socket();

            // 设置Socket选项
            socket.setSoTimeout(config.readTimeout);
            socket.setTcpNoDelay(config.tcpNoDelay);
            socket.setKeepAlive(config.keepAlive);
            socket.setSendBufferSize(config.sendBufferSize);
            socket.setReceiveBufferSize(config.receiveBufferSize);
            socket.setSoLinger(true, 0);  // 关闭时立即释放资源

            // 连接服务器
            socket.connect(new InetSocketAddress(host, port), config.connectionTimeout);

            // 获取输入输出流
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();

            writer = new PrintWriter(new OutputStreamWriter(outputStream, config.charset), true);
            reader = new BufferedReader(new InputStreamReader(inputStream, config.charset));
            dataOutputStream = new DataOutputStream(outputStream);
            dataInputStream = new DataInputStream(inputStream);

            // 连接成功
            isConnecting.set(false);
            reconnectCount.set(0);

            String remoteAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            Log.d(TAG, "TCP连接成功: " + remoteAddress);

            updateState(ConnectionState.CONNECTED);

            // 启动接收线程
            startReceiveThread();

            // 通知监听器
            if (listener != null) {
                mainHandler.post(() -> listener.onConnected(remoteAddress));
            }

        } catch (Exception e) {
            closeQuietly();
            throw e;
        }
    }

    private void handleConnectionFailure(Throwable t) {
        updateState(ConnectionState.DISCONNECTED);

        if (listener != null) {
            mainHandler.post(() -> listener.onError(t));
        }

        // 网络异常时自动重连
        if (shouldReconnect(t)) {
            scheduleReconnect();
        }
    }

    // ====================== 数据接收 ======================

    private void startReceiveThread() {
        if (isReceiving.get()) {
            return;
        }

        isReceiving.set(true);

        messageProcessor.execute(() -> {
            Log.d(TAG, "开始接收数据线程");
            Log.d(TAG, "isReceiving：" + isReceiving.get());

            if (socket != null) {
                Log.d(TAG, "socket.isClosed:" + (socket.isClosed()));
            }

            // 创建字节数组输出流，用于缓存接收到的数据
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // 数据接收循环
            while (isReceiving.get() && socket != null && !socket.isClosed()) {
                try {
                    int readLen = -1;
                    byte[] buf = new byte[4096]; // 4KB接收缓冲区

                    // 读取Socket数据
                    if ((readLen = dataInputStream.read(buf)) != -1) {
                        Log.w(TAG, "readLen:" + readLen);
                        // 触发十六进制数据接收事件
                        handleReceivedHexEvent(NumberUtil.bytesToHex(buf, readLen));

                        // 将读取的数据写入缓存
                        baos.write(buf, 0, readLen);

                        // 解析数据包循环
                        do {
                            byte[] recvData = baos.toByteArray();
                            Log.w(TAG, "解析数据包循环:" + NumberUtil.bytesToHex(recvData));

                            // 1. 检查数据长度是否足够读取头部(12字节)
                            if (recvData.length < 12) {
                                Log.w(TAG, "数据不足，等待更多数据");
                                break; // 数据不足，等待更多数据
                            }

                            // 2. 验证魔数(协议标识)
                            if (NumberUtil.bytesToInt(recvData, 0) != MESSAGE_START_CODE) {
                                // 魔数不匹配，清空缓存并退出
                                Log.w(TAG, "魔数不匹配，清空缓存并退出");
                                baos.reset();
                                break;
                            }

                            // 3. 验证固定标志(原代码中为0xFFFFFFFF，但发送时使用了messageCode)
                            // 注意：这里与原逻辑有差异，原代码接收时检查0xFFFFFFFF，但发送时使用messageCode
                            // 这里修改为检查消息编号的有效性（不为负即可）
                            int msgCode = NumberUtil.bytesToInt(recvData, 4);
                            if (msgCode != DVR_MESSAGE_CODE) {
                                Log.w(TAG, "验证固定标志");
                                baos.reset();
                                break;
                            }

                            // 4. 读取消息体长度
                            int msgLen = NumberUtil.bytesToInt(recvData, 8);
                            int pktLen = msgLen + 12; // 计算完整数据包长度

                            // 5. 检查是否有完整的数据包
                            if (pktLen > recvData.length) {
                                Log.w(TAG, "检查是否有完整的数据包");
                                break; // 数据包不完整，等待更多数据
                            }

                            // 6. 提取消息体数据
                            byte[] msgData = new byte[msgLen];
                            System.arraycopy(recvData, 12, msgData, 0, msgLen);

                            String dataStr = new String(msgData, StandardCharsets.UTF_8);
                            Log.w(TAG, "触发消息接收事件:" + dataStr);
                            // 7. 触发消息接收事件
                            handleReceivedText(dataStr);

                            // 8. 清空缓存，准备处理剩余数据
                            baos.reset();

                            // 9. 检查是否还有剩余数据
                            if (pktLen == recvData.length) {
                                break; // 没有剩余数据，退出解析循环
                            }

                            // 10. 提取剩余数据并重新写入缓存
                            byte[] leftData = new byte[recvData.length - pktLen];
                            System.arraycopy(recvData, pktLen, leftData, 0, leftData.length);
                            baos.write(leftData);

                        } while (true); // 继续解析，直到没有完整数据包
                    }

                } catch (SocketTimeoutException e) {
                    // 读取超时，继续循环（非错误情况）
                    handleReadTimeout();
                    continue;
                } catch (Exception e) {
                    // 发生错误，触发错误事件并退出循环
                    handleReadError(e);
                    break;
                }
            }

        });

        // 启动读超时检测
        startReadTimeoutMonitor();
    }

    private String readLineWithTimeout() throws IOException {
        if (reader != null) {
            return reader.readLine();
        }
        return null;
    }

    private void handleReceivedHexEvent(String hexMessage) {
        Log.w(TAG, "收到十六进制消息: " + hexMessage);
    }

    //接收消息
    private void handleReceivedText(String JsonData) {
        Log.v(TAG, "收到文本: " + JsonData);

        // 异步处理消息
        messageProcessor.submit(() -> processMessage(JsonData));

        // 通知监听器
        if (listener != null) {
            mainHandler.post(() -> listener.onMessageReceived(JsonData));
        }
    }

    private void handleReadTimeout() {
        Log.w(TAG, "读超时，连接可能已断开");
        disconnect();
        scheduleReconnect();
    }

    private void handleReadError(Exception e) {
        e.printStackTrace();
        Log.e(TAG, "读取错误，断开连接", e);
        disconnect();
        scheduleReconnect();
    }

    private void startReadTimeoutMonitor() {
        readTimeoutExecutor.scheduleWithFixedDelay(() -> {
            if (currentState != ConnectionState.CONNECTED) {
                return;
            }

            long now = System.currentTimeMillis();
            long lastReceived = lastReceivedTime.get();

            // 检查是否长时间未收到消息
            if (now - lastReceived > config.readTimeout) {
                Log.w(TAG, "长时间未收到数据，可能连接已断开");
                disconnect();
                scheduleReconnect();
            }
        }, config.readTimeout, config.readTimeout, TimeUnit.MILLISECONDS);
    }

    private void stopReceiveThread() {
        isReceiving.set(false);
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
    }

    // ====================== 心跳机制 ======================

    private void startHeartbeat() {

        heartbeatExecutor.scheduleWithFixedDelay(() -> {
            Log.d(TAG, "启动心跳检测");

            if (currentState != ConnectionState.CONNECTED) {
                return;
            }

            long currentTimeMillis = System.currentTimeMillis();

            // 检查是否10s未收到消息
            if (lastReceivedTime.get() - currentTimeMillis > config.heartbeatInterval * 5) {
                Log.w(TAG, "长时间未收到心跳响应，可能连接已断开");
                disconnect();
                scheduleReconnect();
                return;
            }

            // 发送心跳
            sendHeartbeat();
        }, config.heartbeatInterval, config.heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        if (currentState == ConnectionState.CONNECTED) {
            try {
                // 发送文本心跳
                boolean success = sendData(DeviceCmd.HEART_BEAT);
                if (success) {
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
        Log.d(TAG, "停止心跳检测");
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        if (readTimeoutExecutor != null) {
            readTimeoutExecutor.shutdownNow();
            readTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        }
    }

    // ====================== 重连机制 ======================

    private void scheduleReconnect() {
        int currentAttempt = reconnectCount.incrementAndGet();

        if (currentAttempt > config.maxReconnectAttempts) {
            Log.w(TAG, "达到最大重连次数: " + config.maxReconnectAttempts);
            updateState(ConnectionState.DISCONNECTED);
            return;
        }

        updateState(ConnectionState.RECONNECTING);

        // 指数退避算法
        long delay = (long) (config.reconnectInitialDelay *
                Math.pow(2, currentAttempt - 1));
        delay = Math.min(delay, config.reconnectMaxDelay);

        Log.d(TAG, String.format("将在 %,dms 后重连，第 %d 次尝试", delay, currentAttempt));

        reconnectExecutor.schedule(() -> {
            if (currentState != ConnectionState.CONNECTED) {
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private boolean shouldReconnect(Throwable t) {
        if (t instanceof ConnectException) {
            Log.d(TAG, "连接被拒绝");
            return true;
        }
        if (t instanceof SocketTimeoutException) {
            Log.d(TAG, "连接超时");
            return true;
        }
        if (t instanceof IOException) {
            Log.d(TAG, "网络IO异常");
            return true;
        }
        if (t instanceof UnknownHostException) {
            Log.d(TAG, "未知主机");
            return false;  // 未知主机不重连
        }
        return true;
    }

    public void resetReconnectCount() {
        reconnectCount.set(0);
    }

    // ====================== 数据发送 ======================

    public boolean sendData(int cmd) {
        return sendData(cmd, true);
    }

    public boolean sendData(int cmd, boolean enqueueIfDisconnected) {
        if (currentState == ConnectionState.CONNECTED && dataOutputStream != null) {
            return sendDataImmediately(cmd);
        } else if (enqueueIfDisconnected) {
            // 放入数据队列
            boolean success = dataQueue.offer(cmd);
            if (success) {
                Log.d(TAG, "数据已加入队列: " + cmd);
            } else {
                Log.w(TAG, "数据队列已满，丢弃数据");
            }

            // 如果已断开连接，触发重连
            if (currentState == ConnectionState.DISCONNECTED) {
                connect();
            }
            return success;
        } else {
            Log.w(TAG, "连接未就绪，无法发送数据");
            return false;
        }
    }

    private boolean sendDataImmediately(int cmd) {
        writeLock.lock();
        try {
            // 1. 创建JSON数据
            JSONObject jsonObject = buildJsonData(cmd);

            // 获取JSON数据的字节数组
            byte[] jsonBytes = jsonObject.toString().getBytes(StandardCharsets.UTF_8);

            // 2. 构建十六进制字符串
            String rechargeInfo = "0000ABBC";      // 魔数
//            String rechargeInfo1 = "00000000";     // 消息码
            String rechargeInfo1 = String.format("000000%02X", messageCode++);
            // 消息码
            String rechargeInfo2 = String.format("000000%02X", jsonBytes.length); // 数据长度

            // 3. 创建可变字节数组
            ByteArrayOutputStream mutableData = new ByteArrayOutputStream();

            // 4. 将十六进制字符串转换为字节数组并追加
            mutableData.write(NumberUtil.convertHexStrToData(rechargeInfo));
            mutableData.write(NumberUtil.convertHexStrToData(rechargeInfo1));
            mutableData.write(NumberUtil.convertHexStrToData(rechargeInfo2));

            // 5. 追加JSON数据
            mutableData.write(jsonBytes);

            // 6. 获取完整的数据包
            byte[] packet = mutableData.toByteArray();

            // 7. 这里应该是通过Socket发送数据
            dataOutputStream.write(packet);
            dataOutputStream.flush();

            System.out.println("数据包构建完成，长度: " + packet.length + " 字节");
            System.out.println("十六进制: " + NumberUtil.bytesToHex(packet));

            return true;
        } catch (Exception e) {
            Log.e(TAG, "发送数据异常", e);
            dataQueue.offer(cmd);  // 放入队列稍后重试
            return false;
        } finally {
            writeLock.unlock();
        }
    }


    /**
     * 构建JSON数据
     * 对应Objective-C中的: @{"token":@0,@"Msg_ID":@257}
     */
    private JSONObject buildJsonData(int cmd) {
        // 使用JSONObject构建JSON数据
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("token", config.token);
            jsonObject.put("Msg_ID", cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    // ====================== 消息处理 ======================

    private void processMessage(String jsonData) {

        try {
            // 解析JSON消息
            JSONObject jsonObject = new JSONObject(jsonData);
            int rval = jsonObject.optInt("rval", -1);
            if (rval != 0 && rval != -25) {
                return;
            }

            lastReceivedTime.set(System.currentTimeMillis());
            int msgId = jsonObject.getInt("Msg_ID");
            // 根据消息类型处理
            switch (msgId) {
                case DeviceCmd.LOGIN_CMD:
                    handleLoginMessage(jsonData);
                    break;
                case DeviceCmd.GET_PARAMS:
                    handleDataMessage(jsonData);
                    // 启动心跳
                    startHeartbeat();
                    break;
                case DeviceCmd.HEART_BEAT:
                    //心跳
                    handleHeartBeatMessage(jsonData);
                    break;
            }

        } catch (Exception e) {
            Log.w(TAG, "解析消息失败: " + jsonData, e);
            // 如果不是JSON，按普通文本处理
        }
    }

    private void handleLoginMessage(String jsonData) {
        LoginBean loginBean = new Gson().fromJson(jsonData, LoginBean.class);
        Log.d(TAG, "收到登录消息: " + loginBean);
        config.token = loginBean.getParam();
        //登录成功后获取设备信息
        sendData(DeviceCmd.GET_PARAMS);
    }

    private void handleDataMessage(String jsonData) {
        // 处理数据消息
        DeviceInfoBean deviceInfoBean = new Gson().fromJson(jsonData, DeviceInfoBean.class);
        Log.d(TAG, "收到数据消息:" + deviceInfoBean);
    }

    private void handleHeartBeatMessage(String jsonData) {
        // 处理数据消息
        DeviceInfoBean deviceInfoBean = new Gson().fromJson(jsonData, DeviceInfoBean.class);
        Log.d(TAG, "收到心跳消息:" + deviceInfoBean);
        lastHeartbeatTime.set(System.currentTimeMillis());
    }


    // ====================== 状态管理 ======================

    private void updateState(ConnectionState newState) {
        ConnectionState oldState = this.currentState;
        this.currentState = newState;

        Log.d(TAG, "连接状态变更: " + oldState + " -> " + newState);

        if (listener != null) {
            mainHandler.post(() -> listener.onStateChanged(newState, oldState));
        }
    }

    public ConnectionState getCurrentState() {
        return currentState;
    }

    public boolean isConnected() {
        return currentState == ConnectionState.CONNECTED &&
                socket != null &&
                socket.isConnected() &&
                !socket.isClosed();
    }

    public String getRemoteAddress() {
        if (socket != null && socket.isConnected()) {
            return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }
        return null;
    }

    public InetAddress getLocalAddress() {
        if (socket != null) {
            return socket.getLocalAddress();
        }
        return null;
    }

    // ====================== 断开连接 ======================

    public synchronized void disconnect() {
        Log.d(TAG, "断开TCP连接");
        closeQuietly();

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }

        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
        }

        if (readTimeoutExecutor != null) {
            readTimeoutExecutor.shutdownNow();
        }

        if (messageProcessor != null) {
            messageProcessor.shutdownNow();
        }

        stopReceiveThread();
        config.token = 0;
        isConnecting.set(false);
        reconnectCount.set(0);

        updateState(ConnectionState.DISCONNECTED);
    }

    private void closeQuietly() {
        // 关闭写入流
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭writer异常", e);
            }
            writer = null;
        }

        // 关闭读取流
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭reader异常", e);
            }
            reader = null;
        }

        // 关闭数据输出流
        if (dataOutputStream != null) {
            try {
                dataOutputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭dataOutputStream异常", e);
            }
            dataOutputStream = null;
        }

        // 关闭数据输入流
        if (dataInputStream != null) {
            try {
                dataInputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭dataInputStream异常", e);
            }
            dataInputStream = null;
        }

        // 关闭Socket
        if (socket != null) {
            try {
//                socket.shutdownInput();
//                socket.shutdownOutput();
                socket.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭socket异常", e);
            }
            socket = null;
        }
    }

    // ====================== 配置和监听器 ======================

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Config getConfig() {
        return config;
    }

    // 清理资源
    public void destroy() {
        disconnect();


    }
}