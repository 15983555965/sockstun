package hev.sockstun;

import static hev.sockstun.Constants.LOOPBACK;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;


import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class TProxyService extends VpnService implements ServiceControl {
    private static final String TAG = "Socks5VpnService";
    private static final String ACTION_DISCONNECT = "com.socks5vpn.action.disconnect";

    private ParcelFileDescriptor mInterface;
    private boolean isRunning = false;
    private boolean isGlobalMode = true;
    private final ReceiveMessageHandler mMsgReceive = new ReceiveMessageHandler();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<String> allowedApps = new ArrayList<>();

    private NetworkRequest defaultNetworkRequest;
    private ConnectivityManager.NetworkCallback defaultNetworkCallback;
    private ConnectivityManager connectivity;
    private Process process;

    private static native void TProxyStartService(String config_path, int fd);

    private static native void TProxyStopService();

    private static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "VPN服务创建");
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Log.d(TAG, "初始化网络连接管理器");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.d(TAG, "Android P及以上版本，设置网络回调");
            defaultNetworkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .build();

            defaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.d(TAG, "网络可用: " + network);
                    setUnderlyingNetworks(new Network[]{network});
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    Log.d(TAG, "网络能力变化: " + network + ", 能力: " + networkCapabilities);
                    setUnderlyingNetworks(new Network[]{network});
                }

                @Override
                public void onLost(@NonNull Network network) {
                    Log.w(TAG, "网络丢失: " + network);
                    setUnderlyingNetworks(null);
                }
            };
        }

        // 注册广播接收器
        try {
            IntentFilter mFilter = new IntentFilter(Constants.BROADCAST_ACTION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(mMsgReceive, mFilter, Utils.receiverFlags());
                Log.d(TAG, "广播接收器注册成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "注册广播接收器失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN权限被撤销");
        stopVpn();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "VPN服务销毁");
        super.onDestroy();
        try {
            unregisterReceiver(mMsgReceive);
            Log.d(TAG, "广播接收器注销成功");
        } catch (Exception e) {
            Log.e(TAG, "注销广播接收器失败: " + e.getMessage(), e);
        }

        NotificationService.cancelNotification(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "VPN服务启动命令，Action: " + (intent != null ? intent.getAction() : "null"));
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            Log.d(TAG, "收到断开连接命令");
            stopVpn();
            return START_NOT_STICKY;
        }

        // 获取代理模式和应用列表
        if (intent != null) {
            isGlobalMode = intent.getBooleanExtra("is_global_mode", true);
            Log.d(TAG, "代理模式: " + (isGlobalMode ? "全局" : "局部"));

            if (!isGlobalMode && intent.hasExtra("selected_packages")) {
                allowedApps = intent.getStringArrayListExtra("selected_packages");
                Log.d(TAG, "允许的应用数量: " + allowedApps.size());
            }
        }

        startVpn();
        return START_STICKY;
    }

    /**
     * 启动VPN
     */
    private void startVpn() {
        if (isRunning) {
            Log.w(TAG, "VPN已经在运行中，忽略启动请求");
            return;
        }

        Log.i(TAG, "开始启动VPN服务");
        try {
            if (setupVpn()) {
                Log.i(TAG, "VPN设置成功，准备启动tun2socks");
                if (isHarmonyOS()) {
                    runTun2socks();
                } else {
                    runTun2socksForAndroid();
                }
                isRunning = true;

                Notification notification = NotificationService.createNotification(
                        this,
                        "Socks5 VPN",
                        "VPN正在运行中"
                );
                startForeground(Constants.NOTIFICATION_ID, notification);
                Log.d(TAG, "VPN前台服务通知已创建");

                MessageUtil.sendMsg2UI(this, Constants.MSG_STATE_START_SUCCESS, "VPN已启动");
                Log.i(TAG, "VPN服务启动成功");
            } else {
                Log.e(TAG, "VPN设置失败，无法启动服务");
                MessageUtil.sendMsg2UI(this, Constants.MSG_STATE_START_FAILURE, "VPN启动失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "启动VPN时发生异常: " + e.getMessage(), e);
            MessageUtil.sendMsg2UI(this, Constants.MSG_STATE_START_FAILURE, "VPN启动失败: " + e.getMessage());
        }
    }

    private void runTun2socksForAndroid() {
        /* TProxy */
        File tproxy_file = new File(getCacheDir(), "tproxy.conf");
        try {
            tproxy_file.createNewFile();
            FileOutputStream fos = new FileOutputStream(tproxy_file, false);

            String tproxy_conf = "misc:\n" +
                    "  task-stack-size: " + Constants.TASK_STACK_SIZE + "\n" +
                    "tunnel:\n" +
                    "  mtu: " + Constants.VPN_MTU + "\n";

            tproxy_conf += "socks5:\n" +
                    "  port: " + Constants.SOCKS_PORT + "\n" +
                    "  address: '" + Constants.LOOPBACK + "'\n" +
                    "  udp: '" + "udp" + "'\n";

//            if (!prefs.getSocksUsername().isEmpty() &&
//                    !prefs.getSocksPassword().isEmpty()) {
//                tproxy_conf += "  username: '" + prefs.getSocksUsername() + "'\n";
//                tproxy_conf += "  password: '" + prefs.getSocksPassword() + "'\n";
//            }

            fos.write(tproxy_conf.getBytes());
            fos.close();
        } catch (IOException e) {
            return;
        }
        TProxyStartService(tproxy_file.getAbsolutePath(), mInterface.getFd());
    }


    /**
     * 停止VPN
     */
    private void stopVpn() {
        if (!isRunning) {
            Log.w(TAG, "VPN未在运行，忽略停止请求");
            return;
        }
        TProxyStopService();
        isRunning = false;
        Log.i(TAG, "开始停止VPN服务");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && connectivity != null) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback);
                Log.d(TAG, "网络回调注销成功");
            } catch (Exception e) {
                Log.e(TAG, "注销网络回调失败: " + e.getMessage());
            }
        }

        try {
            Log.d(TAG, "正在停止tun2socks进程");
            process.destroy();
            Log.d(TAG, "tun2socks进程已停止");
        } catch (Exception e) {
            Log.e(TAG, "停止tun2socks进程失败: " + e.getMessage(), e);
        }

        try {
            mInterface.close();
            Log.d(TAG, "VPN接口已关闭");
        } catch (Exception e) {
            Log.e(TAG, "关闭VPN接口失败: " + e.getMessage());
        }

        stopForeground(true);
        stopSelf();
        Log.i(TAG, "VPN服务已完全停止");

        MessageUtil.sendMsg2UI(this, Constants.MSG_STATE_STOP_SUCCESS, "VPN已停止");
    }

    /**
     * 设置VPN
     */
    private boolean setupVpn() {
        Log.d(TAG, "开始设置VPN");
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            Log.e(TAG, "VPN准备失败，用户未授权");
            return false;
        }

        try {
            Builder builder = new Builder();
            String session = new String();
            if (isHarmonyOS()) {
                Log.d(TAG, "创建VPN Builder");

                // 设置MTU
                builder.setMtu(Constants.VPN_MTU);
                Log.d(TAG, "设置MTU: " + Constants.VPN_MTU);

                // 设置地址
                builder.addAddress(Constants.PRIVATE_VLAN4_CLIENT, 30);
                Log.d(TAG, "设置IPv4地址: " + Constants.PRIVATE_VLAN4_CLIENT);

                // 添加路由
                builder.addRoute("0.0.0.0", 0);  // 默认路由
                Log.d(TAG, "添加IPv4路由配置完成");
                //添加DNS服务器
//            val DNS_GOOGLE_ADDRESSES = arrayListOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")

                builder.addDnsServer("8.8.8.8");
//            builder.addDnsServer("8.8.4.4");
//            builder.addDnsServer("8.8.8.8");
//            builder.addDnsServer("8.8.8.8");

                // 添加IPv6支持（可选）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.addAddress(Constants.PRIVATE_VLAN6_CLIENT, 126);
                    builder.addRoute("::", 0);
                    Log.d(TAG, "添加IPv6支持");
                }
                session = "Socks5 VPN";
            } else {
                /* VPN */
                builder.setBlocking(false);
                builder.setMtu(Constants.VPN_MTU);
                builder.addAddress(Constants.PRIVATE_VLAN4_CLIENT, Constants.PRIVATE_VLAN4_PREFIX);
                builder.addRoute("0.0.0.0", 0);
                builder.addDnsServer("8.8.8.8");
                session += "IPv4";
            }

            // 排除自己的包名
            String selfPackageName = getPackageName();
            builder.addDisallowedApplication(selfPackageName);
            Log.d(TAG, "排除应用包名: " + selfPackageName);

            // 根据模式设置应用
            if (isGlobalMode) {
                if (!isHarmonyOS()){
                    session += "/Global";
                }
                Log.d(TAG, "使用全局代理模式");
            } else {
                Log.d(TAG, "使用局部代理模式");
                if (!isHarmonyOS()){
                    session += "/per-App";
                }
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                for (ApplicationInfo app : installedApps) {
                    String packageName = app.packageName;

                    // 排除自己
                    if (packageName.equals(selfPackageName)) {
                        continue;
                    }

                    // 如果不在允许列表中，添加到排除列表
                    if (!allowedApps.contains(packageName)) {
                        try {
                            builder.addDisallowedApplication(packageName);
                            Log.d(TAG, "排除应用: " + packageName);
                        } catch (Exception e) {
                            Log.e(TAG, "排除应用失败: " + packageName + ", " + e.getMessage());
                        }
                    }
                }
            }
            builder.setSession(session);

            // 关闭旧接口（如果有）
            try {
                if (mInterface != null) {
                    mInterface.close();
                    Log.d(TAG, "关闭旧的VPN接口");
                }
            } catch (Exception e) {
                Log.w(TAG, "关闭旧VPN接口时发生异常: " + e.getMessage());
            }

            // 设置网络回调（Android P及以上）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && connectivity != null) {
                try {
                    connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback);
                    Log.d(TAG, "设置网络回调成功");
                } catch (Exception e) {
                    Log.e(TAG, "设置网络回调失败: " + e.getMessage());
                }
            }

            // 创建VPN接口
            Log.d(TAG, "尝试建立VPN接口");
            mInterface = builder.establish();
            if (mInterface == null) {
                Log.e(TAG, "VPN接口创建失败");
                throw new Exception("无法创建VPN接口");
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "设置VPN失败: " + e.getMessage(), e);
            Utils.toast(this, "设置VPN失败: " + e.getMessage());
            stopVpn();
        }
        return false;
    }

    /**
     * 运行tun2socks
     */
    private void runTun2socks() {
        Log.d(TAG, "准备启动tun2socks");
        List<String> cmd = new ArrayList<>();
        cmd.add(new File(getApplicationInfo().nativeLibraryDir, Constants.TUN2SOCKS).getAbsolutePath());
        cmd.add("--netif-ipaddr");
        cmd.add(Constants.PRIVATE_VLAN4_ROUTER);
        cmd.add("--netif-netmask");
        cmd.add("255.255.255.252");
        cmd.add("--socks-server-addr");
        cmd.add(LOOPBACK + ":" + Constants.SOCKS_PORT);
        cmd.add("--tunmtu");
        cmd.add(String.valueOf(Constants.VPN_MTU));
        cmd.add("--sock-path");
        cmd.add("sock_path");
        cmd.add("--enable-udprelay");
        cmd.add("--loglevel");
        cmd.add("notice");

        // 添加IPv6支持（可选）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cmd.add("--netif-ip6addr");
            cmd.add(Constants.PRIVATE_VLAN6_ROUTER);
        }

        Log.d(TAG, "tun2socks命令: " + cmd);

        try {
            ProcessBuilder proBuilder = new ProcessBuilder(cmd);
            proBuilder.redirectErrorStream(true);
            process = proBuilder
                    .directory(getFilesDir())
                    .start();

            new Thread(() -> {
                Log.d(TAG, "监控tun2socks进程");
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    Log.e(TAG, "等待tun2socks进程失败: " + e.getMessage());
                }
                Log.d(TAG, "tun2socks进程退出");
                if (isRunning) {
                    Log.d(TAG, "重启tun2socks进程");
                    runTun2socks();
                }
            }).start();

            sendFd();
        } catch (Exception e) {
            Log.e(TAG, "启动tun2socks失败: " + e.getMessage());
            Utils.toast(this, "启动tun2socks失败: " + e.getMessage());
        }
    }

    private void sendFd() {
        FileDescriptor fd = mInterface.getFileDescriptor();
        String path = new File(getFilesDir(), "sock_path").getAbsolutePath();
        Log.d(TAG, "准备发送文件描述符到: " + path);
        executorService.execute(() -> {
            int tries = 0;
            while (true) {
                try {
                    Thread.sleep(50L << tries);
                    Log.d(TAG, "尝试发送文件描述符，尝试次数: " + tries);
                    LocalSocket localSocket = new LocalSocket();
                    try {
                        Log.d(TAG, "连接本地Socket: " + path);
                        localSocket.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
                        localSocket.setFileDescriptorsForSend(new FileDescriptor[]{fd});
                        localSocket.getOutputStream().write(42);
                        Log.d(TAG, "文件描述符发送成功");
                        localSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "发送文件描述符时发生IO异常: " + e.getMessage());
                        localSocket.close();
                        throw e;
                    }
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "发送文件描述符失败: " + e.getMessage());
                    if (tries > 5) {
                        Log.e(TAG, "发送文件描述符失败，已达到最大重试次数");
                        break;
                    }
                    tries += 1;
                }
            }
        });
    }


    @Override
    public Service getService() {
        return this;
    }

    @Override
    public void startService() {
        startVpn();
    }

    @Override
    public void stopService() {
        stopVpn();
    }

    @Override
    public boolean vpnProtect(int socket) {
        return protect(socket);
    }

    /**
     * 内部广播接收器类
     */
    private class ReceiveMessageHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int action = MessageUtil.getActionFromMsg(intent != null ? intent.getExtras() : null);
            if (action == Constants.MSG_STATE_START) {
                startVpn();
            } else if (action == Constants.MSG_STATE_STOP) {
                stopVpn();
            }
        }
    }

    /**
     * 检测是否为鸿蒙系统
     *
     * @return true表示是鸿蒙系统，false表示是Android系统
     */
    private boolean isHarmonyOS() {
        try {
            Class<?> buildExClass = Class.forName("com.huawei.system.BuildEx");
            Object osBrand = buildExClass.getMethod("getOsBrand").invoke(buildExClass);
            return "harmony".equalsIgnoreCase(osBrand.toString());
        } catch (Exception e) {
            Log.d(TAG, "Not running on HarmonyOS");
            return false;
        }
    }
}