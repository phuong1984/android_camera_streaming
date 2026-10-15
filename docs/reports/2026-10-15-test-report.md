# Báo cáo kiểm thử — IP Camera Streaming

- Ngày chạy: 2026-10-15
- Phiên bản app: `1.0` (versionCode 1)
- Commit: `9c9cfb8` — "Add app version and language selection"
- Lệnh chạy: `./gradlew clean testDebugUnitTest`
- Nền tảng test tự động: JVM (JUnit4 + Robolectric 4.13, `@Config(sdk=[34])`)
- Thiết bị test thủ công: Aris_Pro `BHB00020726`, Android 11 (SDK 30), qua adb

## Tóm tắt kết quả

| Chỉ số | Giá trị |
|---|---|
| Số lớp test | 15 |
| Tổng số test | 66 |
| Pass | 66 |
| Fail | 0 |
| Error | 0 |
| Skip | 0 |

**Kết luận: 66/66 test PASS. Không có test fail/error/skip. Build `clean testDebugUnitTest` thành công.**

---

# Tính năng 1: Hiển thị phiên bản app + Chuyển ngôn ngữ (Việt/Anh)

Lớp test: `AppVersionTest`, `LanguageTest`, `ActivitySmokeTest`.

### Scenario 1: Nhãn phiên bản có tiền tố "v"
- Mô tả: `AppVersion.label("1.0")` phải trả về `"v1.0"`.
- Kết quả: **PASS** (`AppVersionTest.prefixesVersionNameWithV`)

### Scenario 2: Tên phiên bản null → chỉ còn "v"
- Mô tả: `AppVersion.label(null)` phải trả về `"v"`.
- Kết quả: **PASS** (`AppVersionTest.nullNameBecomesBareV`)

### Scenario 3: Tên phiên bản rỗng → chỉ còn "v"
- Mô tả: `AppVersion.label("")` phải trả về `"v"`.
- Kết quả: **PASS** (`AppVersionTest.blankNameBecomesBareV`)

### Scenario 4: Ngôn ngữ mặc định là Tiếng Việt
- Mô tả: `Language.DEFAULT` phải bằng `"vi"`.
- Kết quả: **PASS** (`LanguageTest.defaultIsVietnamese`)

### Scenario 5: Đổi từ Tiếng Việt sang Tiếng Anh
- Mô tả: `Language.next("vi")` phải trả về `"en"`.
- Kết quả: **PASS** (`LanguageTest.togglesFromVietnameseToEnglish`)

### Scenario 6: Đổi từ Tiếng Anh sang Tiếng Việt
- Mô tả: `Language.next("en")` phải trả về `"vi"`.
- Kết quả: **PASS** (`LanguageTest.togglesFromEnglishToVietnamese`)

### Scenario 7: Ngôn ngữ lạ/trống → chuyển sang Tiếng Anh (an toàn)
- Mô tả: `Language.next("")` và `Language.next("fr")` đều trả về `"en"`.
- Kết quả: **PASS** (`LanguageTest.unknownLanguageTogglesToEnglish`)

### Scenario 8: Ánh xạ cờ theo ngôn ngữ sẽ chuyển tới
- Mô tả: `Language.flagRes("vi")` → `flag_uk`, `Language.flagRes("en")` → `flag_vietnam`.
- Kết quả: **PASS** (`LanguageTest.flagResPointsToTheTargetLanguageFlag`)

### Scenario 9: TextView phiên bản hiển thị đúng trên màn hình chính
- Mô tả: `tvVersion` = `AppVersion.label(BuildConfig.VERSION_NAME)`.
- Kết quả: **PASS** (`ActivitySmokeTest.versionLabel_isShownAsVeePrefixedVersionName`)

### Scenario 10: Nút ngôn ngữ hiển thị đúng cờ của ngôn ngữ sẽ chuyển tới
- Mô tả: Khi ngôn ngữ lưu là `vi`, nút `btnLanguage` (ImageButton) hiển thị drawable `flag_uk` (so khớp kích thước intrinsic).
- Kết quả: **PASS** (`ActivitySmokeTest.languageButton_showsFlagOfTargetLanguage`)

### Scenario 11: Bấm nút ngôn ngữ → lưu và áp dụng Tiếng Anh
- Mô tả: Bấm `btnLanguage` phải lưu `en` vào `SettingsStore` và đặt locale app = `en`.
- Kết quả: **PASS** (`ActivitySmokeTest.languageButton_switchesStoredLanguageToEnglish`)

