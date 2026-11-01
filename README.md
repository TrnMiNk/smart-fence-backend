# Smart Fence IoT – Hệ thống hàng rào an ninh thông minh

Hệ thống hàng rào an ninh thông minh tích hợp tia laser và cảnh báo trực tuyến qua IoT. Hệ thống sử dụng ESP32 để giám sát trạng thái hàng rào, phát hiện xâm nhập và gửi dữ liệu về Backend thông qua MQTT.

## 1. Giới thiệu

Smart Fence IoT là hệ thống hỗ trợ giám sát và cảnh báo xâm nhập tại khu vực cần bảo vệ.

Các chức năng chính:

- Giám sát trạng thái thiết bị ESP32.
- Phát hiện xâm nhập thông qua cảm biến laser.
- Gửi dữ liệu cảm biến và sự kiện về máy chủ qua MQTT.
- Quản lý trạng thái hệ thống và gửi lệnh bật/tắt từ xa.
- Hiển thị dữ liệu giám sát trên giao diện Web.
- Theo dõi độ ẩm môi trường thông qua cảm biến DHT22.
- Hiển thị hình ảnh camera ESP32-CAM thông qua luồng MJPEG.
- Lưu trữ thông tin thiết bị, dữ liệu cảm biến và sự kiện trong MySQL.

## 2. Kiến trúc hệ thống

```text
                 ┌─────────────────────┐
                 │     Frontend        │
                 │    React + Vite     │
                 └──────────┬──────────┘
                            │ HTTP REST API
                            ▼
                 ┌─────────────────────┐
                 │      Backend        │
                 │    Spring Boot      │
                 └──────┬────────┬─────┘
                        │        │
                  JDBC  │        │ MQTT
                        ▼        ▼
                ┌──────────┐  ┌──────────┐
                │  MySQL   │  │ Mosquitto│
                │ Database │  │  Broker  │
                └──────────┘  └────┬─────┘
                                   │ MQTT
                                   ▼
                            ┌─────────────┐
                            │    ESP32    │
                            ├─────────────┤
                            │ Laser       │
                            │ DHT22       │
                            │ Siren       │
                            │ ESP32-CAM   │
                            └─────────────┘
```

### Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Frontend | ReactJS, TypeScript, Vite |
| Backend | Java 25, Spring Boot 4 |
| Database | MySQL 8 |
| ORM | Spring Data JPA, Hibernate |
| Giao tiếp IoT | MQTT |
| MQTT Broker | Eclipse Mosquitto |
| Camera | ESP32-CAM, MJPEG |
| Quản lý mã nguồn | Git, GitHub |

## 3. Cấu trúc thư mục

```text
smart-fence-iot/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   ├── build.gradle
│   ├── gradlew
│   ├── gradlew.bat
│   └── application-example.properties
│
├── frontend/
│   ├── public/
│   ├── src/
│   ├── package.json
│   ├── package-lock.json
│   ├── vite.config.ts
│   └── .env.example
│
├── .gitignore
└── README.md
```

## 4. Yêu cầu môi trường

Trước khi chạy dự án, cần cài đặt:

- Git.
- Java Development Kit (JDK) 25.
- MySQL Server 8.x.
- Node.js và npm.
- Eclipse Mosquitto MQTT Broker.

Kiểm tra môi trường:

```bash
java -version
node -v
npm -v
git --version
```

## 5. Cài đặt dự án

### Bước 1: Clone repository

```bash
git clone https://github.com/TrnMiNk/smart-fence-backend.git
```

Di chuyển vào thư mục dự án:

```bash
cd smart-fence-backend
```

Repository hiện chứa cả Backend và Frontend trong cùng một dự án.

### Bước 2: Cấu hình Database

Khởi động MySQL Server và tạo database:

