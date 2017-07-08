package com.andy.mymediacodec.decoder;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;
import com.andy.mymediacodec.utils.AvcUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Andy.chen on 2017/5/24.
 */

public class H264Decoder {
    private final static String TAG = H264Decoder.class.getSimpleName();
    private MediaCodec mDecoder;
    private Surface mSurface;
    private boolean mDecoderConfigured = false;
    private DecoderThread mDecoderThread;
    private RenderAvcThread mRenderAvcThread;
    private boolean mIsDecoderReady;
    private int outputImageFileType;
    private MediaCodec.BufferInfo mBufferInfo;
    private long mLastRenderTimestamp;
    private long mLastDecoderTimestamp;
    private FrameBufferQueue mFrameBufferQueue;

    public H264Decoder(Surface surface, FrameBufferQueue frameBufferQueue) {
        Log.d(TAG, "H264Decoder construct");
        mSurface = surface;
        mFrameBufferQueue = frameBufferQueue;
        outputImageFileType = AvcUtils.FILE_TypeJPEG;

    }

    public boolean isDecodingReady() {
        return mIsDecoderReady;
    }

    private void configDecoder(int width, int height, ByteBuffer nalSPS, ByteBuffer nalPPS) {
        Log.d(TAG, "configDecoder entry ");
        if (nalSPS == null && nalPPS == null) {
            return;
        }
        //print log
        AvcUtils.printByteData("Decoder SPS", nalSPS.array());
        AvcUtils.printByteData("Decoder PPS", nalPPS.array());

        //check support MIME
        MediaCodecInfo mediaCodecInfo = AvcUtils.selectCodec(MediaFormat.MIMETYPE_VIDEO_AVC);
        int colorFormat = AvcUtils.selectColorFormat(mediaCodecInfo,MediaFormat.MIMETYPE_VIDEO_AVC);
        Log.d(TAG,"decoder color format = "+colorFormat);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        mediaFormat.setByteBuffer("csd-0", nalSPS);
        mediaFormat.setByteBuffer("csd-1", nalPPS);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,colorFormat);
        try {
            mDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDecoder.configure(mediaFormat, mSurface, null, 0);
        mDecoder.start();

        Log.d(TAG, "Decoder config ::  " + mDecoder.getOutputFormat());
        mBufferInfo = new MediaCodec.BufferInfo();
        mDecoderConfigured = true;
    }

    public void startDecoderThread() {
        if (mRenderAvcThread == null) {
            mRenderAvcThread = new RenderAvcThread();
            new Thread(mRenderAvcThread).start();
        }
        if (mDecoderThread == null) {
            Log.d(TAG, "startDecoderThread");
            mDecoderThread = new DecoderThread();
            new Thread(mDecoderThread).start();
        }
    }

    public void stopDecoderThread() {
        Log.d(TAG, "stopDecoderThread");
        if (mDecoderThread != null) {
            mDecoderThread.stop();
            mDecoderThread = null;
        }
        if (mRenderAvcThread != null) {
            mRenderAvcThread.stop();
            mRenderAvcThread = null;
        }
    }

    class DecoderThread implements Runnable {
        private boolean decoding = false;
        public DecoderThread() {
            decoding = true;
        }

        public void stop() {
            decoding = false;
        }

