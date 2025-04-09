package hev.sockstun;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.widget.Toast;

import java.util.regex.Pattern;

public class Utils {
    /**
     * 显示Toast消息
     */
    public static void toast(Context context, String message, int duration) {
        Toast.makeText(context, message, duration).show();
    }

    public static void toast(Context context, String message) {
        toast(context, message, Toast.LENGTH_SHORT);
    }

    /**
     * 检查地址是否为IPv4或IPv6地址
     */
    public static boolean isIpAddress(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // 简单的IPv4验证
        String ipv4Regex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        // 简单的IPv6验证
        String ipv6Regex = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";

        return Pattern.matches(ipv4Regex, value) || Pattern.matches(ipv6Regex, value);
    }

    /**
     * 检查网络连接状态
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    /**
     * 获取广播Receiver的Flag
     */
    public static int receiverFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Context.RECEIVER_NOT_EXPORTED;
        } else {
            return 0;
        }
    }
}