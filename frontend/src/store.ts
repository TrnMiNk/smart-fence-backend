// Store quản lý toàn bộ dữ liệu, bộ đếm thời gian thực và hành vi của ứng dụng
import React, { createContext, useContext, useState, useEffect, useMemo } from 'react';
import {
  Language,
  PageId,
  SecurityStatus,
  FenceStatus,
  FenceZoneId,
  FenceZone,
  Device,
  SensitivityConfig,
  ToastInfo,
} from './types';
import {
  initialZones,
  defaultSensitivityConfig,
  presetSensitivityConfigs,
} from './mockData';
import { getDevices, setSirenState } from './services/deviceService';
import { setLanguage as setI18nLanguage, getLanguage } from './i18n';
import { BackendIntrusionEvent, getIntrusionEvents } from './services/overviewService';

const SENSITIVITY_STORAGE_KEY = 'smart_fence_sensitivity';
const validSensitivityConfig = (value: unknown): value is SensitivityConfig => {
  if (!value || typeof value !== 'object') return false;
  const config = value as Partial<SensitivityConfig>;
  return Number.isInteger(config.laserSensitivity) && config.laserSensitivity! >= 10 && config.laserSensitivity! <= 100 &&
    Number.isInteger(config.confirmCount) && config.confirmCount! >= 1 && config.confirmCount! <= 10 &&
    [1, 2, 3, 5, 10].includes(config.confirmWindow!);
};

const normalizeSensitivityMode = (config: SensitivityConfig): SensitivityConfig => {
  const preset = (['normal', 'strong_wind', 'leaf_fall'] as const).find((mode) => {
    const values = presetSensitivityConfigs[mode];
    return config.laserSensitivity === values.laserSensitivity && config.confirmCount === values.confirmCount &&
      config.confirmWindow === values.confirmWindow;
  });
  return {
    mode: preset ?? 'custom',
    laserSensitivity: config.laserSensitivity,
    confirmCount: config.confirmCount,
    confirmWindow: config.confirmWindow,
  };
};

interface StoreContextValue {
  // Điều hướng & Cài đặt
  page: PageId;
  setPage: (page: PageId) => void;
  language: Language;
  changeLanguage: (lang: Language) => void;
  isSettingsOpen: boolean;
  setIsSettingsOpen: (open: boolean) => void;

  // Giám sát
  currentTime: string;
  dangerBannerDismissed: boolean;
  dismissDangerBanner: () => void;

  // Môi trường

  // Hàng rào & Thiết bị
  zones: FenceZone[];
  selectedZoneId: FenceZoneId;
  setSelectedZoneId: (id: FenceZoneId) => void;
  devices: Device[];
  devicesLoading: boolean;
  devicesError: string | null;
  refreshDevices: () => Promise<void>;
  selectedDeviceId: string | null;
  setSelectedDeviceId: (id: string | null) => void;
  setSiren: (id: string, on: boolean) => Promise<void>;
  isSirenActive: boolean;

  // Cảnh báo & Sự kiện
  events: BackendIntrusionEvent[];
  eventsLoading: boolean;
  eventsError: string | null;
  updateEvent: (event: BackendIntrusionEvent) => void;

  // Cấu hình độ nhạy
  sensitivityConfig: SensitivityConfig;
  setSensitivityConfig: React.Dispatch<React.SetStateAction<SensitivityConfig>>;
  saveSensitivityConfig: (config: SensitivityConfig) => boolean;
  resetSensitivityConfig: () => boolean;

  // Toast
  toasts: ToastInfo[];
  showToast: (messageKey: string, type?: 'success' | 'error' | 'info') => void;
  removeToast: (id: string) => void;

  // Giá trị tính toán (Computed)
  securityStatus: SecurityStatus;
  zoneStatuses: Record<FenceZoneId, FenceStatus>;
  unresolvedCount: number;
  unresolvedDangerCount: number;
  activeDevicesCount: number;
  totalDevicesCount: number;
}

const StoreContext = createContext<StoreContextValue | null>(null);

// Hàm phụ trợ kẹp giá trị số trong khoảng [min, max]

// Lấy giờ hiện tại dạng HH:mm:ss
const getCurrentTimeStr = (): string => new Date().toTimeString().split(' ')[0];

