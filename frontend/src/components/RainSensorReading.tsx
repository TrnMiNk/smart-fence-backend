import React, { useEffect, useState } from 'react';
import { CloudRain } from 'lucide-react';
import { getLatestSensorReading, SensorReading } from '../services/overviewService';
import { t } from '../i18n';

export const RainSensorReading: React.FC<{ componentId: string }> = ({ componentId }) => {
  const [reading, setReading] = useState<SensorReading | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    getLatestSensorReading(Number(componentId)).then((value) => { if (active) setReading(value); })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : 'Unable to load sensor reading.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [componentId]);

  return <div className="mt-2 rounded-lg border border-cyan-900/60 bg-cyan-950/20 p-3 text-xs">
    <div className="mb-2 flex items-center gap-2 text-cyan-200"><CloudRain className="h-4 w-4" />YL-83 · {t('rain_sensor_reading')}</div>
    {loading ? <div className="h-4 w-24 animate-pulse rounded bg-slate-700/60" aria-label={t('overview_sensor_loading')} /> : error ? <p role="alert" className="text-red-300">{error}</p> : !reading ? <p className="text-slate-400">{t('overview_sensor_empty')}</p> : <div className="grid gap-2 sm:grid-cols-2">
      <p className="text-slate-400">{t('rain_detection_title')} <span className={`ml-1 font-medium ${reading.rainDetected === true ? 'text-sky-300' : reading.rainDetected === false ? 'text-emerald-300' : 'text-slate-400'}`}>{reading.rainDetected === null ? t('rain_state_unknown') : t(reading.rainDetected ? 'rain_detected' : 'rain_not_detected')}</span></p>
      <p className="text-slate-400">{t('rain_level_title')} <span className="ml-1 font-mono font-medium text-cyan-200">{reading.rainLevel === null ? '—' : `ADC ${reading.rainLevel} / 4095`}</span></p>
      <p className="text-slate-500 sm:col-span-2">{t('overview_last_reading')}: {reading.recordedAt.replace('T', ' ')}</p>
    </div>}
  </div>;
};