```sql
CREATE DATABASE smart_fence_iot
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

Tiếp theo, cấu hình thông tin kết nối MySQL trong:

```text
backend/src/main/resources/application.properties
```

Có thể tham khảo cấu trúc cấu hình trong `application-example.properties`.

Điền thông tin kết nối phù hợp với máy tính cá nhân:

- Database URL.
- Database username.
- Database password.

Không đưa mật khẩu hoặc thông tin bí mật cá nhân lên GitHub.

Lưu ý: Một số thay đổi cấu trúc database của chức năng bật/tắt hệ thống đang có file migration thủ công tại:

```text
backend/src/main/resources/db/manual-migration/
```

Migration này chưa được xác nhận đã chạy. Cần kiểm tra và thực hiện migration theo đúng hướng dẫn trước khi sử dụng các chức năng phụ thuộc vào cấu trúc database mới.

### Bước 3: Cấu hình MQTT Broker

Cài đặt và khởi động Eclipse Mosquitto.

Backend cần được cấu hình để kết nối tới MQTT Broker đang hoạt động.

Thông tin cần kiểm tra:

- MQTT Broker Host.
- MQTT Broker Port.
- Username và Password nếu Broker có bật xác thực.

ESP32 và Backend phải kết nối được tới cùng MQTT Broker.

Các topic MQTT sử dụng trong hệ thống:

| Topic | Chức năng |
|---|---|
| `smart-fence/+/telemetry` | Nhận dữ liệu cảm biến |
| `smart-fence/+/events` | Nhận sự kiện xâm nhập |
| `smart-fence/+/command-status` | Nhận trạng thái lệnh |
| `smart-fence/{deviceCode}/commands` | Gửi lệnh điều khiển |

Ví dụ payload gửi lệnh:

```json
{
  "commandId": 123,
  "command": "SYSTEM_OFF"
}
```

Ví dụ payload phản hồi ACK:

```json
{
  "commandId": 123,
  "commandType": "SYSTEM_OFF",
  "status": "SUCCESS",
  "systemState": "OFF"
}
```

Các giá trị lệnh hệ thống:

- `SYSTEM_ON`: Bật chức năng giám sát.
- `SYSTEM_OFF`: Tắt chức năng giám sát.

Lưu ý: Tắt hệ thống bằng lệnh từ Web không đồng nghĩa với việc ngắt nguồn điện ESP32. Vi điều khiển cần tiếp tục hoạt động để có thể nhận lệnh bật lại.

### Bước 4: Chạy Backend

Mở terminal tại thư mục `backend`:

```bash
cd backend
```

Chạy ứng dụng:

Windows:

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```bash
./gradlew bootRun
```

Backend mặc định chạy tại:

```text
http://localhost:8080
```

Kiểm tra Backend:

```text
GET http://localhost:8080/api/health
```

Nếu hoạt động bình thường, API trả về:

```text
Smart Fence Backend is running!
```

### Bước 5: Chạy Frontend

Mở terminal mới tại thư mục gốc dự án:

```bash
cd frontend
```

Cài đặt các thư viện:

```bash
npm install
```

Tạo file `.env.development` với nội dung:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Khởi động Frontend:

```bash
npm run dev
```

Truy cập giao diện:

```text
http://localhost:5173
```

## 6. Luồng hoạt động

### 6.1. Giám sát và phát hiện xâm nhập

1. ESP32 nhận dữ liệu từ các cảm biến.
2. Khi phát hiện tia laser bị gián đoạn, ESP32 xử lý sự kiện.
3. ESP32 kích hoạt cảnh báo tại chỗ theo logic firmware.
4. ESP32 gửi sự kiện lên MQTT Broker.
5. Backend nhận dữ liệu MQTT và xử lý.
6. Thông tin được lưu vào MySQL.
7. Frontend lấy dữ liệu từ Backend để hiển thị.

### 6.2. Điều khiển bật/tắt hệ thống

1. Người dùng thao tác trên giao diện Web.
2. Frontend gửi yêu cầu tới Backend.
3. Backend kiểm tra trạng thái thiết bị và tạo lệnh điều khiển.
4. Backend gửi lệnh qua MQTT.
5. ESP32 nhận và xử lý lệnh.
6. ESP32 gửi ACK về Backend.
7. Backend xác nhận kết quả và cập nhật trạng thái hệ thống.
8. Frontend tải lại trạng thái mới.

### 6.3. Camera

ESP32-CAM cung cấp luồng hình ảnh MJPEG qua HTTP. Backend cung cấp thông tin URL luồng camera cho Frontend sử dụng để hiển thị.

## 7. Một số API chính

Backend cung cấp REST API phục vụ giao tiếp với Frontend.

| API | Phương thức | Chức năng |
|---|---|---|
| `/api/health` | GET | Kiểm tra Backend |
| `/api/devices` | GET | Lấy danh sách thiết bị |
| `/api/device-commands/device/{deviceId}/system` | POST | Gửi lệnh bật/tắt hệ thống |
| `/api/device-commands/device/{deviceId}` | GET | Lấy lịch sử lệnh của thiết bị |
| `/api/cameras/component/{componentId}` | GET | Lấy thông tin camera theo component |

Các API khác được triển khai theo từng module trong Backend.

## 8. Kiểm thử

### Backend

Chạy kiểm tra biên dịch:

```bash
.\gradlew.bat compileJava
```

Chạy Unit Test:

```bash
.\gradlew.bat test
```

### Frontend

Kiểm tra TypeScript:

```bash
npx tsc -b --pretty false
```

Build Frontend:

```bash
npm run build
```

Lưu ý: Kiểm thử biên dịch và Unit Test không thay thế việc kiểm thử tích hợp với thiết bị IoT thực tế.

## 9. Lưu ý khi phát triển

- Không commit file `application.properties` chứa thông tin đăng nhập cá nhân.
- Không commit file `.env.development`.
- Không commit thư mục `node_modules`, `build` hoặc `dist`.
- Không tự ý thay đổi cấu trúc database nếu chưa thống nhất với nhóm.
- Khi thay đổi API Backend, cần kiểm tra ảnh hưởng tới Frontend.
- Khi thay đổi MQTT topic hoặc payload, cần đồng bộ giữa Backend và firmware ESP32.
- Chỉ xác nhận chức năng phần cứng hoạt động sau khi kiểm thử trực tiếp trên thiết bị.

## 10. Thành viên và đóng góp

Dự án được phát triển theo hình thức làm việc nhóm.

Các thành viên phối hợp triển khai:

- Backend và quản lý dữ liệu.
- Frontend và giao diện giám sát.
- Firmware ESP32 và tích hợp phần cứng.
- Kiểm thử và hoàn thiện hệ thống.

---

Smart Fence IoT – Hệ thống hàng rào an ninh thông minh tích hợp IoT.