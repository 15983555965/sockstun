package hev.sockstun;


import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledExecutorService;

import hev.sockstun.v2ray.Constants;
import hev.sockstun.v2ray.IOUtils;

public class MyVpnApplication extends Application {
    private static final String[] EXECUTABLES = {Constants.PDNSD,
            Constants.PROXYCHAINS4,
            Constants.SS_LOCAL,
            Constants.TUN2SOCKS};
    private static final String TAG="MyVpnApplication";
    public static MyVpnApplication app;

    static {
        // 静态初始化块，如果有需要的话
    }

    public SharedPreferences settings;
    public SharedPreferences.Editor editor;
    public ScheduledExecutorService mThreadPool;

    private void initVariable() {
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        editor = settings.edit();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        initVariable();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

    }


    private void copyAssets(String path) {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list(path);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (files != null) {
            for (String file : files) {
                InputStream inputStream = null;
                FileOutputStream fos = null;
                try {
                    if (path.isEmpty()) {
                        inputStream = assetManager.open(file);
                    } else {
                        inputStream = assetManager.open(path + File.separator + file);
                    }
                    fos = new FileOutputStream(getApplicationInfo().dataDir + "/" + file);
                    IOUtils.copy(inputStream, fos);
                } catch (IOException e) {
                    // VayLog.e(TAG, "copyAssets", e);
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (Exception e) {
                        // VayLog.e(TAG, "copyAssets", e);
                    }
                    try {
                        if (fos != null) {
                            fos.close();
                        }
                    } catch (Exception e) {
                        // VayLog.e(TAG, "copyAssets", e);
                    }
                }
            }
        }
    }

    public void crashRecovery() {
        for (String exe : EXECUTABLES) {
            try {
                new ProcessBuilder("sh", "killall", exe).start();
                new ProcessBuilder("sh", "rm", "-f", getApplicationInfo().dataDir + File.separator + exe + "-vpn.conf").start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void copyAssets() {
        // ensure executables are killed before writing to them
        crashRecovery();
        copyAssets("acl");

        for (String exe : EXECUTABLES) {
            try {
                new ProcessBuilder("sh", "chmod", "755", getApplicationInfo().nativeLibraryDir + File.separator + exe).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // save current version code
//        editor.putInt(Constants.currentVersionCode, 1)
//                .apply();
    }

    public void updateAssets() {
//        if (settings.getInt(Constants.currentVersionCode, -1) != 1) {
            copyAssets();
//        }
    }
}