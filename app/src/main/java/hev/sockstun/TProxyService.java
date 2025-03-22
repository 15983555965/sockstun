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
import java.lang.reflect.Method;
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

	// 添加VPN服务类名常量
	private static final String ANDROID_VPN_SERVICE = "android.net.VpnService";
	private static final String HARMONY_VPN_SERVICE = "ohos.net.VpnService";
	
	// 添加VPN服务实例
	private Object vpnService = null;
	private Class<?> vpnServiceClass = null;

	static {
		System.loadLibrary("hev-socks5-tunnel");
	}

	private ParcelFileDescriptor tunFd = null;
	private BroadcastReceiver systemEventReceiver = null;

	/**
	 * 初始化VPN服务
	 */
	private void initVpnService() {
		try {
			if (isHarmonyOS()) {
				// 使用鸿蒙VPN服务
				Log.i(TAG, "正在初始化鸿蒙VPN服务...");
				// 加载鸿蒙VPN服务类
				vpnServiceClass = Class.forName(HARMONY_VPN_SERVICE);
				Log.i(TAG, "成功加载鸿蒙VPN服务类: " + HARMONY_VPN_SERVICE);
				
				// 获取鸿蒙VPN服务实例
				Method getInstanceMethod = vpnServiceClass.getMethod("getInstance", Context.class);
				vpnService = getInstanceMethod.invoke(null, this);
				Log.i(TAG, "成功获取鸿蒙VPN服务实例");
				
				// 检查VPN服务是否可用
				Method isAvailableMethod = vpnServiceClass.getMethod("isAvailable");
				boolean isAvailable = (boolean) isAvailableMethod.invoke(vpnService);
				Log.i(TAG, "鸿蒙VPN服务可用状态: " + isAvailable);
				
				if (!isAvailable) {
					throw new Exception("鸿蒙VPN服务不可用");
				}
			} else {
				// 使用Android VPN服务
				Log.i(TAG, "正在初始化Android VPN服务...");
				vpnServiceClass = Class.forName(ANDROID_VPN_SERVICE);
				Log.i(TAG, "成功加载Android VPN服务类: " + ANDROID_VPN_SERVICE);
				
				// 创建Android VPN服务实例
				vpnService = vpnServiceClass.newInstance();
				Log.i(TAG, "成功创建Android VPN服务实例");
			}
			
			Log.i(TAG, "VPN服务初始化成功");
		} catch (Exception e) {
			Log.e(TAG, "VPN服务初始化失败: " + e.getMessage());
			Log.e(TAG, "错误堆栈: " + Arrays.toString(e.getStackTrace()));
		}
	}

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
		
		// 初始化VPN服务
		initVpnService();
		
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
		Log.i(TAG, "TProxyService onStartCommand: action=" + intent.getAction());
		Log.i(TAG, "TProxyService 当前VPN状态: " + (tunFd != null ? "已连接" : "未连接"));
		
		if (intent.getAction().equals(ACTION_CONNECT)) {
			Log.i(TAG, "开始建立VPN连接...");
			if (tunFd != null) {
				Log.w(TAG, "VPN已存在，先停止现有连接");
				stopService();
			}
			startService();
			return START_STICKY;
		} else if (intent.getAction().equals(ACTION_DISCONNECT)) {
			Log.i(TAG, "收到断开连接请求");
			stopService();
			return START_NOT_STICKY;
		}
		return START_NOT_STICKY;
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

		try {
			// 使用反射创建VPN Builder
			Class<?> builderClass = vpnServiceClass.getDeclaredClasses()[0];
			Object builder = builderClass.getConstructor(vpnServiceClass).newInstance(this);
			
			// 设置VPN配置
			if (isHarmonyOS()) {
				Log.d(TAG, "Configuring VPN for HarmonyOS");
				builderClass.getMethod("setBlocking", boolean.class).invoke(builder, true);
				builderClass.getMethod("setMtu", int.class).invoke(builder, 1500);
			} else {
				Log.d(TAG, "Configuring VPN for Android");
				builderClass.getMethod("setBlocking", boolean.class).invoke(builder, false);
				builderClass.getMethod("setMtu", int.class).invoke(builder, prefs.getTunnelMtu());
			}

			// 设置会话名称
			builderClass.getMethod("setSession", String.class).invoke(builder, "SocksTun");
			
			// IPv4配置
			if (prefs.getIpv4()) {
				String addr = prefs.getTunnelIpv4Address();
				int prefix = prefs.getTunnelIpv4Prefix();
				String dns = prefs.getDnsIpv4();
				builderClass.getMethod("addAddress", String.class, int.class).invoke(builder, addr, prefix);
				builderClass.getMethod("addRoute", String.class, int.class).invoke(builder, "0.0.0.0", 0);
				if (!dns.isEmpty())
					builderClass.getMethod("addDnsServer", String.class).invoke(builder, dns);
			}

			// IPv6配置
			if (prefs.getIpv6()) {
				String addr = prefs.getTunnelIpv6Address();
				int prefix = prefs.getTunnelIpv6Prefix();
				String dns = prefs.getDnsIpv6();
				builderClass.getMethod("addAddress", String.class, int.class).invoke(builder, addr, prefix);
				builderClass.getMethod("addRoute", String.class, int.class).invoke(builder, "::", 0);
				if (!dns.isEmpty())
					builderClass.getMethod("addDnsServer", String.class).invoke(builder, dns);
			}

			// 应用过滤配置
			if (prefs.getGlobal()) {
				Log.d(TAG, "使用全局模式 - 所有应用允许");
				builderClass.getMethod("allowAll").invoke(builder);
			} else {
				Log.d(TAG, "使用应用过滤模式");
				for (String appName : prefs.getApps()) {
					try {
						builderClass.getMethod("addAllowedApplication", String.class).invoke(builder, appName);
						Log.d(TAG, "添加应用到允许列表: " + appName);
					} catch (Exception e) {
						Log.e(TAG, "应用未找到: " + appName);
					}
				}
			}

			// 建立VPN连接
			Log.d(TAG, "开始建立VPN连接...");
			Object fd = builderClass.getMethod("establish").invoke(builder);
			if (fd == null) {
				Log.e(TAG, "VPN连接建立失败");
				stopSelf();
				return;
			}
			
			// 调试信息：打印fd对象的类型和可用方法
			Log.d(TAG, "VPN连接对象类型: " + fd.getClass().getName());
			Method[] methods = fd.getClass().getMethods();
			Log.d(TAG, "VPN连接对象可用方法:");
			for (Method method : methods) {
				Log.d(TAG, "  - " + method.getName() + "(" + 
					Arrays.toString(method.getParameterTypes()) + ")");
			}
			
			// 获取文件描述符
			try {
				if (isHarmonyOS()) {
					// 鸿蒙系统使用 VpnInterface
					Log.d(TAG, "鸿蒙系统：使用 VpnInterface");
					// 获取 VpnInterface 实例
					Class<?> vpnInterfaceClass = Class.forName("ohos.net.VpnInterface");
					Object vpnInterface = fd.getClass().getMethod("getInterface").invoke(fd);
					
					// 调试信息：打印VpnInterface对象的类型和可用方法
					Log.d(TAG, "VpnInterface对象类型: " + vpnInterface.getClass().getName());
					Method[] vpnInterfaceMethods = vpnInterface.getClass().getMethods();
					Log.d(TAG, "VpnInterface对象可用方法:");
					for (Method method : vpnInterfaceMethods) {
						Log.d(TAG, "  - " + method.getName() + "(" + 
							Arrays.toString(method.getParameterTypes()) + ")");
					}
					
					// 获取文件描述符
					Method getFdMethod = vpnInterfaceClass.getMethod("getFd");
					int fdInt = (int) getFdMethod.invoke(vpnInterface);
					Log.d(TAG, "获取到文件描述符值: " + fdInt);
					
					// 创建 ParcelFileDescriptor
					Class<?> pfdClass = Class.forName("android.os.ParcelFileDescriptor");
					Method fromFdMethod = pfdClass.getMethod("fromFd", int.class);
					tunFd = (ParcelFileDescriptor) fromFdMethod.invoke(null, fdInt);
				} else {
					// Android 系统直接获取 ParcelFileDescriptor
					Log.d(TAG, "Android系统：使用 ParcelFileDescriptor");
					tunFd = (ParcelFileDescriptor) fd.getClass().getMethod("getFileDescriptor").invoke(fd);
				}
				Log.d(TAG, "VPN连接建立成功");
			} catch (Exception e) {
				Log.e(TAG, "获取文件描述符失败: " + e.getMessage());
				Log.e(TAG, "错误堆栈: " + Arrays.toString(e.getStackTrace()));
				stopSelf();
				return;
			}

			// 启动TProxy服务
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
		} catch (Exception e) {
			Log.e(TAG, "VPN错误: " + e.getMessage());
			Log.e(TAG, "错误堆栈: " + Arrays.toString(e.getStackTrace()));
			stopSelf();
			return;
		}
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

		// 2. 在后台线程中停止 TProxy 服务
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Log.d(TAG, "在后台线程中停止 TProxy 服务");
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
				} catch (Exception e) {
					Log.e(TAG, "停止服务时发生错误: " + e.getMessage());
					Log.e(TAG, "错误堆栈: " + Arrays.toString(e.getStackTrace()));
					stopSelf();
				}
			}
		}).start();
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
