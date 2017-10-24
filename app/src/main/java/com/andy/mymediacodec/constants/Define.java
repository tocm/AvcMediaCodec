package com.andy.mymediacodec.constants;

import android.os.Environment;

/**
 * Created by Andy.chen on 2017/10/23.
 */

public class Define {

    public final static boolean AAC_ENABLE_HEADER_CFG = false;//push the AAC decoder if containers the ADTS header frame

    public final static String DECODE_LOCAL_FILE_PATH_H264 = Environment.getExternalStorageDirectory() + "/AvcEncode/testEncode.h264";
        public final static String DECODE_LOCAL_FILE_PATH_AAC = Environment.getExternalStorageDirectory() + "/AvcEncode/testAudio.aac";
//    public final static String DECODE_LOCAL_FILE_PATH_AAC = Environment.getExternalStorageDirectory() + "/AvcEncode/test.aac";
    public final static String SDCARD_TEMP_FILE_DIR = "AvcEncode";
    public final static String SDCARD_TEMP_FILE_NAME = "testEncode.h264";
    public final static String SDCARD_TEMP_FILE_NAME_YUV = "test_420_1280_720.yuv";



}
