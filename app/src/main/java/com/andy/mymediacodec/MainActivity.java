package com.andy.mymediacodec;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.andy.mymediacodec.decoder.H264Decoder;
import com.andy.mymediacodec.encoder.H264Encoder;
import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;
import com.andy.mymediacodec.utils.AvcUtils;
import com.andy.mymediacodec.utils.PermissionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.andy.mymediacodec.utils.AvcUtils.DECODE_FILE_PATH;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private TextView mTextMessage;
    private PermissionUtils mPermissionUtils;
    private CameraSurfaceView mCameraSurfaceView;
    private SurfaceView mSurfaceViewRender;
    private Surface mSurfaceRender;
    private H264Encoder mH264Encoder;
    private H264Decoder mH264Decoder;
    private FrameBufferQueue mFrameBufferQueueDecoder;
    private FrameBufferQueue mFrameBufferQueueEncoder;
    Button mBtnEncodeCameraYuv;
    Button mBtnDecoderLocVideo;
    boolean mDecodingLocStreamFlag = false;
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title_home);
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }
    };


    private InputStream mH264LocFileStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mTextMessage = (TextView) findViewById(R.id.message);
        mCameraSurfaceView = (CameraSurfaceView) findViewById(R.id.surface_preview);
        mSurfaceViewRender = (SurfaceView) findViewById(R.id.surface_decode);
        mSurfaceRender = mSurfaceViewRender.getHolder().getSurface();

        mBtnEncodeCameraYuv = (Button) this.findViewById(R.id.button_encode_decode_video);
        mBtnDecoderLocVideo = (Button) this.findViewById(R.id.button_decode_video);

        mBtnEncodeCameraYuv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCamera();
            }
        });

        //decoder local video
        mBtnDecoderLocVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDecodingLocStreamFlag = !mDecodingLocStreamFlag;
                mH264LocFileStream = AvcUtils.getFileInputStream(DECODE_FILE_PATH);
                if(mH264LocFileStream == null) {
                    Toast.makeText(MainActivity.this,"Please record a video from camera first",Toast.LENGTH_SHORT);
                    return;
                }
                if(mDecodingLocStreamFlag) {
                    Log.d(TAG,"-------- START ----------");
                    //start decoder
                    startDecoder();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            readStreamData();
                        }
                    }).start();
                }

            }
        });
        mBtnEncodeCameraYuv.setEnabled(false);
        mBtnDecoderLocVideo.setEnabled(false);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPermissionUtils = new PermissionUtils(this);
            mPermissionUtils.setOnApplyPermissionListener(permissionListener);
            mPermissionUtils.applyPermissions();
        } else {
            mBtnEncodeCameraYuv.setEnabled(true);
            mBtnDecoderLocVideo.setEnabled(true);
        }

        File file = new File(DECODE_FILE_PATH);
        if (file.exists()) {
            mBtnDecoderLocVideo.setEnabled(true);
        }
    }

    private void readStreamData() {
        byte[] fileAllStreamBuf = AvcUtils.getBytes(mH264LocFileStream);
        int fileStreamSize = 0;
        if(fileAllStreamBuf != null) {
            fileStreamSize = fileAllStreamBuf.length;
            Log.d(TAG,"GET FILE BYTE SIZE = "+fileStreamSize);
            int decodeEachSize = 1024;
            byte[] decodeBuf = null;
            int startIndex = 0;
            int endIndex = fileStreamSize;
//            int endIndex = decodeEachSize > fileStreamSize ? fileStreamSize : decodeEachSize ;
            boolean isNalSpsPps = false;
            while(mDecodingLocStreamFlag) {
                if(mH264Decoder != null) {
                    if(!mH264Decoder.isDecodingReady()) {
                        continue;
                    }
                }

                if(startIndex >= fileStreamSize) {
                    //end of
                    Log.d(TAG,"======READ STREAM END=========");
                    break;
                }
                int endNalPrefix = AvcUtils.findH264NalPrefix(fileAllStreamBuf,startIndex + 5,fileStreamSize);
                Log.d(TAG,"find NAL prefix index = "+endNalPrefix);
                if(endNalPrefix != -1) {
                    //NAL sps/pps
//                    while (endNalPrefix < (fileStreamSize - 5)) {
                        if((fileAllStreamBuf[endNalPrefix + 4] & 0x1f) == AvcUtils.AVC_NAL_TYPE_H264SPS || (fileAllStreamBuf[endNalPrefix + 4] & 0x1f) == AvcUtils.AVC_NAL_TYPE_H264PPS ) {
                            endNalPrefix = AvcUtils.findH264NalPrefix(fileAllStreamBuf,endNalPrefix + 5,fileStreamSize);
                            isNalSpsPps = true;
//                            break;
                        }
//                    }

                    endIndex = endNalPrefix;

                } else {
                    endIndex = fileStreamSize;
                }
                decodeBuf = Arrays.copyOfRange(fileAllStreamBuf,startIndex,endIndex);

//                startIndex += decodeEachSize;
//                endIndex = startIndex + decodeEachSize;
//                if(endIndex > fileStreamSize) {
//                    //read the end
//                    endIndex = fileStreamSize;
//                    Log.d(TAG,"======READ STREAM END=========");
//                }
//                if(startIndex > fileStreamSize) {
//                    //reset
//                    startIndex = 0;
//                    endIndex = decodeEachSize;
//                    Log.d(TAG,"=====READ STREAM RESET========");
//                    mDecodingLocStreamFlag = false;
//                    continue;
//                }
                Log.d(TAG,"push decoder startIndex = "+startIndex +", endIndex = "+endIndex);
                FrameEntity frameEntity = new FrameEntity();
                frameEntity.setId(Long.toString(System.currentTimeMillis()));
                frameEntity.setBuf(decodeBuf);
                frameEntity.setSize(decodeBuf.length);
                frameEntity.setTimestamp(System.currentTimeMillis());
                mFrameBufferQueueDecoder.pushFrameData(frameEntity);

                //move the start index pointer
                startIndex = endIndex;
                try {
                    int gap = 1000/AvcUtils.FPS;
                    Log.d(TAG,"read file frame gap = "+gap);
                    Thread.sleep(gap);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (mPermissionUtils != null) {
            mPermissionUtils.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mPermissionUtils != null) {
            mPermissionUtils.onActivityResult(requestCode, resultCode, data);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private PermissionUtils.IAppPermissionListener permissionListener = new PermissionUtils.IAppPermissionListener() {
        @Override
        public void getPermissionSuccess() {
            //todo sth after grant permission
//            startCamera();
            mBtnEncodeCameraYuv.setEnabled(true);
            mBtnDecoderLocVideo.setEnabled(true);
        }

        @Override
        public void getPermissionFailed() {
            finish();
        }
    };

    private void startCamera() {
        if(mCameraSurfaceView != null) {
            try {
                mCameraSurfaceView.setVideoFrameListener(new CameraSurfaceView.VideoFrameListener() {
                    @Override
                    public void onPreviewFrame(byte[] data, int fps) {
                        if(mH264Encoder != null) {
                            FrameEntity frameEntity = new FrameEntity();
                            frameEntity.setId(Long.toString(System.currentTimeMillis()));
                            frameEntity.setBuf(data);
                            frameEntity.setTimestamp(System.currentTimeMillis());

                            //get the original YUV data push to encoder
                            mFrameBufferQueueEncoder.pushFrameData(frameEntity);
                        }
                    }
                });
                boolean isSuccess = mCameraSurfaceView.startCamera();
                Log.d(TAG,"startCamera isSuccess = "+isSuccess);
                if (isSuccess) {
                    startEncoder();
                    startDecoder();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startEncoder() {
        if(mH264Encoder == null) {
            if(mFrameBufferQueueEncoder == null) {
                mFrameBufferQueueEncoder = new FrameBufferQueue();
            }
            mH264Encoder = new H264Encoder(mFrameBufferQueueEncoder);
            mH264Encoder.setAvcDataFrameListener(new H264Encoder.AvcDataFrameListener() {
                @Override
                public void onAvcEncoderFrame(FrameEntity frameEntity) {
                    //encoder result
                    if(mH264Decoder != null) {
                     //   Log.d("H264Encoder"," onAvcEncoderFrame size = "+size);
//                        FrameEntity frameEntity = new FrameEntity();
//                        frameEntity.setBuf(frame);
//                        frameEntity.setSize(size);
//                        frameEntity.setTimestamp(System.currentTimeMillis());

//                        Log.d(TAG,"onAvcEncoderFrame thread = "+Thread.currentThread().getName());
                        //push the decoder and render to screen after get the encoder finish callback
                        if (mFrameBufferQueueDecoder != null) {
                            mFrameBufferQueueDecoder.pushFrameData(frameEntity);
                        }

                        try {
                            Thread.sleep(1000/AvcUtils.FPS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onAvcEncoderNALFrame(byte[] nalFrame) {
                    if(mH264Decoder != null) {
                     //   Log.d("H264Encoder"," onAvcEncoderNALFrame ");
//                        mH264Decoder.clearQueue();
//                        mH264Decoder.configDecoder(AvcUtils.WIDTH, AvcUtils.HEIGHT, ByteBuffer.wrap(nalFrame));
                    }
                }
            });
            mH264Encoder.startEncoderThread();
        }
    }

    private void startDecoder() {
        if(mH264Decoder == null) {
            mFrameBufferQueueDecoder = new FrameBufferQueue();
            mH264Decoder = new H264Decoder(mSurfaceRender, mFrameBufferQueueDecoder);
            mH264Decoder.startDecoderThread();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mDecodingLocStreamFlag = false;
        if(mCameraSurfaceView != null) {
            mCameraSurfaceView.stop();
        }
        if(mH264Encoder != null) {
            mH264Encoder.stopEncoderThread();
        }
        if(mH264Decoder != null) {
            mH264Decoder.stopDecoderThread();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mCameraSurfaceView != null) {
            mCameraSurfaceView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mCameraSurfaceView != null) {
            mCameraSurfaceView.destroyCamera();
        }
        if(mH264Encoder != null) {
            mH264Encoder.close();
        }
    }

    public static void toHexString(int i) {
        byte b = (byte)i;
        Log.d("TEST",Integer.toHexString(b));
        Log.d("TEST",Integer.toHexString(b & 0xff));
    }

}
