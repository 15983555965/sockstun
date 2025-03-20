package hev.sockstun;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

// VPNManager.java
public class VPNManager {
    private Context context;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private long startTime = 0;
    //private long pausedTime = 0;

    private boolean isConnected = false;

    public VPNManager(Context context) {
        this.context = context;
        this.timerHandler = new Handler(Looper.getMainLooper());
        startTime = System.currentTimeMillis();
        initTimer();
    }


    private void initTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                //if (isTimerPaused) return;
                long currentTime = System.currentTimeMillis() - startTime ;
                if (timerListener != null) {
                    timerListener.onTimerUpdate(currentTime);
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
    }

    public void startTiming() {
        // 新连接
        startTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);
    }

    public void stopTiming() {

        isConnected = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void resetTimer() {
        startTime = System.currentTimeMillis();

    }

    public interface TimerListener {
        void onTimerUpdate(long millis);
    }

    private TimerListener timerListener;

    public void setTimerListener(TimerListener listener) {
        this.timerListener = listener;
    }
}
