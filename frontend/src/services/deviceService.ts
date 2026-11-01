import { Device, DeviceComponent, DeviceType } from '../types';
import { ApiError, apiRequest } from './apiClient';

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

const componentToFrontend = (component: BackendComponent): DeviceComponent => ({
  id: String(component.id),
  code: component.componentCode,
  name: component.componentName,
  type: component.componentType,
  status: component.status,
});

const typeForComponents = (components: DeviceComponent[]): DeviceType => {
  const types = [...new Set(components.map((component) => component.type.toUpperCase()))];
  if (types.length !== 1) return 'unknown';
  if (types.includes('LASER')) return 'laser';
  if (types.includes('DHT22')) return 'humidity';
  if (types.includes('BUZZER')) return 'siren';
  if (types.includes('CAMERA')) return 'camera';
  return 'unknown';
};

const deviceToFrontend = (device: BackendDevice, components: DeviceComponent[]): Device => {
  const status = device.status.trim().toUpperCase();
  if (status !== 'ONLINE' && status !== 'OFFLINE') {
    throw new Error(`Trạng thái thiết bị không được hỗ trợ: ${device.status}`);
  }

  return {
    id: String(device.id),
    code: device.deviceCode,
    displayName: device.deviceName,
    type: typeForComponents(components),
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
    return deviceToFrontend(device, backendComponents.map(componentToFrontend));
  }));
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
