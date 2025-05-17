package com.example.a2024dachuang;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TSEModel_2spk
{
    private final String TAG = "TSEModel_2spk";
    private final Context context;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Integer model_count = 1;

    private int chunkSize = 16000;

    private ONNXModelInference[] tsemodels = new ONNXModelInference[model_count];
    private final AudioManager.ModelStateListener modelStateListener;
    public AudioManager inputManager, embed1Manager, embed2Manager, output1Manager,output2Manager;

    private float[] model_input , model_emb , model_output;

    public TSEModel_2spk(Context context, String modelPath, AudioManager.ModelStateListener modelStateListener) throws Exception {
        this.context = context;
        //初始化系列模组
        for (int i=0 ; i<model_count ; i++)
            this.tsemodels[i] = new ONNXModelInference(context,modelPath);

        this.modelStateListener = modelStateListener;
        this.inputManager = new AudioManager(context,null , modelStateListener);
        this.embed1Manager = new AudioManager(context,null , modelStateListener);
        this.embed2Manager = new AudioManager(context,null , modelStateListener) ;
        this.output1Manager = new AudioManager(context,null , modelStateListener);
        this.output2Manager = new AudioManager(context,null , modelStateListener);

    }

    public void bindManagerStatusText(String Tag , AudioManager manager, TextView textView){
        manager.setAudioStateListener(new AudioManager.AudioStateListener() {
            @Override
            public void updateStatusText(String text) {
                String show = "[" + Tag + "]:" + text;
                textView.setText(show);
            }
        });
    }

    public void testWork(){
        //把输入1的信息导入输出1然后检查效果
        output1Manager.setCurrentSamples(inputManager.getCurrentSamples());
        output1Manager.setCurrentChannels(inputManager.getCurrentChannels());
        output1Manager.setCurrentEncoding(inputManager.getCurrentEncoding());
        output1Manager.setCurrentSampleRate(inputManager.getCurrentSampleRate());
        output1Manager.LoadAudioFromFloat();
    }

    public void infer() {
        new Thread(() -> {
            if (inputManager.getCurrentSamples() == null) {
                uiHandler.post(() -> modelStateListener.updateStatusText("未加载输入音频，无法推理！"));
                return;
            }
            uiHandler.post(() -> modelStateListener.updateStatusText("开始加载输入"));

            float[] input_sample = inputManager.getCurrentSamples();

            model_input = new float[input_sample.length * 2];

            //填充输入输出。2维度都是一样的，一个分离spk1，一个分离spk2。
            for (int i = 0; i < input_sample.length; i++) {
                model_input[i] = input_sample[i];
            }
            for (int i = 0; i < input_sample.length; i++) {
                model_input[i + input_sample.length] = input_sample[i];
            }

            float[] emb1 = embed1Manager.getCurrentSamples();
            float[] emb2 = embed2Manager.getCurrentSamples();
            // min 参数训练 决定 emb 取 min
            int emblen = min(emb1.length, emb2.length);
            model_emb = new float[emblen * 2];

            //填充embeddings
            for (int i = 0; i < emblen; i++) {
                model_emb[i] = emb1[i];
            }
            for (int i = 0; i < emblen; i++) {
                model_emb[i + emblen] = emb2[i];
            }

            long[] inputshape = {2, input_sample.length};
            long[] embshape = {2, emblen};

            uiHandler.post(() -> modelStateListener.updateStatusText("开始推理"));

            //开始推理
            long startTime = System.currentTimeMillis();
            try {
                model_output = tsemodels[0].infer(model_input, model_emb, inputshape, embshape);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            long endTime = System.currentTimeMillis();

            uiHandler.post(() -> modelStateListener.updateStatusText("完成推理，用时" + (endTime - startTime) + "ms"));
            Log.d(TAG, "完成推理,用时" + (endTime - startTime) + "ms");
            // 填充输出Manager
            float[] out1 = new float[input_sample.length];
            float[] out2 = new float[input_sample.length];

            //归一化
            float absmax = 0;

            for (int i = 0; i < input_sample.length; i++) {
                absmax = max(Math.abs(model_output[i]), absmax);
            }
            for (int i = 0; i < input_sample.length; i++) {
                out1[i] = model_output[i] / absmax;
            }

            absmax = 0;

            for (int i = 0; i < input_sample.length; i++) {
                absmax = max(Math.abs(model_output[i + input_sample.length]), absmax);
            }

            for (int i = 0; i < input_sample.length; i++) {
                out2[i] = model_output[i + input_sample.length] / absmax;
            }

            output1Manager.setCurrentSamples(out1);
            output1Manager.setCurrentEncoding(inputManager.getCurrentEncoding());
            output1Manager.setCurrentChannels(inputManager.getCurrentChannels());
            output1Manager.setCurrentSampleRate(inputManager.getCurrentSampleRate());
            output1Manager.LoadAudioFromFloat();

            output2Manager.setCurrentSamples(out2);
            output2Manager.setCurrentEncoding(inputManager.getCurrentEncoding());
            output2Manager.setCurrentChannels(inputManager.getCurrentChannels());
            output2Manager.setCurrentSampleRate(inputManager.getCurrentSampleRate());
            output2Manager.LoadAudioFromFloat();

        }).start();
    }
    public void inferMulti2() {
        new Thread(() -> {

            if (inputManager.getCurrentSamples() == null) {
                uiHandler.post(() -> modelStateListener.updateStatusText("未加载输入音频，无法推理！"));
                return;
            }
            uiHandler.post(() -> modelStateListener.updateStatusText("开始加载输入"));

            float[] input_sample = inputManager.getCurrentSamples();
            model_input = new float[input_sample.length * 2];

            // 填充输入（2条通道）
            for (int i = 0; i < input_sample.length; i++) {
                model_input[i] = input_sample[i];
                model_input[i + input_sample.length] = input_sample[i];
            }

            // === 分块推理 ===
            // 比如每1秒是16000点
            int totalChunks = (input_sample.length + chunkSize - 1) / chunkSize;  // 保证不丢最后一点

            float[] emb1 = embed1Manager.getCurrentSamples();
            float[] emb2 = embed2Manager.getCurrentSamples();
            int emblen = min(emb1.length, emb2.length);
            model_emb = new float[emblen * 2];

            // 填充 embedding
            for (int i = 0; i < emblen; i++) {
                model_emb[i] = emb1[i];
                model_emb[i + emblen] = emb2[i];
            }

            long[] inputshape = {2, input_sample.length};
            long[] embshape = {2, emblen};

            uiHandler.post(() -> modelStateListener.updateStatusText("开始推理"));
//            // === 分块推理 ===
//            int chunkSize = 16000;  // 比如每1秒是16000点
//            int totalChunks = (input_sample.length + chunkSize - 1) / chunkSize;  // 保证不丢最后一点

            long startTime = System.currentTimeMillis();
            ExecutorService executorService = Executors.newFixedThreadPool(8);  // 8线程
            List<Future<float[]>> futures = new ArrayList<>();

//            for (int i = 0; i < totalChunks; i++) {
//                final int chunkIndex = i;
//                int finalI = i;
//                futures.add(executorService.submit(() -> {
//                    int start = chunkIndex * chunkSize;
//                    int end = min(start + chunkSize, input_sample.length);
//
//                    // 提取一段输入
//                    float[] chunkInput1 = Arrays.copyOfRange(model_input, start, end);
//                    float[] chunkInput2 = Arrays.copyOfRange(model_input, start + input_sample.length, end + input_sample.length);
//
//                    // 组合成 [2, chunk_len]
//                    int chunkLen = end - start;
//                    float[] chunkInput = new float[chunkLen * 2];
//                    for (int j = 0; j < chunkLen; j++) {
//                        chunkInput[j] = chunkInput1[j];
//                        chunkInput[j + chunkLen] = chunkInput2[j];
//                    }
//
//                    long[] chunkShape = {2, chunkLen};
//
//                    // 单块推理
//                    return tsemodels[finalI % model_count].infer(chunkInput, model_emb, chunkShape, embshape);
//                }));
//            }
            for (int i = 0; i < totalChunks; i++) {
                final int chunkIndex = i;
                int finalI = i;
                futures.add(executorService.submit(() -> {
                    int start = chunkIndex * chunkSize;
                    int end = min(start + chunkSize, input_sample.length);

                    // 创建固定大小的chunk数组并初始化为0
                    float[] chunkInput1 = new float[chunkSize];
                    float[] chunkInput2 = new float[chunkSize];

                    // 复制有效数据部分
                    int copyLength = end - start;
                    System.arraycopy(model_input, start, chunkInput1, 0, copyLength);
                    System.arraycopy(model_input, start + input_sample.length, chunkInput2, 0, copyLength);

                    // 组合成 [2, chunkSize]
                    float[] chunkInput = new float[chunkSize * 2];
                    for (int j = 0; j < chunkSize; j++) {
                        chunkInput[j] = chunkInput1[j];
                        chunkInput[j + chunkSize] = chunkInput2[j];
                    }

                    long[] chunkShape = {2, chunkSize};

                    // 单块推理
                    return tsemodels[finalI % model_count].infer(chunkInput, model_emb, chunkShape, embshape);
                }));
            }
            // 收集所有块
            List<float[]> chunkOutputs = new ArrayList<>();
            try {
                for (Future<float[]> future : futures) {
                    chunkOutputs.add(future.get());  // 阻塞等待
                }
            } catch (Exception e) {
                Log.e(TAG, "推理出错", e);
                uiHandler.post(() -> modelStateListener.updateStatusText("推理出错"));
                return;
            }

            executorService.shutdown();

            // === 拼接输出 ===
            int totalLength = input_sample.length;
            model_output = new float[totalLength * 2]; // 双通道

            int offset = 0;
            for (float[] chunk : chunkOutputs) {
                int chunkLen = chunk.length / 2;
                for (int j = 0; j < chunkLen; j++) {
                    if (offset + j >= totalLength) break;  // 避免超出
                    model_output[offset + j] = chunk[j];  // 第一通道
                    model_output[offset + j + totalLength] = chunk[j + chunkLen];  // 第二通道
                }
                offset += chunkLen;
            }

            long endTime = System.currentTimeMillis();

            uiHandler.post(() -> modelStateListener.updateStatusText("完成推理，用时" + (endTime - startTime) + "ms"));
            Log.d(TAG, "完成推理,用时" + (endTime - startTime) + "ms");

            // === 填充输出Manager ===
            float[] out1 = new float[input_sample.length];
            float[] out2 = new float[input_sample.length];

            // 归一化
            float absmax1 = 0;
            for (int i = 0; i < input_sample.length; i++) {
                absmax1 = max(Math.abs(model_output[i]), absmax1);
            }
            for (int i = 0; i < input_sample.length; i++) {
                out1[i] = model_output[i] / absmax1;
            }

            float absmax2 = 0;
            for (int i = 0; i < input_sample.length; i++) {
                absmax2 = max(Math.abs(model_output[i + input_sample.length]), absmax2);
            }
            for (int i = 0; i < input_sample.length; i++) {
                out2[i] = model_output[i + input_sample.length] / absmax2;
            }

            output1Manager.setCurrentSamples(out1);
            output1Manager.setCurrentEncoding(inputManager.getCurrentEncoding());
            output1Manager.setCurrentChannels(inputManager.getCurrentChannels());
            output1Manager.setCurrentSampleRate(inputManager.getCurrentSampleRate());
            output1Manager.LoadAudioFromFloat();

            output2Manager.setCurrentSamples(out2);
            output2Manager.setCurrentEncoding(inputManager.getCurrentEncoding());
            output2Manager.setCurrentChannels(inputManager.getCurrentChannels());
            output2Manager.setCurrentSampleRate(inputManager.getCurrentSampleRate());
            output2Manager.LoadAudioFromFloat();

        }).start();
    }
//
//        public void inferMultithread() {
//            new Thread(() -> {
//                if (inputManager.getCurrentSamples() == null) {
//                    uiHandler.post(() -> modelStateListener.updateStatusText("未加载输入音频，无法推理！"));
//                    return;
//                }
//                uiHandler.post(() -> modelStateListener.updateStatusText("开始加载输入"));
//
//                final float[] input_sample = inputManager.getCurrentSamples();
//                final int totalLength = input_sample.length;
//
//                // Determine chunk size (you can adjust the number of chunks as needed)
//                final int numChunks = 4; // Using 4 chunks/threads as an example
//                final int chunkSize = (int) Math.ceil((double) totalLength / numChunks);
//
//                // Prepare embeddings (same for all chunks)
//                float[] emb1 = embed1Manager.getCurrentSamples();
//                float[] emb2 = embed2Manager.getCurrentSamples();
//                int emblen = min(emb1.length, emb2.length);
//                float[] model_emb = new float[emblen * 2];
//
//                for (int i = 0; i < emblen; i++) {
//                    model_emb[i] = emb1[i];
//                    model_emb[i + emblen] = emb2[i];
//                }
//                long[] embshape = {2, emblen};
//
//                // Prepare for parallel processing
//                ExecutorService executor = Executors.newFixedThreadPool(numChunks);
//                List<Future<ChunkResult>> futures = new ArrayList<>();
//                final long[] inputshape = {2, chunkSize}; // Each chunk will have this shape
//
//                uiHandler.post(() -> modelStateListener.updateStatusText("开始分块推理 (" + numChunks + " 线程)"));
//
//                long startTime = System.currentTimeMillis();
//
//                // Submit tasks for each chunk
//                for (int chunkIdx = 0; chunkIdx < numChunks; chunkIdx++) {
//                    final int start = chunkIdx * chunkSize;
//                    final int end = Math.min(start + chunkSize, totalLength);
//                    final int actualChunkSize = end - start;
//
//                    futures.add(executor.submit(() -> {
//                        // Prepare input chunk (duplicated for both channels)
//                        float[] chunkInput = new float[actualChunkSize * 2];
//                        for (int i = 0; i < actualChunkSize; i++) {
//                            chunkInput[i] = input_sample[start + i];
//                            chunkInput[i + actualChunkSize] = input_sample[start + i];
//                        }
//
//                        // Adjust shape for this chunk
//                        long[] chunkInputShape = {2, actualChunkSize};
//
//                        // Run inference on this chunk
//                        float[] chunkOutput = tsemodel.infer(chunkInput, model_emb, chunkInputShape, embshape);
//
//                        return new ChunkResult(start, actualChunkSize, chunkOutput);
//                    }));
//                }
//
//                // Initialize output arrays
//                float[] combinedOutput = new float[totalLength * 2];
//
//                // Wait for all threads to complete and combine results
//                try {
//                    for (Future<ChunkResult> future : futures) {
//                        ChunkResult result = future.get();
//                        System.arraycopy(result.output, 0, combinedOutput, result.start, result.length);
//                        System.arraycopy(result.output, result.length, combinedOutput,
//                                totalLength + result.start, result.length);
//                    }
//                } catch (Exception e) {
//                    uiHandler.post(() -> modelStateListener.updateStatusText("推理错误: " + e.getMessage()));
//                    Log.e(TAG, "Inference error", e);
//                    return;
//                } finally {
//                    executor.shutdown();
//                }
//
//                long endTime = System.currentTimeMillis();
//                uiHandler.post(() -> modelStateListener.updateStatusText("完成推理，用时" + (endTime-startTime) + "ms"));
//                Log.d(TAG, "完成推理,用时"+ (endTime-startTime) + "ms");
//
//                // Normalize and prepare outputs
//                processFinalOutput(combinedOutput, totalLength);
//            }).start();
//        }
//    // Helper class to store chunk results
//    private static class ChunkResult {
//        final int start;
//        final int length;
//        final float[] output;
//
//        public ChunkResult(int start, int length, float[] output) {
//            this.start = start;
//            this.length = length;
//            this.output = output;
//        }
//    }
//// Process the final combined output
//    private void processFinalOutput(float[] combinedOutput, int totalLength) {
//        float[] out1 = new float[totalLength];
//        float[] out2 = new float[totalLength];
//
//        // Normalize channel 1
//        float absmax1 = 0;
//        for (int i = 0; i < totalLength; i++) {
//            absmax1 = Math.max(Math.abs(combinedOutput[i]), absmax1);
//        }
//        for (int i = 0; i < totalLength; i++) {
//            out1[i] = combinedOutput[i] / absmax1;
//        }
//
//        // Normalize channel 2
//        float absmax2 = 0;
//        for (int i = 0; i < totalLength; i++) {
//            absmax2 = Math.max(Math.abs(combinedOutput[i + totalLength]), absmax2);
//        }
//        for (int i = 0; i < totalLength; i++) {
//            out2[i] = combinedOutput[i + totalLength] / absmax2;
//        }
//
//        // Set outputs
//        output1Manager.setCurrentSamples(out1);
//        output1Manager.setCurrentEncoding(inputManager.getCurrentEncoding());
//        output1Manager.setCurrentChannels(inputManager.getCurrentChannels());
//        output1Manager.setCurrentSampleRate(inputManager.getCurrentSampleRate());
//        output1Manager.LoadAudioFromFloat();
//
//        output2Manager.setCurrentSamples(out2);
//        output2Manager.setCurrentEncoding(inputManager.getCurrentEncoding());
//        output2Manager.setCurrentChannels(inputManager.getCurrentChannels());
//        output2Manager.setCurrentSampleRate(inputManager.getCurrentSampleRate());
//        output2Manager.LoadAudioFromFloat();
//    }

}
