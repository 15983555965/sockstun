package hev.sockstun.v2ray;

import android.app.Service;

public interface ServiceControl {
    /**
     * 获取服务实例
     */
    Service getService();

    /**
     * 启动服务
     */
    void startService();

    /**
     * 停止服务
     */
    void stopService();

    /**
     * 保护VPN socket
     */
    boolean vpnProtect(int socket);
}