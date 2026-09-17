# 💡 BRIEF: MakeAiSound - AI Voice Studio & Core Engine Upgrade (VieNeu-TTS v3.8.1)

**Ngày cập nhật:** 2026-09-17  
**Dự án:** MakeAiSound (`/home/skul9x/Desktop/Code/MakeAISound-main`)  
**Mục tiêu:** Nâng cấp Core AI Engine của ứng dụng Android Kotlin từ bản cập nhật mới nhất của `VieNeu-TTS-main` (v3.8.1).  
**Nền tảng:** Android 7.0+ (Kotlin, Jetpack Compose, ONNX Runtime Android)  

---

## 1. VẤN ĐỀ & MỤC TIÊU CẬP NHẬT (CORE VALUE)
Bản cập nhật `VieNeu-TTS v3.8.1` mang đến nhiều cải tiến thuật toán quan trọng và mở rộng catalog giọng đọc tiếng Việt.  
Ứng dụng Android Kotlin `MakeAiSound` hiện đang dùng cấu hình cũ (20 giọng, ngắt nghỉ cứng 350ms/180ms/40ms, chưa có cơ chế bù im lặng thông minh và khử frame đuôi thừa của codec).  

Mục tiêu đợt nâng cấp này là đồng bộ toàn bộ các cải tiến cốt lõi từ `VieNeu-TTS-main` vào app Android native.

---

## 2. CÁC ĐIỂM CẬP NHẬT CỐT LÕI TỪ VIENEU-TTS v3.8.1

### 🎙️ 1. Mở rộng Voice Catalog: 20 ➔ 25 Giọng Đọc Độc Bản
- Bổ sung **5 giọng đọc mới chất lượng cao**:
  1. **Thiền Tâm Đức** (`Nam · Bắc/Trung · Phong cách Thiền / Tĩnh tại`, Featured: #8)
  2. **Minh Quân Pro** (`Nam · Bắc · Phong cách Tin tức / MC Chuyên nghiệp`, Featured: #3)
  3. **Adam bựa** (`Nam · Nam · Phong cách Hài hước / Vlog / Đời thường`)
  4. **Mạnh Dũng** (`Nam · Bắc · Trầm ấm, dầy dặn`)
  5. **Anh Khôi** (`Nam · Nam · Trẻ trung, tự nhiên`)
- **Hệ thống Xếp hạng Giọng Tuyển Chọn (`featured` rank 1..10):**
  - Đánh dấu các giọng đạt chuẩn phòng thu cao nhất (Editor's Pick ⭐) để hiển thị ưu tiên hàng đầu trong UI Studio.

### 🧹 2. Khử Sọc Nhiễu Đuôi Codec (Issue #198 Pad Frame Fix)
- **Vấn đề upstream:** MOSS Audio Tokenizer khi mã hóa audio tham chiếu lẻ độ dài thường tự đệm frame cuối cùng với `codebook_0 == 455`. Frame này gây ra một tiếng bật/click hoặc phát âm lạ ở cuối câu khi AI hoàn tất tổng hợp.
- **Giải pháp:** Tích hợp hàm `stripEncoderPadFrame` loại bỏ frame `455` ở đuôi mã tham chiếu `codes` ngay khi nạp preset và trước khi decode.

### 🔇 3. Chuẩn Hóa Nhịp Ngắt Nghỉ Tự Nhiên (`V3_GAP_SILENCE` & `pause_pad_samples`)
- **Khoảng lặng chuẩn V3:**
  - Đoạn văn (`paragraph`): `0.70s` (700ms)
  - Hết câu (`sentence`): `0.50s` (500ms)
  - Vế câu (`clause / minor`): `0.30s` (300ms)
- **Thuật toán bù im lặng động (`pause_pad_samples`):**
  - Không chèn cứng một đoạn zeros cố định (gây ra tình trạng "đúp" im lặng khi model đã tự sinh đuôi dài).
  - Tự động đo độ dài im lặng đuôi của chunk trước + đầu chunk sau (`edgeSilence`), chỉ chèn thêm lượng zeros còn thiếu để đạt đúng mục tiêu ngắt nghỉ.

### 🛡️ 4. Bộ Chặn Nói Nhảm Theo Âm Tiết (`Syllable-Aware Babble Guard`)
- **Vấn đề:** Các câu ngắn 1–3 từ (vd: "Xin chào", "Dạ vâng", "Cảm ơn") dễ bị trượt EOS token và AI tự "nói thêm" (bịa từ nhảm).
- **Giải pháp:** Tính số âm tiết tiếng Việt/Anh (`syllableCount`) để áp trần frame chặt chẽ:
  $$\text{Cap} = \min\left(\text{FormulaLen}, 13 + 5 \times (\text{syl} - 1)\right)$$
  cho các câu từ 1 đến 4 âm tiết.

---

## 3. PHÂN CHIA HẠNG MỤC TRIỂN KHAI

### 🚀 MVP (Bắt buộc có ngay):
- [ ] Đồng bộ `voices_v3_turbo.json` (25 giọng) vào `app/src/main/assets/vieneu/`.
- [ ] Nâng cấp `VoicePreset` data class: Thêm `val featured: Int? = null`.
- [ ] Nâng cấp `VoicePresets.kt`: Parser hỗ trợ `featured`, tích hợp `stripEncoderPadFrame`.
- [ ] Nâng cấp UI Voice Selector: Hiển thị huy hiệu ⭐ Tuyển chọn và sắp xếp các giọng Featured lên đầu danh sách.
- [ ] Nâng cấp `SmartTextSegmenter.kt`: Cập nhật `V3_GAP_SILENCE` (700ms/500ms/300ms).
- [ ] Nâng cấp `VieNeuStudioSynthesizer.kt` & `PcmUtils.kt`: Tích hợp `pausePadSamples` (đo `edgeSilence` và chèn phần bù).
- [ ] Nâng cấp `VieNeuOnnxEngine.kt`: Tích hợp `syllableCount` vào `maxExpectedFrames`.

### 🎁 Phase 2 (Tối ưu nâng cao):
- [ ] Đánh giá nâng cấp context window từ 1024 lên 2048 tokens nếu cần đọc đoạn văn siêu dài.
- [ ] Unit test và stress test đo đạc lại latency/RTF trên máy thật Android.

---

## 4. ĐÁNH GIÁ KỸ THUẬT & KHẢ THI
- **Độ phức tạp:** Trung bình (toàn bộ logic toán học và xử lý mảng đã có bản mẫu chuẩn xác trong `VieNeu-TTS-main`).
- **Tương thích:** Hoàn toàn tương thích 100% với kiến trúc Android Jetpack Compose và ONNX Runtime hiện hành của app.