export const StoreProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Trạng thái giao diện
  const [page, setPage] = useState<PageId>('overview');
  const [language, setLangState] = useState<Language>(getLanguage);
  const [isSettingsOpen, setIsSettingsOpen] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<string>(getCurrentTimeStr);
  const [dangerBannerDismissed, setDangerBannerDismissed] = useState<boolean>(false);

  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTime(getCurrentTimeStr()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  // Số liệu môi trường giả lập ban đầu

  // Danh sách đoạn hàng rào & thiết bị (đọc qua service)
  const [zones] = useState<FenceZone[]>(initialZones);
  const [selectedZoneId, setSelectedZoneId] = useState<FenceZoneId>('A');
  const [devices, setDevices] = useState<Device[]>([]);
  const [devicesLoading, setDevicesLoading] = useState<boolean>(true);
  const [devicesError, setDevicesError] = useState<string | null>(null);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | null>(null);

  const refreshDevices = async () => {
    const result = await getDevices();
    setDevices(result);
    setDevicesError(null);
  };

  useEffect(() => {
    let active = true;
    getDevices()
      .then((res) => { if (active) setDevices(res); })
      .catch((error: unknown) => {
        if (active) setDevicesError(error instanceof Error ? error.message : 'Không tải được danh sách thiết bị.');
      })
      .finally(() => { if (active) setDevicesLoading(false); });
    return () => { active = false; };
  }, []);

  // Alert state comes only from the backend; never seed it with mock events.
  const [events, setEvents] = useState<BackendIntrusionEvent[]>([]);
  const [eventsLoading, setEventsLoading] = useState(true);
  const [eventsError, setEventsError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getIntrusionEvents()
      .then((result) => { if (active) { setEvents(result); setEventsError(null); } })
      .catch((error: unknown) => { if (active) setEventsError(error instanceof Error ? error.message : 'Could not load events.'); })
      .finally(() => { if (active) setEventsLoading(false); });
    return () => { active = false; };
  }, []);

  const updateEvent = (updated: BackendIntrusionEvent) => setEvents((current) => current.map((event) => event.id === updated.id ? updated : event));

  // Cấu hình độ nhạy (lấy từ localStorage nếu có)
  const [sensitivityConfig, setSensitivityConfig] = useState<SensitivityConfig>(() => {
    try {
      const saved = localStorage.getItem(SENSITIVITY_STORAGE_KEY);
      if (saved) {
        const parsed: unknown = JSON.parse(saved);
        if (validSensitivityConfig(parsed)) return normalizeSensitivityMode(parsed);
      }
    } catch {
      // Bỏ qua dữ liệu hỏng hoặc storage không khả dụng, dùng mặc định.
    }
    return defaultSensitivityConfig;
  });

  // Quản lý Toast
  const [toasts, setToasts] = useState<ToastInfo[]>([]);

  // Hàm hiển thị Toast thông báo
  const showToast = (messageKey: string, type: 'success' | 'error' | 'info' = 'success') => {
    const id = Date.now().toString() + Math.random().toString().slice(2, 6);
    setToasts((prev) => [...prev, { id, messageKey, type }]);
    setTimeout(() => {
      removeToast(id);
    }, 3000);
  };

  const removeToast = (id: string) => {
    setToasts((prev) => prev.filter((item) => item.id !== id));
  };

  // Đổi ngôn ngữ
  const changeLanguage = (lang: Language) => {
    setI18nLanguage(lang);
    setLangState(lang);
  };

  // Tắt banner cảnh báo nguy hiểm
  const dismissDangerBanner = () => {
    setDangerBannerDismissed(true);
  };

  // Điều khiển bật/tắt còi báo động qua service
  const setSiren = async (id: string, on: boolean) => {
    const dev = devices.find((d) => d.id === id);
    if (!dev || !dev.isOnline) {
      return;
    }
    const buzzer = (dev.components ?? []).find((component) => component.type.trim().toUpperCase() === 'BUZZER');
    if (!buzzer) return;
    const result = await setSirenState(id, buzzer.id, on);
    if (result !== 'SUCCESS') return;
    setDevices((prev) =>
      prev.map((d) =>
        d.id === id
          ? {
              ...d,
              sirenOn: on,
              lastValue: on ? 'siren_state_on' : 'siren_state_off',
              lastUpdated: getCurrentTimeStr(),
            }
          : d
      )
    );
    showToast(on ? 'toast_siren_on' : 'toast_siren_off', 'info');
  };

  // Tạo sự kiện thử nghiệm tại hàng rào
  // Lưu cấu hình độ nhạy vào localStorage
  const saveSensitivityConfig = (cfg: SensitivityConfig): boolean => {
    if (!validSensitivityConfig(cfg)) {
      showToast('toast_invalid_sensitivity', 'error');
      return false;
    }
    const normalized = normalizeSensitivityMode(cfg);
    try {
      localStorage.setItem(SENSITIVITY_STORAGE_KEY, JSON.stringify(normalized));
      setSensitivityConfig(normalized);
      showToast('toast_saved_config', 'success');
      return true;
    } catch {
      showToast('toast_storage_error', 'error');
      return false;
    }
  };

  // Khôi phục cấu hình độ nhạy mặc định
  const resetSensitivityConfig = (): boolean => {
    try {
      localStorage.setItem(SENSITIVITY_STORAGE_KEY, JSON.stringify(defaultSensitivityConfig));
      setSensitivityConfig({ ...defaultSensitivityConfig });
      showToast('toast_reset_config', 'info');
      return true;
    } catch {
      showToast('toast_storage_error', 'error');
      return false;
    }
  };

  // =========================================================================
  // TIMER THỜI GIAN THỰC DUY NHẤT (Chu kỳ 2 giây)
  // GHI CHÚ QUAN TRỌNG: Đây là đoạn mô phỏng thời gian thực.
  // Khi kết nối hệ thống thật, thay thế khối setInterval này bằng MQTT client
  // (e.g., mqtt.js lắng nghe topic 'fence/+/telemetry') hoặc WebSocket / REST API.
  // =========================================================================


  // =========================================================================
  // TÍNH TOÁN TRẠNG THÁI HỆ THỐNG VÀ CÁC ĐOẠN HÀNG RÀO
  // =========================================================================
  const securityStatus = useMemo<SecurityStatus>(() => {
    if (eventsLoading || eventsError) return 'unknown';
    const pending = events.filter((event) => ['NEW', 'UNRESOLVED'].includes(event.eventStatus.trim().toUpperCase()));
    if (pending.some((event) => event.eventType.trim().toUpperCase() === 'LASER_BLOCKED')) return 'intrusion';
    return pending.length > 0 ? 'suspicious' : 'safe';
  }, [events, eventsLoading, eventsError]);

  const zoneStatuses = useMemo<Record<FenceZoneId, FenceStatus>>(() => {
    const statuses: Record<FenceZoneId, FenceStatus> = {
      A: 'safe',
    };

    const zone = zones[0];
    if (zone) {
      const dev = devices.find((d) => d.id === zone.deviceId);
      if (dev && !dev.isOnline) {
        statuses[zone.id] = 'offline';
        return statuses;
      }

      statuses[zone.id] = 'safe';
    }

    return statuses;
  }, [zones, devices, events]);

  const isSirenActive = useMemo(
    () => devices.find((d) => d.type === 'siren')?.sirenOn ?? false,
    [devices]
  );

  const unresolvedCount = useMemo(
    () => events.filter((ev) => ['NEW', 'UNRESOLVED'].includes(ev.eventStatus.trim().toUpperCase())).length,
    [events]
  );

  const unresolvedDangerCount = useMemo(
    () => events.filter((ev) => ['NEW', 'UNRESOLVED'].includes(ev.eventStatus.trim().toUpperCase()) && ev.eventType.trim().toUpperCase() === 'LASER_BLOCKED').length,
    [events]
  );

  const activeDevicesCount = useMemo(
    () => devices.filter((d) => d.isOnline).length,
    [devices]
  );

  const totalDevicesCount = devices.length;

  const value: StoreContextValue = {
    page,
    setPage,
    language,
    changeLanguage,
    isSettingsOpen,
    setIsSettingsOpen,
    currentTime,
    dangerBannerDismissed,
    dismissDangerBanner,
    zones,
    selectedZoneId,
    setSelectedZoneId,
    devices,
    devicesLoading,
    devicesError,
    refreshDevices,
    selectedDeviceId,
    setSelectedDeviceId,
    setSiren,
    isSirenActive,
    events,
    eventsLoading,
    eventsError,
    updateEvent,
    sensitivityConfig,
    setSensitivityConfig,
    saveSensitivityConfig,
    resetSensitivityConfig,
    toasts,
    showToast,
    removeToast,
    securityStatus,
    zoneStatuses,
    unresolvedCount,
    unresolvedDangerCount,
    activeDevicesCount,
    totalDevicesCount,
  };

  return React.createElement(StoreContext.Provider, { value }, children);
};

// Hook useStore để các trang truy cập trạng thái toàn cục
export const useStore = (): StoreContextValue => {
  const context = useContext(StoreContext);
  if (!context) {
    throw new Error('useStore must be used within a StoreProvider');
  }
  return context;
};
