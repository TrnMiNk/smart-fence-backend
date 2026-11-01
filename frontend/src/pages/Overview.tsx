// Trang Tổng quan: số liệu cảm biến, thiết bị và sự kiện lấy từ Backend.
import React, { useEffect, useMemo, useState } from 'react';
import { ShieldAlert, Cpu, Bell, Droplets, Volume2, Power } from 'lucide-react';
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';
import { useStore } from '../store';
import { Card } from '../components/Card';
import { StatusBadge, BadgeVariant } from '../components/StatusBadge';
import { t } from '../i18n';
import { getLatestSensorReading, getSensorHistory, SensorReading } from '../services/overviewService';
import { setSystemState, SystemCommandResult } from '../services/deviceService';

const formatDateTime = (value: string): string => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const formatNumber = (value: number | null): string =>
  value === null ? '—' : value.toLocaleString(undefined, { maximumFractionDigits: 2 });

const eventStatus = (value: string): { badge?: BadgeVariant; label: string } => {
  const status = value.trim().toUpperCase();
  if (status === 'NEW' || status === 'UNRESOLVED') return { badge: 'unresolved', label: t('overview_status_new') };
  if (status === 'RESOLVED') return { badge: 'resolved', label: t('status_resolved') };
  if (status === 'FALSE_ALARM') return { badge: 'false_alarm', label: t('status_false_alarm') };
  return { label: value || '—' };
};

const eventTypeLabel = (value: string): string => {
  if (value.trim().toUpperCase() === 'LASER_BLOCKED') return t('event_laser_blocked');
  return value.replace(/_/g, ' ');
};

const sensorRange = (): { start: Date; end: Date } => {
  const end = new Date();
  const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
  return { start, end };
};

