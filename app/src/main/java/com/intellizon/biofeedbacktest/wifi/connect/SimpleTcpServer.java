package com.intellizon.biofeedbacktest.wifi.connect;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 多客户端
 */
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
    private final CopyOnWriteArrayList<ClientConn> clients = new CopyOnWriteArrayList<>();

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
                    ClientConn conn = new ClientConn(s);
                    Log.i(TAG, "accepted " + conn.peer);
                    Log.i(TAG, "accepted " + s.getInetAddress().getHostAddress() + ":" + s.getPort());
                    // 新连接加入集合
                    clients.add(conn);
                    //todo 新连接替换旧连接  同 IP 新连接进来时，踢掉旧连接
                    removeOldClientsWithSameIp(conn);

                    Log.i(TAG, "client count=" + clients.size());
                    pool.execute(() -> handle(conn));
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
     *
     * @param newClientConn
     */
    private void removeOldClientsWithSameIp(ClientConn newClientConn) {
        if (newClientConn == null || newClientConn.socket == null) return;
        if (newClientConn.socket.getInetAddress() == null) return;

        String newIp = newClientConn.socket.getInetAddress().getHostAddress();

        for (ClientConn old : clients) {
            if (old == null || old == newClientConn || old.socket == null) continue;

            try {
                if (old.socket.getInetAddress() == null) continue;

                String oldIp = old.socket.getInetAddress().getHostAddress();
                if (!newIp.equals(oldIp)) continue;

                Log.w(TAG, "same ip reconnect: remove old client "
                        + oldIp + ":" + old.socket.getPort()
                        + " -> keep new " + newIp + ":" + newClientConn.socket.getPort());

                try {
                    old.socket.close();
                } catch (Exception ignored) {
                }

                clients.remove(old);

            } catch (Exception e) {
                Log.w(TAG, "removeOldClientsWithSameIp error: " + e.getMessage());
                try {
                    old.socket.close();
                } catch (Exception ignored) {
                }
                clients.remove(old);
            }
        }
    }

    /**
     * 每连上一个设备，就会跑一个对应的 handle() 线程来收它发来的数据
     *
     * @param clientConn
     */
    //private long lastReadAtMs = 0L;
    private void handle(ClientConn clientConn) {
        Socket s = clientConn.socket;
        final String peer = clientConn.peer;
        Log.i(TAG, "handle() enter: " + peer);

        long lastReadAtMs = 0L;

        try (Socket sock = s;
//             InputStream in = sock.getInputStream();
//             OutputStream out = sock.getOutputStream()) {

             InputStream in = sock.getInputStream()) {

            sock.setTcpNoDelay(true);
            sock.setKeepAlive(true);

//            out.write("HELLO FROM AP HOST\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
//            out.flush();

            byte[] buf = new byte[4096];
            int n;


            while (true) {
                n = in.read(buf);
                Log.i(TAG, "read() return n = " + n + ", peer=" + peer);

                if (n == -1) {
                    Log.w(TAG, "peer EOF (likely FIN), peer=" + peer);
                    break;
                }

                long now = SystemClock.elapsedRealtime();
                if (lastReadAtMs != 0L) {
                    long dt = now - lastReadAtMs;
                    Log.e(TAG, "READ_GAP dt=" + dt + "ms, n=" + n);
                }
                lastReadAtMs = now;

                String text = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
                Log.i(TAG, "recv [" + peer + "]: " + n + " bytes");
                Log.i(TAG, "text: " + text.replace("\r", "\\r").replace("\n", "\\n"));
                Log.i(TAG, "hex : " + toHex(buf, n));

                byte[] copy = java.util.Arrays.copyOf(buf, n);

                // 身份包：上游直接吃掉，不往下传
                if (looksLikeIdentityFrame(copy, copy.length)) {
                    parseIdentityFrame(clientConn, copy);
                    continue;
                }

                // 其他数据继续原链路
//                if (listener != null) {
//                    listener.onReceive(peer, copy, copy.length);
//                }
                if (listener != null) {
                    try {
                        listener.onReceive(peer, copy, copy.length);
                    } catch (Throwable t) {
                        Log.e(TAG, "listener parse error, keep socket alive, peer=" + peer, t);
                    }
                }
            }
            Log.i(TAG, "peer closed write: " + peer);
        } catch (Exception e) {
            Log.w(TAG, "conn error [" + peer + "]: " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            Log.i(TAG, "handle() leave: " + peer);
            // 从集合中移除并关闭（如果还在）
            try {
                clients.remove(clientConn);
                Log.i(TAG, "client removed, left=" + clients.size());
                if (!s.isClosed()) s.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 广播发送到所有已连接的客户端；返回 true 表示至少有一个发送成功
     */
    public synchronized boolean send(byte[] data) {
        if (clients.isEmpty()) {
            Log.w(TAG, "send() failed: no active client");
            return false;
        }

        boolean anyOk = false;
        for (ClientConn conn : clients) {
            Socket sock = conn.socket;
            try {
                if (sock == null || sock.isClosed() || !sock.isConnected()) {
                    clients.remove(conn);
                    continue;
                }

                OutputStream out = new java.io.BufferedOutputStream(sock.getOutputStream());
                out.write(data);
                out.flush();
                anyOk = true;

                Log.i(TAG, "sent to " + conn.peer);
            } catch (IOException e) {
                Log.w(TAG, "send() error to " + conn.peer + ": " + e.getMessage()
                        + " -> removing client");
                try {
                    if (sock != null && !sock.isClosed())
                        sock.close();
                } catch (Exception ignored) {
                }
                clients.remove(conn);
            } catch (Exception e) {
                Log.w(TAG, "send() unexpected error: " + e.getMessage());
            }
        }

        Log.i(TAG, "send() complete, anyOk=" + anyOk + ", clientsLeft=" + clients.size());
        return anyOk;
    }


    public synchronized boolean sendTo(String peer, byte[] data) {
        for (ClientConn conn : clients) {
            Socket sock = conn.socket;
            try {
                if (sock == null || sock.isClosed()) continue;
                if (!peer.equals(conn.peer)) continue;

                OutputStream out = sock.getOutputStream();
                out.write(data);
                out.flush();
                return true;
            } catch (Exception e) {
                try {
                    if (sock != null && !sock.isClosed()) {
                        sock.close();
                    }
                } catch (Exception ignored) {
                }
                clients.remove(conn);
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
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;

        // 关闭所有客户端
        for (ClientConn conn : clients) {
            try {
                if (conn != null && conn.socket != null) {
                    conn.socket.close();
                }
            } catch (Exception ignored) {
            }
        }

        clients.clear();
        pool.shutdownNow();
        Log.i(TAG, "stop() done");
    }

    public synchronized boolean closePeer(String peer) {
        for (ClientConn conn : clients) {
            if (conn == null || conn.socket == null) continue;
            if (!peer.equals(conn.peer)) continue;

            try {
                conn.socket.close();
            } catch (Exception ignored) {
            }

            clients.remove(conn);
            Log.i(TAG, "closePeer: " + peer + ", left=" + clients.size());
            return true;
        }
        return false;
    }

    public boolean isRunning() {
        return running;
    }

    // SimpleTcpServer.java 里新增
    public List<String> getClientPeers() {
        ArrayList<String> out = new ArrayList<>();
        for (ClientConn conn : clients) {
            if (conn == null || conn.socket == null) continue;
            try {
                Socket s = conn.socket;
                if (!s.isClosed() && s.getInetAddress() != null) {
                    out.add(conn.peer);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public List<String> getClientDisplayTexts() {
        ArrayList<String> out = new ArrayList<>();
        for (ClientConn conn : clients) {
            if (conn == null || conn.socket == null) continue;
            try {
                Socket s = conn.socket;
                if (!s.isClosed() && s.getInetAddress() != null) {
                    String id = (conn.deviceId == null || conn.deviceId.isEmpty()) ? "?" : conn.deviceId;
                    out.add("设备ID[" + id + "] : " + conn.peer);
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }


    /**
     * 身份包解析
     *
     * @param data
     * @param len
     * @return
     */
    private boolean looksLikeIdentityFrame(byte[] data, int len) {
        return len == 31
                && (data[0] & 0xFF) == 0x5A
                && (data[1] & 0xFF) == 0xA5
                && (data[2] & 0xFF) == 0x1B
                && (data[3] & 0xFF) == 0x13
                && (data[4] & 0xFF) == 0x00;
    }

    private void parseIdentityFrame(ClientConn conn, byte[] data) {
        // 协议：
        // 0  1   2    3        4                      5..12            13..28              29     30
        // 5A A5 LEN  CMD     CMDTYPE(0x00)         deviceId(8)      deviceName(16)        type    CS

        // 设备号直接取前两个字节
        int id = ((data[5] & 0xFF) << 8) | (data[6] & 0xFF);
        conn.deviceId = String.valueOf(id);

        conn.deviceName = readAsciiTrimZero(data, 13, 16);
        conn.deviceType = data[29] & 0xFF;

        Log.i(TAG, "identity updated: peer=" + conn.peer
                + ", id=" + conn.deviceId
                + ", name=" + conn.deviceName
                + ", type=" + conn.deviceType);
    }

    private String readAsciiTrimZero(byte[] data, int offset, int len) {
        int end = offset + len;
        int realEnd = offset;
        while (realEnd < end && data[realEnd] != 0x00) {
            realEnd++;
        }
        return new String(
                data,
                offset,
                realEnd - offset,
                java.nio.charset.StandardCharsets.US_ASCII
        ).trim();
    }

    public int getClientCount() {
        return clients.size();
    }

}

