package hev.sockstun.certificate;

import android.webkit.URLUtil;

public class URLUtils {
    private static final String TAG = "URLUtils";

    /**
     * 检查URL是否为HTTPS
     * @param url 需要检查的URL
     * @return 是否为HTTPS URL
     */
    public static boolean isHttpsUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("https");
    }

    /**
     * 检查URL是否有效
     * @param url 需要检查的URL
     * @return 是否为有效URL
     */
    public static boolean isValidUrl(String url) {
        return URLUtil.isValidUrl(url);
    }

    /**
     * 检查URL是否为HTTP
     * @param url 需要检查的URL
     * @return 是否为HTTP URL
     */
    public static boolean isHttpUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("http");
    }
} 