export const Overview: React.FC = () => {
  const {
    activeDevicesCount,
    totalDevicesCount,
    devices,
    devicesLoading,
    devicesError,
    refreshDevices,
    events,
    eventsLoading,
    eventsError,
  } = useStore();

  const [latestReading, setLatestReading] = useState<SensorReading | null>(null);
  const [history, setHistory] = useState<SensorReading[]>([]);
  const [sensorLoading, setSensorLoading] = useState(true);
  const [sensorError, setSensorError] = useState<string | null>(null);
  const [sensorMissing, setSensorMissing] = useState(false);
  const [systemCommandPending, setSystemCommandPending] = useState(false);
  const [systemCommandMessage, setSystemCommandMessage] = useState<string | null>(null);

  const systemDevice = devices[0];
  const systemState = systemDevice?.systemState;
  const canControlSystem = Boolean(systemDevice?.isOnline && (systemState === 'ON' || systemState === 'OFF'));

  const handleSystemToggle = async () => {
    if (!systemDevice || !canControlSystem || systemCommandPending) return;
    setSystemCommandPending(true);
    setSystemCommandMessage(null);
    try {
      const result: SystemCommandResult = await setSystemState(systemDevice.id, systemState === 'OFF');
      if (result === 'SUCCESS') {
        setSystemCommandMessage(t('monitoring_command_success'));
        try { await refreshDevices(); } catch { /* Keep the ACK result; next device refresh can update systemState. */ }
      } else if (result === 'FAILED') {
        setSystemCommandMessage(t('monitoring_command_failed'));
      } else {
        setSystemCommandMessage(t('monitoring_command_timeout'));
      }
    } catch {
      setSystemCommandMessage(t('monitoring_command_error'));
    } finally {
      setSystemCommandPending(false);
    }
  };

  const dhtComponent = useMemo(
    () => devices.flatMap((device) => (device.components ?? []).map((component) => ({ device, component })))
      .find(({ component }) => component.type.trim().toUpperCase() === 'DHT22'),
    [devices],
  );

  useEffect(() => {
    if (devicesLoading) return;
    if (devicesError) {
      setSensorError(devicesError);
      setSensorLoading(false);
      return;
    }
    if (!dhtComponent) {
      setSensorMissing(true);
      setSensorLoading(false);
      return;
    }

    let active = true;
    const { start, end } = sensorRange();
    setSensorLoading(true);
    setSensorError(null);
    setSensorMissing(false);
    Promise.all([
      getLatestSensorReading(Number(dhtComponent.component.id)),
      getSensorHistory(Number(dhtComponent.component.id), start, end),
    ])
      .then(([latest, readings]) => {
        if (active) {
          setLatestReading(latest);
          setHistory(readings);
        }
      })
      .catch((error: unknown) => {
        if (active) setSensorError(error instanceof Error ? error.message : 'Không tải được số đo cảm biến.');
      })
      .finally(() => { if (active) setSensorLoading(false); });
    return () => { active = false; };
  }, [devicesLoading, devicesError, dhtComponent]);

  const sortedEvents = useMemo(
    () => [...events].sort((a, b) => b.detectedAt.localeCompare(a.detectedAt)),
    [events],
  );
  const recentEvents = sortedEvents.slice(0, 5);
  const isDeviceOffline = Boolean(dhtComponent && !dhtComponent.device.isOnline);
  const securityBadge: BadgeVariant | null = useMemo(() => {
    if (eventsLoading || eventsError || devicesLoading || devicesError || devices.length === 0) return null;
    const pendingEvents = events.filter((event) => ['NEW', 'UNRESOLVED'].includes(event.eventStatus.trim().toUpperCase()));
    if (pendingEvents.some((event) => event.eventType.trim().toUpperCase() === 'LASER_BLOCKED')) return 'intrusion';
    if (pendingEvents.length > 0) return 'suspicious';
    if (devices.some((device) => !device.isOnline)) return 'offline';
    if (sensorLoading || sensorError || sensorMissing || !latestReading) return null;
    return 'safe';
  }, [events, eventsLoading, eventsError, devices, devicesLoading, devicesError, sensorLoading, sensorError, sensorMissing, latestReading]);

  const chartData = useMemo(
    () => history
      .filter((reading) => reading.humidity !== null || reading.temperature !== null)
      .map((reading) => ({
        time: new Date(reading.recordedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        temperature: reading.temperature,
        humidity: reading.humidity,
      })),
    [history],
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-white">{t('nav_overview')}</h1>
          <p className="text-xs text-slate-400 mt-0.5">
            {systemState === 'ON' ? t('monitoring_state_active') : systemState === 'OFF' ? t('monitoring_state_paused') : t('monitoring_state_unavailable')}
          </p>
        </div>
        <button
          type="button"
          disabled={!canControlSystem || systemCommandPending}
          onClick={() => void handleSystemToggle()}
          title={!systemDevice?.isOnline ? t('status_offline') : !canControlSystem ? t('monitoring_state_unavailable') : undefined}
          className={`inline-flex items-center px-4 py-2 rounded-lg font-medium text-sm ${canControlSystem && !systemCommandPending ? 'bg-blue-600 text-white hover:bg-blue-500' : 'bg-navy-800 text-slate-500 cursor-not-allowed'}`}
        >
          <Power className="w-4 h-4 mr-2" />
          {systemCommandPending ? t('monitoring_command_sending') : systemState === 'ON' ? t('btn_monitoring_off') : t('btn_monitoring_on')}
        </button>
      </div>
      {systemCommandMessage && <p role="status" className="-mt-4 text-xs text-slate-300">{systemCommandMessage}</p>}

      <div className="grid grid-cols-5 gap-4">
        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>{t('card_security_status')}</span>
            <ShieldAlert className="w-4 h-4 text-blue-400" />
          </div>
          <div className="mt-3">
            {securityBadge ? <StatusBadge status={securityBadge} /> : <span className="text-sm text-slate-400">{t('overview_monitoring_unknown')}</span>}
          </div>
        </Card>
        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>{t('card_online_devices')}</span>
            <Cpu className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="mt-2 text-2xl font-bold text-white">
            {devicesLoading || devicesError ? '— / —' : `${activeDevicesCount} / ${totalDevicesCount}`}
          </div>
        </Card>
        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>{t('card_events_total')}</span>
            <Bell className="w-4 h-4 text-yellow-400" />
          </div>
          <div className="mt-2 text-2xl font-bold text-white">{eventsLoading || eventsError ? '—' : events.length}</div>
        </Card>
        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>{t('card_temperature_humidity')}</span>
            <Droplets className="w-4 h-4 text-cyan-400" />
          </div>
          {sensorLoading ? (
            <p className="mt-2 text-xs text-slate-400">{t('overview_sensor_loading')}</p>
          ) : sensorError ? (
            <p role="alert" className="mt-2 text-xs text-red-400">{sensorError}</p>
          ) : sensorMissing ? (
            <p className="mt-2 text-xs text-slate-400">{t('overview_sensor_unavailable')}</p>
          ) : (
            <div className="mt-2">
              <div className={`text-xl font-bold ${latestReading ? 'text-cyan-300' : 'text-slate-400'}`}>
                {latestReading ? `${formatNumber(latestReading.temperature)}°C / ${formatNumber(latestReading.humidity)}%` : '— / —'}
              </div>
              {isDeviceOffline && latestReading ? (
                <p className="mt-1 text-[10px] leading-4 text-amber-300">
                  {t('overview_sensor_offline')}. {t('overview_last_reading')}: {formatDateTime(latestReading.recordedAt)}
                </p>
              ) : latestReading ? (
                <p className="mt-1 text-[10px] text-slate-400">{t('overview_last_reading')}: {formatDateTime(latestReading.recordedAt)}</p>
              ) : (
                <p className="mt-1 text-[10px] text-slate-400">{t('overview_sensor_empty')}</p>
              )}
            </div>
          )}
        </Card>
        <Card className="flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 text-xs">
            <span>{t('card_siren')}</span>
            <Volume2 className="w-4 h-4 text-slate-400" />
          </div>
          <div className="mt-2">
            <span className="text-2xl font-bold text-slate-400">—</span>
            <p className="mt-1 text-[10px] text-slate-400">{t('overview_siren_unknown')}</p>
          </div>
        </Card>
      </div>

      <Card>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-200">{t('chart_title')}</h2>
          <span className="text-xs text-slate-400">{t('chart_timespan')}</span>
        </div>
        <div className="h-64 w-full">
          {sensorLoading ? (
            <div className="h-full flex items-center justify-center text-xs text-slate-400">{t('overview_sensor_loading')}</div>
          ) : sensorError ? (
            <div role="alert" className="h-full flex items-center justify-center text-xs text-red-400">{sensorError}</div>
          ) : chartData.length === 0 ? (
            <div className="h-full flex items-center justify-center text-xs text-slate-400">{t('overview_chart_empty')}</div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 5, right: 20, left: -15, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="time" stroke="#64748b" tick={{ fontSize: 11 }} />
                <YAxis yAxisId="temperature" stroke="#f97316" tick={{ fontSize: 11 }} domain={['auto', 'auto']} />
                <YAxis yAxisId="humidity" orientation="right" stroke="#06b6d4" tick={{ fontSize: 11 }} domain={[0, 100]} />
                <Tooltip contentStyle={{ backgroundColor: '#0b1329', borderColor: '#1e293b', fontSize: '12px', borderRadius: '6px' }} />
                <Legend wrapperStyle={{ fontSize: '12px' }} />
                <Line yAxisId="temperature" type="monotone" dataKey="temperature" name={t('chart_temp_label')} stroke="#f97316" strokeWidth={2} dot={false} isAnimationActive={false} connectNulls />
                <Line yAxisId="humidity" type="monotone" dataKey="humidity" name={t('chart_humidity_label')} stroke="#06b6d4" strokeWidth={2} dot={false} isAnimationActive={false} connectNulls />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
      </Card>

      <Card>
        <h2 className="text-sm font-semibold text-slate-200 mb-3">{t('recent_events_title')}</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-300">
            <thead className="bg-navy-850 text-slate-400 uppercase text-[11px] border-b border-navy-800">
              <tr>
                <th className="py-2.5 px-3">{t('col_time')}</th>
                <th className="py-2.5 px-3">{t('overview_device_label')}</th>
                <th className="py-2.5 px-3">{t('col_type')}</th>
                <th className="py-2.5 px-3">{t('overview_status_label')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-navy-800/60">
              {eventsLoading ? (
                <tr><td colSpan={4} className="py-8 text-center text-slate-400">{t('overview_events_loading')}</td></tr>
              ) : eventsError ? (
                <tr><td colSpan={4} role="alert" className="py-8 text-center text-red-400">{eventsError}</td></tr>
              ) : recentEvents.length === 0 ? (
                <tr><td colSpan={4} className="py-8 text-center text-slate-400">{t('overview_events_empty')}</td></tr>
              ) : recentEvents.map((event) => {
                const status = eventStatus(event.eventStatus);
                return (
                  <tr key={event.id} className="hover:bg-navy-850/50">
                    <td className="py-2.5 px-3 font-mono text-slate-400">{formatDateTime(event.detectedAt)}</td>
                    <td className="py-2.5 px-3 font-medium text-slate-200">#{event.deviceId}</td>
                    <td className="py-2.5 px-3">{eventTypeLabel(event.eventType)}</td>
                    <td className="py-2.5 px-3">{status.badge ? <StatusBadge status={status.badge} labelKey={status.badge === 'unresolved' ? 'status_unresolved' : status.badge === 'resolved' ? 'status_resolved' : 'status_false_alarm'} /> : status.label}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};
