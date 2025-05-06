package com.example.a2024dachuang;

import android.content.Context;
import android.util.Log;

import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession.SessionOptions;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class ONNXModelInference {
    private OrtSession session;
    private OrtEnvironment env;
    private static final String TAG = "ONNXModelInference";

    public ONNXModelInference(Context context, String modelName) throws Exception {
        // 初始化ONNX Runtime环境
        env = OrtEnvironment.getEnvironment();

        // 从assets加载模型文件
        String filePath = FileUtils.assetFilePath(context, modelName);
        if (filePath == null) {
            throw new Exception("Failed to copy model file from assets");
        }

        // 创建会话选项
        SessionOptions options = new SessionOptions();
        // 可以根据需要设置选项，例如：
        // options.setOptimizationLevel(OptimizationLevel.ALL_OPT);
        // options.setExecutionMode(ExecutionMode.PARALLEL);

        // 加载ONNX模型
        session = env.createSession(filePath, options);
        Log.d(TAG, "ONNX model loaded successfully from: " + filePath);

    }


    /**
     * 执行模型推理
     *
     * @param input1 第一个输入张量数据，形状为 [2, 48000]
     * @param input2 第二个输入张量数据，形状为 [2, emb_len]
     * @param inputShape1 第一个输入的形状
     * @param inputShape2 第二个输入的形状
     * @return 输出结果，包含 4 个张量：
     *         - 前三个张量：形状为 [2, 48000]
     *         - 最后一个张量：形状为 [2, 251]
     */
    public float[] infer(float[] input1, float[] input2, long[] inputShape1, long[] inputShape2) throws Exception {
        // 创建输入张量
        OnnxTensor tensorInput1 = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(input1),
                inputShape1
        );
        OnnxTensor tensorInput2 = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(input2),
                inputShape2
        );
        // 准备输入Map，需要根据模型实际的输入名称调整
        // 注意：这里的输入名称需要与ONNX模型中的输入名称匹配
        // 可以通过session.getInputInfo()获取模型输入名称
        Map<String, OnnxTensor> inputs;
        if (session.getInputInfo().size() == 2) {
            // 如果模型有两个输入，按顺序获取输入名称
            String[] inputNames = session.getInputInfo().keySet().toArray(new String[0]);
            inputs = Map.of(
                    inputNames[0], tensorInput1,
                    inputNames[1], tensorInput2
            );
        } else {
            // 默认情况下假设输入名称为"input1"和"input2"
            inputs = Map.of(
                    "audio", tensorInput1,
                    "embeddings", tensorInput2
            );
        }

        Log.d(TAG, "Input1 shape: " + Arrays.toString(inputShape1) + ", length: " + input1.length);
        Log.d(TAG, "Input2 shape: " + Arrays.toString(inputShape2) + ", length: " + input2.length);

        // 执行推理
        OrtSession.Result output = session.run(inputs);

        // 获取输出结果
        // 注意：这里的输出名称需要与ONNX模型中的输出名称匹配
        // 可以通过session.getOutputInfo()获取模型输出名称
        float[][] result = null;
        int totalLength = 0;
        if (output.size() > 0) {
            // 获取第一个输出
            OnnxTensor outputTensor = (OnnxTensor) output.get(0);
            result = (float[][]) outputTensor.getValue();

            for (float[] subArray : result) {
                totalLength += subArray.length;
            }

            Log.d(TAG, "Output shape: " + Arrays.toString(outputTensor.getInfo().getShape()) +
                    ", length: " + result.length);
        }

        // 关闭输入张量以释放资源
        tensorInput1.close();
        tensorInput2.close();

        // 2. 创建目标一维数组
        float[] arr = new float[totalLength];

        // 3. 按顺序填充数据
        int currentPos = 0;
        for (float[] subArray : result) {
            System.arraycopy(subArray, 0, arr, currentPos, subArray.length);
            currentPos += subArray.length;
        }

        return arr;
    }

    public void close() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing session", e);
            }
        }
    }
}