# Khung xem trước (preview) trong trang chính — Thiết kế

Ngày: 2026-10-14
Trạng thái: Đã duyệt

## 1. Mục tiêu

Thêm một khung preview nhỏ ngay trên màn hình chính để xem trước luồng camera,
kèm một switch bật/tắt. Mặc định **TẮT**, trạng thái được lưu bền.

## 2. Phạm vi

- Preview hiển thị frame JPEG mới nhất của camera trong chính app.
- Switch `swPreview` ("Xem trước"), mặc định tắt, nhớ trạng thái giữa các lần mở app.
- Bật preview khi streaming chưa chạy → tự khởi động streaming/camera.
- Tắt preview → chỉ dừng preview, **không** tự dừng streaming.
- Preview giữ camera sống (không bị IdlePolicy tắt), nhưng **không** tính vào "Số client".

Ngoài phạm vi: audio, ghi hình, chỉnh chất lượng riêng cho preview.

## 3. Kiến trúc (phương án A — đọc frame trực tiếp trong tiến trình)

```
StreamingService ── set ──► StreamState.broadcaster (FrameBroadcaster)
                               ▲
MainActivity (khi preview ON) ─┘  vòng nền: awaitFrame() → decode → ImageView
```

- `StreamState` giữ con trỏ `@Volatile broadcaster: FrameBroadcaster?` (service gán
  trong `onCreate`, xoá trong `onDestroy`) và `previewActive: MutableLiveData<Boolean>`.
- MainActivity dùng `awaitFrame(seq, timeout)` (đã có sẵn) để lấy frame mới nhất.
- Không đăng ký như HTTP client → `clients` không tính preview.

## 4. Giữ camera sống

- `IdlePolicy.shouldStopNow(...)` thêm tham số `previewActive`; nếu true → không dừng.
- Vòng idle của service truyền `StreamState.previewActive.value == true`.

## 5. Bật/tắt preview

- `SettingsStore`: thêm `loadPreviewEnabled(): Boolean` (mặc định false) và
  `savePreviewEnabled(Boolean)`.
- Bật: lưu pref, `previewActive=true`, nếu chưa chạy thì `StreamingService.start()`,
  bắt đầu vòng decode.
- Tắt: lưu pref, `previewActive=false`, dừng vòng decode.

## 6. Hiển thị

- Layout: `SwitchCompat` `swPreview` + `FrameLayout` nền đen chứa `ImageView`
  (`scaleType="fitCenter"`, cao cố định ~200dp), đặt phía trên nút Start.
- Decode JPEG→Bitmap ở luồng nền, throttle ~10fps, tái dùng bitmap.
- Xoay đúng hướng: API 28+ dùng `ImageDecoder` (tự áp EXIF); API 24–27 dùng
  `BitmapFactory` + `android.media.ExifInterface` đọc orientation rồi xoay.
  Hàm tính ma trận/hướng xoay tách thuần để unit-test.

## 7. Vòng đời

- Vòng decode chỉ chạy khi Activity hiển thị: `onStart` (nếu switch on) bật;
  `onStop` dừng và đặt `previewActive=false` để không hao pin khi rời app.
  Quay lại thì tự chạy lại theo trạng thái switch.

## 8. Kiểm thử

- `IdlePolicyTest`: `previewActive` true → không dừng; false → như cũ.
- `SettingsStoreTest`: lưu/đọc `previewEnabled` (mặc định false).
- `PreviewRotationTest`: hàm thuần `exifRotationDegrees` / kích thước sau xoay.
