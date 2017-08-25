package com.andy.mymediacodec.video;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.andy.mymediacodec.utils.AvcUtils;
import com.andy.mymediacodec.utils.CameraUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by Andy.chen on 2017/5/4.
 */

public class CameraYuvCapture extends SurfaceView implements SurfaceHolder.Callback {
    private final static String TAG = CameraYuvCapture.class.getSimpleName();
    private final static int ROTATION_OBVERSE_FACE = 2;//正面
    private final static int ROTATION_REVERSE_FACE = 1;//反面

    private final static boolean ORIENTATION_LISTENER_ENABLE = false;
    private SurfaceHolder mSurfaceHolder = null;
    private Camera mCamera = null;
    private Context mContext;
    private boolean mIsPreviewing;
    private long mLastCalFpsTick;
    private int mFpsCount;
    private boolean mConnectedSocket;
    private OrientationEventListener mOrientationListener;
    private int mCurrentRotation;
    private int mLastRotation;

    public CameraYuvCapture(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public CameraYuvCapture(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public CameraYuvCapture(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CameraYuvCapture(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        init();
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "=== surfaceCreated ====");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "=== surfaceChanged ====");
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mSurfaceHolder == null || mSurfaceHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        stop();

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        resume();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "=== surfaceDestroyed ====");
    }

    private void init() {
        mSurfaceHolder = this.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setOrientationListener();
    }


