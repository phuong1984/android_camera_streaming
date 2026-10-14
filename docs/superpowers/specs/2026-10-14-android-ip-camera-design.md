# Android IP Camera Streaming — Thiết kế

Ngày: 2026-10-14
Trạng thái: Đã duyệt (chờ triển khai)

## 1. Mục tiêu

Biến một điện thoại Android cũ thành một **IP camera streaming video**, dùng như
một nguồn camera thay thế/bổ sung cho ESP32-CAM trong robot hiện có. Trang HTML
điều khiển robot hiện tại hiển thị video qua thẻ `<img>` với URL:

```js
const CAM_IP = "192.168.1.49";
const CAMERA_URL = `http://${CAM_IP}:81/stream`;
```

App Android phải **giả lập đúng giao diện HTTP MJPEG của ESP32-CAM** để trang HTML
dùng lại được, chỉ cần đổi port (xem mục 4).

Chỉ **video**, không audio.

## 2. Phạm vi

**Trong phạm vi**

- HTTP server MJPEG, snapshot JPEG, điều khiển từ xa, trạng thái.
- Camera2 + `ImageReader(JPEG)` (hardware JPEG).
- Điều khiển: bật/tắt flash (torch), chuyển camera trước/sau, cân bằng trắng, môi
  trường (scene), bộ lọc màu (effect), chế độ nhìn đêm (low-light), độ sáng/tương
  phản, độ phân giải/FPS/chất lượng JPEG (manual + auto).
- Chạy nền bằng Foreground Service, tắt màn hình vẫn stream.
- Tự dừng camera khi không có client xem (tiết kiệm pin).
- Cấu hình lưu trên máy; hiển thị IP/MAC; hỗ trợ đặt IP tĩnh (thực nghiệm).

**Ngoài phạm vi**

- Audio (đã bỏ).
- Ghi file video, RTSP/HLS, xem qua internet ngoài LAN.
- Night vision hồng ngoại thật (không thể bằng phần mềm).

## 3. Nền tảng & tương thích

- `minSdk = 24` (Android 7.0 Nougat) — phủ đa số máy cũ tại Việt Nam.
- `targetSdk = 34`, `compileSdk = 34`.
- Kotlin, Gradle Kotlin DSL, Android Gradle Plugin 8.x, JDK 17+ (máy build có JDK 21).
- Đa hãng (Samsung, Oppo, Xiaomi, Vivo, Realme…): dò khả năng qua
  `CameraCharacteristics`, ẩn điều khiển không hỗ trợ thay vì crash.

## 4. Endpoint (giả lập ESP32-CAM)

Server bind `0.0.0.0` trên port **8081** (mặc định, chỉnh được trong Settings).

| Method | Path | Mô tả |
|---|---|---|
| GET | `/stream` | MJPEG `multipart/x-mixed-replace; boundary=<boundary>` |
| GET | `/capture` | Một ảnh JPEG (`image/jpeg`) |
| GET | `/control?var=<tên>&val=<giá trị>` | Đổi thiết lập, trả 200 |
| GET | `/status` | JSON: khả năng hỗ trợ + giá trị hiện tại |

HTML cần sửa 1 dòng:

```js
const CAMERA_URL = `http://${CAM_IP}:8081/stream`;
```

### Lý do port 8081 (không phải 81)

Trên Linux/Android, app không root **không bind được port < 1024** (81 nằm trong
dải privileged). Do đó dùng 8081 để chạy trên mọi máy không cần root.

### Biến `/control` (tên tham số giống ESP32-CAM khi có thể)

`framesize`, `quality`, `fps`, `flash` (0/1), `camera` (0=rear,1=front),
`wb`, `env`, `effect`, `night` (0/1), `brightness`, `contrast`, `hmirror`, `vflip`.

## 5. Kiến trúc

```
┌─────────────────────────────────────────────────────────┐
│                    Android App                            │
│                                                           │
│  UI (Main + Settings) ──┐                                 │
│                         ▼                                 │
│  CameraControlConfig  CameraController (Camera2)          │
│         │                   │                             │
│         │                   ▼                             │
│         │            ImageReader(JPEG)                    │
│         │                   │ frame bytes                 │
│         │                   ▼                             │
│         │            FrameBroadcaster ──────► clients     │
│         │                   ▲                             │
│  HttpServer (NanoHTTPD) ────┘  /stream /capture           │
│    /control /status ─────────► CameraController           │
│                                                           │
│  IdleManager: 0 client + grace 15s → stop camera          │
│  Foreground Service + PARTIAL_WAKE_LOCK + BootReceiver    │
│  SettingsStore (DataStore/SharedPreferences)              │
└─────────────────────────────────────────────────────────┘
```

## 6. Pipeline camera

- Chọn `cameraId` theo `LENS_FACING` mong muốn.
- Đọc `StreamConfigurationMap.getOutputSizes(ImageFormat.JPEG)` để chọn size hợp
  lệ gần nhất với preset.
- Output duy nhất tới `ImageReader(JPEG, W, H)`; repeating request.
- Mỗi frame: `acquireNextImage` → copy bytes → cập nhật "latest frame" trong
  `FrameBroadcaster`.
- **Một pipeline dùng chung**, fan-out cho nhiều client (không chụp lại theo
  client). Throttle theo FPS cấu hình.
- `/capture` trả frame mới nhất (hoặc kích hoạt chụp nếu chưa có).

## 7. Điều khiển camera (map Camera2)

| UI / var | Camera2 |
|---|---|
| flash (torch) | `CameraManager.setTorchMode` + `FLASH_MODE` |
| camera trước/sau | đổi `cameraId` theo `LENS_FACING` |
| `wb` | `CONTROL_AWB_MODE` (Auto/Incandescent/Fluorescent/Daylight/Cloudy…) |
| `env` | `CONTROL_SCENE_MODE` (Auto/Night/Sports/Portrait…) |
| `effect` | `CONTROL_EFFECT_MODE` (None/Mono/Negative/Sepia/Posterize…) |
| `night` | `SCENE_MODE_NIGHT` + `AE_EXPOSURE_COMPENSATION` (low-light) |
| `brightness` | `AE_EXPOSURE_COMPENSATION` / hậu kỳ |
| `contrast` | hậu kỳ ảnh JPEG (Camera2 không có control trực tiếp) |
| `framesize`/`quality`/`fps` | JPEG size, `JPEG_QUALITY`, `AE_TARGET_FPS_RANGE` |

Dò capability trước khi áp dụng; key không hỗ trợ → bỏ qua (ẩn nút, `/status` báo).

## 8. Vòng đời & tiết kiệm pin

- **IdleManager**: camera chỉ chạy khi có ≥1 kết nối `/stream` (hoặc `/capture`).
  Khi còn 0 client, chờ **grace 15s** rồi dừng camera session, giải phóng sensor.
- Có client mới → start lại camera (chậm ~1–2s cho frame đầu).
- Tuỳ chọn "giữ ấm" (không dừng hẳn) trong Settings.
- Foreground Service `foregroundServiceType="camera"` (API 29+ khai báo; API 34 bắt
  buộc) + notification thường trực + `PARTIAL_WAKE_LOCK`.
- Android 9+ bắt buộc foreground service để truy cập camera khi ở background.
- Nút xin **miễn tối ưu pin** và mở trang **autostart** của hãng (OEM).
- Tuỳ chọn autostart sau reboot (`RECEIVE_BOOT_COMPLETED`).

## 9. Độ phân giải / FPS / chất lượng

- Manual presets: độ phân giải `320×240 / 640×480 / 800×600 / 1280×720`,
  FPS `5/10/15/30`, JPEG quality `1–100`.
- Mặc định: **640×480 @ 15fps** (nhẹ, hợp máy cũ, gần ESP32-CAM VGA).
- Auto: hạ bước khi phát hiện nghẽn (socket write chậm / frame bị bỏ), hồi lại khi
  ổn định. Đổi được từ `/control`.

## 10. Cấu hình & mạng

- `SettingsStore` lưu: port, resolution, fps, quality, autostart, keep-warm,
  static-IP (thực nghiệm), các giá trị điều khiển.
- Hiển thị IP hiện tại + MAC.
- Đặt IP tĩnh: **thực nghiệm**, chỉ Android 7–9 (`WifiManager`); Android 10+ không
  cho app đổi IP tĩnh → hướng dẫn **DHCP reservation theo MAC trên router**.
- Server bind `0.0.0.0` nên chạy độc lập với IP.

## 11. Quyền

`CAMERA`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`,
`POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`CHANGE_WIFI_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## 12. UI