### Scenario 12: MainActivity khởi động không crash
- Mô tả: Dựng `MainActivity` qua Robolectric không ném lỗi.
- Kết quả: **PASS** (`ActivitySmokeTest.mainActivity_launchesWithoutCrash`)

### Scenario 13: SettingsActivity khởi động không crash
- Mô tả: Dựng `SettingsActivity` qua Robolectric không ném lỗi.
- Kết quả: **PASS** (`ActivitySmokeTest.settingsActivity_launchesWithoutCrash`)

---

# Tính năng 2: Khoá hướng dọc (portrait) & xoay ảnh JPEG

Lớp test: `CameraOrientationTest`.

### Scenario 1: Làm tròn góc về bội số 90
- Mô tả: `snapOrientation` làm tròn góc bất kỳ về 0/90/180/270 gần nhất.
- Kết quả: **PASS** (`snapOrientation_roundsToNearest90`)

### Scenario 2: Camera sau — hướng JPEG = sensor + xoay thiết bị
- Mô tả: `jpegOrientation(sensor, front=false, deviceDeg)` cộng hướng thiết bị.
- Kết quả: **PASS** (`jpegOrientation_backCamera_addsDeviceRotation`)

### Scenario 3: Camera trước — hướng JPEG = sensor − xoay thiết bị
- Mô tả: `jpegOrientation(sensor, front=true, deviceDeg)` trừ hướng thiết bị.
- Kết quả: **PASS** (`jpegOrientation_frontCamera_subtractsDeviceRotation`)

### Scenario 4: Hướng JPEG luôn trong khoảng hợp lệ 0..359
- Mô tả: Mọi tổ hợp đầu vào cho kết quả trong [0,360).
- Kết quả: **PASS** (`jpegOrientation_isAlwaysInValidRange`)

### Scenario 5: Kích thước hiển thị đổi chỗ rộng/cao khi xoay 90/270
- Mô tả: `displaySize` chỉ hoán vị rộng↔cao khi ảnh bị xoay 90/270.
- Kết quả: **PASS** (`displaySize_backSensor90_swapsOnlyWhenImageRotated90or270`)

### Scenario 6: displaySize không đổi diện tích, chỉ hoán vị
- Mô tả: Kiểm tra tính bất biến diện tích và hoán vị đúng.
- Kết quả: **PASS** (`displaySize_neverChangesAreaJustSwaps`)

---

# Tính năng 3: Khả năng camera (scene / AWB / effect)

Lớp test: `CapabilitiesTest`.

### Scenario 1: Ánh xạ tên chế độ AWB
- Mô tả: Tên AWB (`auto`, `daylight`…) ánh xạ đúng sang hằng số Camera2.
- Kết quả: **PASS** (`mapsAwbModeNames`)

### Scenario 2: Tra ngược giá trị AWB
- Mô tả: Từ giá trị hằng số tra ngược ra tên.
- Kết quả: **PASS** (`awbValue_reverseLookup`)

### Scenario 3: Tên scene/effect hợp lệ; `night` không còn tồn tại
- Mô tả: `sceneNameToValue`/`effectNameToValue` đúng; chế độ "night" đã bị loại bỏ.
- Kết quả: **PASS** (`sceneAndEffectNames`)

---

# Tính năng 4: Tham số điều khiển tương thích ESP32

Lớp test: `ControlParamsTest`.

### Scenario 1: Phân tích tham số "flash"
- Mô tả: `flash=1/0` → `ControlAction.SetFlash`.
- Kết quả: **PASS** (`parsesFlash`)

### Scenario 2: Phân tích tham số "camera"
- Mô tả: Chọn camera trước/sau → `ControlAction.SetCameraFront`.
- Kết quả: **PASS** (`parsesCamera`)

### Scenario 3: Phân tích "framesize"
- Mô tả: Chọn độ phân giải → `ControlAction.SetResolution`.
- Kết quả: **PASS** (`parsesFrameSize`)

### Scenario 4: Phân tích "framesize" theo chỉ số kiểu ESP32
- Mô tả: Chỉ số framesize (0,1,2…) ánh xạ đúng như ESP32.
- Kết quả: **PASS** (`parsesFrameSizeByEsp32Index`)

