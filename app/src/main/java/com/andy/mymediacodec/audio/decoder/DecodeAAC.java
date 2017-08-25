package com.andy.mymediacodec.audio.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

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

public class DecodeAAC {

    private final static String TAG = DecodeAAC.class.getSimpleName();
    private MediaCodec mDecoder;
    private AudioTrackPcmPlayer mAudioTrackPcmPlayer;
    private FrameBufferQueue mFrameBufferQueue;
    private boolean mDecoderRunning;
    private boolean mRenderRunning;
    private boolean mConfigured;
    public DecodeAAC(FrameBufferQueue decoderQueue) {
        mFrameBufferQueue = decoderQueue;
    }

    public void start(){
        if(mFrameBufferQueue != null) {
            startDecoder();
            startRender();
        }
    }

    private boolean configDecoder(ByteBuffer headerADTS) {
        if(headerADTS == null || mConfigured) {
            return false;
        }
        AvcUtils.printByteData("AAC ADTS",headerADTS.array());
        try {
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(AudioUtils.AUDIO_MIME_TYPE,AudioUtils.AUDIO_SAMPLE_RATE_HZ,AudioUtils.AUDIO_FORMAT);
            //optional, if decoding AAC audio content, setting this key to 1 indicates that each audio frame is prefixed by the ADTS header.
            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS,1);
            mediaFormat.setByteBuffer("csd-0",headerADTS);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            Log.d(TAG,"AAC decoder mime = "+mime);
//            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);

//            byte[] bytes = new byte[]{(byte) 0x11, (byte)0x90};
//            ByteBuffer bb = ByteBuffer.wrap(bytes);
//            mediaFormat.setByteBuffer("csd-0", bb);

            mDecoder = MediaCodec.createDecoderByType(mime);
            mDecoder.configure(mediaFormat,null,null,0);
            mDecoder.start();

            if(mAudioTrackPcmPlayer != null) {
                mAudioTrackPcmPlayer.startPlayer();
            }
            mConfigured = true;
            Log.d(TAG,"AAC decoder configure info "+mDecoder.getOutputFormat());
            Log.d(TAG,"AAC decoder start");

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
        mDecoderRunning = true;
        mAudioTrackPcmPlayer = new AudioTrackPcmPlayer(AudioUtils.AUDIO_SAMPLE_RATE_HZ,AudioUtils.AUDIO_CHANNEL,AudioUtils.AUDIO_FORMAT,AudioUtils.getAudioBufferSize());
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
        //looping to decode a frame until decode a frame finish;
        while (mConfigured){
            int inputIndex = -1;
            inputIndex = mDecoder.dequeueInputBuffer(-1);
            Log.d(TAG,"aac decoder dequeueInputBuffer = "+ inputIndex);
            if(inputIndex >= 0) {
                ByteBuffer byteBuffer = mDecoder.getInputBuffer(inputIndex);
                if(byteBuffer != null) {
                    byteBuffer.clear();
                    byteBuffer.put(frame);
                    byteBuffer.limit(frame.length);
                    mDecoder.queueInputBuffer(inputIndex,0,frame.length,0,0);
                    break;
                }
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void render() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputIndex = mDecoder.dequeueOutputBuffer(bufferInfo,-1);
        if(outputIndex >= 0) {
            ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outputIndex);
            if(outputBuffer != null) {
                byte[] renderBuf = new byte[bufferInfo.size];
                outputBuffer.get(renderBuf);
                outputBuffer.clear();

                if(mAudioTrackPcmPlayer != null) {
                    mAudioTrackPcmPlayer.pushFrame(renderBuf);
                }
            }
            mDecoder.releaseOutputBuffer(outputIndex,false);
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
                        Thread.sleep(50);
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
                        boolean isConfigured = configDecoder(adtsHeaderBuffer);
                        if(isConfigured) {
                            Log.d(TAG,"decoder aac config done");
                        }
                    }
                    if(mConfigured == false) {
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }

                    //push to decode
                    synchronized (DecoderAACThread.this){
                        pushDecoder(frameBuf);
                    }


                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }
    }

    class AACRender implements Runnable{
        @Override
        public void run() {
            while (mRenderRunning) {
                if(mConfigured){
                    synchronized (AACRender.this) {
                        render();
                    }

                }

                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
        if(mFrameBufferQueue != null) {
            mFrameBufferQueue.clearQueue();
        }
        try {
            stopDecoder();
        } catch (Exception e) {
            e.printStackTrace();
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
