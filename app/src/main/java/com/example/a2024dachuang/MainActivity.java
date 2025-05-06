package com.example.a2024dachuang;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private TextView inputAudioStatusText;
    private Button playAudioBeforeButton;
    private TSEModel_2spk model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.Layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //选文件Activity注册
       ActivityResultLauncher<String>
               InputAudioPickerLauncher = registerForActivityResult(
                        new ActivityResultContracts.GetContent(),
                        uri -> {if (uri != null) {
                            model.inputManager.loadAudio(uri);
                        }}) ,
               InputEmbed1PickerLauncher = registerForActivityResult(
                        new ActivityResultContracts.GetContent(),
                        uri -> {if (uri != null) {
                            model.embed1Manager.loadAudio(uri);
                        }}) ,
               InputEmbed2PickerLauncher = registerForActivityResult(
                        new ActivityResultContracts.GetContent(),
                        uri -> {if (uri != null) {
                           model.embed2Manager.loadAudio(uri);
                        }}) ;

        Log.d("LoadingModel", "[yhyu]: Loading Model");

        try {
            //ModelInference modelInference = new ModelInference(this, "avg_bst_jit.pt");

            model = new TSEModel_2spk(this, "avg_bst_verysmall3.onnx", new AudioManager.ModelStateListener() {
                @Override
                public void updateStatusText(String text) {
                    TextView view = findViewById(R.id.modelInfoText);
                    view.setText("[模型]:"+text);
                }
            });
            // 绑定状态显示框
            model.bindManagerStatusText("输入音频",model.inputManager,findViewById(R.id.inputAudioStatusText));
            model.bindManagerStatusText("输入嵌入1",model.embed1Manager,findViewById(R.id.inputEnbed1StatusText));
            model.bindManagerStatusText("输入嵌入2",model.embed2Manager,findViewById(R.id.inputEnbed2StatusText));
            model.bindManagerStatusText("输出音频1",model.output1Manager,findViewById(R.id.outputAudioStatusText1));
            model.bindManagerStatusText("输出音频2",model.output2Manager,findViewById(R.id.outputAudioStatusText2));

            // 绑定读入
            findViewById(R.id.chooseInputAudioButton).setOnClickListener(v -> InputAudioPickerLauncher.launch("audio/*"));
            findViewById(R.id.chooseInputEmbed1Button).setOnClickListener(v -> InputEmbed1PickerLauncher.launch("audio/*"));
            findViewById(R.id.chooseInputEmbed2Button).setOnClickListener(v -> InputEmbed2PickerLauncher.launch("audio/*"));

            // 绑定播放按钮
            findViewById(R.id.playAudioBeforeButton).setOnClickListener(v -> model.inputManager.togglePlayback());
            findViewById(R.id.playEnbed1Button).setOnClickListener(v -> model.embed1Manager.togglePlayback());
            findViewById(R.id.playEnbed2Button).setOnClickListener(v -> model.embed2Manager.togglePlayback());
            findViewById(R.id.playAudioAfterButton1).setOnClickListener(v -> model.output1Manager.togglePlayback());
            findViewById(R.id.playAudioAfterButton2).setOnClickListener(v -> model.output2Manager.togglePlayback());

            //测试
            //findViewById(R.id.startInferButton).setOnClickListener(v -> model.testWork());
            //正式1
            //findViewById(R.id.startInferButton).setOnClickListener(v -> model.infer());
            //正式2
            findViewById(R.id.startInferButton).setOnClickListener(v -> model.inferMulti2());

            Log.d("LoadingModel", "[yhyu]: Load Model Successful");
        } catch (Exception e) {
            Log.d("LoadingModel", "[yhyu]: Load Model failed");
            throw new RuntimeException(e);
        }

        // 权限检查：能读写文件!
        if (!StoragePermissionHelper.hasStoragePermission(this)) {
            StoragePermissionHelper.requestStoragePermission(this);
        }
    }

//    private void loadAudioViaButtonFromFileToManager(AudioManager manager) {
//        .launch("audio/*");
//    };
}

