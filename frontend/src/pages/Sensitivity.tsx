// Trang cấu hình độ nhạy: các thay đổi chỉ được áp dụng sau khi lưu cục bộ.
import React, { useEffect, useState } from 'react';
import { AlertCircle, RotateCcw, Save, Sliders } from 'lucide-react';
import { Card } from '../components/Card';
import { useStore } from '../store';
import { defaultSensitivityConfig, presetSensitivityConfigs } from '../mockData';
import { SensitivityConfig, SensitivityMode } from '../types';
import { t } from '../i18n';

const WINDOW_OPTIONS = [1, 2, 3, 5, 10];
const PRESET_MODES: Exclude<SensitivityMode, 'custom'>[] = ['normal', 'strong_wind', 'leaf_fall'];

const sameValues = (a: SensitivityConfig, b: Omit<SensitivityConfig, 'mode'>) =>
  a.laserSensitivity === b.laserSensitivity &&
  a.confirmCount === b.confirmCount &&
  a.confirmWindow === b.confirmWindow &&
  a.humidityThreshold === b.humidityThreshold;

const identifyMode = (config: SensitivityConfig): SensitivityMode =>
  PRESET_MODES.find((mode) => sameValues(config, presetSensitivityConfigs[mode])) ?? 'custom';

const validConfig = (config: SensitivityConfig) =>
  Number.isInteger(config.laserSensitivity) && config.laserSensitivity >= 10 && config.laserSensitivity <= 100 &&
  Number.isInteger(config.confirmCount) && config.confirmCount >= 1 && config.confirmCount <= 10 &&
  WINDOW_OPTIONS.includes(config.confirmWindow) &&
  Number.isInteger(config.humidityThreshold) && config.humidityThreshold >= 30 && config.humidityThreshold <= 95;

