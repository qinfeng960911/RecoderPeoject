package com.feng.socketdemo.service;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP Socket客户端模块
 * 功能：提供TCP连接、数据发送、接收等基础网络通信能力
 * 协议格式：12字节头部 + 变长消息体
 * 头部结构：
 * - 0-3字节: 魔数(0x0000ABBC)
 * - 4-7字节: 消息编号(递增)
 * - 8-11字节: 消息体长度
 */
public class SocketTcpClient {

    private static final String TAG = "SocketTcpClient";

    // 单例实例
    private static SocketTcpClient instance;

    // 网络IO流
    private BufferedInputStream bufferedInputStream;
    private BufferedOutputStream bufferedOutputStream;

    // 单线程执行器，用于处理数据接收
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 消息编号计数器，每发送一条消息递增
    private int messageCode = 0;

    // Socket连接对象
    private Socket socket;

    // 事件回调接口
    private SocketEventListener eventListener;

    // 连接状态标志
    private volatile boolean isConnected = false;

    private static final int MESSAGE_START_CODE = 0x0000ABBC; // 起始码固定
    private static final int DVR_MESSAGE_CODE = 0xFFFFFFFF;   // 从DVR发送的消息码

    /**
     * Socket事件监听器接口
     */
    public interface SocketEventListener {
        void onConnect();

        void onDisconnect();

        void onError(String error);

        void onMessageReceived(String message);

        void onHexMessageReceived(String hexMessage);

        void onMessageSent(String message);

        void onHexMessageSent(String hexMessage);
    }

    /**
     * 私有构造函数，实现单例模式
     */
    private SocketTcpClient() {
        // 单例模式，防止外部实例化
    }

    /**
     * 获取单例实例
     */
    public static synchronized SocketTcpClient getInstance() {
        if (instance == null) {
            instance = new SocketTcpClient();
        }
        return instance;
    }

