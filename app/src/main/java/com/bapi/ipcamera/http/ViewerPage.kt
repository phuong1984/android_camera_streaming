package com.bapi.ipcamera.http

/**
 * Trang xem MJPEG nhỏ do app tự phục vụ, dùng khi mở `http://<ip>:<port>/` bằng
 * browser. Giữ ảnh đúng tỉ lệ (không méo) và tự lật khung dọc/ngang theo hướng
 * cầm điện thoại bằng cách đọc `displayWidth`/`displayHeight` từ `/status`.
 *
 * Trang robot hoặc thẻ `<img src="/stream">` vẫn nhận MJPEG thô vì request ảnh
 * gửi Accept dạng ảnh, không chứa text/html.
 */
object ViewerPage {
    fun html(): String = """
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>IP Camera</title>
<style>
  html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
  #stage {
    position: fixed; inset: 0; display: flex;
    align-items: center; justify-content: center;
  }
  #cam {
    display: block;
    max-width: 100%; max-height: 100%;
    object-fit: contain;
    background: #000;
  }
</style>
</head>
<body>
<div id="stage"><img id="cam" src="/stream" alt="camera"></div>
<script>
(function () {
  var cam = document.getElementById('cam');
  var stage = document.getElementById('stage');
  function fit(w, h) {
    if (!w || !h) return;
    cam.style.aspectRatio = w + ' / ' + h;
    var vw = stage.clientWidth, vh = stage.clientHeight;
    var scale = Math.min(vw / w, vh / h);
    cam.style.width = Math.round(w * scale) + 'px';
    cam.style.height = Math.round(h * scale) + 'px';
  }
  function poll() {
    fetch('/status', { cache: 'no-store' })
      .then(function (r) { return r.json(); })
      .then(function (s) { fit(s.displayWidth, s.displayHeight); })
      .catch(function () {});
  }
  setInterval(poll, 1000);
  poll();
  window.addEventListener('resize', poll);
})();
</script>
</body>
</html>
""".trimIndent()

    /** True nếu client (browser điều hướng) chấp nhận HTML; false cho request ảnh/API. */
    fun isHtmlRequest(accept: String?): Boolean =
        accept?.contains("text/html", ignoreCase = true) == true
}
