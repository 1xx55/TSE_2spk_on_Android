package com.example.a2024dachuang;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Random;

public class FileUtils {

    private static final String TAG = "FileUtils";

    /**
     * 将 res/raw 中的文件复制到内部存储
     *
     * @param context  上下文
     * @param resId    资源 ID（如 R.raw.avgbst）
     * @param fileName 目标文件名
     * @return 复制后的文件路径，如果失败则返回 null
     */
    public static String copyRawResourceToInternalStorage(Context context, int resId, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        try (InputStream inputStream = context.getResources().openRawResource(resId);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[64*1024*1024]; //64MB
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Log.d(TAG, "File copied to: " + file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error copying file from res/raw: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将 assets 中的文件复制到内部存储
     *
     * @param context  上下文
     * @param assetFileName assets 中的文件名
     * @return 复制后的文件路径，如果失败则返回 null
     */
    public static String assetFilePath(Context context, String assetFileName) {
        File file = new File(context.getFilesDir(), assetFileName);
        try (InputStream inputStream = context.getAssets().open(assetFileName);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Log.d(TAG, "File copied to: " + file.getAbsolutePath());
            Log.d(TAG, "File size: " + file.length() + " bytes");
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error copying file from assets: " + e.getMessage());
            return null;
        }
    }
//    public static String assetFilePath(Context context, String assetFileName) {
//        // 1. 解析原文件名和扩展名（如 "model.txt" → "model" + ".txt"）
//        String fileNameWithoutExt;
//        String fileExt;
//        int dotIndex = assetFileName.lastIndexOf('.');
//        if (dotIndex > 0) {
//            fileNameWithoutExt = assetFileName.substring(0, dotIndex); // "model"
//            fileExt = assetFileName.substring(dotIndex);               // ".txt"
//        } else {
//            fileNameWithoutExt = assetFileName; // 如果没有扩展名，直接使用原文件名
//            fileExt = "";
//        }
//
//        // 2. 生成随机数字后缀（例如 12345）
////        int randomSuffix = new Random().nextInt(100000); // 0~99999 的随机数
////
////        // 3. 构造新文件名（如 "model_12345.txt"）
////        String newFileName = fileNameWithoutExt + "_" + randomSuffix + fileExt;
////
////        // 4. 创建目标文件
////        File file = new File(context.getFilesDir(), newFileName);
//        File file;
//        do {
//            int randomSuffix = new Random().nextInt(100000);
//            String newFileName = fileNameWithoutExt + "_" + randomSuffix + fileExt;
//            file = new File(context.getFilesDir(), newFileName);
//        } while (file.exists()); // 如果文件已存在，重新生成随机数
//
//        try (InputStream inputStream = context.getAssets().open(assetFileName);
//             FileOutputStream outputStream = new FileOutputStream(file)) {
//            byte[] buffer = new byte[1024];
//            int length;
//            while ((length = inputStream.read(buffer)) > 0) {
//                outputStream.write(buffer, 0, length);
//            }
//            Log.d(TAG, "File copied to: " + file.getAbsolutePath());
//            Log.d(TAG, "File size: " + file.length() + " bytes");
//            return file.getAbsolutePath();
//        } catch (Exception e) {
//            Log.e(TAG, "Error copying file from assets: " + e.getMessage());
//            return null;
//        }
//    }


}