package com.example.a2024dachuang;

import static java.io.File.createTempFile;

import android.content.Context;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.nio.*;
import java.util.concurrent.Executors;

public class AudioFloatConverter {
    private static final String TAG = "AudioFloatConverter";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    // 音频转float数组
    public void convertToFloat(Context context, Uri orgAudioUri, ConversionListener listener) {
        new Thread(() -> {
            try {

                // 0. 获取原音频采样率
                MediaExtractor extractor = new MediaExtractor();
                Log.d(TAG, "convert float from " + orgAudioUri);
                extractor.setDataSource(context, orgAudioUri, null);

                // 选择音频轨道
                int audioTrack = selectAudioTrack(extractor);
                if (audioTrack == -1) {
                    listener.onError("未找到音频轨道");
                    return;
                }

                extractor.selectTrack(audioTrack);
                MediaFormat format = extractor.getTrackFormat(audioTrack);

                // 获取音频参数
                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                int encoding = getEncoding(format);

                // 1. 获取文件MIME类型
                String mimeType = context.getContentResolver().getType(orgAudioUri);
                Log.d(TAG, "Original file MIME type: " + mimeType);

                Uri audioUri;
                // 2. 检查是否需要转码
                if (mimeType != null && !mimeType.equals("audio/x-wav") && !mimeType.equals("audio/wav")) {
                    // 需要转码为WAV
                    File wavFile = convertToWav(context, orgAudioUri, sampleRate);
                    if (wavFile == null) {
                        listener.onError("无法将音频转换为WAV格式");
                        return;
                    }
                    audioUri = Uri.fromFile(wavFile);
                }
                else{
                    audioUri = orgAudioUri;
                }

                // 3. 继续原有的处理流程
                // 选择新音频为Extractor
                extractor = new MediaExtractor();
                Log.d(TAG, "convert float from " + orgAudioUri);
                extractor.setDataSource(context, audioUri, null);

                // 选择音频轨道
                audioTrack = selectAudioTrack(extractor);
                if (audioTrack == -1) {
                    listener.onError("未找到音频轨道");
                    return;
                }

                extractor.selectTrack(audioTrack);
                // 读取并转换数据
                float[] samples = readAndConvert(extractor, encoding);
                extractor.release();

                // 如果是立体声，转换为单声道
                if (channels == 2) {
                    Log.d(TAG,"这是立体声，转化为单声道");
                    samples = convertStereoToMono(samples);
                    channels = 1; // 更新声道数为1
                }

                // 由于训练数据采样率为16k 所以必须转化成16k数据
                samples = resample(samples,sampleRate,16000);
                sampleRate = 16000;

                final int finalSampleRate = sampleRate;
                final int finalChannels = channels;
                final float[] finalSamples = samples;
                uiHandler.post(() -> listener.onSuccess(finalSamples, finalSampleRate, finalChannels, encoding));

            } catch (Exception e) {
                Log.e(TAG, "转换失败", e);
                listener.onError(e.getMessage());
            }
        }).start();
    }

    //重整采样率至16K
    public static float[] resample(float[] data, int originalRate, int targetRate) {
        if (originalRate == targetRate) {
            // 如果原始采样率和目标采样率相同，直接返回原始数据
            return data;
        }
        Log.d(TAG,"采样率变化: "+originalRate+" -> "+targetRate);

        // 计算转换倍数
        double ratio = (double) targetRate / originalRate;

        // 计算新数据的长度
        int newLength = (int) Math.ceil(data.length * ratio);
        float[] resampledData = new float[newLength];

        // 线性插值
        for (int i = 0; i < newLength; i++) {
            // 找到原始数据中的对应位置
            double idx = i / ratio;
            int idxFloor = (int) Math.floor(idx);
            int idxCeil = (int) Math.ceil(idx);

            // 边界检查
            if (idxCeil >= data.length) {
                idxCeil = data.length - 1;
            }

            // 线性插值
            if (idxCeil == idxFloor) {
                resampledData[i] = data[idxFloor];
            } else {
                resampledData[i] = (float) (data[idxFloor] + (data[idxCeil] - data[idxFloor]) * (idx - idxFloor));
            }
        }

        return resampledData;
    }
    /**
     * 将音频文件转换为WAV格式
     * 支持多种格式，包括AMR、M4A等
     *
     * @param context 上下文
     * @param audioUri 要转换的音频URI
     * @param targetSampleRate 目标采样率，如果为0则保持原采样率
     * @return 转换后的WAV文件，转换失败返回null
     */
    private File convertToWav(Context context, Uri audioUri, int targetSampleRate) {
        File outputDir = new File(context.getCacheDir(), "audio_conversions");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // 创建输出WAV文件
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File outputFile = new File(outputDir, "converted_" + timeStamp + ".wav");

        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        MediaFormat format = null;

        try {
            // 设置媒体提取器
            extractor = new MediaExtractor();
            extractor.setDataSource(context, audioUri, null);

            // 查找音频轨道
            int audioTrack = selectAudioTrack(extractor);
            if (audioTrack == -1) {
                Log.e(TAG, "No audio track found");
                return null;
            }

            // 选择音频轨道
            extractor.selectTrack(audioTrack);
            format = extractor.getTrackFormat(audioTrack);

            // 获取音频参数
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int bitRate = format.containsKey(MediaFormat.KEY_BIT_RATE) ?
                    format.getInteger(MediaFormat.KEY_BIT_RATE) : 128000; // 默认比特率

            // 如果指定了目标采样率，则使用它
            if (targetSampleRate > 0) {
                sampleRate = targetSampleRate;
            }

            // 创建解码器
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();

            // 创建临时ByteArrayOutputStream存储解码后的数据，用于检测和去除起始的零数据
            ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();

            // 设置缓冲区
            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            boolean isEOS = false;
            long presentationTimeUs = 0;
            boolean foundNonZero = false;
            int zeroDataCounter = 0;

            // 解码循环
            while (!isEOS) {
                // 获取输入缓冲区索引
                int inIndex = decoder.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];
                    buffer.clear();

                    // 从提取器读取数据
                    int sampleSize = extractor.readSampleData(buffer, 0);

                    if (sampleSize < 0) {
                        // 文件结束
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }

                // 获取解码后的数据
                int outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d(TAG, "Output format changed");
                        break;
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        outputBuffers = decoder.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "No output available yet");
                        break;
                    default:
                        if (outIndex >= 0) {
                            ByteBuffer outputBuffer = outputBuffers[outIndex];
                            byte[] chunk = new byte[bufferInfo.size];
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.get(chunk);
                            outputBuffer.clear();

                            // 检查数据块是否都是零
                            if (!foundNonZero) {
                                boolean nonZeroFound = false;
                                // 检查前16个字节以确定是否有非零数据
                                // 对于16位音频，一个样本占2个字节
                                int checkLimit = Math.min(16, chunk.length);
                                for (int i = 0; i < checkLimit; i += 2) {
                                    // 16位音频每个样本占2个字节，检查是否有显著非零值
                                    if (chunk.length > i+1) {
                                        short sample = (short) ((chunk[i] & 0xFF) | ((chunk[i+1] & 0xFF) << 8));
                                        if (Math.abs(sample) > 10) { // 允许一点点的噪声
                                            nonZeroFound = true;
                                            break;
                                        }
                                    }
                                }

                                if (nonZeroFound) {
                                    foundNonZero = true;
                                    Log.d(TAG, "检测到音频有效数据，开始写入");
                                } else {
                                    zeroDataCounter += chunk.length;
                                    if (zeroDataCounter > 4000) { // 如果有太多零数据，强制接受后面的数据
                                        foundNonZero = true;
                                        Log.d(TAG, "超过4000字节的零数据，强制开始写入");
                                    }
                                }
                            }

                            // 将数据写入临时缓冲区
                            if (foundNonZero) {
                                tempBaos.write(chunk);
                            }

                            decoder.releaseOutputBuffer(outIndex, false);
                        }
                        break;
                }

