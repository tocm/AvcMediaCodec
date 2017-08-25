package com.andy.mymediacodec.utils;

import android.util.Log;

import com.andy.mymediacodec.entity.FrameBufferQueue;
import com.andy.mymediacodec.entity.FrameEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Created by Andy.chen on 2017/8/10.
 */

public class LocalStreamReader {

    private final static String TAG = LocalStreamReader.class.getSimpleName();
    FrameBufferQueue mH264FrameBufferQueue;
    FrameBufferQueue mAACFrameBufferQueue;
    private boolean mH264LocalStreamReading;
    private boolean mAACLocalStreamReading;
    public LocalStreamReader() {
        mH264FrameBufferQueue = new FrameBufferQueue();
        mAACFrameBufferQueue = new FrameBufferQueue();
    }

    public void setH264FrameReader(boolean isReaderAvcLocalStream) {
        mH264LocalStreamReading = isReaderAvcLocalStream;
    }

    public void setAACFrameReader(boolean isReaderAvcLocalStream) {
        mAACLocalStreamReading = isReaderAvcLocalStream;
    }

    public void readAvcStreamData(InputStream inputStream) {
        byte[] fileAllStreamBuf = AvcUtils.getBytes(inputStream);
//        AvcUtils.printByteData("read src file byte : ",fileAllStreamBuf);
        int fileStreamSize = 0;
        if (fileAllStreamBuf != null) {
            fileStreamSize = fileAllStreamBuf.length;
            Log.d(TAG, "GET FILE BYTE SIZE = " + fileStreamSize);
            int decodeEachSize = 1024;
            byte[] decodeBuf = null;
            int startIndex = 0;
            int endIndex = fileStreamSize;
//            int endIndex = decodeEachSize > fileStreamSize ? fileStreamSize : decodeEachSize ;
            boolean isNalSpsPps = false;
            while (mH264LocalStreamReading) {
                if (startIndex >= fileStreamSize) {
                    //end of
                    Log.d(TAG, "======READ STREAM END=========");
                    break;
                }
                //find 0,0,0,1
                int endNalPrefix = AvcUtils.findH264NalPrefix(fileAllStreamBuf, startIndex + 5, fileStreamSize);
                Log.d(TAG, "find NAL prefix index = " + endNalPrefix);
                if (endNalPrefix != -1) {
                    //NAL sps/pps
//                    while (endNalPrefix < (fileStreamSize - 5)) {
                    if ((fileAllStreamBuf[endNalPrefix + 4] & 0x1f) == AvcUtils.AVC_NAL_TYPE_H264SPS || (fileAllStreamBuf[endNalPrefix + 4] & 0x1f) == AvcUtils.AVC_NAL_TYPE_H264PPS) {
                        endNalPrefix = AvcUtils.findH264NalPrefix(fileAllStreamBuf, endNalPrefix + 5, fileStreamSize);
                        isNalSpsPps = true;
//                            break;
                    }
//                    }

                    endIndex = endNalPrefix;

                } else {
                    endIndex = fileStreamSize;
                }
                decodeBuf = Arrays.copyOfRange(fileAllStreamBuf, startIndex, endIndex);
                Log.d(TAG, "push decoder startIndex = " + startIndex + ", endIndex = " + endIndex);
//                AvcUtils.printByteData("read frame data : ",decodeBuf);
                FrameEntity frameEntity = new FrameEntity();
                frameEntity.setId("H264 data");
                frameEntity.setBuf(decodeBuf);
                frameEntity.setSize(decodeBuf.length);
                frameEntity.setTimestamp(System.currentTimeMillis());
                mH264FrameBufferQueue.pushFrameData(frameEntity);

                //move the start index pointer
                startIndex = endIndex;
                try {
                    int gap = 1000 / AvcUtils.FPS;
                    Log.d(TAG, "read file frame gap = " + gap);
                    Thread.sleep(gap);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }



    public void readAACStreamData(InputStream inputStream, int fileSize) {
        if(inputStream == null) {
            return;
        }
        int offset = 0;
        int frameDataSize = 0;
        byte[] inputBuffer = new byte[fileSize];
        byte[] frameData;

        while (mAACLocalStreamReading) {
            int ret = 0;
            try {
                //read the src file data to temp buffer
                ret = inputStream.read(inputBuffer, offset, inputBuffer.length-offset);
                if(ret == -1) {
                    return;
                }
                //check if AAC file
                boolean isAACFileStream = AudioUtils.isAACADTSHeader(inputBuffer);
                if(!isAACFileStream) {
                    return;
                }

                //get the a frame end index
                int endFrameIndex = AudioUtils.findAACADTSHeaderIndex(inputBuffer,7, inputBuffer.length);
                Log.d(TAG,"find AAC frame end index = "+endFrameIndex);
                frameData = new byte[endFrameIndex];
                //take the temp buffer data copy to dest frame buffer
                System.arraycopy(inputBuffer,offset, frameData,0,endFrameIndex);

                //push to frameQueue
                FrameEntity frameEntity = new FrameEntity();
                frameEntity.setId("AAC local stream");
                frameEntity.setBuf(frameData);
                frameEntity.setSize(frameData.length);
                frameEntity.setTimestamp(System.currentTimeMillis());
                mAACFrameBufferQueue.pushFrameData(frameEntity);

                offset += endFrameIndex;


            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

    }

}
