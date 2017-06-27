package com.andy.mymediacodec.utils;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.os.Build;

/**
 * Created by Andy.chen on 2017/5/4.
 */

public class CameraUtils {

    public static int getFrontCamera() {
        int camCount = 0;
        int sdkVersion = Build.VERSION.SDK_INT;
        if (sdkVersion > Build.VERSION_CODES.GINGERBREAD) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            camCount = Camera.getNumberOfCameras(); // get cameras number
            for (int camIdx = 0; camIdx < camCount; camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    return camIdx;
                }
            }
        }
        return 0;
    }

    @TargetApi(9)
    public static int getBackCamera() {
        int camCount = 0;
        int sdkVersion = Build.VERSION.SDK_INT;
        if (sdkVersion > Build.VERSION_CODES.GINGERBREAD) {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            camCount = Camera.getNumberOfCameras(); // get cameras number

            for (int camIdx = 0; camIdx < camCount; camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    return camIdx;
                }
            }
        }
        return 0;
    }

}