### Scenario 5: framesize không hợp lệ → null
- Mô tả: Giá trị rác trả về null (không áp dụng).
- Kết quả: **PASS** (`invalidFrameSizeReturnsNull`)

### Scenario 6: Phân tích "quality" và "fps"
- Mô tả: `quality`/`fps` → `SetQuality`/`SetFps`.
- Kết quả: **PASS** (`parsesQualityAndFps`)

### Scenario 7: Tham số lạ → null
- Mô tả: Tham số không nhận diện trả về null.
- Kết quả: **PASS** (`unknownReturnsNull`)

---

# Tính năng 5: HTTP server & Trang xem trước cho browser

Lớp test: `HttpServerTest`, `ViewerPageTest`.

### Scenario 1: Yêu cầu ảnh `/stream` trả về MJPEG
- Mô tả: Request kiểu ảnh tới `/stream` trả MJPEG với boundary đúng.
- Kết quả: **PASS** (`HttpServerTest.imageRequestToStreamServesMjpeg`)

### Scenario 2: Browser mở `/` nhận trang HTML viewer
- Mô tả: Request HTML tới `/` trả `ViewerPage` HTML.
- Kết quả: **PASS** (`HttpServerTest.browserNavigationToRootServesViewerHtml`)

### Scenario 3: Browser mở `/stream` nhận trang HTML viewer
- Mô tả: Request HTML tới `/stream` trả viewer (không phải MJPEG).
- Kết quả: **PASS** (`HttpServerTest.browserNavigationToStreamServesViewerHtml`)

### Scenario 4: API `/` không nhận HTML → trả text
- Mô tả: Request không có `Accept: text/html` giữ nguyên JSON/text.
- Kết quả: **PASS** (`HttpServerTest.apiRootWithoutHtmlAcceptStaysText`)

### Scenario 5: Viewer tham chiếu endpoint stream
- Mô tả: HTML viewer chứa `/stream`.
- Kết quả: **PASS** (`ViewerPageTest.referencesStreamEndpoint`)

### Scenario 6: Viewer hỏi `/status` để căn tỉ lệ khung
- Mô tả: HTML viewer gọi `/status` lấy kích thước hiển thị.
- Kết quả: **PASS** (`ViewerPageTest.pollsStatusForAspectRatio`)

### Scenario 7: Ảnh không bị méo (fit đúng tỉ lệ)
- Mô tả: CSS/JS giữ đúng tỉ lệ, không kéo giãn.
- Kết quả: **PASS** (`ViewerPageTest.fitsImageWithoutDistortion`)

### Scenario 8: Nhận diện request HTML của browser
- Mô tả: Hàm nhận diện `Accept` HTML chính xác.
- Kết quả: **PASS** (`ViewerPageTest.detectsBrowserHtmlRequest`)

---

# Tính năng 6: Vòng đời camera — IdlePolicy

Lớp test: `IdlePolicyTest`.

### Scenario 1: Không dừng khi còn trong thời gian ân hạn
- Mô tả: Trong grace period, camera chưa bị dừng.
- Kết quả: **PASS** (`doesNotStopWithinGracePeriod`)

### Scenario 2: Dừng sau khi hết ân hạn và không còn client
- Mô tả: Quá grace, count=0, không keepWarm/preview → dừng.
- Kết quả: **PASS** (`stopsAfterGracePeriodWithNoClients`)

### Scenario 3: Không bao giờ dừng khi bật "giữ camera luôn bật"
- Mô tả: `keepWarm=true` → không dừng.
- Kết quả: **PASS** (`neverStopsWhenKeepWarm`)

### Scenario 4: Không dừng khi đang có client
- Mô tả: `clientCount>0` → không dừng.
- Kết quả: **PASS** (`doesNotStopWhenClientPresent`)

### Scenario 5: Không dừng camera đã dừng rồi
- Mô tả: `cameraRunning=false` → không xử lý dừng.
- Kết quả: **PASS** (`doesNotStopAlreadyStoppedCamera`)

### Scenario 6: Chưa từng có hoạt động → không dừng
- Mô tả: `lastActivityMs` chưa ghi nhận → không dừng.
- Kết quả: **PASS** (`doesNotStopWhenNoActivityRecordedYet`)

### Scenario 7: Không dừng khi preview đang bật
- Mô tả: `previewActive=true` → giữ camera sống.
- Kết quả: **PASS** (`neverStopsWhenPreviewActive`)

---

# Tính năng 7: Tự động điều chỉnh chất lượng/FPS

