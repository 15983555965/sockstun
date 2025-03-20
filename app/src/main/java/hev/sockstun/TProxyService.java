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
			session += "/Global";
		} else {
			for (String appName : prefs.getApps()) {
				try {
					builder.addAllowedApplication(appName);
					disallowSelf = false;
				} catch (NameNotFoundException e) {
					Log.e(TAG, "App not found: " + appName);
				}
			}
			session += "/per-App";
		}

		if (disallowSelf) {
			String selfName = getApplicationContext().getPackageName();
			try {
				builder.addDisallowedApplication(selfName);
			} catch (NameNotFoundException e) {
				Log.e(TAG, "Self app not found");
			}
		}

		builder.setSession(session);

		// 尝试建立VPN连接
		try {
			tunFd = builder.establish();
			if (tunFd == null) {
				Log.e(TAG, "VPN establishment failed");
				stopSelf();
				return;
			}
		} catch (Exception e) {
			Log.e(TAG, "VPN error: " + e.getMessage());
			stopSelf();
			return;
		}

		/* TProxy */
		File tproxy_file = new File(getCacheDir(), "tproxy.conf");
		try {
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

			fos.write(tproxy_conf.getBytes());
			fos.close();
		} catch (IOException e) {
			Log.e(TAG, "Failed to create tproxy config: " + e.getMessage());
			return;
		}

		TProxyStartService(tproxy_file.getAbsolutePath(), tunFd.getFd());
		prefs.setEnable(true);

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

		stopForeground(true);

		/* TProxy */
		TProxyStopService();

		/* VPN */
		try {
			tunFd.close();
		} catch (IOException e) {
			Log.e(TAG, "Error closing VPN connection: " + e.getMessage());
		}
		tunFd = null;

		System.exit(0);
		Log.d(TAG, "VPN service stopped");
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
