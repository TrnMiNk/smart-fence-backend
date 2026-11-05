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
- Theo dõi trạng thái mưa và giá trị ADC thô từ cảm biến YL-83 (AO + DO).
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
                            │ YL-83       │
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

Khởi động MySQL Server 8.x.

#### 2.1. Khởi tạo Database

File cấu trúc database được lưu tại:

```text
database/schema.sql
```

File này chứa câu lệnh tạo database `smart_fence_iot` và cấu trúc của 6 bảng:

- `devices`
- `device_components`
- `device_commands`
- `intrusion_events`
- `sensor_readings`
- `cameras`

Có thể import bằng MySQL Workbench:

1. Mở MySQL Workbench và kết nối MySQL Server.
2. Chọn Server → Data Import.
3. Chọn Import from Self-Contained File.
4. Chọn file `database/schema.sql` trong thư mục dự án.
5. Nhấn Start Import.
6. Kiểm tra database `smart_fence_iot` đã xuất hiện và có đủ 6 bảng.

Lưu ý: File `schema.sql` dùng để khởi tạo database mới. Không chạy lại trên database đã tồn tại đầy đủ các bảng vì các câu lệnh `CREATE TABLE` có thể bị lỗi trùng bảng. File chỉ chứa cấu trúc, không chứa dữ liệu thiết bị hoặc dữ liệu mẫu.

#### 2.2. DataSource configuration

Configure the datasource through Spring Boot environment variables in the same terminal used to start Backend. This is the supported run configuration; `application-example.properties` is a reference file and is not loaded automatically.

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/smart_fence_iot"
$env:SPRING_DATASOURCE_USERNAME="<MySQL username>"
$env:SPRING_DATASOURCE_PASSWORD="<MySQL password>"
```

Provide all three values explicitly, especially outside local development. Hibernate uses `ddl-auto=none`; Backend will not create or alter the database schema. Do not put real credentials in project files.

#### 2.3. Migration

See `database/MIGRATION_DHT22_TO_YL83.md`. The migration was performed manually on the current database; application startup does not run it. Preserve historical `sensor_readings` rows.

### Bước 3: Cấu hình MQTT Broker

Cài đặt và khởi động Eclipse Mosquitto. Backend MQTT uses these environment variables (host must be reachable by Backend):

```powershell
$env:MQTT_HOST="<broker-host>"
$env:MQTT_PORT="1883"
$env:MQTT_USERNAME="<broker-username>" # empty if authentication is disabled
$env:MQTT_PASSWORD="<broker-password>" # empty if authentication is disabled
$env:MQTT_CLIENT_ID="smart-fence-backend"
```

Use `MQTT_HOST=127.0.0.1` when the backend runs on the same machine as a local broker. If the backend runs in Docker and the broker runs on the Windows host, use `MQTT_HOST=host.docker.internal`; if both containers share a Docker network, use the broker's service/container DNS name. The backend does not assume a broker address in its application configuration.

Backend starts when the broker is unavailable. A single background connection manager retries with exponential backoff, and retries failed topic subscriptions independently. Publishing while disconnected returns an error to the calling API.

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

`contextLoads` uses the `test` profile and requires a dedicated MySQL test database. Set its credentials before running tests:

```powershell
$env:TEST_DB_URL="jdbc:mysql://localhost:3306/smart_fence_iot_test"
$env:TEST_DB_USERNAME="<test database username>"
$env:TEST_DB_PASSWORD="<test database password>"
```

The test profile disables MQTT and ignores `MQTT_HOST`, `MQTT_USERNAME`, and other production broker settings. Use a dedicated test database, never a production database. `ddl-auto=none` means the application will not create or alter its schema. Missing test database variables fail with an explicit `TEST_DB_*` configuration error; `contextLoads` remains enabled.

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
