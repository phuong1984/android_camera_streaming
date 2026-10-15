# Version hiển thị + Chuyển ngôn ngữ Anh/Việt — Thiết kế

Ngày: 2026-10-15
Trạng thái: Đã duyệt

## 1. Mục tiêu

- Hiển thị **version của app** ở góc dưới-phải màn hình chính (luôn nhìn thấy).
- Thêm chỗ **chuyển đổi ngôn ngữ Tiếng Anh ↔ Tiếng Việt** thuận tiện ở góc trên-phải
  màn hình chính. Mặc định **Tiếng Việt**.

## 2. Phạm vi

- `TextView tvVersion` ở góc dưới-phải, hiển thị `"v" + versionName` (ví dụ `v1.0`).
- Nút `btnLanguage` (ImageButton) ở góc trên-phải: hiển thị **cờ của ngôn ngữ sẽ
  chuyển tới** (đang Việt → cờ Anh UK; đang Anh → cờ Việt Nam); bấm để đổi.
- Bản địa hoá toàn bộ chuỗi giao diện app: màn hình chính + màn Cài đặt.
- Ngôn ngữ được lưu bền, áp dụng lại khi mở app.

Ngoài phạm vi: trang HTML `/` cho browser (`ViewerPage`) và các tham số điều khiển
kiểu ESP32 — không thuộc ngôn ngữ giao diện app.

## 3. Version

- Bật `buildConfig = true` trong `app/build.gradle.kts`; đọc `BuildConfig.VERSION_NAME`.
- Helper thuần `AppVersion.label(name: String?): String` → `"v$name"` (name rỗng/null → `"v"`).
- Root `activity_main.xml` đổi thành `FrameLayout`: giữ nguyên `ScrollView`, thêm
  `tvVersion` với `layout_gravity="bottom|end"`, chữ nhỏ mờ, margin 12dp → không bị cuộn mất.

## 4. Chuyển ngôn ngữ

Cơ chế: per-app locale của AppCompat (`AppCompatDelegate.setApplicationLocales`,
appcompat 1.7.0). Đây là nguồn duy nhất lưu locale và **tự lưu qua các lần mở app**.

- Thêm `App : Application` (đăng ký trong manifest). Trong `onCreate`, nếu
  `AppCompatDelegate.getApplicationLocales()` trống → đặt Tiếng Việt (`vi`).
- `btnLanguage` (ImageButton):
  - Hình = cờ của ngôn ngữ sẽ chuyển tới: `Language.flagRes(current)` trả về
    `R.drawable.flag_uk` (khi đang Việt) / `R.drawable.flag_vietnam` (khi đang Anh).
  - Bấm → lưu lựa chọn vào `SettingsStore` rồi
    `AppCompatDelegate.setApplicationLocales(next(current))`. Activity tự recreate.
- Helper thuần `Language`:
  - `const val DEFAULT = "vi"`
  - `fun next(current: String): String` → `vi` ↔ `en`
  - `fun flagRes(current: String): Int` → id drawable cờ của ngôn ngữ sẽ chuyển tới
- Nguồn dữ liệu ngôn ngữ là `SettingsStore.loadLanguage()/saveLanguage()` (mặc định `vi`),
  vì per-app locale của AppCompat không lưu bền trên một số máy (đã gặp trên Android 11).
  AppCompat chỉ giữ vai trò áp dụng cấu hình/tài nguyên.

## 5. Tài nguyên

- `values/strings.xml`: Tiếng Việt (mặc định) — thêm `language_switch` (mô tả nút).
  Chuỗi version không cần bản địa hoá.
- `drawable/flag_uk.xml`, `drawable/flag_vietnam.xml`: vector cờ cho nút ngôn ngữ.
- `values-en/strings.xml`: bản dịch Tiếng Anh cho **toàn bộ** chuỗi app.
- Bản địa hoá màn Cài đặt:
  - `preferences.xml`: chuyển các tiêu đề/mô tả hardcode → `@string`.
  - `arrays.xml`: nhãn `resolution_entries` (tên độ phân giải giữ nguyên số, chỉ đổi phần chữ nếu có).
  - `SettingsFragment`: 2 Toast hardcode → `@string`.
- `MainActivity`: Toast "URL copied" → `@string`.

## 6. Manifest

- Thêm `<service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
  android:enabled="false" android:exported="false">` với meta-data `autoStoreLocales=true`
  để API 33+ lưu locale chuẩn hệ thống.
- `android:name=".App"` cho `<application>`.

## 7. Kiểm thử

- `LanguageTest`: `next("vi")=="en"`, `next("en")=="vi"`, `next(unknown)=="en"` (an toàn).
- `AppVersionTest`: `label("1.0")=="v1.0"`, `label(null)=="v"`, `label("")=="v"`.
- `ActivitySmokeTest`: `tvVersion` hiển thị đúng `"v"+versionName`; `btnLanguage` hiện
  đúng cờ theo ngôn ngữ hiện tại; bấm nút đổi lưu sang `en` và áp dụng locale `en`.
- Các test cũ vẫn phải xanh.
