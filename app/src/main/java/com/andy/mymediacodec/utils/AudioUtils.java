package com.andy.mymediacodec.utils;

import android.media.AudioFormat;
import android.media.MediaFormat;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Andy.chen on 2017/7/12.
 */

public class AudioUtils {

    private final static String TAG = AudioUtils.class.getSimpleName();
    public final static String SDCARD_TEMP_FILE_NAME_PCM = "testAudio.pcm";
    public final static String SDCARD_TEMP_FILE_NAME_WAV = "testAudio.wav";
    public final static String SDCARD_TEMP_FILE_NAME_AAC = "testAudio.aac";
    public final static int AAC_ADTS_HEADER_BYTE_LEN = 7;

    public final static String AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
    public final static int AUDIO_SAMPLE_RATE_HZ = 44100;//sampleRateInHz the sample rate expressed in Hertz. 44100Hz
    public final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    public final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public final static int AUDIO_BITRATE = 64000; //audio bitrate 64kbps

    /**
     * * WAVE格式音频（扩展名为“.wav”）是Windows系统中最常见的一种音频, 无压缩。
     * 该格式的实质就是在PCM文件的前面加了一个文件头。
     * 函数通过在PCM文件前面加一个WAVE文件头从而封装为WAVE格式音频
     * <p>
     * WAVE文件是一种RIFF格式的文件。其基本块名称是“WAVE”，其中包含了两个子块“fmt”和“data”。
     * WAV 头文件长度 44 byte
     * 由WAVE_HEADER、WAVE_FMT、WAVE_DATA、采样数据共4个部分组成
     *
     * @param out
     * @param audioPcmSize
     * @param sampleRate
     * @param channels
     * @throws IOException
     */
    public static void addWaveHeader(FileOutputStream out, long audioPcmSize, long sampleRate, int channels)
            throws IOException {
        int WAV_HEADER_LEN = 44;
        long audioWavDataSize = audioPcmSize + WAV_HEADER_LEN;
        long byteRate = (channels * 8) * sampleRate * channels / 8;//byte rate = bit * hz * channel / 8bit
        byte[] header = new byte[WAV_HEADER_LEN];
        //HEADER WAVE RIFF fccId
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';

        //dwsize
        header[4] = (byte) (audioWavDataSize & 0xff);
        header[5] = (byte) ((audioWavDataSize >> 8) & 0xff);
        header[6] = (byte) ((audioWavDataSize >> 16) & 0xff);
        header[7] = (byte) ((audioWavDataSize >> 24) & 0xff);

        //fccType
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        //WAVE_FMT id
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';

        //dwSize
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;

        //wFormatTag
        header[20] = 1; // format = 1
        header[21] = 0;

        //wChannels
        header[22] = (byte) channels;
        header[23] = 0;

        //dwSamplesPerSec
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);

        //dwAvgBytesPerSec
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);

        //wBlockAlign
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;

        //uiBitsPerSample
        header[34] = 16; // bits per sample
        header[35] = 0;

        //DATA fccID
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';

        //dwSize
        header[40] = (byte) (audioPcmSize & 0xff);
        header[41] = (byte) ((audioPcmSize >> 8) & 0xff);
        header[42] = (byte) ((audioPcmSize >> 16) & 0xff);
        header[43] = (byte) ((audioPcmSize >> 24) & 0xff);
        out.write(header, 0, WAV_HEADER_LEN);
    }


    /**
     * Add ADTS header at the beginning of each and every AAC packet.
     * This is needed as MediaCodec encoder generates a packet of raw
     * AAC data.
     * <p>
     * Note the packetLen must count in the ADTS header itself.
     **/
    public static void addAACADTSHeader(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public static void addAACADIFHeader(byte[] packet, int packetLen){

        packet[0] = 'A';
        packet[1] = 'D';
        packet[2] = 'I';
        packet[3] = 'F';

//        packet[4] = 0; //copyright id
//        packet[5] = 0;
    }


    /**
     * //java.lang.IllegalArgumentException: Bad limit (capacity 4096): 7104
     * //Because the ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex); capacity limit is 4096, so can't use getMinBufferSize().
     *
     * @return
     */
    public static int getAudioBufferSize() {
        //   int bufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE_HZ, AUDIO_CHANNEL, AUDIO_FORMAT);
        int bufferSize = 4096;//the ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex); capacity limit is 4096
        return bufferSize;
    }


    /**
     * check if AAC ADTS Header data
     * @param buf
     * @return
     */
    public static boolean isAACADTSHeader(byte[] buf) {
        if (buf != null && buf.length > 7) {
            if ((buf[0] & 0xff) == 0xff && (buf[1] & 0xf0) == 0xf0) {
                return true;
            }
        }
        return false;
    }

    /**
     * get AAC Actual frame data
     * @param buf
     * @return
     */
    public static int getAACESFrame(byte[] buf) {
        int esSize = 0;
        if (buf != null && buf.length > 7) {
            if ((buf[0] & 0xff) == 0xff && (buf[1] & 0xf0) == 0xf0) {
                esSize |= ((buf[3] & 0x03) << 11);     //high 2 bit
                esSize |= buf[4] << 3;                //middle 8 bit
                esSize |= ((buf[5] & 0xe0) >> 5);        //low 3bit
            }
        }
        return esSize;
    }

    /**
     * get the AAC ADTS header
     * 7 byte   | syncword | header() | error_check() | raw_data_block() |
     * @param buf
     * @return
     */
    public static ByteBuffer getAACADTSHeader(byte[] buf) {
        if(buf != null) {
            AvcUtils.printByteData("getAACADTSHeader ",buf);
            if(isAACADTSHeader(buf)) {
                int profile = (buf[2] & 0xC0) >> 6;
                int srate = (buf[2] & 0x3C) >> 2;
                int channel = ((buf[2] & 0x01) << 2) | ((buf[3] & 0xC0) >> 6);

                Log.d(TAG,"AAC ADTS header profile = "+profile+", srate = "+srate+", channel = "+channel);
                ByteBuffer csd = ByteBuffer.allocate(2);
                csd.put(0, (byte)(profile << 4 | srate >> 1));
                csd.put(1, (byte)((srate & 0x01) << 7 | channel << 3));
                return csd;
            }
        }
        return null;
    }

    public static int findAACADTSHeaderIndex(byte[] buf, int offset, int size){
        if(buf != null) {
            if(buf.length > 7) {
                int endIndex = size - 7;
                for(int i= offset; i< endIndex; i++) {
                    if ((buf[i] & 0xFF) == 0xFF && (buf[i+1] & 0xF0) == 0xF0) {
                        return i;
                    }
                }
                return size;
            }
        }
        return -1;
    }
}