export const Sensitivity: React.FC = () => {
  const { sensitivityConfig, saveSensitivityConfig, resetSensitivityConfig, showToast } = useStore();
  const [formConfig, setFormConfig] = useState<SensitivityConfig>(() => ({ ...sensitivityConfig }));
  const [customDraft, setCustomDraft] = useState<SensitivityConfig>(() => ({ ...sensitivityConfig, mode: 'custom' }));
  const [showConfirmReset, setShowConfirmReset] = useState(false);
  const dirty = JSON.stringify(formConfig) !== JSON.stringify(sensitivityConfig);
  const isCustom = formConfig.mode === 'custom';

  useEffect(() => {
    setFormConfig({ ...sensitivityConfig });
    if (sensitivityConfig.mode === 'custom') setCustomDraft({ ...sensitivityConfig });
  }, [sensitivityConfig]);

  const handleSelectMode = (mode: SensitivityMode) => {
    if (mode === 'custom') {
      setFormConfig((current) => ({ ...customDraft, mode: 'custom', ...(current.mode === 'custom' ? current : {}) }));
      return;
    }
    setFormConfig({ mode, ...presetSensitivityConfigs[mode] });
  };

  const updateValue = <K extends keyof Omit<SensitivityConfig, 'mode'>>(key: K, value: number) => {
    setFormConfig((current) => {
      const next = { ...current, [key]: value };
      const mode = identifyMode(next);
      const updated = { ...next, mode };
      if (mode === 'custom') setCustomDraft(updated);
      return updated;
    });
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validConfig(formConfig)) {
      showToast('toast_invalid_sensitivity', 'error');
      return;
    }
    if (dirty) saveSensitivityConfig(formConfig);
  };

  const handleConfirmReset = () => {
    if (!resetSensitivityConfig()) return;
    setCustomDraft({ ...defaultSensitivityConfig, mode: 'custom' });
    setShowConfirmReset(false);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-white">{t('sensitivity_title')}</h1>
          <p className="text-xs text-slate-400 mt-0.5">{t('sensitivity_desc')}</p>
        </div>
        <div className="flex items-center space-x-3">
          <button type="button" onClick={() => setShowConfirmReset(true)} className="inline-flex items-center px-3 py-2 bg-navy-850 hover:bg-navy-800 text-slate-300 rounded-lg text-sm border border-navy-750 transition">
            <RotateCcw className="w-4 h-4 mr-1.5" />{t('reset_default')}
          </button>
          <button type="submit" form="sensitivity-form" disabled={!dirty || !validConfig(formConfig)} className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:bg-navy-800 disabled:text-slate-500 disabled:cursor-not-allowed text-white rounded-lg text-sm font-medium shadow transition">
            <Save className="w-4 h-4 mr-1.5" />{t('save')}
          </button>
        </div>
      </div>

      <Card>
        <span className="text-xs text-slate-400 font-medium block mb-3">{t('sensitivity_modes_label')}</span>
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
          {(['normal', 'strong_wind', 'leaf_fall', 'custom'] as SensitivityMode[]).map((mode) => (
            <button key={mode} type="button" aria-pressed={formConfig.mode === mode} onClick={() => handleSelectMode(mode)} className={`py-3 px-4 rounded-lg border text-sm font-medium transition ${formConfig.mode === mode ? 'bg-blue-600 border-blue-500 text-white shadow' : 'bg-navy-850 border-navy-800 text-slate-300 hover:bg-navy-800'}`}>
              {t(`mode_${mode}`)}
            </button>
          ))}
        </div>
      </Card>

      <Card>
        <div className="flex items-center space-x-2 pb-3 mb-5 border-b border-navy-800">
          <Sliders className="w-5 h-5 text-blue-400" />
          <h2 className="text-base font-semibold text-slate-200">{t(`mode_${formConfig.mode}`)}</h2>
          {!isCustom && <span className="text-xs text-slate-400 italic">({t('sensitivity_preset_note')})</span>}
        </div>

        <form id="sensitivity-form" onSubmit={handleSave} className="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-6">
          <div className="space-y-2 bg-navy-850 p-4 rounded-lg border border-navy-800">
            <div className="flex justify-between items-center text-sm font-medium"><label htmlFor="laser-slider" className="text-slate-300">{t('laser_sensitivity_label')}</label><span className="font-mono text-blue-400 font-bold">{formConfig.laserSensitivity}%</span></div>
            <input id="laser-slider" type="range" min="10" max="100" step="1" disabled={!isCustom} value={formConfig.laserSensitivity} onChange={(e) => updateValue('laserSensitivity', Number(e.target.value))} className="w-full accent-blue-500 disabled:opacity-50 cursor-pointer" />
          </div>
          <div className="space-y-2 bg-navy-850 p-4 rounded-lg border border-navy-800">
            <div className="flex justify-between items-center text-sm font-medium"><label htmlFor="count-slider" className="text-slate-300">{t('confirm_count_label')}</label><span className="font-mono text-blue-400 font-bold">{formConfig.confirmCount}</span></div>
            <input id="count-slider" type="range" min="1" max="10" step="1" disabled={!isCustom} value={formConfig.confirmCount} onChange={(e) => updateValue('confirmCount', Number(e.target.value))} className="w-full accent-blue-500 disabled:opacity-50 cursor-pointer" />
          </div>
          <div className="space-y-2 bg-navy-850 p-4 rounded-lg border border-navy-800">
            <label htmlFor="window-select" className="block text-sm font-medium text-slate-300">{t('confirm_window_label')}</label>
            <select id="window-select" disabled={!isCustom} value={formConfig.confirmWindow} onChange={(e) => updateValue('confirmWindow', Number(e.target.value))} className="w-full bg-navy-900 border border-navy-700 rounded px-3 py-2 text-sm text-slate-100 disabled:opacity-50 focus:outline-none focus:border-blue-500">
              {WINDOW_OPTIONS.map((seconds) => <option key={seconds} value={seconds}>{seconds} {t('unit_seconds')}</option>)}
            </select>
          </div>
          <div className="space-y-2 bg-navy-850 p-4 rounded-lg border border-navy-800">
            <label htmlFor="humidity-input" className="block text-sm font-medium text-slate-300">{t('humidity_threshold_label')}</label>
            <input id="humidity-input" type="number" min="30" max="95" step="1" disabled={!isCustom} value={formConfig.humidityThreshold} onChange={(e) => updateValue('humidityThreshold', e.target.value === '' ? Number.NaN : Number(e.target.value))} className="w-full bg-navy-900 border border-navy-700 rounded px-3 py-2 text-sm text-slate-100 disabled:opacity-50 focus:outline-none focus:border-blue-500 font-mono" />
          </div>
        </form>

        <div className="mt-5 flex items-start space-x-2 text-xs text-slate-300 bg-navy-850 p-3 rounded border border-navy-800"><AlertCircle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" /><span>{t('sensitivity_local_notice')}</span></div>
        <div className="mt-3 text-xs text-slate-400" role="status">{dirty ? t('sensitivity_unsaved') : t('sensitivity_saved')}</div>
        {!validConfig(formConfig) && <p className="mt-2 text-xs text-red-300" role="alert">{t('toast_invalid_sensitivity')}</p>}
      </Card>

      {showConfirmReset && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4" onClick={() => setShowConfirmReset(false)}>
          <div role="dialog" aria-modal="true" aria-labelledby="reset-title" className="w-full max-w-sm bg-navy-900 border border-navy-700 rounded-lg p-5 shadow-xl text-slate-100 space-y-4" onClick={(e) => e.stopPropagation()}>
            <h3 id="reset-title" className="font-semibold text-sm">{t('reset_default')}</h3>
            <p className="text-xs text-slate-300">{t('confirm_reset_prompt')}</p>
            <div className="flex justify-end space-x-3 pt-2">
              <button type="button" onClick={() => setShowConfirmReset(false)} className="px-3 py-1.5 text-xs bg-navy-800 hover:bg-navy-700 rounded text-slate-300">{t('cancel')}</button>
              <button type="button" onClick={handleConfirmReset} className="px-3 py-1.5 text-xs bg-red-600 hover:bg-red-500 rounded text-white font-medium">{t('confirm')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
