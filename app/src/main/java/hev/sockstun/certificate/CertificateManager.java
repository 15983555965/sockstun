package hev.sockstun.certificate;

import android.content.Context;
import android.content.pm.Signature;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CertificateManager {
    private static final String TAG = "CertificateManager";
    private static CertificateManager instance;
    private Context context;
    private List<CertificateConfig.Certificate> certificates;

    private CertificateManager(Context context) {
        this.context = context.getApplicationContext();
        this.certificates = new ArrayList<>();
    }

    public static synchronized CertificateManager getInstance(Context context) {
        if (instance == null) {
            instance = new CertificateManager(context);
        }
        return instance;
    }

    /**
     * 加载证书
     * @param path 证书路径
     * @return 是否加载成功
     */
    public boolean loadCertificate(String path) {
        try {
            File certificateFile = new File(path);
            if (!certificateFile.exists()) {
                Log.e(TAG, "Certificate file not found: " + path);
                return false;
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            FileInputStream fis = new FileInputStream(certificateFile);
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            fis.close();

            if (CertificateChecker.verifyCertificate(cert)) {
                CertificateConfig.Certificate certificate = new CertificateConfig.Certificate();
                certificate.setPath(path);
                certificate.setEnabled(true);
                certificates.add(certificate);
                return true;
            }
            return false;
        } catch (CertificateException | IOException e) {
            Log.e(TAG, "Failed to load certificate: " + path, e);
            return false;
        }
    }

    /**
     * 获取所有证书
     * @return 证书列表
     */
    public List<CertificateConfig.Certificate> getCertificates() {
        return certificates;
    }

    /**
     * 清除所有证书
     */
    public void clearCertificates() {
        certificates.clear();
    }

    /**
     * 检查应用签名
     * @param packageName 包名
     * @return 签名信息
     */
    public Signature[] checkAppSignature(String packageName) {
        Signature[] signatures = CertificateChecker.getSignatures(context, packageName);
        if (signatures != null) {
            CertificateChecker.logCertificateInfo(signatures);
        }
        return signatures;
    }
} 