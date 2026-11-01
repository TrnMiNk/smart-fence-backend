import React, { useMemo, useState } from 'react';
import { Radio, Droplets, Volume2, Power, Cpu, Video, VideoOff, RefreshCw, X } from 'lucide-react';
import { useStore } from '../store';
import { Card } from '../components/Card';
import { StatusBadge } from '../components/StatusBadge';
import { Device, DeviceComponent } from '../types';
import { t } from '../i18n';
import { BackendCamera, getCameraByComponent, setSirenState } from '../services/deviceService';

type CommandFeedback = { componentId: string; kind: 'success' | 'error' | 'pending'; message: string };

export const Fence: React.FC = () => {
  const { devices, devicesLoading, devicesError } = useStore();
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | null>(null);
  const [pendingComponentId, setPendingComponentId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<CommandFeedback | null>(null);
  const [acknowledgedSirenState, setAcknowledgedSirenState] = useState<Record<string, boolean>>({});
  const [cameraComponentId, setCameraComponentId] = useState<string | null>(null);
  const [camera, setCamera] = useState<BackendCamera | null>(null);
  const [cameraLoading, setCameraLoading] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [streamState, setStreamState] = useState<'idle' | 'loading' | 'loaded' | 'error'>('idle');
  const [streamAttempt, setStreamAttempt] = useState(0);

  const selectedDevice = devices.find((device) => device.id === selectedDeviceId) ?? devices[0];
  const cameraDevice = devices.find((device) => (device.components ?? []).some((component) => component.id === cameraComponentId));
  const cameraOffline = camera?.status.trim().toUpperCase() === 'OFFLINE';
  const cameraParentOffline = cameraDevice?.isOnline === false;
  const cameraHasOfflineWarning = cameraOffline || cameraParentOffline;
  const fenceDevices = useMemo(
    () => devices.filter((device) => (device.components ?? []).some((component) => ['LASER', 'BUZZER', 'DHT22', 'CAMERA'].includes(component.type.trim().toUpperCase()))),
    [devices],
  );

  const handleSirenCommand = async (device: Device, component: DeviceComponent, on: boolean) => {
    if (pendingComponentId || !device.isOnline) return;
    setPendingComponentId(component.id);
    setFeedback({ componentId: component.id, kind: 'pending', message: t('fence_command_waiting') });
    try {
      const result = await setSirenState(device.id, component.id, on);
      if (result === 'SUCCESS') {
        setAcknowledgedSirenState((current) => ({ ...current, [component.id]: on }));
        setFeedback({ componentId: component.id, kind: 'success', message: t(on ? 'fence_siren_on_ack' : 'fence_siren_off_ack') });
      } else if (result === 'FAILED') {
        setFeedback({ componentId: component.id, kind: 'error', message: t('fence_command_failed') });
      } else {
        setFeedback({ componentId: component.id, kind: 'error', message: t('fence_command_timeout') });
      }
    } catch (error) {
      setFeedback({ componentId: component.id, kind: 'error', message: error instanceof Error ? error.message : t('fence_command_failed') });
    } finally {
      setPendingComponentId(null);
    }
  };

  const componentIcon = (type: string) => {
    const normalized = type.trim().toUpperCase();
    if (normalized === 'LASER') return <Radio className="w-4 h-4 text-indigo-400" />;
    if (normalized === 'DHT22') return <Droplets className="w-4 h-4 text-blue-400" />;
    if (normalized === 'BUZZER') return <Volume2 className="w-4 h-4 text-amber-400" />;
    if (normalized === 'CAMERA') return <Video className="w-4 h-4 text-emerald-400" />;
    return <Cpu className="w-4 h-4 text-slate-400" />;
  };

  const loadCamera = async (componentId: string) => {
    setCameraComponentId(componentId);
    setCamera(null);
    setCameraLoading(true);
    setCameraError(null);
    setStreamState('idle');
    setStreamAttempt(0);
    try {
      const result = await getCameraByComponent(componentId);
      setCamera(result);
    } catch (error) {
      setCameraError(error instanceof Error ? error.message : t('camera_load_error'));
    } finally {
      setCameraLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-white">{t('fence_devices_title')}</h1>
        <p className="mt-1 text-xs text-slate-400">{t('fence_api_data_note')}</p>
      </div>

      {devicesLoading ? (
        <Card><p className="py-6 text-center text-sm text-slate-400">{t('devices_loading')}</p></Card>
      ) : devicesError ? (
        <Card><p role="alert" className="py-4 text-sm text-red-400">{devicesError}</p></Card>
      ) : fenceDevices.length === 0 ? (
        <Card><p className="py-6 text-center text-sm text-slate-400">{t('fence_components_empty')}</p></Card>
      ) : (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          {fenceDevices.map((device) => {
            const components = (device.components ?? []).filter((component) => ['LASER', 'BUZZER', 'DHT22', 'CAMERA'].includes(component.type.trim().toUpperCase()));
            return (
              <Card key={device.id} className={selectedDevice?.id === device.id ? 'border-blue-600/70' : ''}>
                <button type="button" onClick={() => setSelectedDeviceId(device.id)} className="mb-4 flex w-full items-center justify-between text-left">
                  <div>
                    <h2 className="text-sm font-semibold text-white">{device.displayName || device.code || `Device ${device.id}`}</h2>
                    <p className="mt-1 font-mono text-[11px] text-slate-400">{device.code || device.id}</p>
                  </div>
                  <StatusBadge status={device.isOnline ? 'online' : 'offline'} />
                </button>
                <div className="space-y-2">
                  {components.map((component) => {
                    const type = component.type.trim().toUpperCase();
                    const componentFeedback = feedback?.componentId === component.id ? feedback : null;
                    return (
                      <div key={component.id} className="rounded border border-navy-750 bg-navy-800/50 p-3">
                        <div className="flex items-center justify-between gap-3">
                          <div className="flex min-w-0 items-center gap-2">
                            {componentIcon(type)}
                            <div className="min-w-0">
                              <p className="truncate text-xs font-medium text-slate-200">{component.name || component.code}</p>
                              <p className="text-[10px] text-slate-500">{type} · {t('component_registration_status')}: {component.status}</p>
                            </div>
                          </div>
                          {type === 'BUZZER' && (
                            <div className="flex shrink-0 gap-1.5">
                              <button type="button" disabled={!device.isOnline || pendingComponentId !== null} onClick={() => void handleSirenCommand(device, component, true)} className="rounded bg-red-600/80 px-2.5 py-1.5 text-[11px] font-medium text-white hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-40">{t('btn_siren_on')}</button>
                              <button type="button" disabled={!device.isOnline || pendingComponentId !== null} onClick={() => void handleSirenCommand(device, component, false)} className="rounded border border-navy-700 px-2.5 py-1.5 text-[11px] font-medium text-slate-200 hover:bg-navy-700 disabled:cursor-not-allowed disabled:opacity-40">{t('btn_siren_off')}</button>
                            </div>
                          )}
                        </div>
                        {type === 'LASER' && <p className="mt-2 text-[11px] text-slate-400">{t('fence_laser_state_unavailable')}</p>}
                        {type === 'DHT22' && <p className="mt-2 text-[11px] text-slate-400">{t('fence_sensor_detail_note')}</p>}
                        {type === 'CAMERA' && <div className="mt-2 flex items-center justify-between gap-3"><p className="text-[11px] text-slate-400">{t('camera_backend_status')}: {component.status}</p><button type="button" onClick={() => void loadCamera(component.id)} className="inline-flex items-center gap-1.5 rounded bg-blue-600 px-2.5 py-1.5 text-[11px] font-medium text-white hover:bg-blue-500"><Video className="h-3.5 w-3.5" />{t('view_camera')}</button></div>}
                        {componentFeedback && <p role={componentFeedback.kind === 'error' ? 'alert' : 'status'} className={`mt-2 text-[11px] ${componentFeedback.kind === 'success' ? 'text-emerald-400' : componentFeedback.kind === 'error' ? 'text-red-400' : 'text-amber-300'}`}>{componentFeedback.message}</p>}
                        {type === 'BUZZER' && acknowledgedSirenState[component.id] !== undefined && componentFeedback?.kind !== 'error' && <p className="mt-2 flex items-center gap-1 text-[11px] text-emerald-400"><Power className="h-3 w-3" />{t(acknowledgedSirenState[component.id] ? 'fence_siren_on_ack' : 'fence_siren_off_ack')}</p>}
                      </div>
                    );
                  })}
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {selectedDevice && <Card>
        <h2 className="mb-3 text-sm font-semibold text-slate-200">{t('device_status_detail')}</h2>
        <div className="grid gap-2 text-xs sm:grid-cols-3">
          <p className="text-slate-400">{t('col_device_id')}: <span className="ml-1 font-mono text-white">{selectedDevice.id}</span></p>
          <p className="text-slate-400">{t('col_device_name')}: <span className="ml-1 text-white">{selectedDevice.displayName || selectedDevice.code}</span></p>
          <p className="flex items-center gap-2 text-slate-400">{t('col_device_status')}: <StatusBadge status={selectedDevice.isOnline ? 'online' : 'offline'} /></p>
          <p className="text-slate-400 sm:col-span-3">{t('fence_hardware_disclaimer')}</p>
          <p className="text-slate-400 sm:col-span-3">{t('component_status_note')}</p>
        </div>
      </Card>}

      {cameraComponentId && <div role="presentation" onClick={() => setCameraComponentId(null)} className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-sm">
        <section role="dialog" aria-modal="true" aria-label={t('camera_modal_title')} onClick={(event) => event.stopPropagation()} className="w-full max-w-3xl overflow-hidden rounded-lg border border-navy-700 bg-navy-900 shadow-2xl">
          <header className="flex items-center justify-between border-b border-navy-800 bg-navy-850 p-3.5">
            <div className="flex items-center gap-2"><Video className="h-4 w-4 text-emerald-400" /><h2 className="text-sm font-semibold text-white">{t('camera_modal_title')}</h2></div>
            <button type="button" aria-label={t('close')} onClick={() => setCameraComponentId(null)} className="text-slate-400 hover:text-white"><X className="h-5 w-5" /></button>
          </header>
          <div className="space-y-3 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
              <p className="text-slate-300">{t('camera_backend_status')}: {cameraLoading ? t('camera_loading') : camera?.status ?? (cameraError ? '—' : t('camera_not_registered'))}</p>
              {cameraDevice && <p className={cameraParentOffline ? 'text-amber-300' : 'text-slate-400'}>{t('col_device_status')}: {cameraParentOffline ? t('status_offline') : t('status_online')}</p>}
              <p className={streamState === 'loaded' ? 'text-emerald-400' : streamState === 'error' ? 'text-red-400' : 'text-slate-400'}>{t(`camera_stream_${streamState}` as 'camera_stream_idle' | 'camera_stream_loading' | 'camera_stream_loaded' | 'camera_stream_error')}</p>
            </div>
            {cameraHasOfflineWarning && <p role="alert" className="rounded border border-amber-700/60 bg-amber-950/40 p-2 text-xs text-amber-300">{t('camera_offline_warning')}</p>}
            {camera?.streamUrl?.trim() && <p className="text-[11px] text-slate-400">{t('camera_connection_test_note')}</p>}
            <div className="flex min-h-[320px] items-center justify-center rounded border border-navy-800 bg-black p-2">
              {cameraLoading ? <p className="text-xs text-slate-400">{t('camera_loading')}</p> : cameraError ? <div role="alert" className="space-y-2 text-center text-xs text-red-400"><VideoOff className="mx-auto h-9 w-9 text-slate-600" /><p>{cameraError}</p></div> : !camera ? <div className="space-y-2 text-center text-xs text-slate-400"><VideoOff className="mx-auto h-9 w-9 text-slate-600" /><p>{t('camera_not_registered')}</p></div> : !camera.streamUrl?.trim() ? <div className="space-y-2 text-center text-xs text-slate-400"><VideoOff className="mx-auto h-9 w-9 text-slate-600" /><p>{t('camera_stream_url_missing')}</p></div> : streamAttempt === 0 ? <div className="space-y-2 text-center text-xs text-slate-400"><VideoOff className="mx-auto h-9 w-9 text-slate-600" /><p>{t('camera_stream_idle')}</p></div> : <img key={`${camera.id}-${streamAttempt}`} src={`${camera.streamUrl}${camera.streamUrl.includes('?') ? '&' : '?'}_retry=${streamAttempt}`} alt={t('camera_image_alt')} onLoad={() => setStreamState('loaded')} onError={() => setStreamState('error')} onLoadStart={() => setStreamState('loading')} className="max-h-[65vh] w-full object-contain" />}
            </div>
            {streamState === 'error' && <p role="alert" className="text-xs text-amber-300">{t('camera_stream_unreachable')}</p>}
            <div className="flex items-center justify-between gap-3">
              <p className="truncate text-[10px] text-slate-500" title={camera?.streamUrl ?? ''}>{camera?.streamUrl || ''}</p>
              <button type="button" disabled={!camera?.streamUrl?.trim()} onClick={() => { setStreamState('loading'); setStreamAttempt((attempt) => attempt + 1); }} className="inline-flex shrink-0 items-center gap-1.5 rounded border border-navy-700 px-3 py-1.5 text-xs text-slate-200 hover:bg-navy-800 disabled:opacity-40"><RefreshCw className="h-3.5 w-3.5" />{t(streamAttempt === 0 ? 'camera_try_connection' : 'camera_retry')}</button>
            </div>
            <p className="text-[10px] leading-4 text-slate-500">{t('camera_status_disclaimer')}</p>
          </div>
        </section>
      </div>}
    </div>
  );
};
