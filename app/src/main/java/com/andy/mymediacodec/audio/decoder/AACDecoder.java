package com.andy.mymediacodec.audio.decoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.andy.mymediacodec.audio.AudioPlayThread;
import com.andy.mymediacodec.audio.AudioTrackPcmPlayer;
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

    private AudioPlayThread mAudioPlayThread;
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
//                mAudioPlayThread = new AudioPlayThread(mFrameBufferQueue);
//                mAudioPlayThread.start();

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
            /*
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(AudioUtils.AUDIO_MIME_TYPE,AudioUtils.AUDIO_SAMPLE_RATE_HZ,AudioUtils.AUDIO_FORMAT);
            //optional, if decoding AAC audio content, setting this key to 1 indicates that each audio frame is prefixed by the ADTS header.
            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS,0);
            AvcUtils.printByteData("ADTS HEADER :: ", headerADTS.array());
            mediaFormat.setByteBuffer("csd-0",ByteBuffer.wrap(new byte[]{0x12,0x10}));
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            Log.d(TAG,"AAC decoder mime = "+mime);

            mDecoder = MediaCodec.createDecoderByType(mime);

        */
            mDecoder = MediaCodec.createDecoderByType(AudioCfg.mimeType);
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, AudioCfg.mimeType);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, AudioCfg.channel);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, AudioCfg.sampleRete);
            format.setInteger(MediaFormat.KEY_BIT_RATE, AudioCfg.bitRate);
            format.setInteger(MediaFormat.KEY_IS_ADTS, 0);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
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
//
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
     * @param frame
     */
    private void pushDecoder(byte[] frame){
        if(frame == null) {
            return;
        }
        Log.d(TAG,"AAC frame size = "+frame.length);
        //AvcUtils.printByteData("AAC::src ",frame);
        //looping to decode a frame until decode a frame finish;
       // while (mConfigured){
            int inputIndex = -1;
            inputIndex = mDecoder.dequeueInputBuffer(16);
            Log.d(TAG,"aac decoder dequeueInputBuffer = "+ inputIndex);
            if(inputIndex >= 0) {
                ByteBuffer byteBuffer = mDecoder.getInputBuffer(inputIndex);
                if(byteBuffer != null) {
                    byteBuffer.clear();
                //    AvcUtils.printByteData("AAC::dst decoder ", Arrays.copyOfRange(frame,7,frame.length));
                    byteBuffer.put(frame, 7, frame.length - 7);
                    mDecoder.queueInputBuffer(inputIndex,0,frame.length - 7,0,0);
                  //  break;
                }
            }
        //}

    }

    private byte[] mOutputDataBuffer = null;
    private MediaCodec.BufferInfo mBufferInfo;
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

                    pushDecoder(frameBuf);



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
        if(mAudioPlayThread != null) {
            mAudioPlayThread.quit();
        }
        if(mDecoder != null) {
            mDecoder.flush();
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
    }
}