- **Main**: trạng thái (IP:port, running/stopped), Start/Stop, số client, toggle
  nhanh (flash / đổi camera / night).
- **Settings**: độ phân giải, FPS, quality, port, autostart, keep-warm, IP tĩnh
  (thực nghiệm), WB/effect/scene/brightness/contrast — mục không hỗ trợ bị ẩn.

## 13. Kiểm thử

- Unit test (JVM): format boundary MJPEG, parse `/control`, JSON `/status`, map
  control, parse capability.
- Test LAN: `curl http://<ip>:8081/stream | ffmpeg`, VLC, mở trong browser.
- Máy build **không có thiết bị Android** → build APK debug để sideload; cung cấp
  hướng dẫn test on-device.

## 14. Rủi ro & giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| MJPEG tốn băng thông, máy cũ yếu | mặc định VGA, giới hạn FPS/quality, auto-adapt |
| HAL Camera2 khác nhau giữa hãng | dò capability, ẩn control không hỗ trợ, bắt exception |
| OEM giết app nền | foreground service, xin miễn tối ưu pin, hướng dẫn autostart |
| Không đặt được IP tĩnh Android 10+ | DHCP reservation theo MAC |
| "Night vision" không như kỳ vọng | ghi rõ chỉ là low-light, không phải IR |
| Port 81 không bind được | dùng 8081 |

## 15. Cấu trúc thư mục dự kiến

```
android_camera_streaming/
├── docs/superpowers/specs/2026-10-14-android-ip-camera-design.md
├── docs/plan.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/…
├── gradlew, gradlew.bat
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/<pkg>/
        │   │   ├── MainActivity.kt
        │   │   ├── SettingsActivity.kt
        │   │   ├── camera/CameraController.kt
        │   │   ├── camera/CameraControls.kt
        │   │   ├── camera/Capabilities.kt
        │   │   ├── stream/FrameBroadcaster.kt
        │   │   ├── http/HttpServer.kt
        │   │   ├── service/StreamingService.kt
        │   │   ├── service/BootReceiver.kt
        │   │   ├── config/SettingsStore.kt
        │   │   └── net/NetworkInfo.kt
        │   └── res/…
        └── test/java/<pkg>/…   (unit tests)
```
