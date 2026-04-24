package com.intellizon.biofeedbacktest.wifi.connect;

import java.net.Socket;

/**
 * socket连接对象
 */
public class ClientConn {
    public final Socket socket;
    public volatile String peer;
    public volatile String deviceId;
    public volatile String deviceName;
    public volatile long connectedAt;
    public volatile int deviceType = -1;

    public ClientConn(Socket socket) {
        this.socket = socket;
        this.peer = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        this.connectedAt = android.os.SystemClock.elapsedRealtime();
    }
}
