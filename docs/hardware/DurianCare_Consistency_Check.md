# Đối chiếu Wiring ↔ Schematic ↔ Firmware

> Tệp này do `generate_hardware_docs.py` sinh ra mỗi lần chạy. Wiring và schematic vẽ từ cùng một netlist (`CONNECTIONS`); bước kiểm tra bên dưới xác nhận mỗi dây có mặt trên wiring, các nhãn chân/net tương ứng có mặt trên schematic, và GPIO trong tài liệu khớp với `#define` trong firmware.

## 1. Từng dây

| Dây | Từ | Đến | Net | Wiring | Schematic | Trạng thái phần cứng |
|---|---|---|---|---|---|---|
| W1 | U1 3V3 | J1 chung | `+3V3` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| W2 | J1 chung | S1 VCC (+) | `+3V3` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| W3 | J1 chung | S2 VCC | `+3V3` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| W4 | U1 GND (phải) | S1 GND (−) | `GND` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| W5 | U1 GND (trái) | S2 GND | `GND` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| W6 | U1 D18 / GPIO18 | S1 DATA (OUT) | `DHT_DATA` | có | có | đã xác minh (firmware) |
| W7 | U1 D34 / GPIO34 | S2 AO | `SOIL_AO` | có | có | đã xác minh (firmware) |
| W8 | S2 Đầu dò +/− | P1 2 cực | `PROBE` | có | có | CẦN XÁC NHẬN |
| W9 | PS1 USB 5 V | U1 micro-USB | `+5V_USB` | có | có | CẦN XÁC NHẬN |
| C1 | J2 5V | U2 5V | `+5V` | có | có | CẦN XÁC NHẬN |
| C2 | J2 GND | U2 GND (cạnh 5V) | `GND` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| C3 | J2 TXD | U2 U0R / GPIO3 | `UART_RX` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| C4 | J2 RXD | U2 U0T / GPIO1 | `UART_TX` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| C5 | U2 IO0 / GPIO0 | U2 GND (cạnh IO0) | `BOOT` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |
| C6 | PS2 +5V | U2 5V | `+5V` | có | có | CẦN XÁC NHẬN |
| C7 | PS2 GND | U2 GND (cạnh 5V) | `GND` | có | có | bắt buộc về điện, cách nối chưa kiểm tra |

## 2. GPIO so với firmware

| Nguồn | Hằng số | Tài liệu | Firmware | Kết quả |
|---|---|---|---|---|
| sketch_jul25a.ino | `DHTPIN` | 18 | 18 | khớp |
| sketch_jul25a.ino | `SOIL_PIN` | 34 | 34 | khớp |
| camera_pins.h (AI_THINKER) | `PWDN_GPIO_NUM` | 32 | 32 | khớp |
| camera_pins.h (AI_THINKER) | `RESET_GPIO_NUM` | -1 | -1 | khớp |
| camera_pins.h (AI_THINKER) | `XCLK_GPIO_NUM` | 0 | 0 | khớp |
| camera_pins.h (AI_THINKER) | `SIOD_GPIO_NUM` | 26 | 26 | khớp |
| camera_pins.h (AI_THINKER) | `SIOC_GPIO_NUM` | 27 | 27 | khớp |
| camera_pins.h (AI_THINKER) | `Y9_GPIO_NUM` | 35 | 35 | khớp |
| camera_pins.h (AI_THINKER) | `Y8_GPIO_NUM` | 34 | 34 | khớp |
| camera_pins.h (AI_THINKER) | `Y7_GPIO_NUM` | 39 | 39 | khớp |
| camera_pins.h (AI_THINKER) | `Y6_GPIO_NUM` | 36 | 36 | khớp |
| camera_pins.h (AI_THINKER) | `Y5_GPIO_NUM` | 21 | 21 | khớp |
| camera_pins.h (AI_THINKER) | `Y4_GPIO_NUM` | 19 | 19 | khớp |
| camera_pins.h (AI_THINKER) | `Y3_GPIO_NUM` | 18 | 18 | khớp |
| camera_pins.h (AI_THINKER) | `Y2_GPIO_NUM` | 5 | 5 | khớp |
| camera_pins.h (AI_THINKER) | `VSYNC_GPIO_NUM` | 25 | 25 | khớp |
| camera_pins.h (AI_THINKER) | `HREF_GPIO_NUM` | 23 | 23 | khớp |
| camera_pins.h (AI_THINKER) | `PCLK_GPIO_NUM` | 22 | 22 | khớp |
| camera_pins.h (AI_THINKER) | `LED_GPIO_NUM` | 4 | 4 | khớp |

## 3. Bố cục

- Dây giao nhau trên wiring diagram: không có (kiểm tra tự động trên các đoạn dây vuông góc).