    public void resume() {
        try {
            startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
            }
        } catch (Exception e) {
        }
    }


    private void changeCameraRotation(int rotation) {
        Log.d(TAG, "==========changeCameraRotation=========");
        if (mCamera == null) {
            return;
        }
        mCamera.setDisplayOrientation(rotation);

    }


    public boolean startCamera() throws IOException {
        Log.d(TAG, "==========start of startCamera=========");

        if (mIsPreviewing) {
            stop();
        }
        try {
            int camIndex = CameraUtils.getFrontCamera();
            Log.d(TAG, "getFrontCamera index = " + camIndex);
            mCamera = Camera.open(camIndex);

            int displayOrientation = getCameraDisplayOrientation((Activity) mContext,camIndex);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                mCamera.setDisplayOrientation(displayOrientation);
            }
        } catch (Exception e) {
            Log.e(TAG, "camera open exception == > " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        startPreview();
        mIsPreviewing = true;

        printParameters();
        Log.d(TAG, "==========end of startCamera=========");
        return true;
    }

    private void setOrientationListener() {
        if(!ORIENTATION_LISTENER_ENABLE) {
            return;
        }

        mOrientationListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int rotation) {
//                Log.e(TAG, "OrientationEventListener rotation = " + rotation);
                if(rotation >= 70 && rotation < 100) {
                    mCurrentRotation = ROTATION_REVERSE_FACE;
                    if(mCurrentRotation != mLastRotation) {
                        Log.e(TAG, "OrientationEventListener rotation >= 70 && rotation < 100 " );
                        if(mCamera != null) {
                            changeCameraRotation(180);
                        }
                        mLastRotation = mCurrentRotation;
                    }

                } else if (rotation >= 270) {
                    mCurrentRotation = ROTATION_OBVERSE_FACE;
                    //正向
                    if(mCurrentRotation != mLastRotation) {
                        Log.e(TAG, "OrientationEventListener rotation >= 270 " );
                        if(mCamera != null) {
                            changeCameraRotation(0);
                        }
                        mLastRotation = mCurrentRotation;
                    }
                }
            }
        };
        mOrientationListener.enable();
    }

    private static int getCameraDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG,"camera getCameraDisplayOrientation screen rotation = "+rotation);
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;

            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        Log.d(TAG,"camera getCameraDisplayOrientation info.facing = "+info.facing+", info.ori = "+info.orientation +", degrees = "+degrees);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {
            // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        Log.d(TAG,"camera getCameraDisplayOrientation "+result);
        return result;
    }

    private void startPreview() throws IOException {
        if (mCamera == null) {
            return;
        }
       //set parameters
        setPreviewParameters();
        mCamera.setPreviewCallback(new CaptureYuvStream());
        mCamera.setErrorCallback(new Camera.ErrorCallback() {
            @Override
            public void onError(int i, Camera camera) {
                Log.i(TAG, "Camera onError callback");
            }
        });

        Log.i(TAG, "before startPreview");
        mCamera.setPreviewDisplay(mSurfaceHolder);
        mCamera.startPreview();
        Log.i(TAG, "after startPreview");
    }

        private void setPreviewParameters() {
    /* Camera Service settings*/
        Camera.Parameters para = mCamera.getParameters();
        try {
//            para.setPictureFormat(ImageFormat.JPEG);
            para.setPreviewFormat(ImageFormat.NV21); //Sets the image format for preview picture，NV21 in default

            List<Camera.Size> previewSizes = para.getSupportedPreviewSizes();
            for (Camera.Size size : previewSizes) {
                Log.i(TAG, "getSupportedPreviewSizes " + size.width + "," + size.height);
            }
            para.setPreviewSize(AvcUtils.WIDTH, AvcUtils.HEIGHT);

            List<int[]> supportFpsRange = para.getSupportedPreviewFpsRange();
            for (int[] fpsRange : supportFpsRange) {
                Log.i(TAG, "getSupportedPreviewFpsRange " + fpsRange[0] + "," + fpsRange[1]);
            }
            // 每秒显示5帧
//            para.setPreviewFrameRate(FPS);
//            para.setPreviewFpsRange(5, 10);
            List<String> supportedFocus = para.getSupportedFocusModes();
            if (supportedFocus != null && supportedFocus.indexOf(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) >= 0) {
                Log.i(TAG, "set param, setFocusMode to FOCUS_MODE_CONTINUOUS_VIDEO");
                para.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            mCamera.setParameters(para);

        } catch (Exception ex) {
            Log.i(TAG, "camera set parameters exception=" + ex);
            ex.printStackTrace();
        }
    }

    /**
     * 设置后的图片大小和预览大小以及帧率
     */
    private void printParameters() {
        int previewHeight, previewWidth;
        Camera.Size csize = mCamera.getParameters().getPreviewSize();
        previewHeight = csize.height;
        previewWidth = csize.width;
        Log.i(TAG, "printParameters:: previewSize:width: " + csize.width + " height: " + csize.height);
        csize = mCamera.getParameters().getPictureSize();
        Log.i(TAG, "printParameters:: pictruesize:width: " + csize.width + " height: " + csize.height);
        Log.i(TAG, "printParameters:: previewformate is " + mCamera.getParameters().getPreviewFormat());
        Log.i(TAG, "printParameters:: previewframetate is " + mCamera.getParameters().getPreviewFrameRate());
    }

    class CaptureYuvStream implements Camera.PreviewCallback {
        public CaptureYuvStream() {
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            synchronized (this) {
                ByteArrayOutputStream bos = null;
                byte[] mVideoFrameBuf = null;
                Camera.Size size = camera.getParameters().getPreviewSize();

                try {
                    int fpsTimes = calFps();
                    if(mVideoFrameListener != null) {
                        mVideoFrameListener.onPreviewFrame(data,fpsTimes);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    Log.e(TAG, "CameraYuvStreamInput :" + ex.getMessage());
                } finally {
                    if (bos != null) {
                        try {
                            bos.close();
                            bos = null;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private int calFps() {
        long curTick = System.currentTimeMillis();
        if (mLastCalFpsTick == 0) {
            mLastCalFpsTick = curTick;
        }
        if (curTick > mLastCalFpsTick + 1000) {
            Log.i(TAG, "======> Current FPS = " + mFpsCount);
            mFpsCount = 0;
            mLastCalFpsTick = curTick;
        } else {
            mFpsCount++;
        }
        return mFpsCount;
    }


    public void destroyCamera() {
        Log.d(TAG, "destroyCamera");
        if (mSurfaceHolder != null) {
            mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mSurfaceHolder = null;
        }
        if (mCamera != null) {
            Log.d(TAG, "destroyCamera stop preview");
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.setErrorCallback(null);

            mCamera.release();
            mCamera = null;
        }
        if (mOrientationListener != null) {
            mOrientationListener.disable();
            mOrientationListener = null;
        }
//        if (ConstantsVideo.SWITCH_DEL_LOCAL_TEMP_PIC_ENABLE) {
//            FileUtils.delFolder(FileManagement.getImageTempFolderPath());
//        }
    }

    private void saveToLocal(String fileName,byte[] bufStream) {
        Log.d(TAG, "start save to local ");
        FileOutputStream fos = null;
//        String picPath = FileManagement.getTempFilePath("yuvImage_" + ConstantComm.getTimestampName() + "-" + fileName + ConstantComm.PIC_NAME_SUFFIX_JPG);
        String picPath = "/sdcard/mymediacodec/";
        Log.d(TAG, "===> capture the picture path = " + picPath);
        if (TextUtils.isEmpty(picPath)) {
            return;
        }
        if (bufStream == null) {
            return;
        }
        File file = new File(picPath);
        if (file.exists()) {
            file.delete();
        }
        try {
            fos = new FileOutputStream(file);
            fos.write(bufStream);
            fos.flush();
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                fos = null;
            }
        }
        Log.d(TAG, "end of save to local ");
    }

    public boolean isConnectedSocket() {
        return mConnectedSocket;
    }

    public void setConnectedSocket(boolean mConnectedSocket) {
        Log.d(TAG, "setConnectedSocket = " + mConnectedSocket);
        this.mConnectedSocket = mConnectedSocket;
    }

    private VideoFrameListener mVideoFrameListener;
    public void setVideoFrameListener(VideoFrameListener listener) {
        mVideoFrameListener = listener;
    }
    public interface VideoFrameListener{
        public void onPreviewFrame(byte[] data,int fps);
    }
}
