# Ghi Điểm

Ứng dụng ghi điểm cho bóng đá, cầu lông, bóng chuyền, bóng bàn và tennis. Mỗi môn tính điểm theo luật riêng, có
đồng hồ bấm giờ từ lúc trận bắt đầu, và mỗi điểm / bàn thắng được lưu kèm thời gian.

- Đánh đơn / đánh đôi (app theo dõi ai giao, ai đỡ), hội ý, thẻ phạt, thay người, phản lưới, đá luân lưu
- Đọc tỉ số bằng giọng nói, âm báo hết giờ, ghi điểm bằng phím âm lượng, bảng điểm toàn màn hình
- Thống kê trận (biểu đồ diễn biến), thống kê người chơi và đối đầu
- Giải đấu vòng tròn (bảng xếp hạng) hoặc loại trực tiếp (nhánh đấu)
- Chia sẻ kết quả bằng ảnh, sao lưu ra file, đồng bộ Google Drive, widget màn hình chính

## Build

Nhấn **Ctrl+Shift+B** trong VS Code, rồi chép file APK trong `build\apk\` sang điện thoại và mở để cài.

## Google Drive sync (one-time setup, optional)

Google only lets a registered app use Drive. Reuse the Google Cloud project of Ghi Chú / Sổ Nợ:

1. <https://console.cloud.google.com/> → that project (**Google Drive API** enabled, **External** OAuth consent
   screen with your account as a test user).
2. **APIs & Services → Credentials → Create credentials → OAuth client ID → Android**: package name
   `com.samuelcruz.ghidiem`, SHA-1 of the debug key (same as Ghi Chú's, see its README).
3. In the app: Cài đặt → Google Drive → **Kết nối Google Drive**.

The app only gets its own hidden Drive folder (`drive.appdata`), not your files.

Chi tiết cho người phát triển: xem `CLAUDE.md`.
