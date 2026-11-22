// DurianCare — HTTP/HTTPS + đồng hồ UTC (dùng chung 2 sketch). Không bao giờ in header (khóa/token) ra log.
#include "dc_http.h"
#include "duriancare_config.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <sys/time.h>
#include <time.h>

namespace dc {

void timeBegin() {
  // configTime() khởi động lại SNTP: chỉ gọi một lần mỗi lần Wi-Fi kết nối (gọi lặp làm hủy lượt gửi lại).
  configTime(0, 0, DC_NTP_SERVER_1, DC_NTP_SERVER_2, DC_NTP_SERVER_3);  // UTC
}

bool timeValid() { return time(nullptr) >= (time_t)DC_MIN_VALID_EPOCH; }

int64_t epochMillis() {
  struct timeval tv;
  gettimeofday(&tv, nullptr);
  return (int64_t)tv.tv_sec * 1000LL + (int64_t)(tv.tv_usec / 1000);
}

bool timeSetFromHttpDate(const String &date) {
  struct tm tm = {};
  if (date.length() == 0 || !strptime(date.c_str(), "%a, %d %b %Y %H:%M:%S GMT", &tm)) return false;
  time_t t = mktime(&tm);  // TZ = UTC (configTime(0, 0, ...))
  if (t < (time_t)DC_MIN_VALID_EPOCH) return false;
  struct timeval tv = {t, 0};
  settimeofday(&tv, nullptr);
  return true;
}

String errorText(int code) {
  if (code == DC_HTTP_BEGIN_FAILED) return "HTTP_BEGIN_FAILED";
  if (code == DC_HTTP_TIME_NOT_SYNCED) return "TIME_NOT_SYNCED (HTTPS cần giờ UTC đúng)";
  if (code > 0) return String("HTTP ") + code;
  return HTTPClient::errorToString(code);
}

static String originOf(const String &url) {
  int start = url.indexOf("://");
  if (start < 0) return url;
  int slash = url.indexOf('/', start + 3);
  return slash < 0 ? url : url.substring(0, slash);
}

void HttpChannel::close() {
  if (http_) { static_cast<HTTPClient *>(http_)->end(); delete static_cast<HTTPClient *>(http_); http_ = nullptr; }
  if (plain_) { static_cast<WiFiClient *>(plain_)->stop(); delete static_cast<WiFiClient *>(plain_); plain_ = nullptr; }
  if (secure_) { static_cast<WiFiClientSecure *>(secure_)->stop(); delete static_cast<WiFiClientSecure *>(secure_); secure_ = nullptr; }
  origin_ = "";
}

HttpResponse HttpChannel::send(const HttpRequest &request) {
  HttpResponse response;
  bool https = request.url.startsWith("https://");
  if (https && !timeValid()) { response.code = DC_HTTP_TIME_NOT_SYNCED; return response; }
  String origin = originOf(request.url);
  if (!request.keepAlive || origin != origin_) close();
  if (!http_) http_ = new HTTPClient();
  HTTPClient &http = *static_cast<HTTPClient *>(http_);
  WiFiClient *client;
  if (https) {
    if (!secure_) {
      WiFiClientSecure *secure = new WiFiClientSecure();
      secure->useBuiltinCACertBundle();  // bundle CA của core; KHÔNG setInsecure()
      secure_ = secure;
    }
    client = static_cast<WiFiClientSecure *>(secure_);
  } else {
    if (!plain_) plain_ = new WiFiClient();
    client = static_cast<WiFiClient *>(plain_);
  }
  origin_ = origin;
  http.setReuse(request.keepAlive);
  http.setFollowRedirects(HTTPC_DISABLE_FOLLOW_REDIRECTS);
  http.setConnectTimeout(request.connectTimeoutMs);
  http.setTimeout(request.responseTimeoutMs);
  if (!http.begin(*client, request.url)) {
    response.code = DC_HTTP_BEGIN_FAILED;
    close();
    return response;
  }
  if (request.collectDate) {
    static const char *keys[] = {"Date"};
    http.collectHeaders(keys, 1);
  }
  if (request.contentType) http.addHeader("Content-Type", request.contentType);
  for (size_t i = 0; i < request.headerCount; i++) {
    if (request.headers[i].value && request.headers[i].value[0]) http.addHeader(request.headers[i].name, request.headers[i].value);
  }
  unsigned long started = millis();
  response.code = http.sendRequest(request.method, const_cast<uint8_t *>(request.body), request.bodyLen);
  response.elapsedMs = millis() - started;
  if (response.code > 0) {
    if (request.collectDate) response.date = http.header("Date");
    int size = http.getSize();
    // Thân phản hồi: chỉ đọc khi biết kích thước và đủ nhỏ, hoặc chunked (đọc có giới hạn qua getString).
    if (size > 0 && (size_t)size <= request.maxBody) response.body = http.getString();
    else if (size < 0 && response.code != 204) {
      String body = http.getString();
      if (body.length() <= request.maxBody) response.body = body;
    }
  }
  if (!request.keepAlive || response.code <= 0) close();
  else http.end();  // giữ socket nếu server cho phép keep-alive
  return response;
}

}  // namespace dc
