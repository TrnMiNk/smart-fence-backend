import { Device, DeviceComponent, DeviceType } from '../types';
import { ApiError, apiRequest } from './apiClient';
import { t, TranslationKey } from '../i18n';

interface BackendDevice {
  id: number;
  deviceCode: string;
  deviceName: string;
  status: string;
  systemState?: string | null;
  lastSeen: string | null;
}

interface BackendComponent {
  id: number;
  deviceId: number;
  componentCode: string;
  componentName: string;
  componentType: string;
  status: string;
}

const canonicalComponentType = (value: string): DeviceComponent['type'] | null => {
  switch (value.trim().toUpperCase()) {
    case 'RAIN_SENSOR': return 'RAIN_SENSOR';
    case 'LASER':
    case 'LASER_SENSOR': return 'LASER';
    case 'BUZZER':
    case 'SIREN': return 'BUZZER';
    case 'CAMERA': return 'CAMERA';
    default: return null;
  }
};

const componentToFrontend = (component: BackendComponent): DeviceComponent | null => {
  const type = typeof component.componentType === 'string' ? canonicalComponentType(component.componentType) : null;
  if (!type || !Number.isInteger(component.id) || component.id <= 0 || component.deviceId <= 0 || !Number.isInteger(component.deviceId) || typeof component.componentCode !== 'string' || !component.componentCode.trim()) return null;
  const labelKey = `component_type_${type.toLowerCase()}` as TranslationKey;
  return {
    id: String(component.id),
    code: component.componentCode.trim(),
    name: t(labelKey),
    type,
    status: typeof component.status === 'string' && component.status.trim() ? component.status.trim() : '—',
  };
};

const typeForComponents = (components: DeviceComponent[]): DeviceType | null => {
  const types = components.map((component) => component.type.trim().toUpperCase());
  if (types.includes('RAIN_SENSOR')) return 'rain_sensor';
  if (types.includes('LASER')) return 'laser';
  if (types.includes('BUZZER')) return 'siren';
  if (types.includes('CAMERA')) return 'camera';
  return null;
};

const deviceToFrontend = (device: BackendDevice, components: DeviceComponent[]): Device | null => {
  const type = typeForComponents(components);
  if (!type) return null;
  const status = device.status.trim().toUpperCase();
  if (status !== 'ONLINE' && status !== 'OFFLINE') {
    throw new Error(`Trạng thái thiết bị không được hỗ trợ: ${device.status}`);
  }

  return {
    id: String(device.id),
    code: device.deviceCode,
    displayName: device.deviceName,
    type,
    location: 'unknown',
    isOnline: status === 'ONLINE',
    systemState: device.systemState == null ? 'UNKNOWN' : ['ON', 'OFF', 'UNKNOWN'].includes(device.systemState.trim().toUpperCase())
      ? device.systemState.trim().toUpperCase() as Device['systemState']
      : 'UNKNOWN',
    lastValue: '—',
    lastUpdated: device.lastSeen ? new Date(device.lastSeen).toLocaleString() : '—',
    components,
  };
};

export const getDevices = async (): Promise<Device[]> => {
  const backendDevices = await apiRequest<BackendDevice[]>('/api/devices');
  if (!Array.isArray(backendDevices)) throw new Error('Backend trả về danh sách thiết bị không hợp lệ.');

  return Promise.all(backendDevices.map(async (device) => {
    const backendComponents = await apiRequest<BackendComponent[]>(`/api/components/device/${device.id}`);
    if (!Array.isArray(backendComponents)) throw new Error(`Danh sách linh kiện của thiết bị ${device.deviceCode} không hợp lệ.`);
    const components = backendComponents
      .filter((component) => component && component.deviceId === device.id)
      .map(componentToFrontend)
      .filter((component): component is DeviceComponent => component !== null);
    return deviceToFrontend(device, components);
  })).then((devices) => devices.filter((device): device is Device => device !== null));
};

interface BackendCommand {
  id: number;
  commandStatus: string;
}

export type SirenCommandResult = 'SUCCESS' | 'FAILED' | 'TIMEOUT';

export type SystemCommandResult = 'SUCCESS' | 'FAILED' | 'TIMEOUT';

interface BackendSystemCommand {
  id: number;
  deviceId: number;
  componentId: number | null;
  commandType: string;
  commandStatus: string;
  createdAt: string;
  executedAt: string | null;
}

export const setSystemState = async (deviceId: string, on: boolean): Promise<SystemCommandResult> => {
  const command = await apiRequest<BackendSystemCommand>(
    `/api/device-commands/device/${deviceId}/system`,
    { method: 'POST', body: JSON.stringify({ commandType: on ? 'SYSTEM_ON' : 'SYSTEM_OFF' }) },
  );
  let status = command.commandStatus.toUpperCase();
  if (status === 'SUCCESS' || status === 'FAILED') return status;

  for (let attempt = 0; attempt < 20; attempt += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
    const commands = await apiRequest<BackendSystemCommand[]>(`/api/device-commands/device/${deviceId}`);
    status = commands.find((item) => item.id === command.id)?.commandStatus.toUpperCase() ?? status;
    if (status === 'SUCCESS' || status === 'FAILED') return status;
  }
  return 'TIMEOUT';
};

export const setSirenState = async (deviceId: string, buzzerComponentId: string, on: boolean): Promise<SirenCommandResult> => {
  const command = await apiRequest<BackendCommand>(
    `/api/device-commands/device/${deviceId}/component/${buzzerComponentId}?commandType=${on ? 'BUZZER_ON' : 'BUZZER_OFF'}`,
    { method: 'POST' },
  );
  let status = command.commandStatus.toUpperCase();
  if (status === 'SUCCESS' || status === 'FAILED') return status;

  // The create endpoint returns SENT; poll command history until the device ACK arrives.
  for (let attempt = 0; attempt < 20; attempt += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
    const commands = await apiRequest<BackendCommand[]>(`/api/device-commands/device/${deviceId}`);
    status = commands.find((item) => item.id === command.id)?.commandStatus.toUpperCase() ?? status;
    if (status === 'SUCCESS' || status === 'FAILED') return status;
  }
  return 'TIMEOUT';
};
export interface BackendCamera {
  id: number;
  componentId: number;
  streamUrl: string | null;
  status: string;
  lastSeen: string | null;
  createdAt: string | null;
}

export const getCameraByComponent = async (componentId: string): Promise<BackendCamera | null> => {
  try {
    const camera = await apiRequest<BackendCamera>(`/api/cameras/component/${componentId}`);
    if (
      typeof camera.id !== 'number' ||
      camera.componentId !== Number(componentId) ||
      !(typeof camera.streamUrl === 'string' || camera.streamUrl === null) ||
      typeof camera.status !== 'string'
    ) {
      throw new Error('Backend trả về thông tin Camera không hợp lệ.');
    }
    return camera;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
};
