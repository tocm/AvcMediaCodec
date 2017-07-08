package com.andy.mymediacodec.encoder;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;
import com.andy.mymediacodec.utils.AvcUtils;
import com.andy.mymediacodec.utils.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

/**
 * H264/AVC Encode
 * Created by Andy.chen on 2017/5/24.
 */

public class H264Encoder {
    private final static String TAG = H264Encoder.class.getSimpleName();
    private final static int ENCODER_QUEUE_BUF_SIZE = 3;
    private MediaCodec mEncoder;
    private BufferedOutputStream mOutputStream;
    public byte[] mHeaderCfgFrameBuf;
    private FrameBufferQueue mFrameBufferQueue;
    private EncoderThread mEncodeThread;
    private long mPresentationTimeUs = 0;
    private int mGenerateIndex = 0;

    public H264Encoder(FrameBufferQueue frameBufferQueue) {
        Log.d(TAG, "construct");
        mFrameBufferQueue = frameBufferQueue;
        try {
            configureEncoder();
            createDecodeH264File();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createDecodeH264File() {
        String folderPath = Environment.getExternalStorageDirectory() + File.separator + AvcUtils.SDCARD_TEMP_FILE_DIR;
        File fileFolder = FileUtils.createFolder(folderPath);
        try {
            File file = FileUtils.createFile(fileFolder, AvcUtils.SDCARD_TEMP_FILE_NAME);
            mOutputStream = new BufferedOutputStream(new FileOutputStream(file));
            Log.i(TAG, "mOutputStream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    private void configureEncoder() throws IOException {

        MediaCodecInfo mediaCodecInfo = AvcUtils.selectCodec(MediaFormat.MIMETYPE_VIDEO_AVC);
        int colorFormat = AvcUtils.selectColorFormat(mediaCodecInfo,MediaFormat.MIMETYPE_VIDEO_AVC);
        Log.d(TAG,"encoder color format = "+colorFormat);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, AvcUtils.WIDTH, AvcUtils.HEIGHT);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, AvcUtils.BITRATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, AvcUtils.FPS);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, AvcUtils.I_FRAME_INTERVAL);// 10 seconds between I-frames
        mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();

        MediaFormat mediaFormat1 = mEncoder.getOutputFormat();
        Log.d(TAG, "configureEncoder mediaFormat: " + mediaFormat);
    }

    /**
     * // the referent PTS for video and audio encoder.
     *
     * @return
     */
    private long computerPts() {
        return System.nanoTime() / 1000 - mPresentationTimeUs;
    }

    public void close() {
        try {
            this.stopEncoderThread();
            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
            }
            if (mOutputStream != null) {
                mOutputStream.flush();
                mOutputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void stopEncoderThread() {
        Log.d(TAG, "STOP ENCODE THREAD");
        if (mEncodeThread != null) {
            mEncodeThread.stopThread();
            mEncodeThread = null;
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     * Refer to Android's sources code sample
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / AvcUtils.FPS;
    }

    public void startEncoderThread() {
        mPresentationTimeUs = computerPts();
        mEncodeThread = new EncoderThread();
        new Thread(mEncodeThread).start();
    }

    class EncoderThread implements Runnable {
        private boolean isRunning = false;

        public EncoderThread() {
            isRunning = true;
        }

        public void stopThread() {
            isRunning = false;
        }

        @SuppressLint("NewApi")
        @Override
        public void run() {
            while (isRunning) {
                if (!mFrameBufferQueue.isEmptyQueue()) {
                    FrameEntity frameEntity = mFrameBufferQueue.pollFrameData();
                    pushDataEncoder(frameEntity);

                } else {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * 原始获取NV21 格式数据，需要先转成NV12格式再传给编码器
     *
     * @param frameEntity
     */
    private void pushDataEncoder(FrameEntity frameEntity) {
        //YUV数据 = width * height *3 /2 ;
        byte[] nv21Buf = frameEntity.getBuf();
        byte[] yuv420sp = new byte[AvcUtils.WIDTH * AvcUtils.HEIGHT * 3 / 2];
        AvcUtils.NV21ToNV12(nv21Buf, yuv420sp, AvcUtils.WIDTH, AvcUtils.HEIGHT);

        try {
            ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
            ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
            int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
            Log.d(TAG, " offerEncoder InputBuffer = " + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                long pts = computePresentationTime(mGenerateIndex);
//                long pts = computerPts();
                Log.d(TAG, " offerEncoder pts = " + pts);
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(yuv420sp);

                mEncoder.queueInputBuffer(inputBufferIndex, 0, yuv420sp.length, pts, 0);
                mGenerateIndex += 1;
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, AvcUtils.TIMEOUT_US);
            Log.d(TAG, " offerEncoder outputBufferIndex = " + outputBufferIndex);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);
                Log.d(TAG, " offerEncoder bufferInfo.flags = " + bufferInfo.flags);
                if (bufferInfo.flags == BUFFER_FLAG_CODEC_CONFIG) {
                    //header configuration frame EXP: SPS/PPS
                    mHeaderCfgFrameBuf = new byte[bufferInfo.size];
                    mHeaderCfgFrameBuf = outData;

                    if (mAvcDataFrameListener != null) {
                        mAvcDataFrameListener.onAvcEncoderNALFrame(mHeaderCfgFrameBuf);
                    }
//                    AvcUtils.printByteData("Encoder NAL SPS/PPS HEX ",mHeaderCfgFrameBuf);
                } else if (bufferInfo.flags == BUFFER_FLAG_KEY_FRAME) {
                    /**
                     * IDR帧：IDR帧属于I 帧。解码器收到IDR frame时，将所有的参考帧队列丢弃 ，这点是所有I 帧共有的特性，
                     * 但是收到IDR帧时，解码器另外需要做的工作就是：把所有的PPS和SPS参数进行更新。
                     * 由此可见，在编码器端，每发一个 IDR，就相应地发一个 PPS&SPS_nal_unit
                     */
                    byte[] keyframe = new byte[bufferInfo.size + mHeaderCfgFrameBuf.length];
                    System.arraycopy(mHeaderCfgFrameBuf, 0, keyframe, 0, mHeaderCfgFrameBuf.length);
                    System.arraycopy(outData, 0, keyframe, mHeaderCfgFrameBuf.length, outData.length);
                    mOutputStream.write(keyframe, 0, keyframe.length);

                    frameEntity.setFrameType(BUFFER_FLAG_KEY_FRAME);
                    //print log
//                    AvcUtils.printByteData("Encoder I-KEY-FRAME HEX ",keyframe);

                } else {
                    mOutputStream.write(outData, 0, outData.length);
                }
                //push data to decoding
                if (mAvcDataFrameListener != null) {
                    frameEntity.setBuf(outData);
                    frameEntity.setSize(outData.length);
                    Log.d(TAG, "=====after Encoder frame id = " + frameEntity.getId() + ", spent time = " + (System.currentTimeMillis() - frameEntity.getTimestamp()));

                    mAvcDataFrameListener.onAvcEncoderFrame(frameEntity);
                }

                mEncoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, AvcUtils.TIMEOUT_US);


            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private AvcDataFrameListener mAvcDataFrameListener;

    public void setAvcDataFrameListener(AvcDataFrameListener listener) {
        this.mAvcDataFrameListener = listener;
    }

    public interface AvcDataFrameListener {
        public void onAvcEncoderFrame(FrameEntity frameEntity);

        public void onAvcEncoderNALFrame(byte[] nalFrame);
    }
}
