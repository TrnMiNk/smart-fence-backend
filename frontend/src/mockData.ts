// Dữ liệu giả định ban đầu cho hệ thống Smart Fence
import { FenceZone, Device, EventItem, SensitivityConfig, ChartPoint } from './types';

// Danh sách 1 đoạn hàng rào A
export const initialZones: FenceZone[] = [
  { id: 'A', nameKey: 'zone_a', deviceId: 'LASER-A01' },
];

// Danh sách thiết bị phần cứng IoT
export const initialDevices: Device[] = [
  {
    id: 'LASER-A01',
    nameKey: 'device_type_laser',
    type: 'laser',
    location: 'A',
    isOnline: true,
    lastValue: '100%',
    lastUpdated: '12:00:00',
    laserDetails: {
      transmitterOnline: true,
      receiverOnline: true,
      beamStatus: 'clear',
      signalPercent: 100,
    },
  },
  {
    id: 'HUMIDITY-01',
    nameKey: 'device_type_humidity',
    type: 'humidity',
    location: 'A',
    isOnline: true,
    lastValue: '68%',
    lastUpdated: '12:00:00',
  },
  {
    id: 'SIREN-A01',
    nameKey: 'device_type_siren',
    type: 'siren',
    location: 'A',
    isOnline: true,
    lastValue: 'siren_state_off',
    lastUpdated: '12:00:00',
    sirenOn: false,
  },

  {
    id: 'ESP32-GATEWAY-01',
    nameKey: 'device_type_gateway',
    type: 'gateway',
    location: 'gateway',
    isOnline: true,
    lastValue: 'Mesh (5)',
    lastUpdated: '12:00:00',
  },
];

// 8 sự kiện lịch sử ban đầu với đủ mức độ và trạng thái (chỉ Đoạn A, gateway hoặc system)
export const initialEvents: EventItem[] = [
  {
    id: 'EV-1008',
    time: '11:45:12',
    location: 'A',
    type: 'vibration',
    severity: 'warning',
    status: 'unresolved',
  },
  {
    id: 'EV-1007',
    time: '11:20:05',
    location: 'A',
    type: 'laser_break',
    severity: 'danger',
    status: 'resolved',
  },
  {
    id: 'EV-1006',
    time: '10:55:40',
    location: 'system',
    type: 'high_humidity',
    severity: 'warning',
    status: 'resolved',
  },
  {
    id: 'EV-1005',
    time: '10:15:22',
    location: 'A',
    type: 'high_humidity',
    severity: 'info',
    status: 'resolved',
  },
  {
    id: 'EV-1004',
    time: '09:40:10',
    location: 'A',
    type: 'laser_break',
    severity: 'danger',
    status: 'false_alarm',
  },
  {
    id: 'EV-1003',
    time: '09:12:00',
    location: 'A',
    type: 'sensor_disconnected',
    severity: 'warning',
    status: 'resolved',
  },
  {
    id: 'EV-1002',
    time: '08:30:15',
    location: 'A',
    type: 'manual_test',
    severity: 'info',
    status: 'resolved',
  },
  {
    id: 'EV-1001',
    time: '08:00:00',
    location: 'system',
    type: 'manual_test',
    severity: 'info',
    status: 'resolved',
  },
];

// Cấu hình mẫu cho 4 chế độ độ nhạy
export const presetSensitivityConfigs: Record<'normal' | 'strong_wind' | 'leaf_fall', Omit<SensitivityConfig, 'mode'>> = {
  normal: {
    laserSensitivity: 80,
    confirmCount: 3,
    confirmWindow: 2,
    humidityThreshold: 85,
  },
  strong_wind: {
    laserSensitivity: 60,
    confirmCount: 5,
    confirmWindow: 3,
    humidityThreshold: 85,
  },
  leaf_fall: {
    laserSensitivity: 50,
    confirmCount: 6,
    confirmWindow: 5,
    humidityThreshold: 85,
  },
};

// Cấu hình độ nhạy mặc định ban đầu
export const defaultSensitivityConfig: SensitivityConfig = {
  mode: 'normal',
  ...presetSensitivityConfigs.normal,
};

// Khởi tạo 30 điểm biểu đồ ban đầu
export const generateInitialChartData = (): ChartPoint[] => {
  const points: ChartPoint[] = [];
  const now = new Date();
  for (let i = 29; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 2000);
    const timeStr = d.toTimeString().split(' ')[0];
    points.push({
      time: timeStr,
      humidity: Math.round(68 + Math.sin(i / 3) * 2),
    });
  }
  return points;
};