Lớp test: `AdaptiveControllerTest`.

### Scenario 1: Khởi tạo ở mức tối đa
- Mô tả: Bắt đầu ở fps/quality cao nhất.
- Kết quả: **PASS** (`startsAtMax`)

### Scenario 2: Tắc nghẽn → giảm FPS và chất lượng
- Mô tả: Có frame chậm → hạ dần fps/quality.
- Kết quả: **PASS** (`congestion_lowersFpsAndQuality`)

### Scenario 3: Hồi phục → tăng dần nhưng không vượt mức tối đa
- Mô tả: Mạng tốt trở lại → tăng dần, cap ở max.
- Kết quả: **PASS** (`recovery_raisesGraduallyButCapsAtMax`)

### Scenario 4: Không bao giờ hạ dưới mức tối thiểu
- Mô tả: fps/quality luôn ≥ ngưỡng min.
- Kết quả: **PASS** (`neverBelowMinimums`)

---

# Tính năng 8: Phát frame (FrameBroadcaster)

Lớp test: `FrameBroadcasterTest`.

### Scenario 1: Chưa publish → frame mới nhất là null
- Mô tả: Trước lần publish đầu, `latestFrame` null.
- Kết quả: **PASS** (`latestFrame_isNullBeforeFirstPublish`)

### Scenario 2: Publish lưu frame mới nhất
- Mô tả: `publish` cập nhật frame mới nhất.
- Kết quả: **PASS** (`publish_storesLatest`)

### Scenario 3: Đếm số client theo dõi
- Mô tả: `clientCount` tăng/giảm theo subscriber.
- Kết quả: **PASS** (`clientCount_tracksSubscribers`)

### Scenario 4: awaitFrame đánh thức khi có publish
- Mô tả: `awaitFrame` thức dậy khi có frame mới.
- Kết quả: **PASS** (`awaitFrame_wakesOnPublish`)

---

# Tính năng 9: Ghi luồng MJPEG

Lớp test: `MjpegWriterTest`.

### Scenario 1: Ghi đúng boundary và Content-Length
- Mô tả: Writer xuất đúng boundary và độ dài phần multipart.
- Kết quả: **PASS** (`writesBoundaryAndLength`)

---

# Tính năng 10: Xoay ảnh preview trong app

Lớp test: `PreviewRotationTest`.

### Scenario 1: Ánh xạ EXIF orientation sang độ xoay
- Mô tả: Giá trị EXIF → số độ xoay theo chiều kim đồng hồ.
- Kết quả: **PASS** (`mapsExifOrientationToClockwiseDegrees`)

### Scenario 2: EXIF không xác định → không xoay
- Mô tả: Orientation lạ → 0 độ.
- Kết quả: **PASS** (`unknownExifOrientationNeedsNoRotation`)

### Scenario 3: Kích thước sau xoay hoán vị khi quay 90/270
- Mô tả: `rotatedSize` đổi chỗ rộng/cao cho góc 90/270.
- Kết quả: **PASS** (`rotatedSizeSwapsDimensionsForQuarterTurns`)

### Scenario 4: Giảm kích thước (sample) tới khi dưới ngưỡng
- Mô tả: `sampleSize` chia đôi tới khi ≤ kích thước mục tiêu.
- Kết quả: **PASS** (`sampleSizeHalvesUntilUnderTarget`)

---

# Tính năng 11: Lưu cài đặt (SettingsStore)

Lớp test: `SettingsStoreTest`.

### Scenario 1: Giá trị mặc định kiểu ESP32
- Mô tả: Mặc định port=8081, 640x480, fps=15, autoFramesize=true.
- Kết quả: **PASS** (`defaults_areEsp32Like`)

### Scenario 2: Lưu rồi đọc lại cấu hình
- Mô tả: Save/Load các trường cấu hình round-trip đúng.
- Kết quả: **PASS** (`save_thenLoad_roundTrips`)

### Scenario 3: Chọn độ phân giải gần đúng
- Mô tả: `Resolution.nearest/fromKey` ánh xạ đúng.
- Kết quả: **PASS** (`resolution_nearest`)

### Scenario 4: Preview mặc định tắt
- Mô tả: `loadPreviewEnabled()` mặc định false.
- Kết quả: **PASS** (`previewEnabled_defaultsToFalse`)

