// app_httpd.cpp — DurianCare ESP32-CAM HTTP server
//
// Exposes:
//   GET /         — status page with embedded <img src="/capture">
//   GET /capture  — single JPEG still image (used by backend snapshot proxy)
//   GET /stream   — MJPEG live stream (for browser preview)
//
// Camera grab mode must be CAMERA_GRAB_WHEN_EMPTY (set in .ino) so that
// esp_camera_fb_get() blocks until a complete frame is ready and
// httpd_resp_send() can transmit the full buffer without a DMA race.

#include "esp_http_server.h"
#include "esp_camera.h"
#include "Arduino.h"
#include <string.h>

// ── MJPEG stream constants ────────────────────────────────────────────────────
#define PART_BOUNDARY "duriancare_frame_boundary"
static const char *STREAM_CONTENT_TYPE =
    "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char *STREAM_BOUNDARY  = "\r\n--" PART_BOUNDARY "\r\n";
static const char *STREAM_PART_FMT  = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

// ── Index page ────────────────────────────────────────────────────────────────
static esp_err_t index_handler(httpd_req_t *req) {
    const char *html =
        "<!DOCTYPE html><html><head>"
        "<title>DurianCare ESP32-CAM</title>"
        "<style>body{font-family:sans-serif;text-align:center;padding:20px;}"
        "img{max-width:100%;border:1px solid #ccc;border-radius:4px;}"
        "a{margin:0 8px;color:#16a34a;}</style>"
        "</head><body>"
        "<h2>DurianCare ESP32-CAM</h2>"
        "<img src='/capture' id='img'/>"
        "<br/><br/>"
        "<a href='/capture'>&#128247; Capture</a>"
        "<a href='/stream'>&#127909; Stream</a>"
        "<script>"
        "function refresh(){"
        "  document.getElementById('img').src='/capture?t='+Date.now();"
        "  setTimeout(refresh,2000);"
        "}"
        "setTimeout(refresh,2000);"
        "</script>"
        "</body></html>";
    httpd_resp_set_type(req, "text/html");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_sendstr(req, html);
}

// ── Still capture ─────────────────────────────────────────────────────────────
//
// CRITICAL: With CAMERA_GRAB_WHEN_EMPTY + fb_count=1 (.ino), esp_camera_fb_get()
// blocks until the DMA has finished writing one full frame into the buffer.
// httpd_resp_send() then transmits headers + body atomically.
// esp_camera_fb_return(fb) releases the buffer so the DMA can fill the next frame.
// There is NO race condition — the DMA cannot overwrite while we hold fb.
static esp_err_t capture_handler(httpd_req_t *req) {
    camera_fb_t *fb = esp_camera_fb_get();
    if (!fb) {
        Serial.println("[cam] capture: esp_camera_fb_get() returned NULL");
        httpd_resp_send_500(req);
        return ESP_FAIL;
    }

    esp_err_t res = ESP_OK;

    // Verify JPEG magic bytes (FF D8 FF)
    if (fb->len < 3 || fb->buf[0] != 0xFF || fb->buf[1] != 0xD8 || fb->buf[2] != 0xFF) {
        Serial.printf("[cam] capture: bad JPEG magic — len=%u first=[%02X %02X %02X]\n",
                      fb->len, fb->buf[0], fb->buf[1], fb->buf[2]);
        esp_camera_fb_return(fb);
        if (!httpd_resp_set_status(req, "502 Bad Gateway")) { /* ignore */ }
        httpd_resp_sendstr(req, "Bad frame from camera sensor");
        return ESP_FAIL;
    }

    httpd_resp_set_type(req, "image/jpeg");
    httpd_resp_set_hdr(req, "Content-Disposition", "inline; filename=capture.jpg");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store, no-cache");

    res = httpd_resp_send(req, (const char *)fb->buf, (ssize_t)fb->len);
    esp_camera_fb_return(fb);  // MUST release before DMA can capture next frame

    if (res != ESP_OK) {
        Serial.printf("[cam] capture: httpd_resp_send failed: %d\n", res);
    } else {
        Serial.println("[cam] capture: JPEG sent OK");
    }
    return res;
}

// ── MJPEG stream ──────────────────────────────────────────────────────────────
//
// Continuously calls esp_camera_fb_get() + esp_camera_fb_return() in a loop.
// With CAMERA_GRAB_WHEN_EMPTY this is safe: we always release before the next get.
static esp_err_t stream_handler(httpd_req_t *req) {
    httpd_resp_set_type(req, STREAM_CONTENT_TYPE);
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store, no-cache");

    Serial.println("[cam] stream: client connected");

    char part_buf[64];
    esp_err_t res = ESP_OK;

    while (true) {
        camera_fb_t *fb = esp_camera_fb_get();
        if (!fb) {
            Serial.println("[cam] stream: fb_get() NULL — stopping");
            break;
        }

        size_t hlen = snprintf(part_buf, sizeof(part_buf), STREAM_PART_FMT, fb->len);

        res = httpd_resp_send_chunk(req, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY));
        if (res == ESP_OK)
            res = httpd_resp_send_chunk(req, part_buf, (ssize_t)hlen);
        if (res == ESP_OK)
            res = httpd_resp_send_chunk(req, (const char *)fb->buf, (ssize_t)fb->len);

        esp_camera_fb_return(fb);  // release before next get

        if (res != ESP_OK) {
            Serial.println("[cam] stream: client disconnected");
            break;
        }
    }

    return res;
}

// ── Public entry point ────────────────────────────────────────────────────────
void startCameraServer() {
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port      = 80;
    config.ctrl_port        = 32768;
    config.max_uri_handlers = 8;
    // Allow a slower client to receive the JPEG body (VGA ~11 KB at 12 quality)
    config.recv_wait_timeout = 10;
    config.send_wait_timeout = 10;

    httpd_handle_t server = NULL;
    if (httpd_start(&server, &config) != ESP_OK) {
        Serial.println("[cam] FATAL: failed to start HTTP server");
        return;
    }

    const httpd_uri_t uris[] = {
        { .uri = "/",        .method = HTTP_GET, .handler = index_handler,   .user_ctx = NULL },
        { .uri = "/capture", .method = HTTP_GET, .handler = capture_handler, .user_ctx = NULL },
        { .uri = "/stream",  .method = HTTP_GET, .handler = stream_handler,  .user_ctx = NULL },
    };

    for (size_t i = 0; i < sizeof(uris) / sizeof(uris[0]); i++) {
        if (httpd_register_uri_handler(server, &uris[i]) != ESP_OK) {
            Serial.printf("[cam] Failed to register URI handler %d\n", (int)i);
        }
    }

    Serial.println("========================================");
    Serial.println("[cam] HTTP server started on port 80");
    Serial.println("[cam] GET /         — status page");
    Serial.println("[cam] GET /capture  — still JPEG");
    Serial.println("[cam] GET /stream   — MJPEG stream");
    Serial.println("========================================");
}
