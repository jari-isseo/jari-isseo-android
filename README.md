# jari-isseo-android

자리있어의 사장님용 Android 앱 레포입니다.
매장을 카메라로 스캔해 테이블 배치를 만들고, 테이블 상태를 손님 웹에 실시간으로 반영합니다.

- 서버·손님 웹: [jari-isseo-server](https://github.com/jari-isseo/jari-isseo-server)

> 현재는 매장 스캔 POC(StoreScanPoc)를 이관한 상태입니다.
> ARCore로 카메라 위치·바닥 평면을 추적하고, YOLOv8로 테이블·의자를 탐지해 미니맵에 표시합니다.

## 요구 사항

- **ARCore 지원 기기 필수** ([지원 기기 목록](https://developers.google.com/ar/devices)) — 에뮬레이터에서는 실행되지 않습니다.
- Android Studio (JDK는 Studio 내장 JBR 사용)
- minSdk 24 / targetSdk 35

## 빌드 방법

1. 레포 clone 후 Android Studio에서 루트 폴더 열기
2. Gradle Sync 완료 대기
3. ARCore 지원 기기 연결 후 `app` 실행 (Run ▶)

CLI로 빌드할 때는 `JAVA_HOME`을 Android Studio의 `jbr` 폴더로 지정합니다.

    ./gradlew assembleDebug

## YOLO 모델

- 위치: `app/src/main/assets/yolov8n.tflite`
- 메모리 매핑(mmap)으로 읽기 때문에 `build.gradle.kts`에서 `.tflite`를 압축하지 않도록 설정되어 있습니다.

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어·UI | Kotlin, Jetpack Compose |
| AR | ARCore |
| 객체 탐지 | LiteRT(TFLite) + YOLOv8n |
| 패키지 | `com.jariisseo.android` |
