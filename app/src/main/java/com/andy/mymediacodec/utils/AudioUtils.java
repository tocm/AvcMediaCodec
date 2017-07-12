package com.andy.mymediacodec.utils;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Andy.chen on 2017/7/12.
 */

public class AudioUtils {

    public final static String SDCARD_TEMP_FILE_NAME_PCM = "testAudio.pcm";
    public final static String SDCARD_TEMP_FILE_NAME_WAV = "testAudio.wav";

    /**
     * WAVE格式音频（扩展名为“.wav”）是Windows系统中最常见的一种音频。
     * 该格式的实质就是在PCM文件的前面加了一个文件头。
     * 函数通过在PCM文件前面加一个WAVE文件头从而封装为WAVE格式音频
     */
    private void addWaveHeader(FileOutputStream out, long audioSize,
                                     long audioDataSize, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (audioDataSize & 0xff);
        header[5] = (byte) ((audioDataSize >> 8) & 0xff);
        header[6] = (byte) ((audioDataSize >> 16) & 0xff);
        header[7] = (byte) ((audioDataSize >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (audioSize & 0xff);
        header[41] = (byte) ((audioSize >> 8) & 0xff);
        header[42] = (byte) ((audioSize >> 16) & 0xff);
        header[43] = (byte) ((audioSize >> 24) & 0xff);
        out.write(header, 0, 44);
    }

}
