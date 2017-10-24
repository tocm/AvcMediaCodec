package com.andy.mymediacodec.audio.encoder;

import android.os.Environment;
import android.util.Log;

import com.andy.mymediacodec.utils.AudioUtils;
import com.andy.mymediacodec.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.andy.mymediacodec.constants.Define.SDCARD_TEMP_FILE_DIR;
import static com.andy.mymediacodec.utils.AudioUtils.AUDIO_CHANNEL;
import static com.andy.mymediacodec.utils.AudioUtils.AUDIO_SAMPLE_RATE_HZ;

/**
 * Created by Andy.chen on 2017/7/13.
 *
 * PCM编码的WAV应用【WAV本地音频文件，不支持网络流传播】：
 PCM编码的WAV文件是音质最好的格式，Windows平台下，所有音频软件都能够提供对她的支持。
 Windows提供的WinAPI中有不少函数可以直接播放wav，因此，在开发多媒体软件时，往往大量采用wav，用作事件声效和背景音乐。
 PCM编码的wav可以达到相同采样率和采样大小条件下的最好音质，因此，也被大量用于音频编辑、非线性编辑等领域。
 特点：音质非常好，被大量软件所支持。
 适用于：多媒体开发、保存音乐和音效素材。

 */

public class EncodeWave implements IAudioEncode {
    private final static String TAG = EncodeWave.class.getSimpleName();

    private FileOutputStream mWavOutStream;
    private FileInputStream mPcmInStream;
    private File mFilePathPcm;
    private int mBufferSize;

    public EncodeWave(File pcmFilePath, int bufferSizeInBytes) {
        mFilePathPcm = pcmFilePath;
        mBufferSize = bufferSizeInBytes;
        createAudioEncodeFile();
    }

    private void createAudioEncodeFile() {
        String folderPath = Environment.getExternalStorageDirectory() + File.separator + SDCARD_TEMP_FILE_DIR;
        File fileFolder = FileUtils.createFolder(folderPath);
        try {
            File file = FileUtils.createFile(fileFolder, AudioUtils.SDCARD_TEMP_FILE_NAME_WAV);
            mWavOutStream = new FileOutputStream(file);
            Log.i(TAG, "mOutputStream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeWavHeaderFile( long srcPcmSize, long sampleRate, int channels) {
        Log.d(TAG,"writeWavHeaderFile BEGIN");
        if(mWavOutStream != null) {
            try {
                AudioUtils.addWaveHeader(mWavOutStream, srcPcmSize, sampleRate,channels);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG,"writeWavHeaderFile END");
    }

    public void startEncodeData() {
        Log.d(TAG,"startEncodeData BEGIN");
        if(mFilePathPcm == null || !FileUtils.checkExistFile(mFilePathPcm.getPath())) {
            return;
        }
        try {
            mPcmInStream = new FileInputStream(mFilePathPcm.getPath());
            long pcmDataSize = mPcmInStream.getChannel().size();
            Log.d(TAG,"startEncodeData pcmDataSize = "+pcmDataSize);

            //write wav header
            writeWavHeaderFile(pcmDataSize,AUDIO_SAMPLE_RATE_HZ,AUDIO_CHANNEL);

            //write the pcm data
            byte[] readBuf = new byte[mBufferSize];
            while (mPcmInStream.read(readBuf) != -1) {
                mWavOutStream.write(readBuf);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            stop();
        }
        Log.d(TAG,"Audio WAVE EncodeData END");
    }

    public void stop() {
        Log.d(TAG,"stop");
        try {
            if (mPcmInStream != null) {
                mPcmInStream.close();
                mPcmInStream = null;
            }
            if (mWavOutStream != null) {

                mWavOutStream.flush();
                mWavOutStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
