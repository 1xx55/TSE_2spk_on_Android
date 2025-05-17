plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.a2024dachuang"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.a2024dachuang"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    aaptOptions {
        noCompress.add("pt")  // 禁止压缩 .pt 文件
        noCompress.add("onnx") // 禁止压缩 .onnx 文件
        noCompress.add("bin")  // 禁止压缩 .bin 文件
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 添加 PyTorch 依赖
    //implementation(libs.pytorch.android)
    //implementation(libs.pytorch.torchvision)

    // ONNXRuntime 依赖
    implementation("com.microsoft.onnxruntime:onnxruntime-android:latest.release")
    implementation("com.microsoft.onnxruntime:onnxruntime-extensions-android:latest.release")

}