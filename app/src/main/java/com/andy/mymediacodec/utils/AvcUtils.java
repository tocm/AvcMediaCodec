package com.andy.mymediacodec.utils;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by Andy.chen on 2017/6/9.
 */

public final class AvcUtils {

    private final static String TAG = AvcUtils.class.getSimpleName();
    public final static String DECODE_FILE_PATH = Environment.getExternalStorageDirectory() + "/AvcEncode/testEncode.h264";
    public final static String SDCARD_TEMP_FILE_DIR = "AvcEncode";
    public final static String SDCARD_TEMP_FILE_NAME = "testEncode.h264";
    public final static int WIDTH = 1280;
    public final static int HEIGHT = 720;
    public final static int BITRATE = 6000000;//Kbps
    public final static int FPS = 30;
    public final static int I_FRAME_INTERVAL = 1;
    public final static int TIMEOUT_US = 12000;
    private final static byte[] H264_NAL_PREFIX = {0, 0, 0, 1};
    //开始码之后的第一个字节的低5位判断是否为7(sps)或者8(pps),
    public static final int AVC_NAL_TYPE_H264SPS = 7;
    public static final int AVC_NAL_TYPE_H264PPS = 8;


    public static int findH264NalPrefix(byte[] srcData, int start, int end) {
        //avoid over exception
        int len = end - H264_NAL_PREFIX.length;
        for (int i = start; i < len; i++) {
            if (srcData[i] == H264_NAL_PREFIX[0]
                    && srcData[i + 1] == H264_NAL_PREFIX[1]
                    && srcData[i + 2] == H264_NAL_PREFIX[2]
                    && srcData[i + 3] == H264_NAL_PREFIX[3]) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isH264NalPrefix(byte[] srcData, int start, int end) {
        //avoid over exception
        int len = end - H264_NAL_PREFIX.length;
        for (int i = start; i < len; i++) {
            if (srcData[i] == H264_NAL_PREFIX[0]
                    && srcData[i + 1] == H264_NAL_PREFIX[1]
                    && srcData[i + 2] == H264_NAL_PREFIX[2]
                    && srcData[i + 3] == H264_NAL_PREFIX[3]) {
                return true;
            }
        }
        return false;
    }

    public static byte[][] findAvcNalSpsAndPps(byte[] srcData,int len) {
        Log.d(TAG,"findAvcNalSpsAndPps entry");
        byte[][] nalSpsPps = new byte[2][];
        nalSpsPps[0] = findAvcNALUnitType(srcData,0,len,AvcUtils.AVC_NAL_TYPE_H264SPS);
        nalSpsPps[1] = findAvcNALUnitType(srcData,0,len,AvcUtils.AVC_NAL_TYPE_H264PPS);
        Log.d(TAG,"findAvcNalSpsAndPps END");
        return nalSpsPps;
    }

    /**
     * SPS/PPS = [ 0, 0, 0, 1, 67, 42, 80, 1f, da, 1, 40, 16, e8, 6, d0, a1, 35, 0, 0, 0, 1, 68, ce, 6, e2, ]
     *
     * @param srcData
     * @param start
     * @param end
     * @param type
     * @return
     */
    private static byte[] findAvcNALUnitType(byte[] srcData, int start, int end, int type) {
        Log.d(TAG,"findAvcNalSpsAndPps start = "+start +", end = "+end +", type = "+type);
        int nalSpsPpsNumber = 5;
        byte nalData[] = null;
        int startIndex = 0;
        int endIndex = 0;
        if (srcData == null) {
            return null;
        }
        int len = end - nalSpsPpsNumber;//[0,0,0,1,67]
        int foundSpsStartIndex = -1;
        for (int i = start; i < len; ) {
            int isFoundNalPrefix = findH264NalPrefix(srcData, i, srcData.length);
            if (isFoundNalPrefix == -1) {
                Log.d(TAG,"not found H264 nal prefix return ");
                return null;
            }
            if ((srcData[isFoundNalPrefix + 4] & 0x1f) == type) {
                //found the SPS start index and then continue to find next prefix [0,0,0,1] again in the remaining arrays
                //in order to find the SPS end index.
                Log.d(TAG,"found nal prefix index = "+i);
                foundSpsStartIndex = isFoundNalPrefix;
                break;
            } else {
                i = isFoundNalPrefix + nalSpsPpsNumber;//skip 5 byte and find next order
                Log.d(TAG,"found remaining buffer index = "+i);
            }
        }
        //continue to find the SPS end index
        if (foundSpsStartIndex == -1) {
            Log.d(TAG,"findAvcNalSpsAndPps foundSpsStartIndex return -1");
            return null;
        }
        Log.d(TAG, "foundSpsStartIndex = " + foundSpsStartIndex);
        startIndex = foundSpsStartIndex;

        int isFoundNextNalPrefix = findH264NalPrefix(srcData, foundSpsStartIndex + nalSpsPpsNumber, srcData.length);
        if (isFoundNextNalPrefix == -1) {
            endIndex = srcData.length;
        } else {
            endIndex = isFoundNextNalPrefix;
        }
        Log.d(TAG, "endIndex = " + endIndex);

        //get the actual data range
        nalData = Arrays.copyOfRange(srcData, startIndex, endIndex);

        //print log
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("[");
        for(byte i : nalData) {
            stringBuffer.append(Integer.toHexString(i & 0xff) +", ");
        }
        stringBuffer.append("]");
        Log.d(TAG,"FOUND SPS/PPS HEX = "+ stringBuffer.toString());

        return nalData;
    }

    public static void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    // for the buffer for YV12(android YUV), @see below:
// https://developer.android.com/reference/android/hardware/Camera.Parameters.html#setPreviewFormat(int)
// https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12
    public static int getYuvBuffer(int width, int height) {
        // stride = ALIGN(width, 16)
        int stride = (int) Math.ceil(width / 16.0) * 16;
        // y_size = stride * height
        int y_size = stride * height;
        // c_stride = ALIGN(stride/2, 16)
        int c_stride = (int) Math.ceil(width / 32.0) * 16;
        // c_size = c_stride * height/2
        int c_size = c_stride * height / 2;
        // size = y_size + c_size * 2
        return y_size + c_size * 2;
    }

    public static void printByteData(String outKey, byte[] buf) {
        if(buf == null) {
            return;
        }
        Long printTime = System.currentTimeMillis();
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("[");
        for (byte i : buf) {
            stringBuffer.append(Integer.toHexString(i & 0xff) + " ");
        }
        stringBuffer.append("]");
        Log.d(TAG, outKey  + stringBuffer.toString());
        Log.d(TAG, "printByte ===> End of spent time =  "+Long.toString(System.currentTimeMillis() - printTime));
    }


    /**
     * read the video stream from a local file input steam
     * @param is
     * @return
     * @throws IOException
     */
    public static byte[] getBytes(InputStream is) {
        int len;
        int size = 1024;
        byte[] buf = null;
        try {
            if (is instanceof ByteArrayInputStream) {
                size = is.available();
                buf = new byte[size];

                len = is.read(buf, 0, size);
            } else {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                buf = new byte[size];
                while ((len = is.read(buf, 0, size)) != -1) {
//                    Log.d(TAG,"getBytes loop each time read actual len = "+len +",buf size = "+buf.length);
                    bos.write(buf, 0, len);
                }
                buf = bos.toByteArray();
                Log.d(TAG,"getBytes read buf size = "+buf.length);
            }
        }catch (Exception e) {
            e.printStackTrace();
            if(is != null) {
                try {
                    is.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return buf;
    }

    public static InputStream getFileInputStream(String filePath) {
        DataInputStream dataInputStream = null;
        FileInputStream fileInputStream = null;
        try {
            File file = new File(filePath);
            if (file.exists()) {
                fileInputStream = new FileInputStream(file);
                dataInputStream = new DataInputStream(fileInputStream);
                return dataInputStream;
            }
        }catch (Exception e) {
            e.printStackTrace();
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                if (dataInputStream != null) {
                    dataInputStream.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }

        }
        return null;
    }


    public static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    public static final int COLOR_FormatI420 = 1;
    public static final int COLOR_FormatNV21 = 2;

    public static final int FILE_TypeI420 = 1;
    public static final int FILE_TypeNV21 = 2;
    public static final int FILE_TypeJPEG = 3;

    public static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if (true) Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (true) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            Log.d(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    public static void dumpFile(String fileName, byte[] data) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        try {
            outStream.write(data);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("failed writing data to file " + fileName, ioe);
        }
    }


    public static void compressToJpeg(String fileName, Image image) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        Rect rect = image.getCropRect();
        YuvImage yuvImage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
        yuvImage.compressToJpeg(rect, 100, outStream);
    }

    public static void compressToJpeg(byte[] data, int len,int width,int height) {
        Log.d(TAG, "===> capture the picture");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buf = null;
            Rect rect = new Rect(0, 0, width, height);
            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
            yuvImage.compressToJpeg(rect, 80, bos);
            buf = bos.toByteArray();
            bos.flush();
            bos.close();

            saveToLocal(buf);
        }catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            bos = null;
        }


    }


    private static void saveToLocal(byte[] buf) {
        FileOutputStream fos = null;
        String picPath = Environment.getExternalStorageDirectory() + "/capture_frame+"+System.currentTimeMillis()+".jpg";
        Log.d(TAG, "===> capture the picture path = " + picPath);
        if (TextUtils.isEmpty(picPath)) {
            return;
        }

        File file = new File(picPath);
        if (file.exists()) {
            file.delete();
        }
        try {
            fos = new FileOutputStream(file);
            fos.write(buf);
            fos.flush();
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                fos = null;
            }
        }
        Log.d(TAG, "end of save to local ");
    }


    /**
     * Returns a color format that is supported by the codec and by this test code. If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            Log.d(TAG,"supports colorFormat = "+colorFormat);
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.d(TAG,"couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0; // not reached
    }


    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
        // these are the formats we know how to handle for this test
//            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar://is supported I420
          //  case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
         //   case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
         //   case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
//            case COLOR_FormatYUV420Flexible:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
}




