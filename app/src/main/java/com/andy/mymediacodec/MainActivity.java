package com.andy.mymediacodec;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import com.andy.mymediacodec.audio.AudioPcmCapture;
import com.andy.mymediacodec.audio.decoder.AACDecoder;
import com.andy.mymediacodec.constants.Define;
import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;
import com.andy.mymediacodec.utils.AvcUtils;
import com.andy.mymediacodec.utils.FileUtils;
import com.andy.mymediacodec.utils.LocalStreamReader;
import com.andy.mymediacodec.video.CameraYuvCapture;
import com.andy.mymediacodec.video.decoder.H264Decoder;
import com.andy.mymediacodec.video.encoder.H264Encoder;
import com.tools.permissionlib.PermissionUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static com.andy.mymediacodec.constants.Define.DECODE_LOCAL_FILE_PATH_AAC;


public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private TextView mTextMessage;
    private PermissionUtils mPermissionUtils;
    private CameraYuvCapture mCameraYuvCapture;
    private SurfaceView mSurfaceViewRender;
    private Surface mSurfaceRender;
    private H264Encoder mH264Encoder;
    private H264Decoder mH264Decoder;
    private AACDecoder mAACDecoder;
    private FrameBufferQueue mAACDecoderQueue;

    private FrameBufferQueue mH264DecoderQueue;
    private FrameBufferQueue mH264EncoderQueue;
    Button mBtnEncodeCameraYuv;
    Button mBtnDecoderLocVideo;
    boolean mDecodingLocStreamFlag = false;

    //Audio
    AudioPcmCapture mAudioPcmCapture;
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
        mCameraYuvCapture = (CameraYuvCapture) findViewById(R.id.surface_preview);
        mSurfaceViewRender = (SurfaceView) findViewById(R.id.surface_decode);
        mSurfaceRender = mSurfaceViewRender.getHolder().getSurface();

        mBtnEncodeCameraYuv = (Button) this.findViewById(R.id.button_encode_decode_video);
        mBtnDecoderLocVideo = (Button) this.findViewById(R.id.button_decode_video);

        startDecoder();
        mBtnEncodeCameraYuv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCamera();