### Scenario 5: Bật/tắt preview lưu bền
- Mô tả: Save/Load `previewEnabled` round-trip true/false.
- Kết quả: **PASS** (`previewEnabled_save_thenLoad_roundTrips`)

### Scenario 6: Ngôn ngữ mặc định là "vi"
- Mô tả: `loadLanguage()` mặc định `"vi"`.
- Kết quả: **PASS** (`language_defaultsToVietnamese`)

### Scenario 7: Lưu/đọc ngôn ngữ bền vững
- Mô tả: `saveLanguage("en"/"vi")` rồi đọc lại đúng.
- Kết quả: **PASS** (`language_save_thenLoad_roundTrips`)

---

# Tính năng 12: Đèn flash tắt thực sự khi dừng phát (bug fix)

Lớp test: `CameraControllerTorchTest`.

### Scenario 1: Torch bật lúc camera idle thì phải tắt khi stop
- Mô tả: Khi camera chưa mở (không client/preview), bật torch qua `CameraManager.setTorchMode(id, true)`; gọi `stop()` phải gọi `setTorchMode(id, false)` → `getTorchMode(id) == false`.
- Kết quả: **PASS** (`stop_turnsOffTorchThatWasEnabledWhileCameraIdle`)
- Ghi chú: Test này tái hiện đúng bug (trước khi sửa: FAIL với "torch phải tắt khi dừng"). Nguyên nhân gốc: `stop()` đọc `cameraId` (null khi camera chưa mở) thay vì phân giải `cameraId ?: chooseCameraId(front)`.

---

# Phần bổ sung: Kiểm thử THỰC TẾ trên thiết bị (Aris_Pro, Android 11) — chạy lại 2026-10-15

Các kịch bản dưới đây được chạy trực tiếp trên máy qua adb (**chạy lại toàn bộ**), không nằm trong bộ unit test tự động.

Thiết bị: `BHB00020726`, Android 11 (SDK 30), IP `192.168.1.7`, app cài sạch (`pm clear`) trước khi test.

### Scenario M1: Phiên bản hiển thị đúng
- Mô tả: Mở app, đọc `tvVersion`.
- Kết quả: **PASS** — `tvVersion` text = `"v1.0"`, bounds góc dưới-phải `[977,2111][1044,2160]`.

### Scenario M2: Mặc định là Tiếng Việt
- Mô tả: Sau khi cài sạch, kiểm tra toàn bộ chuỗi UI + prefs.
- Kết quả: **PASS** — Tiêu đề "Truyền phát Camera IP", "BẮT ĐẦU PHÁT", "SAO CHÉP URL", "ĐỔI SANG CAMERA TRƯỚC", "CÀI ĐẶT"; prefs ngôn ngữ chưa ghi (mặc định `vi`).

### Scenario M3: Nút ngôn ngữ hiển thị cờ của ngôn ngữ đích (Anh)
- Mô tả: Đang Tiếng Việt → nút `btnLanguage` phải hiện cờ Vương quốc Anh.
- Kết quả: **PASS** — chụp màn hình, crop vùng nút `[876,135][1020,279]`: navy `#012169` (~1280px), đỏ `#C8102E` (~1458px), trắng (~17488px) → cờ UK.

### Scenario M4: Bấm nút ngôn ngữ → chuyển sang Tiếng Anh
- Mô tả: Tap `btnLanguage`, kiểm tra UI + prefs.
- Kết quả: **PASS** — UI đổi toàn bộ sang tiếng Anh ("IP Camera Streaming", "START STREAMING", "COPY URL"…); `shared_prefs/ipcamera.xml` = `<string name="language">en</string>`.

### Scenario M5: Cờ sau khi chuyển sang Tiếng Anh là cờ Việt Nam
- Mô tả: Sau khi sang English, nút phải hiện cờ Việt Nam.
- Kết quả: **PASS** — crop nút: đỏ `#DA251D` (~5848px), vàng `#FFFF00` (~195px) → cờ Việt Nam.

### Scenario M6: Lưu bền ngôn ngữ qua force-stop
- Mô tả: `am force-stop` rồi mở lại app.
- Kết quả: **PASS** — vẫn tiếng Anh sau khi khởi động lại; chuyển lại Tiếng Việt thì prefs = `vi`.

