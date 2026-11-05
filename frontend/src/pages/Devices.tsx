// Trang Quản lý thiết bị: Bảng 5 thiết bị, bộ lọc và khung chi tiết chuyển đổi Online/Offline
import React, { useState } from 'react';
import { Cpu, X } from 'lucide-react';
import { useStore } from '../store';
import { Card } from '../components/Card';
import { StatusBadge } from '../components/StatusBadge';
import { t, TranslationKey } from '../i18n';
import { RainSensorReading } from '../components/RainSensorReading';

export const Devices: React.FC = () => {
  const { devices, devicesLoading, devicesError, selectedDeviceId, setSelectedDeviceId } = useStore();
  const [filterType, setFilterType] = useState<string>('all');
  const [filterStatus, setFilterStatus] = useState<string>('all');

  const formatValue = (val: string) => {
    if (val === 'siren_state_on' || val === 'siren_state_off' || val === 'status_online' || val === 'status_offline') {
      return t(val as TranslationKey);
    }
    return val;
  };

  const filteredDevices = devices.filter((dev) => {
    if (filterType !== 'all') {
      const componentTypes = (dev.components ?? []).map((component) => component.type.trim().toUpperCase());
      const filterComponentType: Record<string, string[]> = { laser: ['LASER', 'LASER_SENSOR'], rain_sensor: ['RAIN_SENSOR'], siren: ['BUZZER', 'SIREN'], camera: ['CAMERA'] };
      if (filterType in filterComponentType) {
        if (!filterComponentType[filterType].some((type) => componentTypes.includes(type))) return false;
      } else if (dev.type !== filterType) return false;
    }
    if (filterStatus === 'online' && !dev.isOnline) return false;
    if (filterStatus === 'offline' && dev.isOnline) return false;
    return true;
  });

  const selectedDevice = devices.find((d) => d.id === selectedDeviceId) || null;

  return (
    <div className="mx-auto max-w-[1500px] space-y-5 sm:space-y-7">
      <div>
          <p className="mb-1 text-[10px] font-semibold uppercase tracking-[0.2em] text-cyan-400">ASSET INVENTORY</p><h1 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">{t('devices_title')}</h1>
        <p className="text-xs text-slate-400 mt-0.5">{t('devices_desc')}</p>
      </div>

      {/* Bộ lọc */}
      <Card className="border-white/[0.08] bg-[#101a29]">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-xs text-slate-400 font-medium mb-1.5">{t('col_device_type')}</label>
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value)}
              className="w-full bg-navy-850 border border-navy-750 rounded px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-blue-500"
            >
              <option value="all">{t('all')}</option>
              <option value="laser">{t('device_type_laser')}</option>
              <option value="rain_sensor">{t('device_type_rain_sensor')}</option>
              <option value="siren">{t('device_type_siren')}</option>
              <option value="camera">{t('device_type_camera')}</option>
            </select>
          </div>

          <div>
            <label className="block text-xs text-slate-400 font-medium mb-1.5">{t('col_device_status')}</label>
            <select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value)}
              className="w-full bg-navy-850 border border-navy-750 rounded px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-blue-500"
            >
              <option value="all">{t('all')}</option>
              <option value="online">{t('status_online')}</option>
              <option value="offline">{t('status_offline')}</option>
            </select>
          </div>
        </div>
      </Card>

      {/* Bảng thiết bị */}
      <Card className="border-white/[0.08] bg-[#101a29] p-0">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-slate-300">
            <thead className="bg-navy-850 text-slate-400 uppercase text-[11px] border-b border-navy-800">
              <tr>
                <th className="py-3 px-3">{t('col_device_id')}</th>
                <th className="py-3 px-3">{t('col_device_name')}</th>
                <th className="py-3 px-3">{t('col_device_type')}</th>
                <th className="py-3 px-3">{t('col_device_location')}</th>
                <th className="py-3 px-3">{t('col_device_status')}</th>
                <th className="py-3 px-3">{t('col_device_val')}</th>
                <th className="py-3 px-3">{t('col_device_updated')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-navy-800/60">
              {devicesLoading ? (
                <tr><td colSpan={7} className="py-8 text-center text-slate-400">{t('devices_loading')}</td></tr>
              ) : devicesError ? (
                <tr><td colSpan={7} role="alert" className="py-8 text-center text-red-400">{devicesError}</td></tr>
              ) : filteredDevices.length === 0 ? (
                <tr><td colSpan={7} className="py-8 text-center text-slate-400">{devices.length === 0 ? t('devices_empty') : t('devices_filter_empty')}</td></tr>
              ) : filteredDevices.map((dev) => (
                <tr
                  key={dev.id}
                  onClick={() => setSelectedDeviceId(dev.id)}
                  className="hover:bg-navy-850/60 cursor-pointer transition"
                >
                  <td className="py-3 px-3 font-mono font-semibold text-blue-400">{dev.code || dev.id}</td>
                  <td className="py-3 px-3 font-medium text-slate-200">{dev.displayName?.trim() || dev.code || dev.id}</td>
                  <td className="py-3 px-3">{t(`device_type_${dev.type}` as TranslationKey)}</td>
                  <td className="py-3 px-3 font-medium">
                    {dev.location === 'unknown' ? t('location_unavailable') : t(`loc_${dev.location === 'gateway' ? 'gateway' : `zone_${dev.location.toLowerCase()}`}` as TranslationKey)}
                  </td>
                  <td className="py-3 px-3"><StatusBadge status={dev.isOnline ? 'online' : 'offline'} /></td>
                  <td className="py-3 px-3 font-mono text-slate-200">{formatValue(dev.lastValue)}</td>
                  <td className="py-3 px-3 font-mono text-slate-400">{dev.lastUpdated}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Modal chi tiết thiết bị */}
      {selectedDevice && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={() => setSelectedDeviceId(null)}>
          <div className="w-full max-w-md bg-navy-900 border border-navy-700 rounded-lg shadow-xl overflow-hidden text-slate-100" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between px-5 py-3 border-b border-navy-800 bg-navy-850">
              <div className="flex items-center space-x-2">
                <Cpu className="w-4 h-4 text-blue-400" />
                <h3 className="font-semibold text-sm">{t('device_detail_title')}</h3>
              </div>
              <button onClick={() => setSelectedDeviceId(null)} className="text-slate-400 hover:text-white"><X className="w-5 h-5" /></button>
            </div>

            <div className="max-h-[calc(100dvh-8rem)] overflow-y-auto p-4 text-xs sm:p-5 space-y-2.5">
              <div className="flex justify-between py-1.5 border-b border-navy-800"><span className="text-slate-400">{t('col_device_id')}:</span><span className="font-mono font-bold text-slate-200">{selectedDevice.code || selectedDevice.id}</span></div>
              <div className="flex justify-between py-1.5 border-b border-navy-800"><span className="text-slate-400">{t('col_device_name')}:</span><span className="font-medium text-slate-200">{selectedDevice.displayName?.trim() || selectedDevice.code || selectedDevice.id}</span></div>
              <div className="flex justify-between py-1.5 border-b border-navy-800"><span className="text-slate-400">{t('col_device_type')}:</span><span>{t(`device_type_${selectedDevice.type}` as TranslationKey)}</span></div>
              <div className="flex justify-between py-1.5 border-b border-navy-800"><span className="text-slate-400">{t('col_device_location')}:</span><span>{selectedDevice.location === 'unknown' ? t('location_unavailable') : t(`loc_${selectedDevice.location === 'gateway' ? 'gateway' : `zone_${selectedDevice.location.toLowerCase()}`}` as TranslationKey)}</span></div>
              <div className="flex justify-between py-1.5 border-b border-navy-800"><span className="text-slate-400">{t('col_device_status')}:</span><StatusBadge status={selectedDevice.isOnline ? 'online' : 'offline'} /></div>
              <div className="flex justify-between py-1.5 border-b border-navy-800"><span className="text-slate-400">{t('col_device_val')}:</span><span className="font-mono text-cyan-300 font-semibold">{formatValue(selectedDevice.lastValue)}</span></div>
              
              <div className="p-3 bg-navy-850 rounded border border-navy-800 space-y-2 my-2">
                <div className="font-semibold text-slate-300 mb-1">{t('components_label')}</div>
                {!selectedDevice.components?.length ? <p className="text-slate-400">{t('components_empty')}</p> : selectedDevice.components.map((component) => (
                  <div key={component.id} className="border-t border-navy-800 pt-2">
                    <div className="flex justify-between gap-3">
                    <span className="text-slate-300">{component.name} <span className="text-slate-500">({component.code} / {t(`component_type_${component.type.trim().toLowerCase()}` as TranslationKey)})</span></span>
                    <span className="text-slate-400">{component.status}</span>
                    </div>
                    {component.type.trim().toUpperCase() === 'RAIN_SENSOR' && <RainSensorReading componentId={component.id} />}
                  </div>
                ))}
                {!!selectedDevice.components?.length && <p className="pt-1 text-[10px] leading-4 text-slate-500">{t('component_status_note')}</p>}
              </div>

              <div className="flex justify-between py-1.5"><span className="text-slate-400">{t('col_device_updated')}:</span><span className="font-mono text-slate-300">{selectedDevice.lastUpdated}</span></div>

              <div className="pt-4 flex justify-end items-center border-t border-navy-800">
                <button type="button" onClick={() => setSelectedDeviceId(null)} className="px-4 py-2 bg-navy-800 hover:bg-navy-700 text-slate-300 rounded text-xs">{t('close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
