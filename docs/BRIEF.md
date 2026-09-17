# 💡 BRIEF: Tính Năng Voice Sample Sound Preview (Nghe Thử Giọng AI)

**Ngày tạo:** 2026-09-17  
**Dự án:** MakeAiSound (`/home/skul9x/Desktop/Code/MakeAISound-main`)  
**Nền tảng:** Android Studio (Native Kotlin 2.0, Jetpack Compose, Material Design 3)  
**Tác giả:** skul9x & Antigravity  

---

## 1. VẤN ĐỀ CẦN GIẢI QUYẾT
- **Khó khăn của người dùng:** Ứng dụng MakeAiSound hiện sở hữu thư viện phong phú gồm **25 giọng đọc AI** (thuộc VieNeu-TTS v3.8.1) phân hóa đa dạng theo 3 miền (Bắc, Trung, Nam), giới tính (Nam, Nữ) và phong cách (Tự nhiên, Kể chuyện, Tin tức, Podcast, Thiền). Tuy nhiên, trong giao diện chọn giọng, người dùng chỉ nhìn thấy tên và mô tả bằng văn bản.
- **Rào cản trải nghiệm:** Người dùng phải nhập văn bản và bấm "Tạo âm thanh" (chờ inference ONNX) thì mới biết giọng đó thực tế phát âm ra sao. Nếu không ưng ý, họ phải lặp lại chu kỳ chọn - sinh audio nhiều lần, gây tốn thời gian và lãng phí tài nguyên thiết bị.

---

## 2. GIẢI PHÁP ĐỀ XUẤT
- Tích hợp tính năng **Voice Sample Sound Preview** độc lập và tiện lợi:
  1. **Nút Nghe Thử Trực Tiếp (Quick Preview Button):** Đặt nút Play/Stop tinh tế trên từng thẻ giọng trong `VoicePickerBottomSheet`. Chạm vào là nghe ngay một câu đọc mẫu chuẩn đặc trưng của giọng đó (dài ~2–4 giây).
  2. **Tốc độ phản hồi 0ms (Zero Latency):** Đóng gói sẵn 25 file âm thanh mẫu chuẩn phòng thu vào thư mục Assets (`assets/vieneu/samples/`), nén tối ưu (tổng dung lượng cực nhẹ ~1.5MB). Khi bấm nghe thử, âm thanh phát ngay lập tức mà không cần chờ ONNX runtime inference, không gây nóng máy hay tốn pin.
  3. **Bộ phát mẫu độc lập (`VoiceSamplePlayer`):** Hoạt động tách biệt với trình phát chính của Studio (`AudioPlayerManager`), tự động dừng khi người dùng chuyển sang nghe thử giọng khác, khi đóng Bottom Sheet hoặc khi bấm nút "Tạo âm thanh".

---

## 3. ĐỐI TƯỢNG SỬ DỤNG
- **Người sáng tạo nội dung (Content Creators, YouTubers, Podcasters, TikTokers):** Cần lướt nhanh qua các giọng để tìm chất giọng phù hợp nhất với kịch bản (giọng kể chuyện trầm ấm, giọng tin tức đĩnh đạc, hay giọng hài hước vui nhộn).
- **Người dùng phổ thông:** Muốn nghe thử nhanh các giọng địa phương yêu thích (giọng Nam, Bắc, Trung) trước khi tạo audio.

---

## 4. NGHIÊN CỨU & SO SÁNH GIẢI PHÁP

### So sánh các hướng tiếp cận:
| Tiêu chí | Đóng gói sẵn Audio Mẫu (Được chọn ⭐) | Sinh động bằng ONNX |
| :--- | :--- | :--- |
| **Độ trễ phát (Latency)** | **0ms (Tức thì)** | 1.5s – 3.5s (Phải đợi AI tính toán) |
| **Trải nghiệm UX** | Rất mượt, bấm liên tục giữa các giọng không trễ | Dễ đơ/lag nếu bấm nghe thử dồn dập |
| **Dung lượng APK** | Tăng thêm ~1.5MB (rất nhỏ so với model 200MB) | Không tăng dung lượng |
| **Xung đột tài nguyên** | Hoàn toàn không chiếm CPU/GPU của ONNX | Tranh chấp luồng sinh audio với Studio chính |

*Kết luận:* Phương án đóng gói sẵn 25 file audio mẫu là lựa chọn tối ưu tuyệt đối cho trải nghiệm người dùng trên thiết bị di động.

---

## 5. DANH SÁCH TÍNH NĂNG

### 🚀 MVP (Triển khai ngay):
1. **Kho tài nguyên 25 File Audio Mẫu (`assets/vieneu/samples/`):**
   - Đầy đủ 25 file audio mẫu tương ứng với 25 presets trong `voices_v3_turbo.json`.
   - Chuẩn định dạng: WAV/OGG Lossless/High Quality, âm lượng đồng đều, nội dung thể hiện rõ ngữ điệu và phong cách từng giọng.
2. **Trình phát mẫu độc lập (`VoiceSamplePlayer`):**
   - Quản lý trạng thái phát: `currentVoiceName`, `isPlaying`, `error`.
   - Phát trực tiếp từ Asset File Descriptor (`AssetManager.openFd()`) hoặc cache file.
   - Cơ chế tự dừng an toàn khi:
     - Bấm nghe thử một giọng khác (Single-active playback).
     - Bấm lại chính giọng đang phát (Toggle play/stop).
     - Người dùng đóng Bottom Sheet.
     - Ứng dụng chuyển vào background / lifecycle `onPause` / `onDestroy`.
3. **Cập nhật Giao diện `VoicePickerBottomSheet`:**
   - Thêm nút Action icon Play/Stop tinh gọn trên mỗi `VoicePickerItem`.
   - Hiệu ứng visual trạng thái:
     - Đang phát: Icon Stop/Pause đổi màu thương hiệu `ElectricCyan` + micro-animation hiệu ứng sóng âm/pulse.
     - Bình thường: Icon Play tròn nhỏ thanh lịch.
   - Thao tác click trên thẻ giọng: Click vào nút Play thì chỉ nghe thử, click vào thân thẻ thì chọn giọng (`onVoiceSelected`).

### 🎁 Phase 2 (Mở rộng sau):
- Thêm sóng âm mini visualizer (3–4 vạch nhảy theo âm lượng thực) ngay trên nút preview khi đang phát.
- Cho phép người dùng gõ câu mẫu tùy chỉnh ngắn để nghe thử bằng ONNX engine on-demand nếu muốn.

---

## 6. ƯỚC TÍNH SƠ BỘ & ĐÁNH GIÁ KỸ THUẬT
- **Độ phức tạp:** Thấp - Trung bình (Không can thiệp vào ONNX inference core, chỉ phát triển lớp Player nhẹ và nâng cấp giao diện Compose).
- **Rủi ro kỹ thuật:** Rất thấp. Cần chú ý giải phóng `MediaPlayer` (`release()`) đúng vòng đời để không bị rò rỉ bộ nhớ native (Memory Leak).
- **Dung lượng:** Thêm khoảng ~1.5MB tài nguyên audio vào file APK (không đáng kể).

---

## 7. BƯỚC TIẾP THEO
→ Chuyển sang workflow `/plan` để thiết kế chi tiết kiến trúc (`VoiceSamplePlayer`, asset mapping, UI updates và bộ kiểm thử tự động).