### Scenario M7: HTTP server + stream MJPEG tới client thật
- Mô tả: Bấm "BẮT ĐẦU PHÁT" (khởi động foreground service + HTTP server), sau đó kết nối 1 client `curl /stream` trong 5s; kiểm tra `/status` và dữ liệu nhận được.
- Kết quả: **PASS**
  - `/status` khi có client: `running:true, clients:1`, `displayWidth:480, displayHeight:640` (đã hoán vị → portrait), `hasFlash:true`, `awb:[auto,incandescent,fluorescent,daylight,cloudy,shade]`.
  - Nhận **40 frame JPEG hoàn chỉnh** (đúng SOI `FFD8`/EOI `FFD9`), header `Content-Type: image/jpeg` + `Content-Length`.
  - `frame0.jpg` = ảnh JPEG 640x480 hợp lệ, EXIF từ "vsmart Aris Pro", `orientation=upper-right` (đã xử lý xoay).
  - Log: `CameraController: start front=false` → `openInternal id=0` → `onOpened` → `session onConfigured`.

### Scenario M8: Trang viewer cho browser
- Mô tả: `curl -H "Accept: text/html" /`.
- Kết quả: **PASS** — trả HTML có `<title>IP Camera</title>`, tham chiếu `/stream` và `/status`.

### Scenario M9: Preview trong app render frame sống
- Mô tả: Tắt client → camera tự dừng sau idle → bật "Xem trước"; so `/status` và so sánh 2 ảnh chụp liên tiếp vùng preview.
- Kết quả: **PASS**
  - Trước preview: `running:false`; sau khi bật preview: `running:true, clients:0` (preview giữ camera sống).
  - Vùng `previewContainer` đổi giữa 2 frame (diff mean ~1.4, max 26) → ảnh preview được cập nhật liên tục.

### Scenario M10: Khoá hướng dọc
- Mô tả: Kiểm tra rotation hệ thống.
- Kết quả: **PASS** — `dumpsys display` → `rotation 0`; `dumpsys input` → `SurfaceOrientation: 0`.

### Scenario M11: Đổi camera trước/sau
- Mô tả: Tap "ĐỔI SANG CAMERA TRƯỚC".
- Kết quả: **PASS** — log `CameraController: start front=true` → `openInternal id=1`; `/status front:true, hasFlash:false` (camera trước không có flash).

### Scenario M12: Flash tắt thực sự khi dừng phát (camera idle)
- Mô tả: Preview tắt (camera idle, `running:false`) → bật Flash → dừng phát; kiểm tra log app + log HAL.
- Kết quả: **PASS**
  - Bật: `onControl SetFlash(on=true)` → `setTorchMode id=0 on=true`; `/status torch:true`; HAL "Torch for camera id 0 **turned on**".
  - Dừng: `stop (was running=false)` → `setTorchMode id=0 on=false`; HAL "**turned off**"; service count = 0.

### Scenario M13: Flash tắt thực sự khi dừng phát (phiên camera đang chạy)
- Mô tả: Bật preview (camera session đang chạy) → bật Flash (in-session) → dừng phát.
- Kết quả: **PASS**
  - Bật: `setTorch(in-session) on=true` → `updateRepeating … torch=true`; `/status torch:true`.
  - Dừng: `stop (was running=true)` → `setTorchMode id=0 on=false`; HAL "**turned off**"; service count = 0.

### Ghi chú khi test thủ công (bài học)
- Khi preview đang bật, `previewContainer` (cao 200dp) làm các nút bên dưới dịch xuống ~624px; phải dùng lại toạ độ (`uiautomator dump`) trước khi tap.
- Khi camera đang chạy/preview đang bật, `uiautomator dump` có thể báo "could not get idle state" → dùng `/status` (HTTP) và logcat để kiểm chứng thay vì dump UI.
- Sau khi reset app về mặc định cần `pm grant … CAMERA` lại.

---

# Cách tái lập

```bash
./gradlew clean testDebugUnitTest
# Báo cáo HTML: app/build/reports/tests/testDebugUnitTest/index.html
# Kết quả XML: app/build/test-results/testDebugUnitTest/*.xml
```

# Ghi chú

- Không có test instrumented (`app/src/androidTest` không tồn tại); toàn bộ test là unit test chạy trên JVM.
- Test UI dùng Robolectric (`@Config(sdk=[34])`); phần cứng camera/đèn được mô phỏng bằng shadow (`ShadowCameraManager`).
- Các test thuần logic (orientation, control params, idle policy, adaptive, preview rotation…) chạy rất nhanh, không cần thiết bị.
