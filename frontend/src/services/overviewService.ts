import { ApiError, apiRequest } from './apiClient';

export interface SensorReading {
  id: number;
  componentId: number;
  temperature: number | null;
  humidity: number | null;
  /** Backend DTO serializes Java LocalDateTime as ISO local datetime without a timezone. */
  recordedAt: string;
}

export interface BackendIntrusionEvent {
  id: number;
  deviceId: number;
  componentId: number | null;
  eventType: string;
  eventStatus: string;
  description: string | null;
  /** Backend DTO serializes Java LocalDateTime as ISO local datetime without a timezone. */
  detectedAt: string;
  closedAt: string | null;
}

const isLocalDateTime = (value: unknown): value is string =>
  typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/.test(value);

const parseSensorReading = (value: unknown): SensorReading => {
  if (typeof value !== 'object' || value === null) throw new Error('Backend trả về số đo cảm biến không hợp lệ.');
  const reading = value as Record<string, unknown>;
  const validNumber = (item: unknown): item is number | null => item === null || (typeof item === 'number' && Number.isFinite(item));
  if (
    typeof reading.id !== 'number' ||
    typeof reading.componentId !== 'number' ||
    !validNumber(reading.temperature) ||
    !validNumber(reading.humidity) ||
    !isLocalDateTime(reading.recordedAt)
  ) {
    throw new Error('Backend trả về số đo cảm biến sai kiểu dữ liệu.');
  }
  return reading as unknown as SensorReading;
};

const localDateTime = (date: Date): string => {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

export const getLatestSensorReading = async (componentId: number): Promise<SensorReading | null> => {
  try {
    const value = await apiRequest<unknown>(`/api/sensor-readings/component/${componentId}/latest`);
    return parseSensorReading(value);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
};

export const getSensorHistory = async (
  componentId: number,
  start: Date,
  end: Date,
): Promise<SensorReading[]> => {
  const query = new URLSearchParams({ start: localDateTime(start), end: localDateTime(end) });
  const value = await apiRequest<unknown>(`/api/sensor-readings/component/${componentId}/history?${query.toString()}`);
  if (!Array.isArray(value)) throw new Error('Backend trả về lịch sử cảm biến không hợp lệ.');
  return value.map(parseSensorReading);
};

export const getIntrusionEvents = async (): Promise<BackendIntrusionEvent[]> => {
  const value = await apiRequest<unknown>('/api/intrusion-events');
  if (!Array.isArray(value)) throw new Error('Backend trả về danh sách sự kiện không hợp lệ.');
  return value.map(parseIntrusionEvent);
};

const parseIntrusionEvent = (item: unknown): BackendIntrusionEvent => {
  if (typeof item !== 'object' || item === null) throw new Error('Backend trả về sự kiện sai kiểu dữ liệu.');
  const event = item as Record<string, unknown>;
  if (
    typeof event.id !== 'number' ||
    typeof event.deviceId !== 'number' ||
    !(typeof event.componentId === 'number' || event.componentId === null) ||
    typeof event.eventType !== 'string' ||
    typeof event.eventStatus !== 'string' ||
    !(typeof event.description === 'string' || event.description === null) ||
    !isLocalDateTime(event.detectedAt) ||
    !(isLocalDateTime(event.closedAt) || event.closedAt === null)
  ) {
    throw new Error('Backend trả về sự kiện sai kiểu dữ liệu.');
  }
  return event as unknown as BackendIntrusionEvent;
};

export const resolveIntrusionEvent = async (eventId: number): Promise<BackendIntrusionEvent> => {
  const value = await apiRequest<unknown>(`/api/intrusion-events/${eventId}/resolve`, { method: 'PATCH' });
  const event = parseIntrusionEvent(value);
  if (event.id !== eventId || event.eventStatus.toUpperCase() !== 'RESOLVED') {
    throw new Error('Backend chưa xác nhận sự kiện đã được xử lý.');
  }
  return event;
};
