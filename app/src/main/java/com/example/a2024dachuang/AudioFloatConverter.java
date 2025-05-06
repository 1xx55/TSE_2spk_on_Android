package com.example.a2024dachuang;

import static java.io.File.createTempFile;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.Executors;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.nio.*;
import java.util.concurrent.Executors;

//public class AudioFloatConverter {
//    private static final String TAG = "AudioFloatConverter";
//
//    // 音频转float数组
//    public void convertToFloat(Context context, Uri audioUri, ConversionListener listener) {
//        Executors.newSingleThreadExecutor().execute(() -> {
//            try {
//                MediaExtractor extractor = new MediaExtractor();
//                Log.d(TAG, "convert float from " + audioUri);
//                extractor.setDataSource(context, audioUri, null);
//
//                int audioTrack = selectAudioTrack(extractor);
//                if (audioTrack == -1) {
//                    listener.onError("未找到音频轨道");
//                    return;
//                }
//
//                extractor.selectTrack(audioTrack);
//                MediaFormat format = extractor.getTrackFormat(audioTrack);
//
//                int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
//                int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
//                int encoding = getEncoding(format);
//
//                float[] samples = readAndConvert(extractor, encoding);
//                extractor.release();
//
//                listener.onSuccess(samples, sampleRate, channels, encoding);
//
//            } catch (Exception e) {
//                Log.e(TAG, "转换失败", e);
//                listener.onError(e.getMessage());
//            }
//        });
//    }
//
//    // float数组转音频文件
//    public void convertToAudio(float[] samples, String outputPath,
//                               int sampleRate, int channels, int encoding,
//                               SaveListener listener) {
//        Executors.newSingleThreadExecutor().execute(() -> {
//            try {
//                byte[] audioData = convertFromFloat(samples, encoding);
//                saveAsWav(audioData, outputPath, sampleRate, channels, encoding);
//                //listener.onSaveSuccess(outputPath);
//            } catch (Exception e) {
//                Log.e(TAG, "保存失败", e);
//                //listener.onSaveError(e.getMessage());
//            }
//        });
//    }
//
//    // 私有辅助方法
//    private int selectAudioTrack(MediaExtractor extractor) {
//        for (int i = 0; i < extractor.getTrackCount(); i++) {
//            MediaFormat format = extractor.getTrackFormat(i);
//            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
//                return i;
//            }
//        }
//        return -1;
//    }
//
//    private int getEncoding(MediaFormat format) {
//        return format.containsKey(MediaFormat.KEY_PCM_ENCODING)
//                ? format.getInteger(MediaFormat.KEY_PCM_ENCODING)
//                : AudioFormat.ENCODING_PCM_16BIT;
//    }
//
//    private float[] readAndConvert(MediaExtractor extractor, int encoding) throws IOException {
//        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
//        buffer.order(ByteOrder.LITTLE_ENDIAN); // WAV使用小端序
//        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
//
//        while (true) {
//            int read = extractor.readSampleData(buffer, 0);
//            if (read < 0) break;
//
//            byte[] chunk = new byte[read];
//            buffer.get(chunk);
//            byteStream.write(chunk);
//            buffer.clear();
//            extractor.advance();
//        }
//
//        return convertToFloat(byteStream.toByteArray(), encoding);
//    }
//
//    private float[] convertToFloat(byte[] audioData, int encoding) {
//        ByteBuffer buffer = ByteBuffer.wrap(audioData);
//        buffer.order(ByteOrder.LITTLE_ENDIAN);
//
//        switch (encoding) {
//            case AudioFormat.ENCODING_PCM_16BIT:
//                ShortBuffer shortBuffer = buffer.asShortBuffer();
//                float[] floats16 = new float[shortBuffer.remaining()];
//                for (int i = 0; i < floats16.length; i++) {
//                    short sample = shortBuffer.get(i);
//                    floats16[i] = sample / (sample >= 0 ? 32767.0f : 32768.0f);
//                }
//                return floats16;
//
//            case AudioFormat.ENCODING_PCM_8BIT:
//                float[] floats8 = new float[audioData.length];
//                for (int i = 0; i < audioData.length; i++) {
//                    floats8[i] = ((audioData[i] & 0xFF) - 128) / 128.0f;
//                }
//                return floats8;
//
//            case AudioFormat.ENCODING_PCM_FLOAT:
//                FloatBuffer floatBuffer = buffer.asFloatBuffer();
//                float[] floats = new float[floatBuffer.remaining()];
//                floatBuffer.get(floats);
//                return floats;
//
//            default:
//                throw new IllegalArgumentException("Unsupported encoding: " + encoding);
//        }
//    }
//
//    private byte[] convertFromFloat(float[] samples, int encoding) {
//        ByteBuffer buffer;
//
//        switch (encoding) {
//            case AudioFormat.ENCODING_PCM_16BIT:
//                buffer = ByteBuffer.allocate(samples.length * 2);
//                buffer.order(ByteOrder.LITTLE_ENDIAN);
//                for (float sample : samples) {
//                    short value = (short)(sample * (sample >= 0 ? 32767 : 32768));
//                    buffer.putShort(value);
//                }
//                break;
//
//            case AudioFormat.ENCODING_PCM_8BIT:
//                buffer = ByteBuffer.allocate(samples.length);
//                for (float sample : samples) {
//                    byte value = (byte)((sample + 1.0f) * 127.5f);
//                    buffer.put(value);
//                }
//                break;
//
//            case AudioFormat.ENCODING_PCM_FLOAT:
//                buffer = ByteBuffer.allocate(samples.length * 4);
//                buffer.order(ByteOrder.LITTLE_ENDIAN);
//                FloatBuffer floatBuffer = buffer.asFloatBuffer();
//                floatBuffer.put(samples);
//                break;
//
//            default:
//                throw new IllegalArgumentException("Unsupported encoding: " + encoding);
//        }
//
//        return buffer.array();
//    }
//
//    private void saveAsWav(byte[] audioData, String path,
//                           int sampleRate, int channels, int encoding) throws IOException {
//        try (FileOutputStream out = new FileOutputStream(path);
//             DataOutputStream dos = new DataOutputStream(out)) {
//
//            int bitDepth = getBitDepth(encoding);
//            int byteRate = sampleRate * channels * bitDepth / 8;
//            int blockAlign = channels * bitDepth / 8;
//            int dataSize = audioData.length;
//            int totalSize = dataSize + 36;
//            short audioFormat = (encoding == AudioFormat.ENCODING_PCM_FLOAT) ? (short)3 : (short)1;
//
//            // 写入WAV头 (小端序)
//            dos.writeBytes("RIFF");
//            dos.writeInt(Integer.reverseBytes(totalSize));
//            dos.writeBytes("WAVE");
//            dos.writeBytes("fmt ");
//            dos.writeInt(Integer.reverseBytes(16));
//            dos.writeShort(Short.reverseBytes(audioFormat));
//            dos.writeShort(Short.reverseBytes((short)channels));
//            dos.writeInt(Integer.reverseBytes(sampleRate));
//            dos.writeInt(Integer.reverseBytes(byteRate));
//            dos.writeShort(Short.reverseBytes((short)blockAlign));
//            dos.writeShort(Short.reverseBytes((short)bitDepth));
//            dos.writeBytes("data");
//            dos.writeInt(Integer.reverseBytes(dataSize));
//
//            // 写入音频数据
//            dos.write(audioData);
//        }
//    }
//
//    private int getBitDepth(int encoding) {
//        switch (encoding) {
//            case AudioFormat.ENCODING_PCM_8BIT: return 8;
//            case AudioFormat.ENCODING_PCM_16BIT: return 16;
//            case AudioFormat.ENCODING_PCM_FLOAT: return 32;
//            default: throw new IllegalArgumentException("Unsupported encoding");
//        }
//    }
//
//    // 回调接口
//    public interface ConversionListener {
//        void onSuccess(float[] samples, int sampleRate, int channels, int encoding);
//        void onError(String message);
//    }
//
//    public interface SaveListener {
//        void onSaveSuccess(String filePath);
//        void onSaveError(String message);
//    }
//}

