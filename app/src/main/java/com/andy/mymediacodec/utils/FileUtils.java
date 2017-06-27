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
}