                // 检查是否到达流末尾
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "End of stream reached");
                    break;
                }
            }

            // 如果没有找到有效数据，可能是整个文件都是静音
            if (!foundNonZero && zeroDataCounter > 0) {
                Log.w(TAG, "整个音频文件可能都是静音");
            }

            // 获取处理后的音频数据
            byte[] audioData = tempBaos.toByteArray();
            tempBaos.close();

            // 检查是否获取到有效数据
            if (audioData.length == 0) {
                Log.e(TAG, "没有获取到有效的音频数据");
                return null;
            }

            // 现在将处理好的数据写入WAV文件
            FileOutputStream fos = new FileOutputStream(outputFile);

            // 写入WAV文件头
            writeWavHeader(fos, channels, sampleRate, AudioFormat.ENCODING_PCM_16BIT);

            // 写入音频数据
            fos.write(audioData);

            // 更新WAV文件头以反映实际数据大小
            updateWavHeader(outputFile);

            fos.close();
            decoder.stop();
            decoder.release();
            extractor.release();

            Log.d(TAG, "Conversion to WAV completed: " + outputFile.getPath() + ", 数据大小: " + audioData.length + " 字节");
            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Error converting to WAV", e);

            // 清理资源
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            if (extractor != null) {
                extractor.release();
            }

            // 删除可能部分写入的文件
            if (outputFile.exists()) {
                outputFile.delete();
            }

            return null;
        }
    }

    /**
     * 写入WAV文件头
     *
     * @param fos 文件输出流
     * @param channels 声道数
     * @param sampleRate 采样率
     * @param audioFormat 音频格式
     * @throws IOException
     */
    private void writeWavHeader(FileOutputStream fos, int channels, int sampleRate, int audioFormat) throws IOException {
        // 计算每个样本的字节数
        int bitsPerSample = 16; // 16位PCM
        int bytesPerSample = bitsPerSample / 8;

        // 写入RIFF头
        fos.write("RIFF".getBytes());
        // 后面会更新文件大小，先写入占位符
        fos.write(new byte[]{0, 0, 0, 0});
        fos.write("WAVE".getBytes());

        // 写入fmt子块
        fos.write("fmt ".getBytes());
        // 子块大小 (16字节)
        writeInt(fos, 16);
        // 音频格式 (1 = PCM)
        writeShort(fos, (short) 1);
        // 声道数
        writeShort(fos, (short) channels);
        // 采样率
        writeInt(fos, sampleRate);
        // 每秒字节数 = 采样率 * 声道数 * 每个样本的字节数
        writeInt(fos, sampleRate * channels * bytesPerSample);
        // 块对齐 = 声道数 * 每个样本的字节数
        writeShort(fos, (short) (channels * bytesPerSample));
        // 每个样本的位数
        writeShort(fos, (short) bitsPerSample);

        // 写入数据子块头
        fos.write("data".getBytes());
        // 子块大小，后面会更新，先写入占位符
        fos.write(new byte[]{0, 0, 0, 0});
    }

    /**
     * 更新WAV文件头以反映实际数据大小
     *
     * @param wavFile WAV文件
     * @throws IOException
     */
    private void updateWavHeader(File wavFile) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(wavFile, "rw");
        // 获取文件大小
        long fileSize = raf.length();

        // 更新RIFF块大小 (文件大小 - 8字节)
        raf.seek(4);
        writeInt(raf, (int) (fileSize - 8));

        // 更新data块大小 (文件大小 - 44字节)
        raf.seek(40);
        writeInt(raf, (int) (fileSize - 44));

        raf.close();
    }

    /**
     * 写入32位整数 (小端序)
     */
    private void writeInt(FileOutputStream fos, int value) throws IOException {
        fos.write(value & 0xFF);
        fos.write((value >> 8) & 0xFF);
        fos.write((value >> 16) & 0xFF);
        fos.write((value >> 24) & 0xFF);
    }

    /**
     * 写入32位整数 (小端序)
     */
    private void writeInt(RandomAccessFile raf, int value) throws IOException {
        raf.write(value & 0xFF);
        raf.write((value >> 8) & 0xFF);
        raf.write((value >> 16) & 0xFF);
        raf.write((value >> 24) & 0xFF);
    }

    /**
     * 写入16位短整数 (小端序)
     */
    private void writeShort(FileOutputStream fos, short value) throws IOException {
        fos.write(value & 0xFF);
        fos.write((value >> 8) & 0xFF);
    }

    /**
     * 辅助方法：检测输入音频格式并选择合适的处理方式
     *
     * @param context 上下文
     * @param audioUri 音频URI
     * @return 处理后的WAV文件URI
     */
    public Uri ensureWavFormat(Context context, Uri audioUri) {
        try {
            // 获取MIME类型
            String mimeType = context.getContentResolver().getType(audioUri);
            Log.d(TAG, "检测到音频MIME类型: " + mimeType);

            // 检查文件扩展名
            String extension = getFileExtension(context, audioUri);
            Log.d(TAG, "文件扩展名: " + extension);

            // 如果已经是WAV格式，直接返回
            if ((mimeType != null && (mimeType.equals("audio/x-wav") || mimeType.equals("audio/wav"))) ||
                    "wav".equalsIgnoreCase(extension)) {
                Log.d(TAG, "已经是WAV格式，无需转换");
                return audioUri;
            }

            // 确认是AMR、M4A或其他格式
            boolean isAmr = "amr".equalsIgnoreCase(extension) ||
                    (mimeType != null && mimeType.equals("audio/amr"));
            boolean isM4a = "m4a".equalsIgnoreCase(extension) ||
                    (mimeType != null && mimeType.equals("audio/mp4a-latm"));

            if (isAmr || isM4a || mimeType != null) {
                // 需要转换的格式
                Log.d(TAG, "需要转换为WAV格式");

                // 获取原始音频采样率
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(context, audioUri, null);

                int audioTrack = selectAudioTrack(extractor);
                if (audioTrack == -1) {
                    Log.e(TAG, "未找到音频轨道");
                    return null;
                }

                extractor.selectTrack(audioTrack);
                MediaFormat format = extractor.getTrackFormat(audioTrack);
                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                extractor.release();

                // 转换为WAV
                File wavFile = convertToWav(context, audioUri, sampleRate);
                if (wavFile != null) {
                    return Uri.fromFile(wavFile);
                } else {
                    Log.e(TAG, "转换失败");
                    return null;
                }
            } else {
                // 未知格式，尝试转换
                Log.d(TAG, "未知格式，尝试转换为WAV");
                File wavFile = convertToWav(context, audioUri, 0);
                if (wavFile != null) {
                    return Uri.fromFile(wavFile);
                } else {
                    Log.e(TAG, "转换失败");
                    return null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理音频格式时出错", e);
            return null;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(Context context, Uri uri) {
        String extension = "";

        try {
            if (uri.getScheme().equals("content")) {
                // 从ContentResolver获取文件名
                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        String fileName = cursor.getString(nameIndex);
                        if (fileName != null && fileName.lastIndexOf(".") != -1) {
                            extension = fileName.substring(fileName.lastIndexOf(".") + 1);
                        }
                    }
                    cursor.close();
                }
            } else if (uri.getScheme().equals("file")) {
                // 直接从文件路径获取
                String path = uri.getPath();
                if (path != null && path.lastIndexOf(".") != -1) {
                    extension = path.substring(path.lastIndexOf(".") + 1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件扩展名出错", e);
        }

        return extension;
    }
//    }/**
//     * 将音频文件转换为WAV格式
//     * 支持多种格式，包括AMR、M4A等
//     *
//     * @param context 上下文
//     * @param audioUri 要转换的音频URI
//     * @param targetSampleRate 目标采样率，如果为0则保持原采样率
//     * @return 转换后的WAV文件，转换失败返回null
//     */
//    private File convertToWav(Context context, Uri audioUri, int targetSampleRate) {
//        File outputDir = new File(context.getCacheDir(), "audio_conversions");
//        if (!outputDir.exists()) {
//            outputDir.mkdirs();
//        }
//
//        // 创建输出WAV文件
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//        File outputFile = new File(outputDir, "converted_" + timeStamp + ".wav");
//
//        MediaExtractor extractor = null;
//        MediaCodec decoder = null;
//        MediaFormat format = null;
//
//        try {
//            // 设置媒体提取器
//            extractor = new MediaExtractor();
//            extractor.setDataSource(context, audioUri, null);
//
//            // 查找音频轨道
//            int audioTrack = selectAudioTrack(extractor);
//            if (audioTrack == -1) {
//                Log.e(TAG, "No audio track found");
//                return null;
//            }
//
//            // 选择音频轨道
//            extractor.selectTrack(audioTrack);
//            format = extractor.getTrackFormat(audioTrack);
//
//            // 获取音频参数
//            String mime = format.getString(MediaFormat.KEY_MIME);
//            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//            int bitRate = format.containsKey(MediaFormat.KEY_BIT_RATE) ?
//                    format.getInteger(MediaFormat.KEY_BIT_RATE) : 128000; // 默认比特率
//
//            // 如果指定了目标采样率，则使用它
//            if (targetSampleRate > 0) {
//                sampleRate = targetSampleRate;
//            }
//
//            // 创建解码器
//            decoder = MediaCodec.createDecoderByType(mime);
//            decoder.configure(format, null, null, 0);
//            decoder.start();
//
//            // 准备WAV文件输出流
//            FileOutputStream fos = new FileOutputStream(outputFile);
//
//            // 写入WAV文件头
//            writeWavHeader(fos, channels, sampleRate, AudioFormat.ENCODING_PCM_16BIT);
//
//            // 设置缓冲区
//            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
//            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
//            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//            boolean isEOS = false;
//            long presentationTimeUs = 0;
//
//            // 解码循环
//            while (!isEOS) {
//                // 获取输入缓冲区索引
//                int inIndex = decoder.dequeueInputBuffer(10000);
//                if (inIndex >= 0) {
//                    ByteBuffer buffer = inputBuffers[inIndex];
//                    buffer.clear();
//
//                    // 从提取器读取数据
//                    int sampleSize = extractor.readSampleData(buffer, 0);
//
//                    if (sampleSize < 0) {
//                        // 文件结束
//                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        isEOS = true;
//                    } else {
//                        presentationTimeUs = extractor.getSampleTime();
//                        decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
//                        extractor.advance();
//                    }
//                }
//
//                // 获取解码后的数据
//                int outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);
//                switch (outIndex) {
//                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
//                        Log.d(TAG, "Output format changed");
//                        break;
//                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
//                        outputBuffers = decoder.getOutputBuffers();
//                        break;
//                    case MediaCodec.INFO_TRY_AGAIN_LATER:
//                        Log.d(TAG, "No output available yet");
//                        break;
//                    default:
//                        if (outIndex >= 0) {
//                            ByteBuffer outputBuffer = outputBuffers[outIndex];
//                            byte[] chunk = new byte[bufferInfo.size];
//                            outputBuffer.position(bufferInfo.offset);
//                            outputBuffer.get(chunk);
//                            outputBuffer.clear();
//                            Log.d(TAG, "pcmChunk first 10 bytes: " + Arrays.toString(Arrays.copyOf(chunk, 10)));
//                            // 写入WAV数据
//                            fos.write(chunk);
//
//                            decoder.releaseOutputBuffer(outIndex, false);
//                        }
//                        break;
//                }
//
//                // 检查是否到达流末尾
//                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    Log.d(TAG, "End of stream reached");
//                    break;
//                }
//            }
//
//            // 更新WAV文件头以反映实际数据大小
//            updateWavHeader(outputFile);
//
//            fos.close();
//            decoder.stop();
//            decoder.release();
//            extractor.release();
//
//            Log.d(TAG, "Conversion to WAV completed: " + outputFile.getPath());
//            return outputFile;
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error converting to WAV", e);
//
//            // 清理资源
//            if (decoder != null) {
//                decoder.stop();
//                decoder.release();
//            }
//            if (extractor != null) {
//                extractor.release();
//            }
//
//            // 删除可能部分写入的文件
//            if (outputFile.exists()) {
//                outputFile.delete();
//            }
//
//            return null;
//        }
//    }
//
//    /**
//     * 写入WAV文件头
//     *
//     * @param fos 文件输出流
//     * @param channels 声道数
//     * @param sampleRate 采样率
//     * @param audioFormat 音频格式
//     * @throws IOException
//     */
//    private void writeWavHeader(FileOutputStream fos, int channels, int sampleRate, int audioFormat) throws IOException {
//        // 计算每个样本的字节数
//        int bitsPerSample = 16; // 16位PCM
//        int bytesPerSample = bitsPerSample / 8;
//
//        // 写入RIFF头
//        fos.write("RIFF".getBytes());
//        // 后面会更新文件大小，先写入占位符
//        fos.write(new byte[]{0, 0, 0, 0});
//        fos.write("WAVE".getBytes());
//
//        // 写入fmt子块
//        fos.write("fmt ".getBytes());
//        // 子块大小 (16字节)
//        writeInt(fos, 16);
//        // 音频格式 (1 = PCM)
//        writeShort(fos, (short) 1);
//        // 声道数
//        writeShort(fos, (short) channels);
//        // 采样率
//        writeInt(fos, sampleRate);
//        // 每秒字节数 = 采样率 * 声道数 * 每个样本的字节数
//        writeInt(fos, sampleRate * channels * bytesPerSample);
//        // 块对齐 = 声道数 * 每个样本的字节数
//        writeShort(fos, (short) (channels * bytesPerSample));
//        // 每个样本的位数
//        writeShort(fos, (short) bitsPerSample);
//
//        // 写入数据子块头
//        fos.write("data".getBytes());
//        // 子块大小，后面会更新，先写入占位符
//        fos.write(new byte[]{0, 0, 0, 0});
//    }
//
//    /**
//     * 更新WAV文件头以反映实际数据大小
//     *
//     * @param wavFile WAV文件
//     * @throws IOException
//     */
//    private void updateWavHeader(File wavFile) throws IOException {
//        RandomAccessFile raf = new RandomAccessFile(wavFile, "rw");
//        // 获取文件大小
//        long fileSize = raf.length();
//
//        // 更新RIFF块大小 (文件大小 - 8字节)
//        raf.seek(4);
//        writeInt(raf, (int) (fileSize - 8));
//
//        // 更新data块大小 (文件大小 - 44字节)
//        raf.seek(40);
//        writeInt(raf, (int) (fileSize - 44));
//
//        raf.close();
//    }
//
//    /**
//     * 写入32位整数 (小端序)
//     */
//    private void writeInt(FileOutputStream fos, int value) throws IOException {
//        fos.write(value & 0xFF);
//        fos.write((value >> 8) & 0xFF);
//        fos.write((value >> 16) & 0xFF);
//        fos.write((value >> 24) & 0xFF);
//    }
//
//    /**
//     * 写入32位整数 (小端序)
//     */
//    private void writeInt(RandomAccessFile raf, int value) throws IOException {
//        raf.write(value & 0xFF);
//        raf.write((value >> 8) & 0xFF);
//        raf.write((value >> 16) & 0xFF);
//        raf.write((value >> 24) & 0xFF);
//    }
//
//    /**
//     * 写入16位短整数 (小端序)
//     */
//    private void writeShort(FileOutputStream fos, short value) throws IOException {
//        fos.write(value & 0xFF);
//        fos.write((value >> 8) & 0xFF);
//    }
//
//    /**
//     * 辅助方法：检测输入音频格式并选择合适的处理方式
//     *
//     * @param context 上下文
//     * @param audioUri 音频URI
//     * @return 处理后的WAV文件URI
//     */
//    public Uri ensureWavFormat(Context context, Uri audioUri) {
//        try {
//            // 获取MIME类型
//            String mimeType = context.getContentResolver().getType(audioUri);
//            Log.d(TAG, "检测到音频MIME类型: " + mimeType);
//
//            // 检查文件扩展名
//            String extension = getFileExtension(context, audioUri);
//            Log.d(TAG, "文件扩展名: " + extension);
//
//            // 如果已经是WAV格式，直接返回
//            if ((mimeType != null && (mimeType.equals("audio/x-wav") || mimeType.equals("audio/wav"))) ||
//                    "wav".equalsIgnoreCase(extension)) {
//                Log.d(TAG, "已经是WAV格式，无需转换");
//                return audioUri;
//            }
//
//            // 确认是AMR、M4A或其他格式
//            boolean isAmr = "amr".equalsIgnoreCase(extension) ||
//                    (mimeType != null && mimeType.equals("audio/amr"));
//            boolean isM4a = "m4a".equalsIgnoreCase(extension) ||
//                    (mimeType != null && mimeType.equals("audio/mp4a-latm"));
//
//            if (isAmr || isM4a || mimeType != null) {
//                // 需要转换的格式
//                Log.d(TAG, "需要转换为WAV格式");
//
//                // 获取原始音频采样率
//                MediaExtractor extractor = new MediaExtractor();
//                extractor.setDataSource(context, audioUri, null);
//
//                int audioTrack = selectAudioTrack(extractor);
//                if (audioTrack == -1) {
//                    Log.e(TAG, "未找到音频轨道");
//                    return null;
//                }
//
//                extractor.selectTrack(audioTrack);
//                MediaFormat format = extractor.getTrackFormat(audioTrack);
//                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//                extractor.release();
//
//                // 转换为WAV
//                File wavFile = convertToWav(context, audioUri, sampleRate);
//                if (wavFile != null) {
//                    return Uri.fromFile(wavFile);
//                } else {
//                    Log.e(TAG, "转换失败");
//                    return null;
//                }
//            } else {
//                // 未知格式，尝试转换
//                Log.d(TAG, "未知格式，尝试转换为WAV");
//                File wavFile = convertToWav(context, audioUri, 0);
//                if (wavFile != null) {
//                    return Uri.fromFile(wavFile);
//                } else {
//                    Log.e(TAG, "转换失败");
//                    return null;
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "处理音频格式时出错", e);
//            return null;
//        }
//    }
//
//    /**
//     * 获取文件扩展名
//     */
//    private String getFileExtension(Context context, Uri uri) {
//        String extension = "";
//
//        try {
//            if (uri.getScheme().equals("content")) {
//                // 从ContentResolver获取文件名
//                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
//                if (cursor != null && cursor.moveToFirst()) {
//                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
//                    if (nameIndex != -1) {
//                        String fileName = cursor.getString(nameIndex);
//                        if (fileName != null && fileName.lastIndexOf(".") != -1) {
//                            extension = fileName.substring(fileName.lastIndexOf(".") + 1);
//                        }
//                    }
//                    cursor.close();
//                }
//            } else if (uri.getScheme().equals("file")) {
//                // 直接从文件路径获取
//                String path = uri.getPath();
//                if (path != null && path.lastIndexOf(".") != -1) {
//                    extension = path.substring(path.lastIndexOf(".") + 1);
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "获取文件扩展名出错", e);
//        }
//
//        return extension;
//    }
//    public void convertToFloat(Context context, Uri orgAudioUri, ConversionListener listener) {
//        new Thread(() -> {
//            try {
//
//                // 0. 获取原音频采样率
//                MediaExtractor extractor = new MediaExtractor();
//                Log.d(TAG, "convert float from " + orgAudioUri);
//                extractor.setDataSource(context, orgAudioUri, null);
//
//                // 选择音频轨道
//                int audioTrack = selectAudioTrack(extractor);
//                if (audioTrack == -1) {
//                    listener.onError("未找到音频轨道");
//                    return;
//                }
//
//                extractor.selectTrack(audioTrack);
//                MediaFormat format = extractor.getTrackFormat(audioTrack);
//
//                // 获取音频参数
//                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//
//                Uri audioUri;
//                // 1. 获取文件MIME类型
//                String mimeType = context.getContentResolver().getType(orgAudioUri);
//                Log.d(TAG, "Original file MIME type: " + mimeType);
//
//                // 2. 检查是否需要转码
//                if (mimeType != null && !mimeType.equals("audio/x-wav") && !mimeType.equals("audio/wav")) {
//                    // 需要转码为WAV
//                    File wavFile = convertToWav(context, orgAudioUri, sampleRate);
//                    if (wavFile == null) {
//                        listener.onError("无法将音频转换为WAV格式");
//                        return;
//                    }
//                    audioUri = Uri.fromFile(wavFile);
//                }
//                else{
//                    audioUri = orgAudioUri;
//                }
//
//                // 0. 获取原音频采样率
//                extractor = new MediaExtractor();
//                Log.d(TAG, "convert float from " + audioUri);
//                extractor.setDataSource(context, audioUri, null);
//
//                // 选择音频轨道
//                audioTrack = selectAudioTrack(extractor);
//                if (audioTrack == -1) {
//                    listener.onError("未找到音频轨道");
//                    return;
//                }
//
//                extractor.selectTrack(audioTrack);
//                format = extractor.getTrackFormat(audioTrack);
//
//                // 获取音频参数
//                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//                int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//                int encoding = getEncoding(format);
//
//                // 读取音频数据
//                byte[] audioData = readAudioData(extractor);
//
//                // 转换为float数组
//                float[] samples = convertToFloat(audioData, encoding);
//
//                extractor.release();
//
//                // 如果是立体声，转换为单声道
//                if (channels == 2) {
//                    Log.d(TAG,"这是立体声，转化为单声道");
//                    //samples = convertStereoToMono(samples);
//                    channels = 1; // 更新声道数为1
//                }
//                final int finalChannels = channels;
//                final int finalSampleRate = sampleRate;
//                final int finalEncoding = encoding;
//                final float[] finalSamples = samples;
//                uiHandler.post(() -> listener.onSuccess(finalSamples, finalSampleRate, finalChannels, finalEncoding));
//
//            } catch (Exception e) {
//                Log.e(TAG, "转换失败", e);
//                listener.onError(e.getMessage());
//            }
//        }).start();
//    }
//
//    private byte[] readAudioData(MediaExtractor extractor) throws IOException {
//        int trackIndex = -1;
//        for (int i = 0; i < extractor.getTrackCount(); i++) {
//            MediaFormat format = extractor.getTrackFormat(i);
//            String mime = format.getString(MediaFormat.KEY_MIME);
//            if (mime.startsWith("audio/")) {
//                trackIndex = i;
//                break;
//            }
//        }
//        if (trackIndex == -1) {
//            throw new IOException("No audio track found");
//        }
//
//        extractor.selectTrack(trackIndex);
//        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 分配一个足够大的缓冲区
//        byte[] data = new byte[0];
//        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//        while (true) {
//            int sampleSize = extractor.readSampleData(buffer, 0);
//            if (sampleSize < 0) {
//                break;
//            }
//            bufferInfo.size = sampleSize;
//            bufferInfo.offset = 0;
//            byte[] temp = new byte[sampleSize];
//            buffer.get(temp);
//            byte[] newData = new byte[data.length + sampleSize];
//            System.arraycopy(data, 0, newData, 0, data.length);
//            System.arraycopy(temp, 0, newData, data.length, sampleSize);
//            data = newData;
//            extractor.advance();
//        }
//        return data;
//    }
//    /**
//     * 将音频文件转换为WAV格式
//     * 支持多种格式，包括AMR、M4A等
//     *
//     * @param context 上下文
//     * @param audioUri 要转换的音频URI
//     * @return 转换后的WAV文件，转换失败返回null
//     */
//    private File convertToWav(Context context, Uri audioUri,int targetSampleRate) {
//        File outputDir = new File(context.getCacheDir(), "audio_conversions");
//        if (!outputDir.exists()) {
//            outputDir.mkdirs();
//        }
//
//        // 创建输出WAV文件
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//        File outputFile = new File(outputDir, "converted_" + timeStamp + ".wav");
//
//        MediaExtractor extractor = null;
//        MediaCodec decoder = null;
//        MediaFormat format = null;
//
//        try {
//            // 设置媒体提取器
//            extractor = new MediaExtractor();
//            extractor.setDataSource(context, audioUri, null);
//
//            // 查找音频轨道
//            int audioTrack = selectAudioTrack(extractor);
//            if (audioTrack == -1) {
//                Log.e(TAG, "No audio track found");
//                return null;
//            }
//
//            // 选择音频轨道
//            extractor.selectTrack(audioTrack);
//            format = extractor.getTrackFormat(audioTrack);
//
//            // 获取音频参数
//            String mime = format.getString(MediaFormat.KEY_MIME);
//            int sampleRate = targetSampleRate;
//            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//
//            // 创建解码器
//            decoder = MediaCodec.createDecoderByType(mime);
//            decoder.configure(format, null, null, 0);
//            decoder.start();
//
//            // 准备WAV文件输出流
//            FileOutputStream fos = new FileOutputStream(outputFile);
//
//            // 写入WAV文件头
//            writeWavHeader(fos, channels, sampleRate, AudioFormat.ENCODING_PCM_16BIT);
//
//            // 设置缓冲区
//            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
//            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
//            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//            boolean isEOS = false;
//            long presentationTimeUs = 0;
//
//            // 解码循环
//            while (!isEOS) {
//                // 获取输入缓冲区索引
//                int inIndex = decoder.dequeueInputBuffer(10000);
//                if (inIndex >= 0) {
//                    ByteBuffer buffer = inputBuffers[inIndex];
//                    buffer.clear();
//
//                    // 从提取器读取数据
//                    int sampleSize = extractor.readSampleData(buffer, 0);
//
//                    if (sampleSize < 0) {
//                        // 文件结束
//                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        isEOS = true;
//                    } else {
//                        presentationTimeUs = extractor.getSampleTime();
//                        decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
//                        extractor.advance();
//                    }
//                }
//
//                // 获取解码后的数据
//                int outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);
//                switch (outIndex) {
//                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
//                        Log.d(TAG, "Output format changed");
//                        break;
//                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
//                        outputBuffers = decoder.getOutputBuffers();
//                        break;
//                    case MediaCodec.INFO_TRY_AGAIN_LATER:
//                        Log.d(TAG, "No output available yet");
//                        break;
//                    default:
//                        if (outIndex >= 0) {
//                            ByteBuffer outputBuffer = outputBuffers[outIndex];
//                            byte[] chunk = new byte[bufferInfo.size];
//                            outputBuffer.position(bufferInfo.offset);
//                            outputBuffer.get(chunk);
//                            outputBuffer.clear();
//
//                            // 写入WAV数据
//                            fos.write(chunk);
//
//                            decoder.releaseOutputBuffer(outIndex, false);
//                        }
//                        break;
//                }
//
//                // 检查是否到达流末尾
//                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    Log.d(TAG, "End of stream reached");
//                    break;
//                }
//            }
//
//            // 更新WAV文件头以反映实际数据大小
//            updateWavHeader(outputFile);
//
//            fos.close();
//            decoder.stop();
//            decoder.release();
//            extractor.release();
//
//            Log.d(TAG, "Conversion to WAV completed: " + outputFile.getPath());
//            return outputFile;
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error converting to WAV", e);
//
//            // 清理资源
//            if (decoder != null) {
//                decoder.stop();
//                decoder.release();
//            }
//            if (extractor != null) {
//                extractor.release();
//            }
//
//            // 删除可能部分写入的文件
//            if (outputFile.exists()) {
//                outputFile.delete();
//            }
//
//            return null;
//        }
//    }
//
//    /**
//     * 写入WAV文件头
//     *
//     * @param fos 文件输出流
//     * @param channels 声道数
//     * @param sampleRate 采样率
//     * @param audioFormat 音频格式
//     * @throws IOException
//     */
//    private void writeWavHeader(FileOutputStream fos, int channels, int sampleRate, int audioFormat) throws IOException {
//        // 计算每个样本的字节数
//        int bitsPerSample = 16; // 16位PCM
//        int bytesPerSample = bitsPerSample / 8;
//
//        // 写入RIFF头
//        fos.write("RIFF".getBytes());
//        // 后面会更新文件大小，先写入占位符
//        fos.write(new byte[]{0, 0, 0, 0});
//        fos.write("WAVE".getBytes());
//
//        // 写入fmt子块
//        fos.write("fmt ".getBytes());
//        // 子块大小 (16字节)
//        writeInt(fos, 16);
//        // 音频格式 (1 = PCM)
//        writeShort(fos, (short) 1);
//        // 声道数
//        writeShort(fos, (short) channels);
//        // 采样率
//        writeInt(fos, sampleRate);
//        // 每秒字节数 = 采样率 * 声道数 * 每个样本的字节数
//        writeInt(fos, sampleRate * channels * bytesPerSample);
//        // 块对齐 = 声道数 * 每个样本的字节数
//        writeShort(fos, (short) (channels * bytesPerSample));
//        // 每个样本的位数
//        writeShort(fos, (short) bitsPerSample);
//
//        // 写入数据子块头
//        fos.write("data".getBytes());
//        // 子块大小，后面会更新，先写入占位符
//        fos.write(new byte[]{0, 0, 0, 0});
//    }


//    public void convertToFloat(Context context, Uri audioUri, ConversionListener listener) {
//        new Thread(() -> {
//            try {
//                //Uri audioUri = ensureWavFormat(context,orgAudioUri);
//                // 0. 获取原音频采样率
//                MediaExtractor extractor = new MediaExtractor();
//                Log.d(TAG, "convert float from " + audioUri);
//                extractor.setDataSource(context, audioUri, null);
//
//                // 选择音频轨道
//                int audioTrack = selectAudioTrack(extractor);
//                if (audioTrack == -1) {
//                    listener.onError("未找到音频轨道");
//                    return;
//                }
//
//                extractor.selectTrack(audioTrack);
//                MediaFormat format = extractor.getTrackFormat(audioTrack);
//
//                // 获取音频参数
//                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//                int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//                int encoding = getEncoding(format);
////
////                // 1. 获取文件MIME类型
////                String mimeType = context.getContentResolver().getType(orgAudioUri);
////                Log.d(TAG, "Original file MIME type: " + mimeType);
//
////                // 2. 检查是否需要转码
////                if (mimeType != null && !mimeType.equals("audio/x-wav") && !mimeType.equals("audio/wav")) {
////                    // 需要转码为WAV
////                    File wavFile = convertToWav(context, orgAudioUri, sampleRate);
////                    if (wavFile == null) {
////                        listener.onError("无法将音频转换为WAV格式");
////                        return;
////                    }
////                    audioUri = Uri.fromFile(wavFile);
////                }
////                else{
////                    audioUri = orgAudioUri;
////                }
////
////                // 3. 继续原有的处理流程
////                // 选择新音频为Extractor
////                extractor.release();
//
//
////                extractor = new MediaExtractor();
////                Log.d(TAG, "convert float from " + audioUri);
////                extractor.setDataSource(context, audioUri, null);
////
////                // 选择音频轨道
////                audioTrack = selectAudioTrack(extractor);
////                if (audioTrack == -1) {
////                    listener.onError("未找到音频轨道");
////                    return;
////                }
////
////                extractor.selectTrack(audioTrack);
////                //format = extractor.getTrackFormat(audioTrack);
////                format = extractor.getTrackFormat(audioTrack);
////
////                // 获取音频参数
////                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
////                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
////                encoding = getEncoding(format);
//
//                // 读取并转换数据
//                //float[] samples = readAndConvert(extractor, encoding);
//                float[] samples = convertAToFloat(context,audioUri);
//                extractor.release();
//
//                // 如果是立体声，转换为单声道
//                if (channels == 2) {
//                    Log.d(TAG,"这是立体声，转化为单声道");
//                    //samples = convertStereoToMono(samples);
//                    channels = 1; // 更新声道数为1
//                }
//                final int finalChannels = channels;
//                final int finalSampleRate = sampleRate;
//                final int finalEncoding = encoding;
//                final float[] finalSamples = samples;
//                uiHandler.post(() -> listener.onSuccess(finalSamples, finalSampleRate, finalChannels, finalEncoding));
//
//            } catch (Exception e) {
//                Log.e(TAG, "转换失败", e);
//                listener.onError(e.getMessage());
//            }
//        }).start();
//    }
//
//    // 将立体声转换为单声道
    private float[] convertStereoToMono(float[] stereoData) {
        int monoLength = stereoData.length / 2;
        float[] monoData = new float[monoLength];

        // 简单平均法：将左右声道取平均值
        for (int i = 0, j = 0; i < stereoData.length; i += 2, j++) {
            float left = stereoData[i];
            float right = stereoData[i + 1];
            monoData[j] = (left + right) / 2.0f;
            //monoData[j] = stereoData[i];
        }

        return monoData;
    }
//
//    /**
//     * 将音频文件转换为WAV格式
//     * 支持多种格式，包括AMR、M4A等
//     *
//     * @param context 上下文
//     * @param audioUri 要转换的音频URI
//     * @param targetSampleRate 目标采样率，如果为0则保持原采样率
//     * @return 转换后的WAV文件，转换失败返回null
//     */
//    private File convertToWav(Context context, Uri audioUri, int targetSampleRate) {
//        File outputDir = new File(context.getCacheDir(), "audio_conversions");
//        if (!outputDir.exists()) {
//            outputDir.mkdirs();
//        }
//
//        // 创建输出WAV文件
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//        File outputFile = new File(outputDir, "converted_" + timeStamp + ".wav");
//
//        MediaExtractor extractor = null;
//        MediaCodec decoder = null;
//        MediaFormat format = null;
//
//        try {
//            // 设置媒体提取器
//            extractor = new MediaExtractor();
//            extractor.setDataSource(context, audioUri, null);
//
//            // 查找音频轨道
//            int audioTrack = selectAudioTrack(extractor);
//            if (audioTrack == -1) {
//                Log.e(TAG, "No audio track found");
//                return null;
//            }
//
//            // 选择音频轨道
//            extractor.selectTrack(audioTrack);
//            format = extractor.getTrackFormat(audioTrack);
//
//            // 获取音频参数
//            String mime = format.getString(MediaFormat.KEY_MIME);
//            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//            int bitRate = format.containsKey(MediaFormat.KEY_BIT_RATE) ?
//                    format.getInteger(MediaFormat.KEY_BIT_RATE) : 128000; // 默认比特率
//
//            // 如果指定了目标采样率，则使用它
//            if (targetSampleRate > 0) {
//                sampleRate = targetSampleRate;
//            }
//
//            // 创建解码器
//            decoder = MediaCodec.createDecoderByType(mime);
//            decoder.configure(format, null, null, 0);
//            decoder.start();
//
//            // 准备WAV文件输出流
//            FileOutputStream fos = new FileOutputStream(outputFile);
//
//            // 写入WAV文件头
//            writeWavHeader(fos, channels, sampleRate, AudioFormat.ENCODING_PCM_16BIT);
//
//            // 设置缓冲区
//            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
//            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
//            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//
//            boolean isEOS = false;
//            long presentationTimeUs = 0;
//
//            // 解码循环
//            while (!isEOS) {
//                // 获取输入缓冲区索引
//                int inIndex = decoder.dequeueInputBuffer(10000);
//                if (inIndex >= 0) {
//                    ByteBuffer buffer = inputBuffers[inIndex];
//                    buffer.clear();
//
//                    // 从提取器读取数据
//                    int sampleSize = extractor.readSampleData(buffer, 0);
//
//                    if (sampleSize < 0) {
//                        // 文件结束
//                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        isEOS = true;
//                    } else {
//                        presentationTimeUs = extractor.getSampleTime();
//                        decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
//                        extractor.advance();
//                    }
//                }
//
//                // 获取解码后的数据
//                int outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000);
//                switch (outIndex) {
//                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
//                        Log.d(TAG, "Output format changed");
//                        break;
//                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
//                        outputBuffers = decoder.getOutputBuffers();
//                        break;
//                    case MediaCodec.INFO_TRY_AGAIN_LATER:
//                        Log.d(TAG, "No output available yet");
//                        break;
//                    default:
//                        if (outIndex >= 0) {
//                            ByteBuffer outputBuffer = outputBuffers[outIndex];
//                            byte[] chunk = new byte[bufferInfo.size];
//                            outputBuffer.position(bufferInfo.offset);
//                            outputBuffer.get(chunk);
//                            outputBuffer.clear();
//
//                            // 写入WAV数据
//                            fos.write(chunk);
//
//                            decoder.releaseOutputBuffer(outIndex, false);
//                        }
//                        break;
//                }
//
//                // 检查是否到达流末尾
//                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    Log.d(TAG, "End of stream reached");
//                    break;
//                }
//            }
//
//            // 更新WAV文件头以反映实际数据大小
//            updateWavHeader(outputFile);
//
//            fos.close();
//            decoder.stop();
//            decoder.release();
//            extractor.release();
//
//            Log.d(TAG, "Conversion to WAV completed: " + outputFile.getPath());
//            return outputFile;
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error converting to WAV", e);
//
//            // 清理资源
//            if (decoder != null) {
//                decoder.stop();
//                decoder.release();
//            }
//            if (extractor != null) {
//                extractor.release();
//            }
//
//            // 删除可能部分写入的文件
//            if (outputFile.exists()) {
//                outputFile.delete();
//            }
//
//            return null;
//        }
//    }
//    public float[] convertAToFloat(Context context, Uri audioUri) throws IOException {
//        MediaExtractor extractor = new MediaExtractor();
//        extractor.setDataSource(context, audioUri, null);
//
//        // 选择音频轨道
//        int audioTrack = selectAudioTrack(extractor);
//        if (audioTrack == -1) {
//            throw new IOException("No audio track found");
//        }
//
//        extractor.selectTrack(audioTrack);
//        MediaFormat format = extractor.getTrackFormat(audioTrack);
//
//        // 配置MediaCodec解码器
//        String mime = format.getString(MediaFormat.KEY_MIME);
//        MediaCodec codec = MediaCodec.createDecoderByType(mime);
//        codec.configure(format, null, null, 0);
//        codec.start();
//
//        // 准备缓冲区
//        ByteBuffer[] inputBuffers = codec.getInputBuffers();
//        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
//        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//
//        ArrayList<Float> samplesList = new ArrayList<>();
//        boolean sawInputEOS = false;
//        boolean sawOutputEOS = false;
//
//        while (!sawOutputEOS) {
//            // 输入数据
//            if (!sawInputEOS) {
//                int inputBufIndex = codec.dequeueInputBuffer(10000);
//                if (inputBufIndex >= 0) {
//                    ByteBuffer inputBuf = inputBuffers[inputBufIndex];
//                    int sampleSize = extractor.readSampleData(inputBuf, 0);
//
//                    if (sampleSize < 0) {
//                        codec.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        sawInputEOS = true;
//                    } else {
//                        long presentationTimeUs = extractor.getSampleTime();
//                        codec.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0);
//                        extractor.advance();
//                    }
//                }
//            }
//
//            // 输出数据
//            int outputBufIndex = codec.dequeueOutputBuffer(info, 10000);
//            if (outputBufIndex >= 0) {
//                ByteBuffer outputBuf = outputBuffers[outputBufIndex];
//
//                // 将PCM数据转换为float数组
//                if (info.size > 0) {
//                    byte[] pcmData = new byte[info.size];
//                    outputBuf.get(pcmData);
//
//                    // 根据编码格式转换为float
//                    float[] floatSamples = convertPcmToFloat(pcmData, format);
//                    for (float sample : floatSamples) {
//                        samplesList.add(sample);
//                    }
//                }
//
//                codec.releaseOutputBuffer(outputBufIndex, false);
//
//                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    sawOutputEOS = true;
//                }
//            }
//        }
//
//        codec.stop();
//        codec.release();
//        extractor.release();
//
//        // 转换为基本float数组
//        float[] samples = new float[samplesList.size()];
//        for (int i = 0; i < samples.length; i++) {
//            samples[i] = samplesList.get(i);
//        }
//
//        return samples;
//    }
//
//    private float[] convertPcmToFloat(byte[] pcmData, MediaFormat format) {
//        int encoding = 0;
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
//            encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
//        }
//        int channelCount = 0;
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
//            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//        }
//
//        float[] floatSamples;
//
//        switch (encoding) {
//            case AudioFormat.ENCODING_PCM_8BIT:
//                floatSamples = new float[pcmData.length];
//                for (int i = 0; i < pcmData.length; i++) {
//                    floatSamples[i] = (pcmData[i] & 0xFF) / 128.0f - 1.0f;
//                }
//                break;
//
//            case AudioFormat.ENCODING_PCM_16BIT:
//                floatSamples = new float[pcmData.length / 2];
//                for (int i = 0; i < floatSamples.length; i++) {
//                    short sample = (short)((pcmData[i*2+1] << 8) | (pcmData[i*2] & 0xFF));
//                    floatSamples[i] = sample / 32768.0f;
//                }
//                break;
//
//            case AudioFormat.ENCODING_PCM_FLOAT:
//                floatSamples = new float[pcmData.length / 4];
//                ByteBuffer.wrap(pcmData).asFloatBuffer().get(floatSamples);
//                break;
//
//            default:
//                throw new IllegalArgumentException("Unsupported PCM encoding");
//        }
//
//        // 如果是立体声，转换为单声道
//        if (channelCount == 2) {
//            float[] mono = new float[floatSamples.length / 2];
//            for (int i = 0; i < mono.length; i++) {
//                mono[i] = (floatSamples[i*2] + floatSamples[i*2+1]) / 2.0f;
//            }
//            return mono;
//        }
//
//        return floatSamples;
//    }
//    /**
//     * 更新WAV文件头以反映实际数据大小
//     *
//     * @param wavFile WAV文件
//     * @throws IOException
//     */
//    private void updateWavHeader(File wavFile) throws IOException {
//        RandomAccessFile raf = new RandomAccessFile(wavFile, "rw");
//        // 获取文件大小
//        long fileSize = raf.length();
//
//        // 更新RIFF块大小 (文件大小 - 8字节)
//        raf.seek(4);
//        writeInt(raf, (int) (fileSize - 8));
//
//        // 更新data块大小 (文件大小 - 44字节)
//        raf.seek(40);
//        writeInt(raf, (int) (fileSize - 44));
//
//        raf.close();
//    }
//
//    /**
//     * 写入32位整数 (小端序)
//     */
//    private void writeInt(FileOutputStream fos, int value) throws IOException {
//        fos.write(value & 0xFF);
//        fos.write((value >> 8) & 0xFF);
//        fos.write((value >> 16) & 0xFF);
//        fos.write((value >> 24) & 0xFF);
//    }
//
//    /**
//     * 写入32位整数 (小端序)
//     */
//    private void writeInt(RandomAccessFile raf, int value) throws IOException {
//        raf.write(value & 0xFF);
//        raf.write((value >> 8) & 0xFF);
//        raf.write((value >> 16) & 0xFF);
//        raf.write((value >> 24) & 0xFF);
//    }
//
//    /**
//     * 写入16位短整数 (小端序)
//     */
//    private void writeShort(FileOutputStream fos, short value) throws IOException {
//        fos.write(value & 0xFF);
//        fos.write((value >> 8) & 0xFF);
//    }

//    /**
//     * 辅助方法：检测输入音频格式并选择合适的处理方式
//     *
//     * @param context 上下文
//     * @param audioUri 音频URI
//     * @return 处理后的WAV文件URI
//     */
//    public Uri ensureWavFormat(Context context, Uri audioUri) {
//        try {
//            // 获取MIME类型
//            String mimeType = context.getContentResolver().getType(audioUri);
//            Log.d(TAG, "检测到音频MIME类型: " + mimeType);
//
//            // 检查文件扩展名
//            String extension = getFileExtension(context, audioUri);
//            Log.d(TAG, "文件扩展名: " + extension);
//
//            // 如果已经是WAV格式，直接返回
//            if ((mimeType != null && (mimeType.equals("audio/x-wav") || mimeType.equals("audio/wav"))) ||
//                    "wav".equalsIgnoreCase(extension)) {
//                Log.d(TAG, "已经是WAV格式，无需转换");
//                return audioUri;
//            }
//
//            // 确认是AMR、M4A或其他格式
//            boolean isAmr = "amr".equalsIgnoreCase(extension) ||
//                    (mimeType != null && mimeType.equals("audio/amr"));
//            boolean isM4a = "m4a".equalsIgnoreCase(extension) ||
//                    (mimeType != null && mimeType.equals("audio/mp4a-latm"));
//
//            if (isAmr || isM4a || mimeType != null) {
//                // 需要转换的格式
//                Log.d(TAG, "需要转换为WAV格式");
//
//                // 获取原始音频采样率
//                MediaExtractor extractor = new MediaExtractor();
//                extractor.setDataSource(context, audioUri, null);
//
//                int audioTrack = selectAudioTrack(extractor);
//                if (audioTrack == -1) {
//                    Log.e(TAG, "未找到音频轨道");
//                    return null;
//                }
//
//                extractor.selectTrack(audioTrack);
//                MediaFormat format = extractor.getTrackFormat(audioTrack);
//                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//                extractor.release();
//
//                // 转换为WAV
//                File wavFile = convertToWav(context, audioUri, sampleRate);
//                if (wavFile != null) {
//                    return Uri.fromFile(wavFile);
//                } else {
//                    Log.e(TAG, "转换失败");
//                    return null;
//                }
//            } else {
//                // 未知格式，尝试转换
//                Log.d(TAG, "未知格式，尝试转换为WAV");
//                File wavFile = convertToWav(context, audioUri, 0);
//                if (wavFile != null) {
//                    return Uri.fromFile(wavFile);
//                } else {
//                    Log.e(TAG, "转换失败");
//                    return null;
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "处理音频格式时出错", e);
//            return null;
//        }
//    }
//
//    /**
//     * 获取文件扩展名
//     */
//    private String getFileExtension(Context context, Uri uri) {
//        String extension = "";
//
//        try {
//            if (uri.getScheme().equals("content")) {
//                // 从ContentResolver获取文件名
//                Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
//                if (cursor != null && cursor.moveToFirst()) {
//                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
//                    if (nameIndex != -1) {
//                        String fileName = cursor.getString(nameIndex);
//                        if (fileName != null && fileName.lastIndexOf(".") != -1) {
//                            extension = fileName.substring(fileName.lastIndexOf(".") + 1);
//                        }
//                    }
//                    cursor.close();
//                }
//            } else if (uri.getScheme().equals("file")) {
//                // 直接从文件路径获取
//                String path = uri.getPath();
//                if (path != null && path.lastIndexOf(".") != -1) {
//                    extension = path.substring(path.lastIndexOf(".") + 1);
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "获取文件扩展名出错", e);
//        }
//
//        return extension;
//    }
//private File convertToWav(Context context, Uri audioUri, int sampleRate) {
//    MediaExtractor extractor = new MediaExtractor();
//    MediaCodec decoder = null;
//    FileOutputStream wavOutStream = null;
//
//    try {
//        extractor.setDataSource(context, audioUri, null);
//        int audioTrackIndex = selectAudioTrack(extractor);
//        if (audioTrackIndex < 0) return null;
//
//        extractor.selectTrack(audioTrackIndex);
//        MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
//        String mime = format.getString(MediaFormat.KEY_MIME);
//        decoder = MediaCodec.createDecoderByType(mime);
//        decoder.configure(format, null, null, 0);
//        decoder.start();
//
//        // 创建输出 WAV 文件
//        File outputFile = File.createTempFile("converted_", ".wav", context.getCacheDir());
//        wavOutStream = new FileOutputStream(outputFile);
//
//        // WAV Header 先写入占位符
//        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//        writeWavHeader(wavOutStream, sampleRate, channelCount, 16, 0);
//
//        // 解码并写入 PCM 数据
//        ByteBuffer[] inputBuffers = decoder.getInputBuffers();
//        ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
//
//        boolean sawInputEOS = false;
//        boolean sawOutputEOS = false;
//        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
//        int totalPcmBytes = 0;
//
//        while (!sawOutputEOS) {
//            if (!sawInputEOS) {
//                int inputBufferIndex = decoder.dequeueInputBuffer(10000);
//                if (inputBufferIndex >= 0) {
//                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
//                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
//                    if (sampleSize < 0) {
//                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0,
//                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        sawInputEOS = true;
//                    } else {
//                        long presentationTimeUs = extractor.getSampleTime();
//                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize,
//                                presentationTimeUs, 0);
//                        extractor.advance();
//                    }
//                }
//            }
//
//            int outputBufferIndex = decoder.dequeueOutputBuffer(info, 10000);
//            if (outputBufferIndex >= 0) {
//                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//                byte[] pcmChunk = new byte[info.size];
//                outputBuffer.get(pcmChunk);
//                outputBuffer.clear();
//
//                wavOutStream.write(pcmChunk);
//                totalPcmBytes += pcmChunk.length;
//
//                decoder.releaseOutputBuffer(outputBufferIndex, false);
//
//                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    sawOutputEOS = true;
//                }
//            }
//        }
//
//        // 重新写 WAV header
//        wavOutStream.getChannel().position(0);
//        writeWavHeader(wavOutStream, sampleRate, channelCount, 16, totalPcmBytes);
//
//        return outputFile;
//
//    } catch (Exception e) {
//        Log.e(TAG, "convertToWav error: ", e);
//        return null;
//
//    } finally {
//        try {
//            if (decoder != null) decoder.stop();
//            if (decoder != null) decoder.release();
//            extractor.release();
//            if (wavOutStream != null) wavOutStream.close();
//        } catch (IOException ignored) {}
//    }
//}
//    private void writeWavHeader(OutputStream out, int sampleRate, int channels,
//                                int bitsPerSample, int totalAudioLen) throws IOException {
//        int byteRate = sampleRate * channels * bitsPerSample / 8;
//        int totalDataLen = totalAudioLen + 36;
//
//        byte[] header = new byte[44];
//        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
//        header[4] = (byte) (totalDataLen & 0xff);
//        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
//        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
//        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
//        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
//        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
//        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
//        header[20] = 1; header[21] = 0;
//        header[22] = (byte) channels;
//        header[23] = 0;
//        header[24] = (byte) (sampleRate & 0xff);
//        header[25] = (byte) ((sampleRate >> 8) & 0xff);
//        header[26] = (byte) ((sampleRate >> 16) & 0xff);
//        header[27] = (byte) ((sampleRate >> 24) & 0xff);
//        header[28] = (byte) (byteRate & 0xff);
//        header[29] = (byte) ((byteRate >> 8) & 0xff);
//        header[30] = (byte) ((byteRate >> 16) & 0xff);
//        header[31] = (byte) ((byteRate >> 24) & 0xff);
//        header[32] = (byte) (channels * bitsPerSample / 8);
//        header[33] = 0;
//        header[34] = (byte) bitsPerSample;
//        header[35] = 0;
//        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
//        header[40] = (byte) (totalAudioLen & 0xff);
//        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
//        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
//        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
//
//        out.write(header, 0, 44);
//    }
//    private void writeWavHeader(FileOutputStream fos, int sampleRate, int channels, int bitRate) throws IOException {
//        // 写入 RIFF 头
//        fos.write("RIFF".getBytes());
//        fos.write(new byte[]{0, 0, 0, 0}); // 文件大小（暂留）
//        fos.write("WAVE".getBytes());
//
//        // 写入 fmt 子块
//        fos.write("fmt ".getBytes());
//        fos.write(new byte[]{16, 0, 0, 0}); // 子块大小
//        fos.write(new byte[]{1, 0}); // 格式类别（PCM）
//        fos.write(new byte[]{(byte) channels, 0}); // 通道数
//        fos.write(new byte[]{
//                (byte) (sampleRate & 0xFF),
//                (byte) ((sampleRate >> 8) & 0xFF),
//                (byte) ((sampleRate >> 16) & 0xFF),
//                (byte) ((sampleRate >> 24) & 0xFF)
//        }); // 采样率
//        fos.write(new byte[]{
//                (byte) (bitRate & 0xFF),
//                (byte) ((bitRate >> 8) & 0xFF),
//                (byte) ((bitRate >> 16) & 0xFF),
//                (byte) ((bitRate >> 24) & 0xFF)
//        }); // 比特率
//        fos.write(new byte[]{
//                (byte) (channels * sampleRate * 16 / 8 & 0xFF),
//                (byte) ((channels * sampleRate * 16 / 8 >> 8) & 0xFF)
//        }); // 块对齐
//        fos.write(new byte[]{16, 0}); // 每样本位数
//
//        // 写入 data 子块
//        fos.write("data".getBytes());
//        fos.write(new byte[]{0, 0, 0, 0}); // 数据大小（暂留）
//    }
//    private File convertToWavWithFFmpeg(Context context, Uri audioUri , int targetSampleRate) {
//        try {
//            // 获取输入文件路径
//            String inputPath = getRealPathFromURI(context, audioUri);
//            if (inputPath == null) {
//                return null;
//            }
//
//            // 创建输出文件
//            File outputDir = context.getCacheDir();
//            File wavFile = File.createTempFile("converted_", ".wav", outputDir);
//
//            // 执行FFmpeg命令
//            String[] cmd = new String[] {
//                    "-y",                  // 覆盖输出文件
//                    "-i", inputPath,       // 输入文件
//                    "-acodec", "pcm_s16le", // PCM编码
//                    "-ar", String.valueOf(targetSampleRate),       // 采样率
//                    "-ac", "1",           // 单声道
//                    wavFile.getAbsolutePath() // 输出文件
//            };
//
//            int result = FFmpeg.execute(cmd);
//            if (result == RETURN_CODE_SUCCESS) {
//                return wavFile;
//            } else {
//                Log.e(TAG, "FFmpeg转换失败，返回码: " + result);
//                return null;
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "FFmpeg转码失败", e);
//            return null;
//        }
//    }

    // float数组转音频文件
    public void convertToAudio(float[] samples, String outputPath,
                               int sampleRate, int channels, int encoding,
                               SaveListener listener) {
        //new Thread(() -> {
            try {
                // 根据编码格式转换数据
                byte[] audioData = convertFromFloat(samples, encoding);

                // 创建WAV文件
                saveAsWav(audioData, outputPath, sampleRate, channels, encoding);

                uiHandler.post(()-> listener.onSaveSuccess(outputPath));

            } catch (Exception e) {
                Log.e(TAG, "保存失败", e);
                listener.onSaveError(e.getMessage());
            }
        //}).start();
    }

    // 私有辅助方法
    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private int getEncoding(MediaFormat format) {
        return format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                ? format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                : AudioFormat.ENCODING_PCM_16BIT;
    }

    private float[] readAndConvert(MediaExtractor extractor, int encoding) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        while (true) {
            int read = extractor.readSampleData(buffer, 0);
            if (read < 0) break;

            byte[] chunk = new byte[read];
            buffer.get(chunk);
            byteStream.write(chunk);
            extractor.advance();
        }

        return convertToFloat(byteStream.toByteArray(), encoding);
    }

    private float[] convertToFloat(byte[] audioData, int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_16BIT:
                // 字节序！
                ShortBuffer shortBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();;
                float[] floats16 = new float[shortBuffer.remaining()];
                for (int i = 0; i < floats16.length; i++) {
                    floats16[i] = shortBuffer.get(i) / 32768.0f;
                }
                return floats16;

            case AudioFormat.ENCODING_PCM_8BIT:
                float[] floats8 = new float[audioData.length];
                for (int i = 0; i < audioData.length; i++) {
                    floats8[i] = (audioData[i] - 128) / 128.0f;
                }
                return floats8;

            case AudioFormat.ENCODING_PCM_FLOAT:
                FloatBuffer floatBuffer = ByteBuffer.wrap(audioData).asFloatBuffer();
                float[] floats = new float[floatBuffer.remaining()];
                floatBuffer.get(floats);
                return floats;

            default:
                return new float[0];
        }
    }

    private byte[] convertFromFloat(float[] samples, int encoding) {
        ByteBuffer buffer;

        switch (encoding) {
            case AudioFormat.ENCODING_PCM_16BIT:
                buffer = ByteBuffer.allocate(samples.length * 2);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                for (float sample : samples) {
                    buffer.putShort((short)(sample * 32767));
                }
                break;

            case AudioFormat.ENCODING_PCM_8BIT:
                buffer = ByteBuffer.allocate(samples.length);
                for (float sample : samples) {
                    buffer.put((byte)((sample * 127) + 128));
                }
                break;

            case AudioFormat.ENCODING_PCM_FLOAT:
                buffer = ByteBuffer.allocate(samples.length * 4);
                buffer.asFloatBuffer().put(samples);
                break;

            default:
                return new byte[0];
        }

        return buffer.array();
    }

    private void saveAsWav(byte[] audioData, String path,
                           int sampleRate, int channels, int encoding) throws IOException {
        try (FileOutputStream out = new FileOutputStream(path);
             FileChannel channel = out.getChannel()) {

            int bitDepth = getBitDepth(encoding);
            int byteRate = sampleRate * channels * bitDepth / 8;
            int blockAlign = channels * bitDepth / 8;
            int dataSize = audioData.length;
            int totalSize = dataSize + 36;

            // 写入WAV头
            ByteBuffer header = ByteBuffer.allocate(44);
            header.order(ByteOrder.LITTLE_ENDIAN);

            header.put("RIFF".getBytes());
            header.putInt(totalSize);
            header.put("WAVE".getBytes());
            header.put("fmt ".getBytes());
            header.putInt(16); // Subchunk1Size
            header.putShort((short)1); // AudioFormat (PCM)
            header.putShort((short)channels);
            header.putInt(sampleRate);
            header.putInt(byteRate);
            header.putShort((short)blockAlign);
            header.putShort((short)bitDepth);
            header.put("data".getBytes());
            header.putInt(dataSize);

            header.flip();
            channel.write(header);
            channel.write(ByteBuffer.wrap(audioData));
        }
    }

    private int getBitDepth(int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT: return 8;
            case AudioFormat.ENCODING_PCM_16BIT: return 16;
            case AudioFormat.ENCODING_PCM_FLOAT: return 32;
            default: return 16;
        }
    }
    private String getRealPathFromURI(Context context,Uri contentUri) {
        String[] proj = {MediaStore.Audio.Media.DATA};
        android.database.Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor == null) return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }
    // 回调接口
    public interface ConversionListener {
        void onSuccess(float[] samples, int sampleRate, int channels, int encoding);
        void onError(String message);
    }

    public interface SaveListener {
        void onSaveSuccess(String filePath);
        void onSaveError(String message);
    }
}



