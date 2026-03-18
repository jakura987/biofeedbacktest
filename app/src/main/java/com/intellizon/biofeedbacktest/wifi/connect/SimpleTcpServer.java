package com.intellizon.biofeedbacktest.wifi.connect;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 多客户端 */
public final class SimpleTcpServer {

    private static final String TAG = "SimpleTcpServer";

    @FunctionalInterface
    public interface Listener {
        void onReceive(String peer, byte[] data, int len);
    }

    private final int port;
    private final Listener listener;

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = false;
    private ServerSocket server;

    // ---------- 改动：保存多个客户端 ----------
    private final java.util.concurrent.CopyOnWriteArrayList<Socket> clients = new java.util.concurrent.CopyOnWriteArrayList<>();

    public SimpleTcpServer(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    public synchronized boolean start() {
        if (running) {
            Log.i(TAG, "start() ignored: already running");
            return false;
        }
        running = true;
        pool.execute(() -> {
            try {
                Log.i(TAG, "binding port " + port + " ...");
                server = new ServerSocket();              // ✅ 不带 port
                server.setReuseAddress(true);             // ✅ bind 前设置
                server.bind(new InetSocketAddress(port)); // ✅ 现在才 bind
                Log.i(TAG, "server started on port " + port);

                while (running) {
                    Log.i(TAG, "accept() waiting...");
                    Socket s = server.accept();
                    Log.i(TAG, "accepted " + s.getInetAddress().getHostAddress() + ":" + s.getPort());
                    // 新连接加入集合
                    clients.add(s);
                    removeOldClientsWithSameIp(s);
                    Log.i(TAG, "client count=" + clients.size());
                    pool.execute(() -> handle(s));
                }
            } catch (IOException e) {
                Log.w(TAG, "accept loop end: " + e.getMessage());
            } finally {
                running = false;
                Log.i(TAG, "server stopped (loop exit)");
            }
        });
        return true;
    }


    /**
     * 按照IP清理旧连接
     * @param newSock
     */
    private void removeOldClientsWithSameIp(Socket newSock) {
        if (newSock == null || newSock.getInetAddress() == null) return;

        String newIp = newSock.getInetAddress().getHostAddress();

        for (Socket old : clients) {
            if (old == null || old == newSock) continue;

            try {
                if (old.getInetAddress() == null) continue;

                String oldIp = old.getInetAddress().getHostAddress();
                if (!newIp.equals(oldIp)) continue;

                Log.w(TAG, "same ip reconnect: remove old client "
                        + oldIp + ":" + old.getPort()
                        + " -> keep new " + newIp + ":" + newSock.getPort());

                try { old.close(); } catch (Exception ignored) {}
                clients.remove(old);
            } catch (Exception e) {
                Log.w(TAG, "removeOldClientsWithSameIp error: " + e.getMessage());
                try { old.close(); } catch (Exception ignored) {}
                clients.remove(old);
            }
        }
    }

    /**
     * 每连上一个设备，就会跑一个对应的 handle() 线程来收它发来的数据
     * @param s
     */
    private void handle(Socket s) {
        final String peer = s.getInetAddress().getHostAddress() + ":" + s.getPort();
        Log.i(TAG, "handle() enter: " + peer);

        try (Socket sock = s;
             InputStream in = sock.getInputStream();
             OutputStream out = sock.getOutputStream()) {

            sock.setTcpNoDelay(true);
            sock.setKeepAlive(true);

            out.write("HELLO FROM AP HOST\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();

            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                String text = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
                Log.i(TAG, "recv [" + peer + "]: " + n + " bytes");
                Log.i(TAG, "text: " + text.replace("\r", "\\r").replace("\n", "\\n"));
                Log.i(TAG, "hex : " + toHex(buf, n));

                if (listener != null) {
                    byte[] copy = java.util.Arrays.copyOf(buf, n);
                    listener.onReceive(peer, copy, copy.length);
                }

                // 回显给当前客户端（保留）
//                out.write("OK: ".getBytes(java.nio.charset.StandardCharsets.UTF_8));
//                out.write(buf, 0, n);
//                out.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
//                out.flush();
            }
            Log.i(TAG, "peer closed write: " + peer);
        } catch (Exception e) {
            Log.w(TAG, "conn error [" + peer + "]: " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            Log.i(TAG, "handle() leave: " + peer);
            // 从集合中移除并关闭（如果还在）
            try {
                clients.remove(s);
                Log.i(TAG, "client removed, left=" + clients.size());
                if (!s.isClosed()) s.close();
            } catch (Exception ignored) {}
        }
    }

    /** 广播发送到所有已连接的客户端；返回 true 表示至少有一个发送成功 */
    public synchronized boolean send(byte[] data) {
        if (clients.isEmpty()) {
            Log.w(TAG, "send() failed: no active client");
            return false;
        }

        boolean anyOk = false;
        for (Socket sock : clients) {
            try {
                if (sock == null || sock.isClosed() || !sock.isConnected()) {
                    clients.remove(sock);
                    continue;
                }
                // 使用 BufferedOutputStream 能提高效率
                OutputStream out = new java.io.BufferedOutputStream(sock.getOutputStream());
                out.write(data);
                out.flush();
                anyOk = true;
                Log.i(TAG, "sent to " + sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
            } catch (IOException e) {
                Log.w(TAG, "send() error to " + sock.getInetAddress().getHostAddress() + ": " + e.getMessage()
                        + " -> removing client");
                try { sock.close(); } catch (Exception ignored) {}
                clients.remove(sock);
            } catch (Exception e) {
                Log.w(TAG, "send() unexpected error: " + e.getMessage());
            }
        }
        Log.i(TAG, "send() complete, anyOk=" + anyOk + ", clientsLeft=" + clients.size());
        return anyOk;
    }


    public synchronized boolean sendTo(String peer, byte[] data) {
        for (Socket sock : clients) {
            try {
                if (sock == null || sock.isClosed() || !sock.isConnected()) continue;
                String p = sock.getInetAddress().getHostAddress() + ":" + sock.getPort();
                if (!peer.equals(p)) continue;

                OutputStream out = sock.getOutputStream();
                out.write(data);
                out.flush();
                return true;
            } catch (Exception e) {
                // 失败就当没发出去
                try { sock.close(); } catch (Exception ignored) {}
                clients.remove(sock);
                return false;
            }
        }
        return false;
    }

    private static String toHex(byte[] b, int n) {
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02X", b[i]));
            if (i + 1 < n) sb.append(' ');
        }
        return sb.toString();
    }

    public synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        server = null;
        // 关闭所有客户端
        for (Socket s : clients) {
            try { s.close(); } catch (Exception ignored) {}
        }
        clients.clear();
        pool.shutdownNow();
        Log.i(TAG, "stop() done");
    }

    public boolean isRunning() { return running; }

    // SimpleTcpServer.java 里新增
    public java.util.List<String> getClientPeers() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Socket s : clients) {
            if (s == null) continue;
            try {
                if (!s.isClosed() && s.isConnected() && s.getInetAddress() != null) {
                    out.add(s.getInetAddress().getHostAddress() + ":" + s.getPort());
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    public int getClientCount() {
        return clients.size();
    }
}

