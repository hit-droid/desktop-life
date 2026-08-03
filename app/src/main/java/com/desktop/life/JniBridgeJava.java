package com.desktop.life;

import android.app.Activity;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

public class JniBridgeJava {
    public static native void nativeOnStart();
    public static native void nativeOnPause();
    public static native void nativeOnStop();
    public static native void nativeOnDestroy();
    public static native void nativeOnSurfaceCreated();
    public static native void nativeOnSurfaceChanged(int width, int height);
    public static native void nativeOnDrawFrame();
    public static native void nativeOnTouchesBegan(float pointX, float pointY);
    public static native void nativeOnTouchesEnded(float pointX, float pointY);
    public static native void nativeOnTouchesMoved(float pointX, float pointY);

    // ==================== AI动作控制 ====================
    /** 执行AI动作（动作+表情组合） */
    public static native void nativePerformAiAction(String motionGroup, int motionNo, String expressionId, int priority);
    /** 播放指定动作 */
    public static native void nativeStartMotion(String motionGroup, int motionNo, int priority);
    /** 设置表情 */
    public static native void nativeSetExpression(String expressionId);

    public static void SetContext(Context context) {
        JniBridgeJava.context = context;
    }

    public static void SetActivityInstance(Activity activity) {
        activityInstance = activity;
    }

    public static byte[] LoadFile(String filePath) {
        InputStream fileData = null;
        try {
            fileData = context.getAssets().open(filePath);
            int fileSize = fileData.available();
            byte[] fileBuffer = new byte[fileSize];
            fileData.read(fileBuffer, 0, fileSize);
            return fileBuffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (fileData != null) fileData.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void MoveTaskToBack() {
        if (activityInstance != null) {
            activityInstance.moveTaskToBack(true);
        }
    }

    private static Activity activityInstance;
    private static Context context;
    private static final String LIBRARY_NAME = "Live2DEngine";

    static {
        System.loadLibrary(LIBRARY_NAME);
    }
}