//    public static void convertFromUri(Context context, Uri audioUri, ConversionListener listener) {
//        Executors.newSingleThreadExecutor().execute(() -> {
//            try (InputStream inputStream = context.getContentResolver().openInputStream(audioUri)) {
//                if (inputStream == null) {
//                    throw new IOException("无法打开音频流");
//                }
//
//                // 创建临时文件（因MediaExtractor需要文件路径）
//                File tempFile = createTempFile(context, inputStream);
//                float[] samples = extractAudioData(tempFile);
//                tempFile.delete();
//
//                // 回调结果到主线程
//                new Handler(Looper.getMainLooper()).post(() ->
//                        listener.onSuccess(samples[0], samples[1], samples[2], samples[3])
//                );
//
//            } catch (Exception e) {
//                Log.e(TAG, "转换失败", e);
//                new Handler(Looper.getMainLooper()).post(() ->
//                        listener.onError(e.getMessage())
//                );
//            }
//        });
//    }
//    private static float[] extractAudioData(File audioFile) throws IOException {
//        MediaExtractor extractor = new MediaExtractor();
//        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
//                audioFile,
//                ParcelFileDescriptor.MODE_READ_ONLY
//        );
//
//        try {
//            extractor.setDataSource(pfd.getFileDescriptor());
//            int audioTrack = selectAudioTrack(extractor);
//            if (audioTrack == -1) throw new IOException("未找到音频轨道");
//
//            extractor.selectTrack(audioTrack);
//            MediaFormat format = extractor.getTrackFormat(audioTrack);
//
//            return new float[]{
//                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
//                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
//                    getEncoding(format),
//                    readAndConvert(extractor, getEncoding(format))
//            };
//        } finally {
//            extractor.release();
//            pfd.close();
//        }
//    }