        @Override
        public void run() {
            try {
                while (decoding) {
                    mIsDecoderReady = true;
                    if (!mFrameBufferQueue.isEmptyQueue()) {
                        Log.d(TAG, "==============Decoder BEGIN of ============= ");
                        long bTime = System.currentTimeMillis();

                        FrameEntity frameEntity = mFrameBufferQueue.pollFrameData();
                        long captureTime = frameEntity.getTimestamp();
                        byte[] decodingBuf = frameEntity.getBuf();
                        int frameBufSize = frameEntity.getSize();
//                        AvcUtils.printByteData("Decoder get BUF ",decodingBuf);
                        Log.d(TAG, "LINE 156 spent time = " + Long.toString(System.currentTimeMillis() - bTime) +", frameBufSize = "+frameBufSize);
                        if (mDecoderConfigured == false) {
                            byte[][] nalSpsPps = AvcUtils.findAvcNalSpsAndPps(decodingBuf, decodingBuf.length);
                            if (nalSpsPps[0] != null && nalSpsPps[1] != null) {
                                configDecoder(AvcUtils.WIDTH, AvcUtils.HEIGHT, ByteBuffer.wrap(nalSpsPps[0]), ByteBuffer.wrap(nalSpsPps[1]));
                            }
                        }
                        if (mDecoderConfigured == false) {
                            Thread.sleep(5);
                            continue;
                        }
                        Log.d(TAG, "LINE 166 spent time = " + Long.toString(System.currentTimeMillis() - bTime));
                        long beforeDecodeData = System.currentTimeMillis();
                        decodeData(decodingBuf, 0, decodingBuf.length, 0, 0);
                        Log.d(TAG, "==============Decoder end of " + Long.toString(System.currentTimeMillis() - beforeDecodeData) + "==============");
                        Log.d(TAG, "==>Decoder spent time from create frame to decode finish = " + frameEntity.getId() + " spent time = " + (System.currentTimeMillis() - captureTime));
                    } else {
                        Thread.sleep(5);
                    }
                }
                Log.d(TAG, "====Decoding end =====");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mDecoderConfigured) {
                    try {
                        mDecoder.stop();
                        mDecoder.release();
                    }catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void decodeData(byte[] srcData, int offset, int size, int presentationTimeUs, int flags) {
            long deEntryTime = System.currentTimeMillis();
            while (mDecoderConfigured && decoding) {
                try {
                    int inputBufIndex = mDecoder.dequeueInputBuffer(-1);
                    Log.d(TAG, "decodeData inputBufIndex = " + inputBufIndex);
                    if (inputBufIndex >= 0) {
                        ByteBuffer buffer;
                        // since API 21 we have new API to use
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            buffer = mDecoder.getInputBuffers()[inputBufIndex];
                            buffer.clear();
                        } else {
                            buffer = mDecoder.getInputBuffer(inputBufIndex);
                        }
                        if (buffer != null) {
                            buffer.put(srcData, offset, size);
                            buffer.limit(size);
                            mDecoder.queueInputBuffer(inputBufIndex, 0, size, mBufferInfo.presentationTimeUs, mBufferInfo.flags);
                            Log.d(TAG, "Decoder actual frame Gap =  " + Long.toString(System.currentTimeMillis() - mLastDecoderTimestamp) + ", Thread :: " + Thread.currentThread().getName());
                            mLastDecoderTimestamp = System.currentTimeMillis();
                        }
                        break;
                    } else {
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            Log.d(TAG, "Decoder once frame Gap =  " + Long.toString(System.currentTimeMillis() - deEntryTime) + ",Thread :: " + Thread.currentThread().getName());
        }

    }


    private void renderToImage(int decodeIndex) {
        Image image = mDecoder.getOutputImage(decodeIndex);
        if (image != null) {
            Log.d(TAG, "Image format " + image.getFormat());
            if (outputImageFileType != -1) {
                String fileName;
                switch (outputImageFileType) {
                    case AvcUtils.FILE_TypeI420:
                        fileName = Environment.getExternalStorageDirectory() + "/frame_I420.yuv";
                        AvcUtils.dumpFile(fileName, AvcUtils.getDataFromImage(image, AvcUtils.COLOR_FormatI420));
                        break;
                    case AvcUtils.FILE_TypeNV21:
                        fileName = Environment.getExternalStorageDirectory() + "/frame_NV21.yuv";
                        AvcUtils.dumpFile(fileName, AvcUtils.getDataFromImage(image, AvcUtils.COLOR_FormatNV21));
                        break;
                    case AvcUtils.FILE_TypeJPEG:
                        fileName = Environment.getExternalStorageDirectory() + "/decode_frame.jpg";
                        AvcUtils.compressToJpeg(fileName, image);
                        Log.d(TAG, "Image compressToJpeg END ");
                        break;
                }
            }
            image.close();
        }
    }


//    public void setSaveFrames(String dir, int fileType) throws IOException {
//        if (fileType != FILE_TypeI420 && fileType != FILE_TypeNV21 && fileType != FILE_TypeJPEG) {
//            throw new IllegalArgumentException("only support FILE_TypeI420 " + "and FILE_TypeNV21 " + "and FILE_TypeJPEG");
//        }
//        outputImageFileType = fileType;
//        File theDir = new File(dir);
//        if (!theDir.exists()) {
//            theDir.mkdirs();
//        } else if (!theDir.isDirectory()) {
//            throw new IOException("Not a directory");
//        }
//        OUTPUT_DIR = theDir.getAbsolutePath() + "/";
//    }

    class RenderAvcThread implements Runnable {
        private boolean running;

        public RenderAvcThread() {
            running = true;
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            while (running) {
                if (mDecoderConfigured) {
                    try {
                        int decodeIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, AvcUtils.TIMEOUT_US);
                        if (decodeIndex >= 0) {
                            //save image
//                               renderToImage(decodeIndex);
                            ByteBuffer byteBuffer = mDecoder.getOutputBuffer(decodeIndex);
                            if(byteBuffer != null) {
                                byte[] buf = new byte[byteBuffer.capacity()];
                                byteBuffer.get(buf);
                                AvcUtils.printByteData("OutputBuffer is ready to be processed or rendered::",buf);
//                                AvcUtils.compressToJpeg(buf,byteBuffer.capacity(),AvcUtils.WIDTH,AvcUtils.HEIGHT);
                            }


                            // setting true is telling system to render frame onto Surface
                            mDecoder.releaseOutputBuffer(decodeIndex, true);
                            //count the fps of render
                            long renderGap = System.currentTimeMillis() - mLastRenderTimestamp;
                            Log.d(TAG, "Render frame gap = " + renderGap + ",Thread ::" + Thread.currentThread().getName());
                            mLastRenderTimestamp = System.currentTimeMillis();
                            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                                return;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.d(TAG, "==== Render failed exception ====== ");
                        running = false;
                        return;
                    }
                } else {
                    // just waiting to be configured, then decode and render
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        }
    }
}
