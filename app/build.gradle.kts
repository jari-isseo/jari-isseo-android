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
val devServerUrl: String = localProps.getProperty("devServerUrl")?.trim() ?: "http://10.0.2.2:8080/"
// #53: 운영 서버. #57: HTTPS 적용됨 (http://는 301 → OkHttp가 POST를 GET으로 바꾸므로 반드시 https)
val releaseServerUrl = "https://jariisseo.mingu.cloud/"
// #17: debug에서 local.properties에 viewerSample=true면 사장님 뷰가 실제 매장(/s/{publicId}) 대신 샘플 뷰어를 엶
//   (손님 웹 페이지는 server#16 전이라 없음). release는 항상 실제 매장
val viewerSample: Boolean = localProps.getProperty("viewerSample")?.trim()?.toBoolean() ?: false   // properties는 값 뒤 공백을 남김
// #24: 제출용 서명. 키스토어·비밀번호는 레포에 올리지 않고 각자 local.properties에 둠
//   releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
//   없으면 release는 서명 없이 빌드됨 (설치 불가 APK)
val releaseStoreFile: String? = localProps.getProperty("releaseStoreFile")?.trim()

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

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProps.getProperty("releaseStorePassword")?.trim()
                keyAlias = localProps.getProperty("releaseKeyAlias")?.trim()
                keyPassword = localProps.getProperty("releaseKeyPassword")?.trim()
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$devServerUrl\"")
            buildConfigField("boolean", "VIEWER_SAMPLE", "$viewerSample")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"$releaseServerUrl\"")
            buildConfigField("boolean", "VIEWER_SAMPLE", "false")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    implementation(libs.zxing.core)   // #22: 손님 링크 QR
}