/*
 * Copyright (c) 2017- SmartFit Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * SmartFit Inc. ("Confidential Information"). You shall not disclose
 * such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with SmartFit
 * Inc. All unauthorized usages in any manner are expressly prohibited.
 *
*/
package com.andy.mymediacodec.utils;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.text.format.Formatter;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by Andy.chen on 2017/3/16
 */

public class FileUtils {
    private final static String TAG = FileUtils.class.getSimpleName();

    /**
     * get storage root directory
     *
     * @param context
     * @return
     */
    public static File getStorageRootDir(Context context) {
        File dataDir = null;
        if (Environment.MEDIA_MOUNTED.equalsIgnoreCase(Environment
                .getExternalStorageState())) {
            dataDir = Environment.getExternalStorageDirectory();
        } else {
            dataDir = context.getApplicationContext().getFilesDir();
        }
        return dataDir;
    }

    /**
     *
     * get storage cache directory
     * context.getExternalCacheDir() = /mnt/sdcard/Android/data/{packageName}/cache
     * context.getCacheDir() = /data/data/{packageName}/cache
     * @param context
     * @return
     */
    public static String getStorageCacheRootDir(Context context) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return cachePath;
    }

    /**
     * get cache root directory
     * @param filePath
     * @return
     */
    public static File createCacheDirs(String filePath) {
        File cachePath = new File(filePath);
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                new File(cachePath, ".nomedia").createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return cachePath;
    }



    public static void createFolder(File folder) {
        if(folder != null) {
            if (!folder.exists()) {
                folder.mkdirs();
            }
        }
    }

    public static File createFolder(String path) {
        File folder = null;
        if(path != null) {
            folder = new File(path);
            if(folder != null) {
                if (!folder.exists()) {
                    folder.mkdirs();
                }
            }
        }
        return folder;

    }

    public static File createFile(File folder, String fileName) {
        File file = null;
        if (fileName != null) {
            file = new File(folder, fileName);
            if (file.exists()) {
                file.delete();
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                file = null;
            }
        }
        return file;
    }


    public static File copyFileFormRawResources(Context context, String toFileName, int srcRawResourceId) {
        File file = null;
        try {
            FileOutputStream fos = context.openFileOutput(toFileName, MODE_PRIVATE);
            InputStream in = context.getResources().openRawResource(srcRawResourceId);
            byte[] buff = new byte[1024];
            int len = 0;
            while ((len = in.read(buff)) != -1) {
                fos.write(buff, 0, len);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        file = context.getFileStreamPath(toFileName);
        if (file == null || !file.exists())
            throw new RuntimeException("The file has problem ");
        return file;
    }
    public static File getRawFile(Context context, String toFileName, int srcRawResourceId) {
        if(context == null) {
            Log.d(TAG,"getRawFile context is null return");
            return null;

        }
        File file = context.getFileStreamPath(toFileName);
        if (!file.exists()) {
            file = copyFileFormRawResources(context,toFileName,srcRawResourceId);
        }
        return file;
    }


    /**
     * @param filePath
     * @return
     */
    public static boolean deleteFile(String filePath) {
        File file = new File(filePath);
        return deleteFile(file);
    }

    /**
     * @param file
     * @return
     */
    public static boolean deleteFile(File file) {
        if(file != null) {
            if (file.exists()) {
                return file.delete();
            }
            return true;
        }
        return false;
    }


    /**
     * @param dirPath
     * @return
     */
    public static boolean delFolder(String dirPath) {
        return delFolder(new File(dirPath));
    }

    /**
     * @param dir
     * @return
     */
    public static boolean delFolder(File dir) {
        Log.d(TAG,"delFolder = "+dir.getPath() );
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    delFolder(file);
                }
            }
        }
        return deleteFile(dir);
    }

    /**
     * @param filePath
     * @return
     */
    public static boolean checkExistFile(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    /**
     * @param context
     * @param files
     * @return
     */
    public static String getFilesSize(Context context, List<String> files) {
        long result = 0;
        for (String filePath : files) {
            File file = new File(filePath);
            result += file.length();
        }
        return Formatter.formatFileSize(context, result);
    }

    public static void closeSilently(@Nullable Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


    public static void main(String args[]){
        try {
            LocalStreamReader.readLocalAACFileStreaming("D:\\AVCtest\\test\\AvcEncode\\testAudio.aac", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        testByteBufferAPI();
    }

    private final static boolean SYSTEM_LOG_ENABLE = false;
    public static void printSystemByteLog(String key, byte[] buf, int size) {
        if(SYSTEM_LOG_ENABLE) {
            if (buf != null && buf.length > 0) {
                System.out.print(key+ " :: size = " + size + " [");
                for (int i = 0; i < size; i++) {
                    System.out.print(Integer.toHexString(buf[i] & 0xff) + " , ");
                }
                System.out.println("]");
            }
        }
    }


    private static void testByteBufferAPI() {
        System.out.println("----------Test allocate--------");
        System.out.println("before alocate:"
                + Runtime.getRuntime().freeMemory());

        // 如果分配的内存过小，调用Runtime.getRuntime().freeMemory()大小不会变化？
        // 要超过多少内存大小JVM才能感觉到？
        ByteBuffer buffer = ByteBuffer.allocate(102400);
        System.out.println("buffer = " + buffer);

        System.out.println("after alocate:"
                + Runtime.getRuntime().freeMemory());

        // 这部分直接用的系统内存，所以对JVM的内存没有影响
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(102400);
        System.out.println("directBuffer = " + directBuffer);
        System.out.println("after direct alocate:"
                + Runtime.getRuntime().freeMemory());

        System.out.println("----------Test wrap--------");
        byte[] bytes = new byte[32];
        buffer = ByteBuffer.wrap(bytes);
        System.out.println(buffer);

        buffer = ByteBuffer.wrap(bytes, 10, 10);
        System.out.println(buffer);

        System.out.println("--------Test reset----------");
        buffer.clear();
        buffer.position(5);
        buffer.mark();
        buffer.position(10);
        System.out.println("before reset:" + buffer);
        buffer.reset();
        System.out.println("after reset:" + buffer);

        System.out.println("--------Test rewind--------");
        buffer.clear();
        buffer.position(10);
        buffer.limit(15);
        System.out.println("before rewind:" + buffer);
        buffer.rewind();
        System.out.println("before rewind:" + buffer);

        System.out.println("--------Test compact--------");
        buffer.clear();
        buffer.put("abcd".getBytes());
        System.out.println("before compact:" + buffer);
        System.out.println(new String(buffer.array()));
        buffer.flip();
        System.out.println("after flip:" + buffer);
        System.out.println((char) buffer.get());
        System.out.println((char) buffer.get());
        System.out.println((char) buffer.get());
        System.out.println("after three gets:" + buffer);
        System.out.println("\t" + new String(buffer.array()));
        buffer.compact();
        System.out.println("after compact:" + buffer);
        System.out.println("\t" + new String(buffer.array()));

        System.out.println("------Test get-------------");
        buffer = ByteBuffer.allocate(32);
        buffer.put((byte) 'a').put((byte) 'b').put((byte) 'c').put((byte) 'd')
                .put((byte) 'e').put((byte) 'f');
        System.out.println("before flip()" + buffer);
        // 转换为读取模式
        buffer.flip();
        System.out.println("before get():" + buffer);
        System.out.println((char) buffer.get());
        System.out.println("after get():" + buffer);
        // get(index)不影响position的值
        System.out.println((char) buffer.get(2));
        System.out.println("after get(index):" + buffer);
        byte[] dst = new byte[10];
        buffer.get(dst, 0, 2);
        System.out.println("after get(dst, 0, 2):" + buffer);
        System.out.println("\t dst:" + new String(dst));
        System.out.println("buffer now is:" + buffer);
        System.out.println("\t" + new String(buffer.array()));

        System.out.println("--------Test put-------");
        ByteBuffer bb = ByteBuffer.allocate(32);
        System.out.println("before put(byte):" + bb);
        System.out.println("after put(byte):" + bb.put((byte) 'z'));
        System.out.println("\t" + bb.put(2, (byte) 'c'));
        // put(2,(byte) 'c')不改变position的位置
        System.out.println("after put(2,(byte) 'c'):" + bb);
        System.out.println("\t" + new String(bb.array()));
        // 这里的buffer是 abcdef[pos=3 lim=6 cap=32]
        bb.put(buffer);
        System.out.println("after put(buffer):" + bb);
        System.out.println("\t" + new String(bb.array()));
    }


}
