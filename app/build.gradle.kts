import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// #11: debug 서버 주소는 각자 local.properties의 devServerUrl (실기기는 PC의 LAN IP, 예: http://192.168.0.10:8080/)
//   없으면 에뮬레이터에서 PC를 가리키는 10.0.2.2
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val devServerUrl: String = localProps.getProperty("devServerUrl") ?: "http://10.0.2.2:8080/"
// TODO(server#26): 도메인·HTTPS 연결 후 실제 주소로
val releaseServerUrl = "https://jari-isseo.invalid/"

android {
    namespace = "com.jariisseo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jariisseo.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$devServerUrl\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"$releaseServerUrl\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // #11: BASE_URL
    }
    // 모델 파일은 압축하지 않아야 메모리에 바로 매핑(mmap)할 수 있음
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.arcore)
    implementation(libs.litert)
    implementation(libs.androidx.navigation.compose)   // #10: 화면 이동

    // #11: 서버 API
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}