    /**
     * 设置事件监听器
     */
    public void setEventListener(SocketEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 连接到TCP服务器
     *
     * @param host    服务器主机名或IP地址
     * @param port    服务器端口号
     * @param timeout 连接超时时间(秒)
     * @return 连接是否成功
     */
    public synchronized boolean connect(String host, int port, int timeout) {
        try {
            // 如果已经连接，直接返回成功
            if (socket != null && isConnected) {
                emitConnectEvent();
                return true;
            }

            // 创建新的Socket对象
            socket = new Socket();

            // 设置Socket选项
            socket.setReuseAddress(true);     // 允许地址重用
            socket.setTcpNoDelay(true);       // 禁用Nagle算法，减少延迟
            socket.setSoTimeout(50);          // 设置读取超时50ms，避免阻塞

            // 建立连接，设置连接超时
            socket.connect(new InetSocketAddress(host, port), timeout * 1000);

            // 获取输入输出流
            bufferedInputStream = new BufferedInputStream(socket.getInputStream());
            bufferedOutputStream = new BufferedOutputStream(socket.getOutputStream());

            // 创建字节数组输出流，用于缓存接收到的数据
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // 更新连接状态
            isConnected = true;

            // 启动数据接收线程
            executorService.execute(() -> {
                // 数据接收循环
                while (isConnected) {
                    try {
                        int readLen = -1;
                        byte[] buf = new byte[4096]; // 4KB接收缓冲区

                        // 读取Socket数据
                        if ((readLen = bufferedInputStream.read(buf)) != -1) {
                            Log.w(TAG, "readLen:" + readLen);
                            // 触发十六进制数据接收事件
                            emitRecvHexEvent(bytesToHex(buf, readLen));

                            // 将读取的数据写入缓存
                            baos.write(buf, 0, readLen);

                            // 解析数据包循环
                            do {
                                byte[] recvData = baos.toByteArray();
                                Log.w(TAG, "解析数据包循环:" + bytesToHex(recvData));

                                // 1. 检查数据长度是否足够读取头部(12字节)
                                if (recvData.length < 12) {
                                    Log.w(TAG, "数据不足，等待更多数据");
                                    break; // 数据不足，等待更多数据
                                }

                                // 2. 验证魔数(协议标识)
                                if (bytesToInt(recvData, 0) != MESSAGE_START_CODE) {
                                    // 魔数不匹配，清空缓存并退出
                                    Log.w(TAG, "魔数不匹配，清空缓存并退出");
                                    baos.reset();
                                    break;
                                }

                                // 3. 验证固定标志(原代码中为0xFFFFFFFF，但发送时使用了messageCode)
                                // 注意：这里与原逻辑有差异，原代码接收时检查0xFFFFFFFF，但发送时使用messageCode
                                // 这里修改为检查消息编号的有效性（不为负即可）
                                int msgCode = bytesToInt(recvData, 4);
                                if (msgCode != DVR_MESSAGE_CODE) {
                                    Log.w(TAG, "验证固定标志");
                                    baos.reset();
                                    break;
                                }

                                // 4. 读取消息体长度
                                int msgLen = bytesToInt(recvData, 8);
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
                                emitRecvEvent(dataStr);

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
                        continue;
                    } catch (Exception e) {
                        // 发生错误，触发错误事件并退出循环
                        e.printStackTrace();
                        emitErrorEvent(e);
                        break;
                    }
                }

                // 关闭Socket连接
                closeSocket();

                // 触发断开连接事件
                emitDisconnectEvent();
            });

            // 触发连接成功事件
            emitConnectEvent();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            // 连接失败，关闭Socket并触发错误事件
            closeSocket();
            emitErrorEvent(e);
            return false;
        }
    }

    /**
     * 断开TCP连接
     */
    public synchronized void disconnect() {
        // 如果Socket为null，直接触发断开事件
        if (socket == null) {
            emitDisconnectEvent();
            return;
        }

        // 关闭Socket连接
        closeSocket();
    }


    /**
     * 发送消息到服务器
     *
     * @return 发送是否成功
     */
    public synchronized void sendDataToSocket(int cmd) {
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
            mutableData.write(convertHexStrToData(rechargeInfo));
            mutableData.write(convertHexStrToData(rechargeInfo1));
            mutableData.write(convertHexStrToData(rechargeInfo2));

            // 5. 追加JSON数据
            mutableData.write(jsonBytes);

            // 6. 获取完整的数据包
            byte[] packet = mutableData.toByteArray();

            // 7. 这里应该是通过Socket发送数据
            bufferedOutputStream.write(packet);
            bufferedOutputStream.flush();

            System.out.println("数据包构建完成，长度: " + packet.length + " 字节");
            System.out.println("十六进制: " + bytesToHex(packet));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建协议头部
     * 头部结构：12字节
     * 1. 魔数: 4字节 (0x0000ABBC)
     * 2. 消息码: 4字节 (0x00000000)
     * 3. 数据长度: 4字节 (JSON数据的长度)
     */
    private byte[] buildProtocolHeader(JSONObject jsonData) {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // 小端序

        // 1. 魔数: 0x0000ABBC
        buffer.putInt(0x0000ABBC);

        // 2. 消息码: 0x00000000
        buffer.putInt(0x00000000);

        // 3. JSON数据长度
        int jsonLength = jsonData.toString().getBytes(StandardCharsets.UTF_8).length;
        buffer.putInt(jsonLength);

        return buffer.array();
    }

    /**
     * 构建JSON数据
     * 对应Objective-C中的: @{"token":@0,@"Msg_ID":@257}
     */
    private JSONObject buildJsonData(int cmd) {
        // 使用JSONObject构建JSON数据
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("token", 0);
            jsonObject.put("Msg_ID", cmd);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }


    /**
     * 十六进制字符串转换为字节数组的方法
     * 对应Objective-C中的 convertHexStrToData: 方法
     */
    public byte[] convertHexStrToData(String hexString) {
        // 移除可能的空格
        hexString = hexString.replaceAll("\\s", "");

        // 检查长度是否为偶数
        if (hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须为偶数");
        }

        int len = hexString.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            // 每两个字符转换为一个字节
            int firstDigit = Character.digit(hexString.charAt(i), 16);
            int secondDigit = Character.digit(hexString.charAt(i + 1), 16);

            if (firstDigit == -1 || secondDigit == -1) {
                throw new IllegalArgumentException("无效的十六进制字符");
            }

            data[i / 2] = (byte) ((firstDigit << 4) + secondDigit);
        }

        return data;
    }

    /**
     * 关闭Socket连接和相关资源
     */
    private void closeSocket() {
        // 更新连接状态
        isConnected = false;

        // 关闭Socket
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                socket = null;
            }
        }

        // 关闭输入流
        if (bufferedInputStream != null) {
            try {
                bufferedInputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                bufferedInputStream = null;
            }
        }

        // 关闭输出流
        if (bufferedOutputStream != null) {
            try {
                bufferedOutputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                bufferedOutputStream = null;
            }
        }
    }

    /**
     * 检查当前是否已连接
     */
    public boolean isConnected() {
        return isConnected;
    }

    // ========== 事件触发方法 ==========

    private void emitConnectEvent() {
        if (eventListener != null) {
            eventListener.onConnect();
        }
    }

    private void emitDisconnectEvent() {
        if (eventListener != null) {
            eventListener.onDisconnect();
        }
    }

    private void emitErrorEvent(Exception e) {
        if (eventListener != null) {
            eventListener.onError(e.getMessage());
        }
    }

    private void emitRecvEvent(String jsonStr) {
        if (eventListener != null) {
            eventListener.onMessageReceived(jsonStr);
        }
    }

    private void emitRecvHexEvent(String hexStr) {
        if (eventListener != null) {
            eventListener.onHexMessageReceived(hexStr);
        }
    }

    private void emitSendEvent(String jsonStr) {
        if (eventListener != null) {
            eventListener.onMessageSent(jsonStr);
        }
    }

    private void emitSendHexEvent(String hexStr) {
        if (eventListener != null) {
            eventListener.onHexMessageSent(hexStr);
        }
    }

    // ========== 数据转换工具方法 ==========

    /**
     * 将int转换为字节数组（小端序）
     */
    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    private int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
                ((bytes[offset + 1] & 0xFF) << 16) |
                ((bytes[offset + 2] & 0xFF) << 8) |
                (bytes[offset + 3] & 0xFF);
    }

    /**
     * 字节数组转换为十六进制字符串（用于调试）
     */
    public String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();
    }

    /**
     * 关闭客户端，释放资源
     */
    public void shutdown() {
        disconnect();
        executorService.shutdown();
    }
}
