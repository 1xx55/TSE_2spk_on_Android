package com.example.a2024dachuang;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Android音频处理类，实现类似soundfile.read和soundfile.write功能
 */
public class AndroidAudioHandler {
    /**
     * 从文件读取音频数据
     * @param context Android上下文
     * @param uri 音频文件URI
     * @return 音频数据和采样率
     */
    public static AudioData readAudio(Context context, Uri uri) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        // 查找音频轨道
        int trackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                trackIndex = i;
                break;
            }
        }

        if (trackIndex < 0) {
            throw new IOException("No audio track found");
        }

        // 选择音频轨道
        extractor.selectTrack(trackIndex);
        MediaFormat format = extractor.getTrackFormat(trackIndex);

        // 获取采样率和通道数
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        // 创建解码器
        MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();

        ByteBuffer[] inputBuffers = codec.getInputBuffers();
        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        // 用于存储PCM数据
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // 开始解码
        while (!sawOutputEOS) {
            // 处理输入
            if (!sawInputEOS) {
                int inputBufferIndex = codec.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);

                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }

            // 处理输出
            int outputBufferIndex = codec.dequeueOutputBuffer(info, 10000);
            if (outputBufferIndex >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }

                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                byte[] data = new byte[info.size];
                outputBuffer.get(data);
                outputStream.write(data);

                codec.releaseOutputBuffer(outputBufferIndex, false);
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = codec.getOutputBuffers();
            }
        }

        // 释放资源
        codec.stop();
        codec.release();
        extractor.release();

        // 将PCM字节数据转换为double数组
        byte[] pcmData = outputStream.toByteArray();
        double[] audioData = new double[pcmData.length / 2];
        for (int i = 0; i < audioData.length; i++) {
            short sample = (short) ((pcmData[i * 2 + 1] << 8) | (pcmData[i * 2] & 0xFF));
            audioData[i] = sample / 32768.0;
        }

        return new AudioData(audioData, sampleRate, channelCount);
    }

    /**
     * 将音频数据写入文件
     * @param context Android上下文
     * @param audioData 音频数据
     * @param sampleRate 采样率
     * @param channelCount 声道数
     * @param outputUri 输出文件URI
     */
    public static void writeAudio(Context context, double[] audioData, int sampleRate, int channelCount, Uri outputUri) throws IOException {
        // 转换double数组为PCM字节
        byte[] pcmData = new byte[audioData.length * 2];
        for (int i = 0; i < audioData.length; i++) {
            short sample = (short) (audioData[i] * 32767);
            pcmData[i * 2] = (byte) (sample & 0xFF);
            pcmData[i * 2 + 1] = (byte) (sample >> 8);
        }

        // 创建临时PCM文件
        File tempPcmFile = File.createTempFile("temp_audio", ".pcm", context.getCacheDir());
        FileOutputStream fos = new FileOutputStream(tempPcmFile);
        fos.write(pcmData);
        fos.close();

        // 创建临时WAV文件
        File tempWavFile = File.createTempFile("temp_audio", ".wav", context.getCacheDir());

        // PCM转WAV
        FileInputStream in = new FileInputStream(tempPcmFile);
        FileOutputStream out = new FileOutputStream(tempWavFile);

        // 写入WAV头部
        writeWavHeader(out, audioData.length * 2, sampleRate, channelCount);

        // 写入PCM数据
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        in.close();
        out.close();

        // 将WAV文件复制到目标URI
        InputStream inputStream = new FileInputStream(tempWavFile);
        OutputStream outputStream = context.getContentResolver().openOutputStream(outputUri);

        buffer = new byte[1024];
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        outputStream.close();

        // 删除临时文件
        tempPcmFile.delete();
        tempWavFile.delete();
    }

    /**
     * 写入WAV文件头
     */
    private static void writeWavHeader(FileOutputStream out, int audioDataLength, int sampleRate, int channels) throws IOException {
        int byteRate = sampleRate * channels * 2; // 16位音频

        byte[] header = new byte[44];
        // RIFF头
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        // 文件长度
        int totalDataLen = audioDataLength + 36;
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        // WAV标记
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        // fmt子块
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        // fmt子块长度
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        // 音频格式（PCM = 1）
        header[20] = 1;
        header[21] = 0;
        // 声道数
        header[22] = (byte) channels;
        header[23] = 0;
        // 采样率
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        // 字节率
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 块对齐
        header[32] = (byte) (channels * 2);
        header[33] = 0;
        // 每个样本的位数
        header[34] = 16;
        header[35] = 0;
        // data子块
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        // 数据长度
        header[40] = (byte) (audioDataLength & 0xff);
        header[41] = (byte) ((audioDataLength >> 8) & 0xff);
        header[42] = (byte) ((audioDataLength >> 16) & 0xff);
        header[43] = (byte) ((audioDataLength >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    /**
     * 音频数据类
     */
    public static class AudioData {
        private double[] data;
        private int sampleRate;
        private int channels;

        public AudioData(double[] data, int sampleRate, int channels) {
            this.data = data;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }

        public double[] getData() {
            return data;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public int getChannels() {
            return channels;
        }
    }
}