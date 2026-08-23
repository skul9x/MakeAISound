# 🎙️ MakeAiSound - AI Voice Studio & Offline Neural TTS

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%E2%80%9336)-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3%20BOM-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-1.20.0%20(XNNPACK)-005CED.svg)](https://onnxruntime.ai)
[![Audio](https://img.shields.io/badge/Audio-48kHz%2016--bit%20WAV-orange.svg)]()
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**MakeAiSound** là ứng dụng Android Studio Text-to-Speech (TTS) xử lý AI hoàn toàn **Offline On-Device** sử dụng mô hình nơ-ron **VieNeu-TTS v3 Turbo (FP32 Precision)**. Ứng dụng chuyển đổi văn bản tiếng Việt sang tệp âm thanh lossless WAV 48kHz chất lượng phòng thu với độ trễ cực thấp, hỗ trợ biểu cảm cảm xúc tự nhiên, lưu trữ trực tiếp vào bộ nhớ máy và tích hợp trình phát Audio Studio chuyên nghiệp.

---

## ✨ Điểm nổi bật & Tính năng chính

### 🧠 1. Neural TTS Engine chuẩn Studio (FP32 Offline)
* **100% Offline & Private:** Hoạt động hoàn toàn trên thiết bị, không gửi dữ liệu ra ngoài, không cần Internet hay API Key.
* **Độ chính xác FP32 (Full Precision 32-bit Float):** Sử dụng XNNPACK multi-threading mang lại âm thanh trong trẻo, tự nhiên, không bị hiện tượng vỡ tiếng hoặc rè do nén lượng tử hóa (quantization artifacts).
* **Bộ tách âm Rust NDK Sea-G2P:** Tích hợp thư viện C++/Rust native phonemizer cho tốc độ tách âm siêu nhanh.

### 🎭 2. Đa dạng giọng đọc & Thẻ cảm xúc Inline
* **8 Voice Presets:** Tuyển tập 8 giọng đọc biểu cảm cao (Bắc, Trung, Nam, nam, nữ, kể chuyện, đọc tin tức).
* **Inline Emotion Tags:** Hỗ trợ chèn trực tiếp các thẻ cảm xúc vào văn bản:
  * `[chuckle]` hoặc `[cười]` $\rightarrow$ Cười nhẹ tự nhiên.
  * `[sigh]` hoặc `[thở dài]` $\rightarrow$ Thở dài, trầm lắng.
  * `[clear throat]` hoặc `[hắng giọng]` $\rightarrow$ Hắng giọng trước khi nói.
* **Bảo toàn từ tiếng Anh `<en>`:** Đọc chuẩn xác các thuật ngữ tiếng Anh xen kẽ trong văn bản tiếng Việt.

### 🎚️ 3. Audio Studio Player & Waveform Visualizer
* **Dynamic Waveform Visualizer:** Hiệu ứng sóng âm 40 cột chuyển động mượt mà theo biên độ thời gian thực.
* **Precision Scrubber:** Thanh tua thời gian chính xác, hỗ trợ Play / Pause / Replay.
* **50ms (~20fps) Polling:** Tối ưu hóa chu kỳ cập nhật giúp thanh tua mượt mà nhưng tiết kiệm tối đa CPU và pin.

### 💾 4. Lossless WAV Exporter & MediaStore
* **Xuất file chuẩn 48,000 Hz, 16-bit Mono Lossless RIFF WAV.**
* **Tự động lưu vào thư viện:** Ghi trực tiếp vào thư mục hệ thống `Music/MakeAiSound` bằng Android MediaStore API.
* **Chia sẻ tức thì:** Tích hợp Android Share Sheet để gửi file âm thanh qua Zalo, Messenger, Drive, Telegram,...

### 📊 5. Real-Time Telemetry & Memory Profiling
* **RTF (Real-Time Factor) Monitor:** Đo lường chính xác tốc độ sinh âm thanh so với thời lượng thực tế.
* **Stage Latency Breakdowns:** Chi tiết thời gian từng giai đoạn: G2P $\rightarrow$ Prefill $\rightarrow$ Autoregressive Decode $\rightarrow$ Neural Codec.
* **Circular In-App Diagnostics Log Viewer:** Xem log trực tiếp ngay trong giao diện Bottom Sheet.

### 🎨 6. Dark OLED UI & Material You
* Giao diện **OLED Dark Tech** hiện đại, tiết kiệm pin trên màn hình OLED / AMOLED 120Hz.
* Tích hợp **Fluid Scrollbar Indicators** trên tất cả các màn hình cuộn.
* Hỗ trợ **Adaptive & Themed Vector Icon** theo chuẩn Material You trên Android 13+.

---

## 🏗️ Kiến trúc dự án (Architecture)

```
MakeAiSound/
├── app/
│   ├── src/main/
│   │   ├── assets/vieneu/          # Model AI ONNX FP32, Vocoder, Vocab & G2P Binary
│   │   │   ├── backbone/           # Prefill, Decode Step, Acoustic Head & Weights
│   │   │   ├── codec/              # Neural Audio Tokenizer Decoder
│   │   │   ├── denoiser/           # Audio Post-Processing Denoiser
│   │   │   └── sea_g2p.bin         # Phonemizer Dictionary
│   │   ├── jniLibs/                # Native libraries sea-g2p (arm64-v8a, v7a, x86_64, x86)
│   │   └── java/skul9x/example/makesound/
│   │       ├── engine/             # VieNeu AI Engine, Tokenizer, SeaG2P, CacheTensorPool
│   │       ├── player/             # AudioPlayerManager, WaveformSampler, MediaPlayer Adapter
│   │       ├── storage/            # WavWriter (Lossless RIFF WAV), StorageManager
│   │       ├── telemetry/          # TtsMetricsLogger, Real-time RTF Profiler
│   │       ├── ui/                 # Jetpack Compose Screens, ViewModel, M3 Theme & Components
│   │       └── MainActivity.kt     # App Entry Point & ViewModel Binding
│   └── build.gradle.kts
├── docs/                           # Tài liệu kỹ thuật, kiến trúc & báo cáo tối ưu hóa
└── plans/                          # Kế hoạch chi tiết 8 Phase triển khai
```

---

## ⚡ Các kỹ thuật tối ưu hóa chuyên sâu

1. **Zero-Allocation CacheTensor Pool (`Phase 07`):**
   * Tái sử dụng vùng nhớ Direct Float ByteBuffer cho Key/Value Cache trong vòng lặp autoregressive decode.
   * Loại bỏ hiện tượng GC (Garbage Collection) spike giật lag giữa các bước decode.
2. **Streaming Chunked WAV Reader (`Phase 08`):**
   * Đọc và giải mã dữ liệu WAV từ file theo từng luồng chunk 8KB thay vì đọc toàn bộ file vào byte array, tránh nhân đôi bộ nhớ heap.
3. **Quản lý vòng đời bộ nhớ Native (`Phase 06`):**
   * Toàn bộ `OrtSession`, `OrtEnvironment` và `OnnxTensor` được quản lý chặt chẽ với cơ chế `AutoCloseable`, giải phóng 100% native memory khi ViewModel bị hủy.

---

## 🚀 Cài đặt & Chạy ứng dụng

### Yêu cầu hệ thống:
* **Android Studio:** Ladybug / Koala hoặc mới hơn.
* **JDK:** Java 17 hoặc 21.
* **Android SDK:** Min SDK 24 (Android 7.0), Target SDK 35/36 (Android 15+).
* **Thiết bị:** Android 64-bit (`arm64-v8a`), khuyến nghị chip Snapdragon 7 Gen / 8 Gen series hoặc MediaTek Dimensity để đạt RTF tốt nhất.

### Các bước thực hiện:
```bash
# 1. Clone repository
git clone https://github.com/skul9x/MakeAISound.git
cd MakeAISound

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Cài đặt trực tiếp lên điện thoại qua ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Bản quyền & Cảm ơn
* Phát triển bởi **skul9x**.
* Sử dụng mô hình nơ-ron mã nguồn mở **VieNeu-TTS** và runtime **Microsoft ONNX Runtime**.
