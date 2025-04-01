package hev.sockstun.certificate;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class CertificateChecker {
    private static final String TAG = "CertificateChecker";

    /**
     * 获取应用签名证书
     * @param context 上下文
     * @param packageName 包名
     * @return 签名证书信息
     */
    public static Signature[] getSignatures(Context context, String packageName) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo = context.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                packageInfo = context.getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            }
            return packageInfo.signatures;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found: " + packageName, e);
            return null;
        }
    }

    /**
     * 验证证书有效性
     * @param certificate X509证书
     * @return 是否有效
     */
    public static boolean verifyCertificate(X509Certificate certificate) {
        try {
            certificate.checkValidity();
            return true;
        } catch (CertificateException e) {
            Log.e(TAG, "Certificate verification failed", e);
            return false;
        }
    }

    /**
     * 记录证书信息
     * @param signatures 签名数组
     */
    public static void logCertificateInfo(Signature[] signatures) {
        if (signatures != null) {
            for (Signature signature : signatures) {
                Log.d(TAG, "Certificate: " + signature.toCharsString());
            }
        }
    }
} 