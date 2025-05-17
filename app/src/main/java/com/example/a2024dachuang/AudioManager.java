package com.example.a2024dachuang;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public class AudioManager {
    private final Context context;
    private MediaPlayer mediaPlayer;
    private String currentAudioPath;
    private boolean isPlaying = false;
    private AudioStateListener audioStateListener;
    private ModelStateListener modelStateListener;

    private final AudioFloatConverter audioConverter;
    private float[] currentSamples;
    private int currentSampleRate;
    private int currentChannels;
    private int currentEncoding;

    private final String TAG = "AudioManager";

    public interface AudioStateListener {
        void updateStatusText(String text);
    }

    public interface ModelStateListener {
        void updateStatusText(String text);
    }

    public AudioManager(Context context , AudioStateListener audioStateListener , ModelStateListener modelStateListener) {
        this.context = context;
        this.audioStateListener = audioStateListener;
        this.modelStateListener = modelStateListener;
        this.audioConverter = new AudioFloatConverter();
    }

    public void setAudioStateListener(AudioStateListener audioStateListener){
        this.audioStateListener = audioStateListener;
    }

    public void loadAudio(Uri audioUri) {
        try {
            currentAudioPath = getRealPathFromURI(audioUri);

            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context,audioUri);
            mediaPlayer.prepare();

            audioStateListener.updateStatusText("成功加载音频：" + currentAudioPath);
            Log.d("AudioManager","uri: " + audioUri);
            Log.d("AudioManager","curpath: " + currentAudioPath);

            convertToFloatArray(audioUri);
            //Float ele = currentSamples[0];
            //Toast.makeText(context, ele.toString(),Toast.LENGTH_SHORT).show();

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
            });
        } catch (IOException e) {
            handleError("加载音频失败: " + e.getMessage());
            Log.d("AudioManager",e.getMessage());
        }
    }

    private void convertToFloatArray(Uri audioUri) {
        audioConverter.convertToFloat(context,audioUri, new AudioFloatConverter.ConversionListener() {
            @Override
            public void onSuccess(float[] samples, int sampleRate, int channels, int encoding) {
                currentSamples = samples;
                currentSampleRate = sampleRate;
                currentChannels = channels;
                currentEncoding = encoding;

                // 输入归一化
                float absmx = 0.000001f;
                int zerocnt = 0;
                for(int i=0;i<samples.length;i++){
                    absmx = max(absmx,abs(samples[i]));
                }
                for(int i=0;i<samples.length;i++){
                    samples[i] = samples[i] / absmx;
                    if(abs(samples[i])<1e-8) zerocnt++;
                }

                Log.d(TAG, "转换成功 :长度" + samples.length);
                Log.d(TAG, "前几项" + Arrays.toString(Arrays.copyOfRange(samples, 0, 200)));
                Log.d(TAG, "samplesRate:" + sampleRate);
                Log.d(TAG, "channels:" + channels);
                Log.d(TAG, "encoding:" + encoding);
                Log.d(TAG, "转换数据中0的个数" + zerocnt);


                modelStateListener.updateStatusText("转换样本长度：" + samples.length);
            }

            @Override
            public void onError(String message) {
                handleError("转换失败: " + message);
            }
        });
    }

    public void togglePlayback(){
        if (mediaPlayer == null) return;

        try {
           // mediaPlayer.prepare();
            if (isPlaying) {
                Toast.makeText(context, "停止播放", Toast.LENGTH_SHORT).show();

                mediaPlayer.pause();
                mediaPlayer.seekTo(0); // 停止时回到开头
            } else {
                Toast.makeText(context, "开始播放", Toast.LENGTH_SHORT).show();
                mediaPlayer.start();
            }
            isPlaying = !isPlaying;

        } catch (IllegalStateException e) {
            handleError("播放控制失败: " + e.getMessage());
        }
    }

    public void saveAsAudio(String outputPath) {
        if (currentSamples == null || currentSamples.length == 0) {
            handleError("没有可保存的音频数据");
            return;
        }

        // dbg:把这里改成不开新线程，新线程开到LoadAudioFromFloat函数
        audioConverter.convertToAudio(
                currentSamples, outputPath,
                currentSampleRate, currentChannels, currentEncoding,
                new AudioFloatConverter.SaveListener() {
                    //这个listener在其他线程调用
                    @Override
                    public void onSaveSuccess(String filePath) {
                        audioStateListener.updateStatusText("已保存到: " + filePath);
                    }

                    @Override
                    public void onSaveError(String message) {
                        handleError("保存失败: " + message);
                    }
                });
    }

    public void LoadAudioFromFloat() {
        new Thread(() -> {
            try {
                // 创建临时文件
                if (!StoragePermissionHelper.hasStoragePermission(context)) {
                    StoragePermissionHelper.requestStoragePermission((Activity) context);
                }
                File targetDir = new File(context.getFilesDir(), "out_tmp");

                // 确保目录存在
                if (!targetDir.exists()) {
                    if (!targetDir.mkdirs()) {
                        Log.e("MediaPlayer", "无法创建目录");
                        return;
                    }
                }

                // 创建临时文件(这里不填后缀让mediaplayer自动识别，否则会报错)
                File tempFile = File.createTempFile("temp_audio", "", targetDir);
                //要等待此线程结束！
                //保证这里按顺序进行
                saveAsAudio(tempFile.getPath());

                Log.d(TAG,"存储path:"+tempFile.getPath());

                if (mediaPlayer != null) {
                    mediaPlayer.release();
                }

                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(tempFile.getPath());
                mediaPlayer.prepare();
                isPlaying = false;

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    // 获取当前音频数据
    public float[] getCurrentSamples() {
        return currentSamples != null ? currentSamples : new float[0];
    }

    public int getCurrentSampleRate() {
        return currentSampleRate;
    }

    public int getCurrentChannels() {
        return currentChannels;
    }

    public int getCurrentEncoding(){
        return currentEncoding;
    }

    public void setCurrentSamples(float[] samples) {
        this.currentSamples = samples;
    }

    public void setCurrentSampleRate(int currentSampleRate) {
        this.currentSampleRate = currentSampleRate;
    }

    public void setCurrentChannels(int currentChannels) {
        this.currentChannels = currentChannels;
    }

    public void setCurrentEncoding(int currentEncoding){
        this.currentEncoding = currentEncoding;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPlaying = false;
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = {MediaStore.Audio.Media.DATA};
        android.database.Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor == null) return null;
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }

    private void handleError(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}

//        public void playFromBytes(byte[] audioBytes) {
//            try {
//                // 创建临时文件
//                File targetDir = new File(getFilesDir(), "Dachuang");
//
//                // 确保目录存在
//                if (!targetDir.exists()) {
//                    if (!targetDir.mkdirs()) {
//                        Log.e("MediaPlayer", "无法创建目录");
//                        return;
//                    }
//                }
//
//                // 创建临时文件
//                File tempFile = File.createTempFile("temp_audio", ".mp3", targetDir);
//                tempFile.deleteOnExit();
//
//                // 写入字节数据
//                FileOutputStream fos = new FileOutputStream(tempFile);
//                fos.write(audioBytes);
//                fos.close();
//
//                // 使用MediaPlayer播放
//                MediaPlayer mediaPlayer = new MediaPlayer();
//                mediaPlayer.setDataSource(tempFile.getAbsolutePath());
//                mediaPlayer.prepare();
//                mediaPlayer.start();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }