// DurianCare — HTTP/HTTPS client và đồng hồ UTC (dùng chung 2 sketch).
//   http://  -> WiFiClient (LAN, beacon)
//   https:// -> WiFiClientSecure + bundle CA có sẵn của core (useBuiltinCACertBundle). KHÔNG setInsecure().
// HTTPS cần giờ đúng để kiểm tra chứng chỉ: chưa có giờ hợp lệ thì không gọi HTTPS (DC_HTTP_TIME_NOT_SYNCED).
#ifndef DC_HTTP_H
#define DC_HTTP_H
#include <Arduino.h>

namespace dc {

// Mã lỗi nội bộ (<= 0 = lỗi transport, giống HTTPClient)
#define DC_HTTP_BEGIN_FAILED     (-100)
#define DC_HTTP_TIME_NOT_SYNCED  (-101)

struct HttpHeader { const char *name; const char *value; };

struct HttpResponse {
  int code = 0;           // HTTP status (> 0) hoặc lỗi transport (<= 0)
  String body;            // chỉ đọc khi <= maxBody byte
  String date;            // header Date (khi collectDate)
  unsigned long elapsedMs = 0;
};

struct HttpRequest {
  const char *method = "GET";
  String url;
  const char *contentType = nullptr;
  const uint8_t *body = nullptr;
  size_t bodyLen = 0;
  const HttpHeader *headers = nullptr;
  size_t headerCount = 0;
  uint32_t connectTimeoutMs = 8000;
  uint16_t responseTimeoutMs = 10000;
  size_t maxBody = 1024;
  bool collectDate = false;
  bool keepAlive = false;  // dùng lại kết nối (poll lệnh qua HTTPS: tránh bắt tay TLS mỗi 5 s)
};

// Một "kênh" HTTP: giữ client riêng để có thể keep-alive. Mỗi kênh chỉ dùng trong loop() (không đa luồng).
class HttpChannel {
 public:
  HttpResponse send(const HttpRequest &request);
  void close();
 private:
  void *plain_ = nullptr;
  void *secure_ = nullptr;
  void *http_ = nullptr;
  String origin_;
};

void timeBegin();                 // SNTP (UTC). Gọi một lần sau mỗi lần Wi-Fi kết nối.
bool timeValid();
int64_t epochMillis();
bool timeSetFromHttpDate(const String &date);  // dự phòng: header Date của Backend (độ chính xác giây)
String errorText(int code);

}  // namespace dc

#endif
