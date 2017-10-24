package com.andy.mymediacodec.audio.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.andy.mymediacodec.constants.Define;
import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;
import com.andy.mymediacodec.utils.AudioUtils;
import com.andy.mymediacodec.utils.AvcUtils;
import com.andy.mymediacodec.utils.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.andy.mymediacodec.utils.AudioUtils.AAC_ADTS_HEADER_BYTE_LEN;

/**
 * Created by Andy.chen on 2017/7/14.
 * Encode AAC needs use MediaCodec
 */

public class EncodeAAC implements IAudioEncode {

    private final static String TAG = EncodeAAC.class.getSimpleName();
    private MediaCodec mEncoder;
    private FrameBufferQueue mSrcPcmFrameQueue;
    private AACFrameListener mAACFrameListener;
    private boolean mEncodeRunning;
    private BufferedOutputStream mFileOutputSteamAAC;
    private MediaCodec.BufferInfo mBufferInfo;
    private byte[] aacPacketBuf;

    public EncodeAAC(final FrameBufferQueue srcPcmFrameQueue, AACFrameListener frameListener) {
        mSrcPcmFrameQueue = srcPcmFrameQueue;
        mAACFrameListener = frameListener;

        try {
            String fileFolderPath = Environment.getExternalStorageDirectory() + File.separator + Define.SDCARD_TEMP_FILE_DIR;
            File fileFolder = FileUtils.createFolder(fileFolderPath);
            File file = FileUtils.createFile(fileFolder, AudioUtils.SDCARD_TEMP_FILE_NAME_AAC);
            mFileOutputSteamAAC = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void startEncodeData() {
        configEncoder();
        mEncodeRunning = true;
        new Thread(new AACEncoderThread()).start();
    }

    @Override
    public void stop() {
        mEncodeRunning = false;
        try {
            if(mEncoder != null) {
                mEncoder.flush();
                mEncoder.stop();
                mEncoder.release();
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void configEncoder() {
        try {
            //创建对应的编码格式
            mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            //创建声音格式
            MediaFormat mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AudioUtils.AUDIO_SAMPLE_RATE_HZ, AudioUtils.AUDIO_FORMAT);
            //设定AAC profile
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            //设定BITRATE
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,AudioUtils.AUDIO_BITRATE);//bitrate 64kbps
            //生效声音格式
            mEncoder.configure(mediaFormat,null,null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            //启动编码器
            mEncoder.start();
            mBufferInfo = new MediaCodec.BufferInfo();
            Log.d(TAG,"AAC Encoder configure info "+mEncoder.getOutputFormat());


        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    class AACEncoderThread implements Runnable{
        @Override
        public void run() {
            while (mEncodeRunning) {
                if(mSrcPcmFrameQueue == null) {
                    break;
                }
                if(mSrcPcmFrameQueue.isEmptyQueue()) {
                    Log.d(TAG,"null data continue");
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                FrameEntity frameEntity = mSrcPcmFrameQueue.pollFrameData();
                if (frameEntity != null)
                {
                    String uid = frameEntity.getId();
                    byte[] pcmBuf = frameEntity.getBuf();
                    int size = frameEntity.getSize();
                    encoder(pcmBuf);
                }

                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void encoder(byte[] pcmBuf) {
        if(pcmBuf == null || pcmBuf.length == 0) {
            return;
        }

        Log.d(TAG,"pcm src buffer size = "+pcmBuf.length);
        //get the index of an input buffer to be filled with valid data
        int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
        Log.d(TAG,"AAC encoder dequeueInputBuffer = "+inputBufferIndex);
        if(inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = null;
            // since API 21 we have new API to use
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                inputBuffer = mEncoder.getInputBuffers()[inputBufferIndex];
            } else {
                //get the input buffer which can push the pcm data
                inputBuffer = mEncoder.getInputBuffer(inputBufferIndex);
            }
            if(inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(pcmBuf);
                inputBuffer.limit(pcmBuf.length);
                mEncoder.queueInputBuffer(inputBufferIndex,0,pcmBuf.length, System.nanoTime(),0);;
            }

        }


        //get the encoder queue buffer index with valid data
        int outputBufferIndex =  mEncoder.dequeueOutputBuffer(mBufferInfo, -1);
        Log.d(TAG,"AAC encoder outputBufferIndex = "+outputBufferIndex);
        while (outputBufferIndex >= 0) {
            int pcmOutDataSize = mBufferInfo.size;
            int aacPacketSize = pcmOutDataSize + AAC_ADTS_HEADER_BYTE_LEN; // AAC header: ADTS: 7byte
            if(aacPacketBuf == null || aacPacketBuf.length < aacPacketSize) {
                aacPacketBuf = new byte[aacPacketSize];
            }
            //get the encoder output buffer data
            ByteBuffer outputBuffers = null;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                outputBuffers = mEncoder.getOutputBuffers()[outputBufferIndex];
                outputBuffers.clear();
            } else {
                //get the input buffer which can push the pcm data
                outputBuffers = mEncoder.getOutputBuffer(outputBufferIndex);
            }

            //get the original output data buffer frame
            outputBuffers.get(aacPacketBuf, AAC_ADTS_HEADER_BYTE_LEN , pcmOutDataSize);
            outputBuffers.clear();

            //add ADTS header 7byte
            AudioUtils.addAACADTSHeader(aacPacketBuf,0, pcmOutDataSize);//Note: this len is pcm output buffer size

         //   FileUtils.printSystemByteLog("AFTER ENCODER : ", aacPacketBuf, aacPacketSize);
            //save to local aac file
            if(mFileOutputSteamAAC != null) {
                try {
                    mFileOutputSteamAAC.write(aacPacketBuf, AAC_ADTS_HEADER_BYTE_LEN, aacPacketSize - AAC_ADTS_HEADER_BYTE_LEN);
                    mFileOutputSteamAAC.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            AvcUtils.printByteData("Encoded aac :: ", aacPacketBuf);
            //notify the AAC frame buffer to top layer
            if(mAACFrameListener != null) {
                FrameEntity frameEntity = new FrameEntity();
                frameEntity.setId("AAC ::");
                frameEntity.setBuf(aacPacketBuf);
                frameEntity.setSize(aacPacketSize);
                frameEntity.setTimestamp(System.currentTimeMillis());
                mAACFrameListener.callbackADTSFrame(frameEntity);
            }

            mEncoder.releaseOutputBuffer(outputBufferIndex,false);
            outputBufferIndex =  mEncoder.dequeueOutputBuffer(mBufferInfo, 0);

        }

    }

    public interface AACFrameListener{
        public void callbackADTSFrame(FrameEntity frameEntity);
    }

}
