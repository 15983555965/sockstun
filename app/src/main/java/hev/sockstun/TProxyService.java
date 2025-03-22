/*
 ============================================================================
 Name        : TProxyService.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2024 xyz
 Description : TProxy Service
 ============================================================================
 */

package hev.sockstun;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.net.VpnService;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class TProxyService extends VpnService {
	private static final String TAG = "TProxyService";
	private static native void TProxyStartService(String config_path, int fd);
	private static native void TProxyStopService();
	private static native long[] TProxyGetStats();

	public static final String ACTION_CONNECT = "hev.sockstun.CONNECT";
	public static final String ACTION_DISCONNECT = "hev.sockstun.DISCONNECT";

	static {
		System.loadLibrary("hev-socks5-tunnel");
	}

	private ParcelFileDescriptor tunFd = null;
	private BroadcastReceiver systemEventReceiver = null;

	/**
	 * 检测是否为鸿蒙系统
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

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "Service onCreate");
		
		// 设置进程优先级（Android和鸿蒙都支持）
		android.os.Process.setThreadPriority(
			android.os.Process.myTid(),
			android.os.Process.THREAD_PRIORITY_FOREGROUND
		);

		// 如果是鸿蒙系统，注册系统事件监听器
		if (isHarmonyOS()) {
			registerSystemEventReceiver();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "Service onStartCommand");
		if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
			stopService();
			return START_NOT_STICKY;
		}
		startService();
		// 鸿蒙系统使用START_STICKY确保服务被杀死后重启
		return isHarmonyOS() ? START_STICKY : START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "Service onDestroy");
		if (systemEventReceiver != null) {
			unregisterReceiver(systemEventReceiver);
		}
		super.onDestroy();
	}

	@Override
	public void onRevoke() {
		Log.d(TAG, "Service onRevoke");
		stopService();
		super.onRevoke();
	}

	/**
	 * 注册系统事件监听器（仅鸿蒙系统）
	 * 用于监听系统事件以保持VPN服务稳定运行
	 */
	private void registerSystemEventReceiver() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_BATTERY_LOW);
		filter.addAction(Intent.ACTION_BATTERY_OKAY);
		
		systemEventReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				switch (intent.getAction()) {
					case Intent.ACTION_SCREEN_ON:
						Log.d(TAG, "Screen ON - Checking VPN status");
						checkVPNStatus();
						break;
					case Intent.ACTION_BATTERY_LOW:
						Log.d(TAG, "Battery LOW - Optimizing resource usage");
						optimizeResourceUsage();
						break;
				}
			}
		};
		registerReceiver(systemEventReceiver, filter);
	}

	/**
	 * 检查VPN状态
	 */
	private void checkVPNStatus() {
		if (tunFd == null) {
			Log.w(TAG, "VPN connection lost, attempting to reconnect");
			startService();
		}
	}

	/**
	 * 优化资源使用
	 */
	private void optimizeResourceUsage() {
		// 在低电量时优化资源使用
		if (isHarmonyOS()) {
			// 鸿蒙特定的资源优化
			Log.d(TAG, "Optimizing resources for HarmonyOS");
		}
	}

	public void startService() {
		if (tunFd != null) {
			Log.d(TAG, "VPN service already running");
			return;
		}

		Preferences prefs = new Preferences(this);

		/* VPN */
		String session = new String();
		VpnService.Builder builder = new VpnService.Builder();
		
		// 根据系统类型设置不同的VPN配置
		if (isHarmonyOS()) {
			Log.d(TAG, "Configuring VPN for HarmonyOS");
			builder.setBlocking(true);  // 鸿蒙系统使用阻塞模式
			builder.setMtu(1500);       // 鸿蒙默认MTU
		} else {
			Log.d(TAG, "Configuring VPN for Android");
			builder.setBlocking(false);
			builder.setMtu(prefs.getTunnelMtu());
		}

		// IPv4配置
		if (prefs.getIpv4()) {
			String addr = prefs.getTunnelIpv4Address();
			int prefix = prefs.getTunnelIpv4Prefix();
			String dns = prefs.getDnsIpv4();
			builder.addAddress(addr, prefix);
			builder.addRoute("0.0.0.0", 0);
			if (!dns.isEmpty())
				builder.addDnsServer(dns);
			session += "IPv4";
		}

		// IPv6配置
		if (prefs.getIpv6()) {
			String addr = prefs.getTunnelIpv6Address();
			int prefix = prefs.getTunnelIpv6Prefix();
			String dns = prefs.getDnsIpv6();
			builder.addAddress(addr, prefix);
			builder.addRoute("::", 0);
			if (!dns.isEmpty())
				builder.addDnsServer(dns);
			if (!session.isEmpty())
				session += " + ";
			session += "IPv6";
		}

		// 应用过滤配置
		boolean disallowSelf = true;
		if (prefs.getGlobal()) {
			Log.d(TAG, "使用全局模式 - 所有应用允许");
			session += "/Global";
		} else {
			Log.d(TAG, "使用应用过滤模式");
			Log.d(TAG, "选中的应用列表: " + prefs.getApps());
			
			for (String appName : prefs.getApps()) {
				try {
					builder.addAllowedApplication(appName);
					disallowSelf = false;
					Log.d(TAG, "添加应用到允许列表: " + appName);
				} catch (NameNotFoundException e) {
					Log.e(TAG, "应用未找到: " + appName);
				}
			}
			session += "/per-App";
		}

		if (disallowSelf) {
			String selfName = getApplicationContext().getPackageName();
			try {
				Log.d(TAG, "没有选择任何应用，将VPN服务添加到禁止列表: " + selfName);
				builder.addDisallowedApplication(selfName);
			} catch (NameNotFoundException e) {
				Log.e(TAG, "VPN服务未找到: " + selfName);
			}
		}

		builder.setSession(session);
		Log.d(TAG, "VPN会话名称: " + session);

		// 尝试建立VPN连接
		try {
			Log.d(TAG, "开始建立VPN连接...");
			Log.d(TAG, "VPN配置信息:");
			Log.d(TAG, "Session: " + session);
			Log.d(TAG, "MTU: " + (isHarmonyOS() ? 1500 : prefs.getTunnelMtu()));
			Log.d(TAG, "Blocking: " + isHarmonyOS());
			
			tunFd = builder.establish();
			if (tunFd == null) {
				Log.e(TAG, "VPN连接建立失败");
				stopSelf();
				return;
			}
			Log.d(TAG, "VPN连接建立成功");
		} catch (Exception e) {
			Log.e(TAG, "VPN错误: " + e.getMessage());
			Log.e(TAG, "错误堆栈: " + Arrays.toString(e.getStackTrace()));
			stopSelf();
			return;
		}

		/* TProxy */
		File tproxy_file = new File(getCacheDir(), "tproxy.conf");
		try {
			Log.d(TAG, "创建TProxy配置文件...");
			tproxy_file.createNewFile();
			FileOutputStream fos = new FileOutputStream(tproxy_file, false);

			String tproxy_conf = "misc:\n" +
				"  task-stack-size: " + prefs.getTaskStackSize() + "\n" +
				"tunnel:\n" +
				"  mtu: " + prefs.getTunnelMtu() + "\n";

			tproxy_conf += "socks5:\n" +
				"  port: " + prefs.getSocksPort() + "\n" +
				"  address: '" + prefs.getSocksAddress() + "'\n" +
				"  udp: '" + (prefs.getUdpInTcp() ? "tcp" : "udp") + "'\n";

			if (!prefs.getSocksUsername().isEmpty() &&
				!prefs.getSocksPassword().isEmpty()) {
				tproxy_conf += "  username: '" + prefs.getSocksUsername() + "'\n";
				tproxy_conf += "  password: '" + prefs.getSocksPassword() + "'\n";
			}

			Log.d(TAG, "TProxy配置文件路径: " + tproxy_file.getAbsolutePath());
			Log.d(TAG, "TProxy配置内容:\n" + tproxy_conf);
			
			fos.write(tproxy_conf.getBytes());
			fos.close();
			Log.d(TAG, "TProxy配置文件写入成功");
		} catch (IOException e) {
			Log.e(TAG, "创建TProxy配置文件失败: " + e.getMessage());
			Log.e(TAG, "错误堆栈: " + Arrays.toString(e.getStackTrace()));
			return;
		}

		Log.d(TAG, "启动TProxy服务...");
		try {
			Log.d(TAG, "启动TProxy服务");
			TProxyStartService(tproxy_file.getAbsolutePath(), tunFd.getFd());
			Log.d(TAG, "TProxy服务启动成功");
			prefs.setEnable(true);
		} catch (Exception e) {
			Log.e(TAG, "TProxy服务启动失败: " + e.getMessage());
			Log.e(TAG, "错误堆栈: " + Arrays.toString(e.getStackTrace()));
			stopSelf();
			return;
		}

		String channelName = "socks5";
		initNotificationChannel(channelName);
		createNotification(channelName);
		
		Log.d(TAG, "VPN service started successfully");
	}

	public void stopService() {
		if (tunFd == null) {
			Log.d(TAG, "VPN service not running");
			return;
		}

		Log.d(TAG, "开始停止VPN服务...");
		
		// 1. 停止前台服务
		stopForeground(true);
		Log.d(TAG, "前台服务已停止");

		// 2. 停止 TProxy 服务
		TProxyStopService();
		Log.d(TAG, "TProxy服务已停止");

		// 3. 关闭 VPN 连接
		try {
			tunFd.close();
			Log.d(TAG, "VPN连接已关闭");
		} catch (IOException e) {
			Log.e(TAG, "关闭VPN连接时发生错误: " + e.getMessage());
		}
		tunFd = null;

		// 4. 停止自身服务
		stopSelf();
		Log.d(TAG, "VPN服务已停止");
	}

	private void createNotification(String channelName) {
		Intent i = new Intent(this, TProxyService.class);
		PendingIntent pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
		NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelName);
		Notification notify = notification
				.setContentTitle(getString(R.string.app_name))
				.setSmallIcon(android.R.drawable.sym_def_app_icon)
				.setContentIntent(pi)
				.build();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(1, notify);
		} else {
			startForeground(1, notify, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		}
	}

	private void initNotificationChannel(String channelName) {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			CharSequence name = getString(R.string.app_name);
			NotificationChannel channel = new NotificationChannel(channelName, name, NotificationManager.IMPORTANCE_DEFAULT);
			notificationManager.createNotificationChannel(channel);
		}
	}
}
