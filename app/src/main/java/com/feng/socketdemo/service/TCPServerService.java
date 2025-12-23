package com.feng.socketdemo.service;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.ServerSocket;

public class TCPServerService extends Service {
    private ServerSocket serverSocket;
    private static final int PORT = 8080;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (true) {
                    new ClientHandler(serverSocket.accept()).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
