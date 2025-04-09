package hev.sockstun;

public class Constants {
    /** VPN相关常量 */
    public static final int VPN_MTU = 1500;
    public static final String PRIVATE_VLAN4_CLIENT = "198.18.0.1";
    public static final String PRIVATE_VLAN4_ROUTER = "10.10.14.2";
    public static final String PRIVATE_VLAN6_CLIENT = "fc00::10:10:14:1";
    public static final String PRIVATE_VLAN6_ROUTER = "fc00::10:10:14:2";
    public static final String TUN2SOCKS = "libtun2socks.so";

    /** Socks5代理常量 */
    public static final int SOCKS_PORT = 1080; // 默认使用1080端口，不是10808
    public static final String LOOPBACK = "127.0.0.1";

    /** 通知常量 */
    public static final String NOTIFICATION_CHANNEL_ID = "SOCKS5_VPN_CHANNEL_ID";
    public static final String NOTIFICATION_CHANNEL_NAME = "Socks5 VPN Service";
    public static final int NOTIFICATION_ID = 1;

    /** 广播Action */
    public static final String BROADCAST_ACTION_SERVICE = "com.socks5vpn.action.service";
    public static final String BROADCAST_ACTION_ACTIVITY = "com.socks5vpn.action.activity";

    /** 消息常量 */
    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_STATE_RUNNING = 11;
    public static final int MSG_STATE_NOT_RUNNING = 12;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_STATE_START = 3;
    public static final int MSG_STATE_START_SUCCESS = 31;
    public static final int MSG_STATE_START_FAILURE = 32;
    public static final int MSG_STATE_STOP = 4;
    public static final int MSG_STATE_STOP_SUCCESS = 41;

    public static final int RECEIVER_NOT_EXPORTED = 1 << 24;
    public static final boolean IS_TCP = false;
    public static final int TASK_STACK_SIZE = 81920;
    public static final String SOCKS_USERNAME = "";
    public static final String SOCKS_USERNAME_PASSWORD = "";
    public static final boolean PREF_LOCAL_DNS_ENABLED = true;
    public static final int PRIVATE_VLAN4_PREFIX = 32;
}