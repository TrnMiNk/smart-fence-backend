// Cấu hình mặc định phía frontend; không dùng để giả lập dữ liệu thiết bị hoặc telemetry.
import { FenceZone, SensitivityConfig } from './types';

// Danh sách 1 đoạn hàng rào A
export const initialZones: FenceZone[] = [
  { id: 'A', nameKey: 'zone_a', deviceId: 'LASER-A01' },
];

// Cấu hình mẫu cho 4 chế độ độ nhạy
export const presetSensitivityConfigs: Record<'normal' | 'strong_wind' | 'leaf_fall', Omit<SensitivityConfig, 'mode'>> = {
  normal: {
    laserSensitivity: 80,
    confirmCount: 3,
    confirmWindow: 2,
  },
  strong_wind: {
    laserSensitivity: 60,
    confirmCount: 5,
    confirmWindow: 3,
  },
  leaf_fall: {
    laserSensitivity: 50,
    confirmCount: 6,
    confirmWindow: 5,
  },
};

// Cấu hình độ nhạy mặc định ban đầu
export const defaultSensitivityConfig: SensitivityConfig = {
  mode: 'normal',
  ...presetSensitivityConfigs.normal,
};
