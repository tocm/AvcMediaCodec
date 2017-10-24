package com.andy.mymediacodec.audio.decoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.andy.mymediacodec.audio.AudioTrackPcmPlayer;
import com.andy.mymediacodec.constants.Define;
import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;
import com.andy.mymediacodec.utils.AudioUtils;
import com.andy.mymediacodec.utils.AvcUtils;

import java.nio.ByteBuffer;

/**
 * Created by Andy.chen on 2017/8/4.
 * Decode the AAC and reader pcm
 * play the PCM data by AudioTrack
 */

public class AACDecoder {

    private final static String TAG = AACDecoder.class.getSimpleName();
    private MediaCodec mDecoder;
    private AudioTrackPcmPlayer mAudioTrackPcmPlayer;
    private FrameBufferQueue mFrameBufferQueue;
    private boolean mDecoderRunning;
    private boolean mRenderRunning;
    private boolean mConfigured;

    private byte[] mOutputDataBuffer = null;
    private MediaCodec.BufferInfo mBufferInfo;

    public static class AudioCfg {
        public static String mimeType = "audio/mp4a-latm";
        public static int channel = 2;
        public static int sampleRete = 44100;
        public static int  bitRate = 64000;
        public static byte[] csd0 = {0x12,0x10};
    }

    public AACDecoder(FrameBufferQueue decoderQueue) {
        Log.d(TAG, "constrct AACDecoder");
        mFrameBufferQueue = decoderQueue;
    }

    public void start(){
        Log.d(TAG, "start");
        if(mFrameBufferQueue != null) {

            startRender();
            startDecoder();

        }
    }

    private boolean configDecoder(ByteBuffer headerADTS) {
        if(headerADTS == null || mConfigured) {
            return false;
        }
        if(mAudioTrackPcmPlayer != null) {
            mAudioTrackPcmPlayer.startPlayer();
        }

        AvcUtils.printByteData("AAC ADTS",headerADTS.array());
        try {
            mDecoder = MediaCodec.createDecoderByType(AudioCfg.mimeType);
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, AudioCfg.mimeType);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, AudioCfg.channel);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, AudioCfg.sampleRete);
            format.setInteger(MediaFormat.KEY_BIT_RATE, AudioCfg.bitRate);

            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);

            format.setInteger(MediaFormat.KEY_IS_ADTS,  Define.AAC_ENABLE_HEADER_CFG ? 1 : 0);
            format.setByteBuffer("csd-0", headerADTS);
            mDecoder.configure(format, null, null, 0);

            mDecoder.start();

            mBufferInfo = new MediaCodec.BufferInfo();
            mConfigured = true;
           // Log.d(TAG,"AAC decoder configure info "+mDecoder.getOutputFormat());
            Log.d(TAG,"AAC decoder started");

        }catch (Exception e) {
            Log.d(TAG,"AAC decoder config failed exception msg = "+e.getMessage());
            e.printStackTrace();
            return false;
        }
        return mConfigured;
    }

    private void startDecoder() {
        if (mDecoderRunning){
            return;
        }
        Log.d(TAG, "start decoder");
        mDecoderRunning = true;

        mAudioTrackPcmPlayer = new AudioTrackPcmPlayer();
        DecoderAACThread decoderAACThread = new DecoderAACThread();
        new Thread(decoderAACThread).start();

    }

    private void startRender(){
        if(mRenderRunning) {
            return;
        }
        mRenderRunning = true;
        AACRender aacRender = new AACRender();
        new Thread(aacRender).start();
    }

    /**
     * decoder at thread loop
     * @param frameBuf
     */
    private void pushDecoder(byte[] frameBuf, int frameSize){
        if(frameBuf == null) {
            return;
        }
        Log.d(TAG,"AAC frame size = "+frameSize);
        //AvcUtils.printByteData("AAC::src ",frame);
        //looping to decode a frame until decode a frame finish;
       // while (mConfigured){
            int inputIndex = -1;
            inputIndex = mDecoder.dequeueInputBuffer(0);
            Log.d(TAG,"aac decoder dequeueInputBuffer = "+ inputIndex);
            if(inputIndex >= 0) {
                ByteBuffer byteBuffer = mDecoder.getInputBuffer(inputIndex);
                if(byteBuffer != null) {
                    byteBuffer.clear();
//                    AvcUtils.printByteData("AAC::dst decoder ", Arrays.copyOfRange(frame,7,frame.length));
                    if(Define.AAC_ENABLE_HEADER_CFG) {
                        byteBuffer.put(frameBuf, 0, frameSize);
                        mDecoder.queueInputBuffer(inputIndex, 0, frameSize, 0, 0);
                    } else {
                        byteBuffer.put(frameBuf, 7, frameSize - 7);
                        mDecoder.queueInputBuffer(inputIndex, 0, frameSize - 7, 0, 0);
                    }
                }
            }
        //}

    }


    private void render() throws Exception {
        int outputIndex = mDecoder.dequeueOutputBuffer(mBufferInfo,60);
        Log.d(TAG," render BEGIN outputIndex = "+outputIndex);
        if(outputIndex >= 0) {
            ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outputIndex);
            if(outputBuffer != null) {
                if(mOutputDataBuffer == null || mOutputDataBuffer.length < mBufferInfo.size) {
                    mOutputDataBuffer = new byte[mBufferInfo.size];
                }
                outputBuffer.get(mOutputDataBuffer, 0, mBufferInfo.size);
                outputBuffer.clear();

                if(mAudioTrackPcmPlayer != null) {
                    mAudioTrackPcmPlayer.pushFrame(mOutputDataBuffer, 0, mBufferInfo.size);
                }
            }
            mDecoder.releaseOutputBuffer(outputIndex,false);
            Log.d(TAG,"=> render END outputIndex = "+outputIndex);
        }
    }

    class DecoderAACThread implements Runnable {
        @Override
        public void run() {
            while (mDecoderRunning) {
                if(mFrameBufferQueue == null) {
                    return;
                }
                if(mFrameBufferQueue.isEmptyQueue()){
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                FrameEntity frameEntity = mFrameBufferQueue.pollFrameData();
                if(frameEntity != null) {
                    byte[] frameBuf = frameEntity.getBuf();
                    int frameSize = frameEntity.getSize();
                    if(mConfigured == false) {
                        //get the ADTS Header buffer and set configure
                        ByteBuffer adtsHeaderBuffer = AudioUtils.getAACADTSHeader(frameBuf);
                        if(adtsHeaderBuffer != null) {
                            boolean isConfigured = configDecoder(adtsHeaderBuffer);
                            if (isConfigured) {
                                Log.d(TAG, "decoder aac config done");
                            }
                        }
                    }
                    if(mConfigured == false) {
                        try {
                            Thread.sleep(16);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }

                    pushDecoder(frameBuf,frameSize);



                }

            }

        }
    }

    class AACRender implements Runnable{
        @Override
        public void run() {
            while (mRenderRunning) {
                if(mConfigured) {
                    try {
                        Log.d(TAG,"============ render looping  START ============= ");
                        render();
                        Log.d(TAG,"============ render looping  END ============= ");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if(mAudioTrackPcmPlayer != null) {
                mAudioTrackPcmPlayer.stopPlayer();
            }

        }
    }
    public void stop() {
        mDecoderRunning = false;
        mRenderRunning = false;
        mConfigured = false;

        try {
            stopDecoder();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(mFrameBufferQueue != null) {
            mFrameBufferQueue.clearQueue();
            mFrameBufferQueue = null;
        }
    }

    private void stopDecoder() throws Exception{
        if(mDecoder != null) {
            mDecoder.flush();
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
    }
}
