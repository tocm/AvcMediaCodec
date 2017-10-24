package com.andy.mymediacodec.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.andy.mymediacodec.audio.decoder.AACDecoder;
import com.andy.mymediacodec.audio.encoder.IAudioEncode;
import com.andy.mymediacodec.audio.encoder.EncodeAAC;
import com.andy.mymediacodec.audio.encoder.EncodeWave;
import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;
import com.andy.mymediacodec.utils.AudioUtils;
import com.andy.mymediacodec.utils.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.andy.mymediacodec.audio.AudioPcmCapture.RECORD_STATUS.RECORD_STATUS_IDLE;
import static com.andy.mymediacodec.audio.AudioPcmCapture.RECORD_STATUS.RECORD_STATUS_RUNNING;
import static com.andy.mymediacodec.audio.AudioPcmCapture.RECORD_STATUS.RECORD_STATUS_STOP;
import static com.andy.mymediacodec.constants.Define.SDCARD_TEMP_FILE_DIR;

/**
 * Created by Andy.chen on 2017/7/12.
 * get the PCM audio data
 */

public class AudioPcmCapture {
    private final static String TAG = AudioPcmCapture.class.getSimpleName();
    private AudioRecord mAudioRecord;
    private int mBufferSize;
    private BufferedOutputStream mOutputStreamPcmFile;
    private File mFileRecordPcmPath;
    private FrameBufferQueue mSrcPcmFrameQueue;
    private FrameBufferQueue mAACFrameQueue;
    private AACDecoder mDecoderAAC;

    private IAudioEncode mAudioEncodeWave;
    private IAudioEncode mEncoderAAC;
    enum RECORD_STATUS {
        RECORD_STATUS_IDLE,
        RECORD_STATUS_RUNNING,
        RECORD_STATUS_STOP
    }
    private RECORD_STATUS mRecordStatus = RECORD_STATUS_IDLE;
    public AudioPcmCapture() {
        createAudioPCMFile();
        createAudioRecord();
    }

    private void createAudioPCMFile() {
        String folderPath = Environment.getExternalStorageDirectory() + File.separator + SDCARD_TEMP_FILE_DIR;
        File fileFolder = FileUtils.createFolder(folderPath);
        try {
            mFileRecordPcmPath = FileUtils.createFile(fileFolder, AudioUtils.SDCARD_TEMP_FILE_NAME_PCM);
            mOutputStreamPcmFile = new BufferedOutputStream(new FileOutputStream(mFileRecordPcmPath));
            Log.i(TAG, "mOutputStream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void createAudioRecord() {
        Log.d(TAG,"createAudioRecord");
        if (mAudioRecord == null) {
            //java.lang.IllegalArgumentException: Bad limit (capacity 4096): 7104
            //Because the ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex); capacity limit is 4096, so can't use getMinBufferSize().
            mBufferSize =AudioUtils.getAudioBufferSize();
            Log.d(TAG,"AudioRecord.getMinBufferSize = "+mBufferSize);

            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, AACDecoder.AudioCfg.sampleRete, AudioFormat.CHANNEL_IN_STEREO, AudioUtils.AUDIO_FORMAT, mBufferSize);
            //create encode
            createSupportEncoder();
        }
    }

    private void createSupportEncoder() {
        mSrcPcmFrameQueue = new FrameBufferQueue();
        mAudioEncodeWave = new EncodeWave(mFileRecordPcmPath, mBufferSize);
        mEncoderAAC = new EncodeAAC(mSrcPcmFrameQueue, new EncodeAAC.AACFrameListener() {
            @Override
            public void callbackADTSFrame(FrameEntity frameEntity) {
                //get the encoder AAC ADTS FRAME buffer
                if(frameEntity != null) {
//                    frameEntity.setId("Audio AAC");
                    if(mAACFrameQueue == null) {
                        mAACFrameQueue = new FrameBufferQueue();
                        mDecoderAAC = new AACDecoder(mAACFrameQueue);
                        mDecoderAAC.start();
                    }
                    mAACFrameQueue.pushFrameData(frameEntity);

                }
            }
        });
    }

    public void startRecord() {
        if(mRecordStatus != RECORD_STATUS_IDLE) {
            return;
        }
        Log.d(TAG,"startRecord");
        mAudioRecord.startRecording();
        mRecordStatus = RECORD_STATUS_RUNNING;

        if(mEncoderAAC != null) {
            mEncoderAAC.startEncodeData();
        }
        new Thread(new AudioRecordThread()).start();
    }

    public void stopRecord() {
        if(mRecordStatus == RECORD_STATUS_IDLE) {
            return;
        }

        if(mDecoderAAC != null){
            mDecoderAAC.stop();
            mDecoderAAC = null;
        }
        if(mEncoderAAC != null) {
            mEncoderAAC.stop();
            mEncoderAAC = null;
        }
        Log.d(TAG,"stopRecord");
        mRecordStatus = RECORD_STATUS_STOP;
        mAudioRecord.stop();
        mAudioRecord.release();
        mRecordStatus = RECORD_STATUS_IDLE;
    }

    class AudioRecordThread implements Runnable{
        int audioReadNum = 0;
        byte[] srcAudioPcmBuffer = new byte[mBufferSize];
        @Override
        public void run() {
            while (mRecordStatus == RECORD_STATUS_RUNNING) {
                try {
                    audioReadNum = mAudioRecord.read(srcAudioPcmBuffer, 0, mBufferSize);

                    if(mSrcPcmFrameQueue != null) {
                        FrameEntity frameEntity = new FrameEntity();
                        frameEntity.setId("PCM ::");
                        frameEntity.setBuf(srcAudioPcmBuffer);
                        frameEntity.setSize(mBufferSize);
                     //   Log.d(TAG,"audio push pcm frame data size = "+srcAudioPcmBuffer.length);
                        mSrcPcmFrameQueue.pushFrameData(frameEntity);
                    }

                    if (mOutputStreamPcmFile != null) {
                        mOutputStreamPcmFile.write(srcAudioPcmBuffer);
                        mOutputStreamPcmFile.flush();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                mOutputStreamPcmFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                FileUtils.closeSilently(mOutputStreamPcmFile);
            }
            if(mAudioEncodeWave != null) {
                //encoder the WAVE file [add the wave header only base on the src pcm data] after record done.
                mAudioEncodeWave.startEncodeData();
            }
        }
    }
}
