package hev.sockstun;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class FileUtils {
    private static final String TAG = "FileUtils";
    public static final String CONFIG_FILE = "vpn_config.properties";
    private static final String ENCRYPT_KEY = "B3c61EC0v9D5A2f7"; // 示例密钥，实际应使用密钥管理方案

    // 检查配置文件是否存在
    public static boolean configExists(Context context) {
        File file = new File(context.getFilesDir(), CONFIG_FILE);
        return file.exists();
    }

    // 保存加密后的授权码到配置文件
    public static void saveAuthCode(Context context, String code) {
        Properties prop = new Properties();
        FileOutputStream fos = null;

        try {
            // AES加密
            SecretKeySpec key = new SecretKeySpec(ENCRYPT_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(code.getBytes());

            // Base64编码保存
            String encryptedCode = Base64.encodeToString(encrypted, Base64.DEFAULT);

            prop.setProperty("auth_code", encryptedCode);
            fos = context.openFileOutput(CONFIG_FILE, Context.MODE_PRIVATE);
            prop.store(fos, null);
        } catch (Exception e) {
            Log.e(TAG, "保存配置失败: " + e.getMessage());
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException e) { /* 忽略 */ }
            }
        }
    }

    // 从配置文件读取并解密授权码
    public static String readAuthCode(Context context) {
        Properties prop = new Properties();
        FileInputStream fis = null;

        try {
            fis = context.openFileInput(CONFIG_FILE);
            prop.load(fis);
            String encryptedCode = prop.getProperty("auth_code");

            // Base64解码
            byte[] encrypted = Base64.decode(encryptedCode, Base64.DEFAULT);

            // AES解密
            SecretKeySpec key = new SecretKeySpec(ENCRYPT_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted);
        } catch (Exception e) {
            Log.e(TAG, "读取配置失败: " + e.getMessage());
            return null;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException e) { /* 忽略 */ }
            }
        }
    }
}