package com.andy.mymediacodec.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import static com.andy.mymediacodec.utils.FileUtils.printSystemByteLog;

/**
 * Created by Andy.chen on 2017/8/10.
 */

public class LocalStreamReader {
    private final static String TAG = LocalStreamReader.class.getSimpleName();

    private final static int BUFFER_SIZE = 2046;
    private final static int AAC_ADTS_HEADER_LEN = 7;

    public LocalStreamReader() {
    }


    public static byte[][] readYuvFile(String fileName, int resolution[]) throws IOException{
        int readByte;
        byte yuvFileBuf[];
        int yuvFileSize = 0;
        int anFrameSize[] = new int[3];
        byte yuvDataBuf[][] = new byte[3][];
        int frmIndex = 0;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(fileName));
        if(bufferedInputStream != null) {
            while ((readByte = bufferedInputStream.read()) != -1) {
                byteArrayOutputStream.write(readByte);
            }
        }

        yuvFileBuf = byteArrayOutputStream.toByteArray();

        if(yuvFileBuf == null) {
            return null;
        }
        yuvFileSize = yuvFileBuf.length;

        //cla the frame size
        int lumaPlaneSize = resolution[0] * resolution[1]; //Y
        int chromaPlaneSize = lumaPlaneSize >> 2; //UV
        int totalFrame = yuvFileSize / (lumaPlaneSize + chromaPlaneSize + chromaPlaneSize);//Total SIZE / frame size

        int dwInFrameSize = resolution[0] * resolution[1] * 3 / 2;//per frame size
        anFrameSize[0] = resolution[0] * resolution[1];
        anFrameSize[1] = anFrameSize[2] = resolution[0] * resolution[1] / 4;

        yuvDataBuf[0] = new byte[lumaPlaneSize];
        yuvDataBuf[1] = new byte[chromaPlaneSize];
        yuvDataBuf[2] = new byte[chromaPlaneSize];

        //copy the yuv data to memory array
        while(frmIndex < totalFrame) {
            int currentFramePos = dwInFrameSize * frmIndex;//per frame size
            if(frmIndex == 0) {
                currentFramePos = dwInFrameSize;//per frame size
            }
            yuvDataBuf[0] = Arrays.copyOfRange(yuvFileBuf, frmIndex, lumaPlaneSize);
            yuvDataBuf[1] = Arrays.copyOfRange(yuvFileBuf, frmIndex, chromaPlaneSize);
            yuvDataBuf[2] = Arrays.copyOfRange(yuvFileBuf, frmIndex, chromaPlaneSize);

            frmIndex = currentFramePos;
        }
        return  yuvDataBuf;
    }


    public static void readLocalAACFileStreaming(String file, IFrameDataCallback frameDataCallback) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(new File(file));
        if (fileInputStream == null) {
            return;
        }
        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        int size = bufferedInputStream.available();
        System.out.println("available size = " + size);
        byte[] fileBuf = new byte[BUFFER_SIZE];
        byte[] remainingBuf = null;
        byte[] frameBuf = null;
        int offset = 0;
        int frameOffset = 0;
        int eachFrameSize = 0;
        int readLen = BUFFER_SIZE;
        while (offset < size) {
            System.out.println("============BEGIN loop read file=================");
            frameOffset = 0;
            int len = bufferedInputStream.read(fileBuf, 0, BUFFER_SIZE);
            printSystemByteLog("FILE SCR DATA ", fileBuf, len);
            //parse the AAC data from buffer
            while (frameOffset < len) {
                System.out.println("----loop each frame BEGIN------");
                int foundHeaderEndIndex = AudioUtils.findAACADTSHeaderIndex(fileBuf, frameOffset + AAC_ADTS_HEADER_LEN, len);
                System.out.println("find AAC header Index  = " + foundHeaderEndIndex);
                if (foundHeaderEndIndex != -1) {
                    if (foundHeaderEndIndex == len) {
                        //not found the aac header
                        //没有找到结尾标识，并且已到数组末尾位置，先保存下来然后读取下一个BUFFER 然后再拼接。
                        int remainLen = len - frameOffset;
                        remainingBuf = new byte[remainLen];
                        System.arraycopy(fileBuf, frameOffset, remainingBuf, 0, remainLen);
                        printSystemByteLog("save the remaining data ", remainingBuf, remainLen);
                        break;

                    }

                    eachFrameSize = ((remainingBuf == null) ? 0 : remainingBuf.length) + foundHeaderEndIndex - frameOffset;
                    System.out.println("each frame buffer size = " + eachFrameSize);
                    //get the range data
                    frameBuf = new byte[eachFrameSize];

                    if (remainingBuf != null) {
                        //连接上一BUFFER 帧未收尾的数据
                        int remainingLen = remainingBuf.length;
                        printSystemByteLog("get the last remaining data ", remainingBuf, remainingLen);
                        System.arraycopy(remainingBuf, 0, frameBuf, 0, remainingLen);
                        System.arraycopy(fileBuf, frameOffset, frameBuf, remainingLen, eachFrameSize - remainingLen);
                        remainingBuf = null;
                    } else {
                        System.arraycopy(fileBuf, frameOffset, frameBuf, 0, eachFrameSize);
                    }

                    printSystemByteLog("COMPLETE one FRAME DATA ", frameBuf, eachFrameSize);
                    //push the frame data to use
                    pushDataToCallback(frameDataCallback, frameBuf, 0, eachFrameSize);
                    //60fps
                    Thread.sleep(16);

                    frameOffset += eachFrameSize;
                } else {
                    break;
                }
                System.out.println("----loop each frame END frameOffset = " + frameOffset + " --------");
            }

            offset += len;

            if (offset >= size) {
                if(remainingBuf != null) {
                    //to-do sth push last frame buffer
                    printSystemByteLog("The END FRAME DATA", remainingBuf, remainingBuf.length);
                    //push the frame data to use
                    pushDataToCallback(frameDataCallback, frameBuf, 0, eachFrameSize);
                    remainingBuf = null;
                }
            }
            System.out.println("============END loop read offset = " + offset + " ==============");

        }
    }

    public interface IFrameDataCallback {
        public void pushData(byte buf[], int offset, int size);
    }

    private static void pushDataToCallback(IFrameDataCallback callback, byte[] buf, int offset, int size){
        if(callback != null) {
            callback.pushData(buf, offset, size);
        }
    }
}
