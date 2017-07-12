package com.andy.mymediacodec;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.andy.mymediacodec.utils.AudioUtils;
import com.andy.mymediacodec.utils.AvcUtils;
import com.andy.mymediacodec.utils.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.andy.mymediacodec.AudioCapture.RECORD_STATUS.RECORD_STATUS_IDLE;
import static com.andy.mymediacodec.AudioCapture.RECORD_STATUS.RECORD_STATUS_RUNNING;
import static com.andy.mymediacodec.AudioCapture.RECORD_STATUS.RECORD_STATUS_STOP;

/**
 * Created by Andy.chen on 2017/7/12.
 */

public class AudioCapture {
    private final static String TAG = AudioCapture.class.getSimpleName();
    private final static int SAMPLE_RATE_HZ = 44100;//sampleRateInHz the sample rate expressed in Hertz. 44100Hz
    private AudioRecord mAudioRecord;
    private int mBufferSize;
    private BufferedOutputStream mOutputStream;
    enum RECORD_STATUS {
        RECORD_STATUS_IDLE,
        RECORD_STATUS_RUNNING,
        RECORD_STATUS_STOP
    }
    private RECORD_STATUS mRecordStatus;
    public AudioCapture() {
        createAudioPCMFile();
        createAudioRecord();
    }

    private void createAudioPCMFile() {
        String folderPath = Environment.getExternalStorageDirectory() + File.separator + AvcUtils.SDCARD_TEMP_FILE_DIR;
        File fileFolder = FileUtils.createFolder(folderPath);
        try {
            File file = FileUtils.createFile(fileFolder, AudioUtils.SDCARD_TEMP_FILE_NAME_PCM);
            mOutputStream = new BufferedOutputStream(new FileOutputStream(file));
            Log.i(TAG, "mOutputStream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createAudioRecord() {
        Log.d(TAG,"createAudioRecord");
        if (mAudioRecord == null) {
            mBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize);
        }
    }
    public void startRecord() {
        if(mRecordStatus != RECORD_STATUS_IDLE) {
            return;
        }
        Log.d(TAG,"startRecord");
        mAudioRecord.startRecording();
        mRecordStatus = RECORD_STATUS_RUNNING;

        new Thread(new AudioRecordThread()).start();

    }

    public void stopRecord() {
        if(mRecordStatus == RECORD_STATUS_IDLE) {
            return;
        }
        Log.d(TAG,"stopRecord");
        mRecordStatus = RECORD_STATUS_STOP;
        mAudioRecord.stop();
        mAudioRecord.release();
        mRecordStatus = RECORD_STATUS_IDLE;
    }


    class AudioRecordThread implements Runnable{
        int audioReadNum = 0;
        byte[] audioDataBuf = new byte[mBufferSize];
        @Override
        public void run() {
            while (mRecordStatus == RECORD_STATUS_RUNNING) {
                try {
                    audioReadNum = mAudioRecord.read(audioDataBuf, 0, mBufferSize);
                    if (mOutputStream != null) {
                        mOutputStream.write(audioDataBuf);
                        mOutputStream.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    try {
                        mOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



}
