#ifndef SENSOR_TYPES_H
#define SENSOR_TYPES_H

enum NetState {
  BOOT, WIFI_CONNECTING, WIFI_CONNECTED, DISCOVERING_SERVER, SERVER_DISCOVERED, RUNNING,
  WIFI_DISCONNECTED, RECONNECTING, SERVER_NOT_DISCOVERED, DISCOVERY_RETRY
};

// Đọc cảm biến đất: raw riêng, trạng thái riêng.
struct SoilReading {
  int raw;           // trung bình các mẫu
  int spread;        // max - min
  const char *status;  // OK | UNCALIBRATED | NO_SIGNAL | SATURATED | UNSTABLE
  int percent;       // chỉ có nghĩa khi status == "OK"
};

#endif