public class AudioFloatConverter {
    private static final String TAG = "AudioFloatConverter";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    // 音频转float数组

//    public void convertToFloat(Context context,Uri audioUri, ConversionListener listener) {
//        new Thread(() -> {
//            try {
//                MediaExtractor extractor = new MediaExtractor();
//                Log.d(TAG,"convert float from "+audioUri);
//                extractor.setDataSource(context, audioUri,null);
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
//
//                // 读取并转换数据
//                float[] samples = readAndConvert(extractor, encoding);
//                extractor.release();
//
//                uiHandler.post(() -> listener.onSuccess(samples, sampleRate, channels, encoding));  // UI 线程调用
//
//            } catch (Exception e) {
//                Log.e(TAG, "转换失败", e);
//                listener.onError(e.getMessage());
//            }
//        }).start();
//    }
//
    public void convertToFloat(Context context, Uri audioUri, ConversionListener listener) {
        new Thread(() -> {
            try {
                MediaExtractor extractor = new MediaExtractor();
                Log.d(TAG, "convert float from " + audioUri);
                extractor.setDataSource(context, audioUri, null);

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

                // 读取并转换数据
                float[] samples = readAndConvert(extractor, encoding);
                extractor.release();

                // 如果是立体声，转换为单声道
                if (channels == 2) {
                    Log.d(TAG,"这是立体声，转化为单声道");
                    samples = convertStereoToMono(samples);
                    channels = 1; // 更新声道数为1
                }
                final int finalChannels = channels;
                final float[] finalSamples = samples;
                uiHandler.post(() -> listener.onSuccess(finalSamples, sampleRate, finalChannels, encoding));

            } catch (Exception e) {
                Log.e(TAG, "转换失败", e);
                listener.onError(e.getMessage());
            }
        }).start();
    }

    // 将立体声转换为单声道
    private float[] convertStereoToMono(float[] stereoData) {
        int monoLength = stereoData.length / 2;
        float[] monoData = new float[monoLength];

        // 简单平均法：将左右声道取平均值
        for (int i = 0, j = 0; i < stereoData.length; i += 2, j++) {
            float left = stereoData[i];
            float right = stereoData[i + 1];
            monoData[j] = (left + right) / 2.0f;
        }

        return monoData;
    }
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