//                AudioRecordThread audioRecordThread = new AudioRecordThread(null);
//                audioRecordThread.start();
            }
        });

        //decoder local video
        mBtnDecoderLocVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDecodingLocStreamFlag = !mDecodingLocStreamFlag;
                if(mDecodingLocStreamFlag) {
                    decodeAVCFromLocal();

                    decodeAACFromLocal();
                }else {
                    stopDecoder();
                }

            }
        });
        mBtnEncodeCameraYuv.setEnabled(false);
        mBtnDecoderLocVideo.setEnabled(false);

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPermissionUtils = new PermissionUtils(this, permissionListener);
        } else {
            mBtnEncodeCameraYuv.setEnabled(true);

            File file = new File(Define.DECODE_LOCAL_FILE_PATH_H264);
            if (file.exists()) {
                mBtnDecoderLocVideo.setEnabled(true);
            }
        }

    }

    private void decodeAACFromLocal() {
        Log.d(TAG,"-----decodeAACFromLocal -------");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LocalStreamReader.readLocalAACFileStreaming(DECODE_LOCAL_FILE_PATH_AAC, new LocalStreamReader.IFrameDataCallback() {
                        @Override
                        public void pushData(byte[] buf, int offset, int size) {
                            if (mAACDecoderQueue != null) {
                                FrameEntity frameEntity = new FrameEntity();
                                frameEntity.setId(":: AAC LOCAL ::");
                                frameEntity.setBuf(buf);
                                frameEntity.setSize(size);
                                mAACDecoderQueue.pushFrameData(frameEntity);
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();


    }

    private void decodeAVCFromLocal() {
        mH264LocFileStream = AvcUtils.getFileInputStream(Define.DECODE_LOCAL_FILE_PATH_H264);
        if (mH264LocFileStream == null) {
            Toast.makeText(MainActivity.this, "Please record a video from camera first", Toast.LENGTH_SHORT);
            return;
        }
        Log.d(TAG, "-------- START ----------");
        //start decoder
        startDecoder();
        new Thread(new Runnable() {
            @Override
            public void run() {
                readStreamData();
            }
        }).start();

    }

    private void readStreamData() {
        byte[] fileAllStreamBuf = AvcUtils.getBytes(mH264LocFileStream);
//        AvcUtils.printByteData("read src file byte : ",fileAllStreamBuf);
        int fileStreamSize = 0;
        if (fileAllStreamBuf != null) {
            fileStreamSize = fileAllStreamBuf.length;
            Log.d(TAG, "GET FILE BYTE SIZE = " + fileStreamSize);
            int decodeEachSize = 1024;
            byte[] decodeBuf = null;
            int startIndex = 0;
            int endIndex = fileStreamSize;
//            int endIndex = decodeEachSize > fileStreamSize ? fileStreamSize : decodeEachSize ;
            boolean isNalSpsPps = false;
            while (mDecodingLocStreamFlag) {
                if (mH264Decoder != null) {
                    if (!mH264Decoder.isDecodingReady()) {
                        continue;
                    }
                }
                if (startIndex >= fileStreamSize) {
                    //end of
                    Log.d(TAG, "======READ STREAM END=========");
                    break;
                }
                //find 0,0,0,1
                int endNalPrefix = AvcUtils.findH264NalPrefix(fileAllStreamBuf, startIndex + 5, fileStreamSize);
                Log.d(TAG, "find NAL prefix index = " + endNalPrefix);
                if (endNalPrefix != -1) {
                    //NAL sps/pps
//                    while (endNalPrefix < (fileStreamSize - 5)) {
                    if ((fileAllStreamBuf[endNalPrefix + 4] & 0x1f) == AvcUtils.AVC_NAL_TYPE_H264SPS || (fileAllStreamBuf[endNalPrefix + 4] & 0x1f) == AvcUtils.AVC_NAL_TYPE_H264PPS) {
                        endNalPrefix = AvcUtils.findH264NalPrefix(fileAllStreamBuf, endNalPrefix + 5, fileStreamSize);
                        isNalSpsPps = true;
//                            break;
                    }
//                    }

                    endIndex = endNalPrefix;

                } else {
                    endIndex = fileStreamSize;
                }
                decodeBuf = Arrays.copyOfRange(fileAllStreamBuf, startIndex, endIndex);
                Log.d(TAG, "push decoder startIndex = " + startIndex + ", endIndex = " + endIndex);
//                AvcUtils.printByteData("read frame data : ",decodeBuf);
                FrameEntity frameEntity = new FrameEntity();
                frameEntity.setId("H264 data");
                frameEntity.setBuf(decodeBuf);
                frameEntity.setSize(decodeBuf.length);
                frameEntity.setTimestamp(System.currentTimeMillis());
                if(mH264DecoderQueue != null)
                    mH264DecoderQueue.pushFrameData(frameEntity);

                //move the start index pointer
                startIndex = endIndex;
                try {
                    int gap = 1000 / AvcUtils.FPS;
                    Log.d(TAG, "read file frame gap = " + gap);
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

    private BufferedOutputStream mOutputStreamYuv;

    private void createRawYuvFile() {
        String folderPath = Environment.getExternalStorageDirectory() + File.separator + Define.SDCARD_TEMP_FILE_DIR;
        File fileFolder = FileUtils.createFolder(folderPath);
        try {
            File file = FileUtils.createFile(fileFolder, Define.SDCARD_TEMP_FILE_NAME_YUV);
            mOutputStreamYuv = new BufferedOutputStream(new FileOutputStream(file));
            Log.i(TAG, "mOutputStreamYuv initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCamera() {
        mAudioPcmCapture = new AudioPcmCapture();
        if (mCameraYuvCapture != null) {
            try {
                mCameraYuvCapture.setVideoFrameListener(new CameraYuvCapture.VideoFrameListener() {
                    @Override
                    public void onPreviewFrame(byte[] data, int fps) {
                        if (mH264Encoder != null) {
                            FrameEntity frameEntity = new FrameEntity();
                            frameEntity.setId(Long.toString(System.currentTimeMillis()));
                            frameEntity.setBuf(data);
                            frameEntity.setTimestamp(System.currentTimeMillis());


                            //get the original YUV data push to encoder
                            if(mH264EncoderQueue != null) {
                                mH264EncoderQueue.pushFrameData(frameEntity);
                            }
                            //sava yuv file
                            /*
                            try {
                                if(mOutputStreamYuv != null) {
                                    mOutputStreamYuv.write(data);
                                    mOutputStreamYuv.flush();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            */
                        }
                    }
                });
                boolean isSuccess = mCameraYuvCapture.startCamera();
                Log.d(TAG, "startCamera isSuccess = " + isSuccess);
                if (isSuccess) {
//                    createRawYuvFile();
                    startEncoder();
                    startDecoder();

                    if (mAudioPcmCapture != null)
                        mAudioPcmCapture.startRecord();

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startEncoder() {
        if (mH264Encoder == null) {
            if (mH264EncoderQueue == null) {
                mH264EncoderQueue = new FrameBufferQueue();
            }
            mH264Encoder = new H264Encoder(mH264EncoderQueue);
            mH264Encoder.setAvcDataFrameListener(new H264Encoder.AvcDataFrameListener() {
                @Override
                public void onAvcEncoderFrame(FrameEntity frameEntity) {
                    //encoder result
                    if (mH264Decoder != null) {
                        //   Log.d("H264Encoder"," onAvcEncoderFrame size = "+size);
//                        FrameEntity frameEntity = new FrameEntity();
//                        frameEntity.setBuf(frame);
//                        frameEntity.setSize(size);
//                        frameEntity.setTimestamp(System.currentTimeMillis());

//                        Log.d(TAG,"onAvcEncoderFrame thread = "+Thread.currentThread().getName());
                        //push the decoder and render to screen after get the encoder finish callback
                        if (mH264DecoderQueue != null) {
                            mH264DecoderQueue.pushFrameData(frameEntity);
                        }

                        try {
                            Thread.sleep(1000 / AvcUtils.FPS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onAvcEncoderNALFrame(byte[] nalFrame) {
                    if (mH264Decoder != null) {
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
        if (mH264Decoder == null) {
            mH264DecoderQueue = new FrameBufferQueue();
            mH264Decoder = new H264Decoder(mSurfaceRender, mH264DecoderQueue);
            mH264Decoder.startDecoderThread();
        }

        if(mAACDecoder == null) {
            mAACDecoderQueue = new FrameBufferQueue();
            mAACDecoder = new AACDecoder(mAACDecoderQueue);
            mAACDecoder.start();
        }

    }

    private void stopDecoder() {
        if (mCameraYuvCapture != null) {
            mCameraYuvCapture.stop();
        }
        if (mH264Encoder != null) {
            mH264Encoder.stopEncoderThread();
        }
        if (mH264Decoder != null) {
            mH264Decoder.stopDecoderThread();
        }
        if(mAACDecoder != null) {
            mAACDecoder.stop();
            mAACDecoder = null;
            mAACDecoderQueue.clearQueue();
            mAACDecoderQueue = null;
        }

        if (mAudioPcmCapture != null)
            mAudioPcmCapture.stopRecord();
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (mOutputStreamYuv != null) {
            try {
                mOutputStreamYuv.flush();
                mOutputStreamYuv.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mDecodingLocStreamFlag = false;
        if (mCameraYuvCapture != null) {
            mCameraYuvCapture.stop();
        }
        if (mH264Encoder != null) {
            mH264Encoder.stopEncoderThread();
        }
        if (mH264Decoder != null) {
            mH264Decoder.stopDecoderThread();
        }
        if(mAACDecoder != null) {
            mAACDecoder.stop();
            mAACDecoder = null;
            mAACDecoderQueue.clearQueue();
            mAACDecoderQueue = null;
        }

        if (mAudioPcmCapture != null)
            mAudioPcmCapture.stopRecord();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mCameraYuvCapture != null) {
            mCameraYuvCapture.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraYuvCapture != null) {
            mCameraYuvCapture.destroyCamera();
        }
        if (mH264Encoder != null) {
            mH264Encoder.close();
        }
    }

    public static void toHexString(int i) {
        byte b = (byte) i;
        Log.d("TEST", Integer.toHexString(b));
        Log.d("TEST", Integer.toHexString(b & 0xff));
    }

}
