package hev.sockstun.v2ray;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Semaphore;

public class GuardedProcess {
    private static final String TAG = "GuardedProcess";

    private final List<String> cmd;
    private Thread guardThread;
    private boolean isDestroyed = false;
    private Process process;
    private boolean isRestart = false;

    public GuardedProcess(List<String> cmd) {
        this.cmd = cmd;
    }

    public interface RestartCallback {
        void onRestart();
    }

    public GuardedProcess start() {
        return start(null);
    }

    public GuardedProcess start(final RestartCallback onRestartCallback) {
        Log.d(TAG, "开始启动进程，命令: " + String.join(" ", cmd));
        final Semaphore semaphore = new Semaphore(1);
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Log.e(TAG, "获取信号量失败: " + e.getMessage());
            e.printStackTrace();
        }

        guardThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    RestartCallback callback = null;
                    while (!isDestroyed) {
                        Log.d(TAG, "准备执行命令: " + String.join(" ", cmd));
                        long startTime = System.currentTimeMillis();

                        try {
                            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
                            processBuilder.redirectErrorStream(true);
                            Log.d(TAG, "进程工作目录: " + processBuilder.directory().getAbsolutePath());
                            process = processBuilder.start();
                            Log.d(TAG, "进程启动成功");

                            InputStream inputStream = process.getInputStream();
                            new StreamLogger(inputStream, TAG).start();

                            if (callback == null) {
                                callback = onRestartCallback;
                                Log.d(TAG, "设置初始回调");
                            } else if (callback != null) {
                                Log.d(TAG, "执行重启回调");
                                callback.onRestart();
                            }

                            semaphore.release();
                            Log.d(TAG, "等待进程结束");
                            int exitCode = process.waitFor();
                            Log.d(TAG, "进程结束，退出码: " + exitCode);

                            synchronized (this) {
                                if (isRestart) {
                                    Log.d(TAG, "进程被标记为重启");
                                    isRestart = false;
                                } else {
                                    if (System.currentTimeMillis() - startTime < 1000) {
                                        Log.e(TAG, "进程退出太快，停止守护: " + String.join(" ", cmd));
                                        isDestroyed = true;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "执行命令失败: " + e.getMessage(), e);
                            throw e;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "守护线程异常: " + e.getMessage(), e);

                    if (process == null) {
                        Log.e(TAG, "进程对象为空");
                        return;
                    }

                    try {
                        if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
                            Field fPid = process.getClass().getDeclaredField("pid");
                            fPid.setAccessible(true);
                            int pid = fPid.getInt(process);
                            Log.d(TAG, "尝试通过PID终止进程: " + pid);
                            android.os.Process.killProcess(pid);
                        } else {
                            Log.d(TAG, "尝试通过destroy()终止进程");
                            process.destroy();
                        }
                    } catch (Exception e2) {
                        Log.e(TAG, "终止进程失败: " + e2.getMessage(), e2);
                    }
                } finally {
                    semaphore.release();
                }
            }
        }, "GuardThread-" + cmd);
        guardThread.start();
        return this;
    }

    public void destroy() {
        isDestroyed = true;
        if (guardThread != null) {
            guardThread.interrupt();
        }
        if (process != null) {
            process.destroy();
        }
        try {
            if (guardThread != null) {
                guardThread.join();
            }
        } catch (InterruptedException e) {
            // Ignored
        }
    }

    private class StreamLogger extends Thread {
        private final InputStream inputStream;
        private final String tag;

        public StreamLogger(InputStream inputStream, String tag) {
            this.inputStream = inputStream;
            this.tag = tag;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(tag, "进程输出: " + line);
                }
            } catch (IOException e) {
                Log.e(tag, "读取进程输出失败: " + e.getMessage());
            }
        }
    }
}