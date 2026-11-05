// Các kiểu dữ liệu chính cho hệ thống Smart Fence

export type Language = 'vi' | 'en';

export type PageId = 'overview' | 'fence' | 'sensitivity' | 'alerts' | 'devices';

// Trạng thái an ninh toàn hệ thống và từng đoạn
export type SecurityStatus = 'safe' | 'suspicious' | 'intrusion' | 'paused' | 'unknown';
export type FenceStatus = 'safe' | 'suspicious' | 'intrusion' | 'offline';

// Mức độ nghiêm trọng của sự kiện
export type Severity = 'info' | 'warning' | 'danger';

// Trạng thái xử lý sự kiện
export type EventStatus = 'unresolved' | 'resolved' | 'false_alarm';

// Loại sự kiện
export type EventType =
  | 'laser_break'
  | 'vibration'
  | 'rain_detected'
  | 'sensor_disconnected'
  | 'manual_test';

// Đoạn hàng rào (chỉ 1 đoạn A)
export type FenceZoneId = 'A';

export interface FenceZone {
  id: FenceZoneId;
  deviceId: string;
  nameKey: string;
}

// Kiểu thiết bị
export type DeviceType = 'laser' | 'rain_sensor' | 'siren' | 'camera';

export interface LaserDetails {
  transmitterOnline: boolean;
  receiverOnline: boolean;
  beamStatus: 'clear' | 'broken';
  signalPercent: number;
}

export interface Device {
  id: string;
  code?: string;
  displayName?: string;
  nameKey?: string;
  type: DeviceType;
  location: FenceZoneId | 'gateway' | 'unknown';
  isOnline: boolean;
  systemState?: 'ON' | 'OFF' | 'UNKNOWN';
  lastValue: string;
  lastUpdated: string;
  components?: DeviceComponent[];
  laserDetails?: LaserDetails;
  sirenOn?: boolean;
}

export interface DeviceComponent {
  id: string;
  code: string;
  name: string;
  type: string;
  status: string;
}

// Nhật ký sự kiện cảnh báo
export interface EventItem {
  id: string;
  time: string;
  location: FenceZoneId | 'gateway' | 'system';
  type: EventType;
  severity: Severity;
  status: EventStatus;
}

// Chế độ độ nhạy
export type SensitivityMode = 'normal' | 'strong_wind' | 'leaf_fall' | 'custom';

export interface SensitivityConfig {
  mode: SensitivityMode;
  laserSensitivity: number; // 10 - 100%
  confirmCount: number;     // 1 - 10 lần
  confirmWindow: number;    // 1, 2, 3, 5, 10 giây
}

// Điểm dữ liệu biểu đồ
// Thông báo Toast
export interface ToastInfo {
  id: string;
  messageKey: string;
  type: 'success' | 'error' | 'info';
}
