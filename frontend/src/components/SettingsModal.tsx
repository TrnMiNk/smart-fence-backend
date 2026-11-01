// Modal cài đặt hệ thống (thiết kế theo khối để dễ mở rộng sau này)
import React from 'react';
import { X, Globe } from 'lucide-react';
import { useStore } from '../store';
import { t } from '../i18n';

export const SettingsModal: React.FC = () => {
  const { isSettingsOpen, setIsSettingsOpen, language, changeLanguage } = useStore();

  if (!isSettingsOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      onClick={() => setIsSettingsOpen(false)}
    >
      <div
        className="w-full max-w-md bg-navy-900 border border-navy-700 rounded-lg shadow-xl overflow-hidden text-slate-100"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header Modal */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-navy-800 bg-navy-850">
          <h2 className="text-base font-semibold">{t('settings_title')}</h2>
          <button
            onClick={() => setIsSettingsOpen(false)}
            className="p-1 rounded text-slate-400 hover:text-white hover:bg-navy-700 transition"
            aria-label={t('close')}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Nội dung các khối cài đặt */}
        <div className="p-5 space-y-6">
          {/* ========================================================= */}
          {/* KHỐI 1: Ngôn ngữ / Language */}
          {/* ========================================================= */}
          <div className="space-y-3">
            <div className="flex items-center text-sm font-medium text-slate-300">
              <Globe className="w-4 h-4 mr-2 text-blue-400" />
              <span>{t('setting_language_title')}</span>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => changeLanguage('vi')}
                className={`py-2 px-3 text-sm rounded border font-medium transition ${
                  language === 'vi'
                    ? 'bg-blue-600 border-blue-500 text-white'
                    : 'bg-navy-800 border-navy-700 text-slate-300 hover:bg-navy-750'
                }`}
              >
                {t('lang_vi')}
              </button>

              <button
                type="button"
                onClick={() => changeLanguage('en')}
                className={`py-2 px-3 text-sm rounded border font-medium transition ${
                  language === 'en'
                    ? 'bg-blue-600 border-blue-500 text-white'
                    : 'bg-navy-800 border-navy-700 text-slate-300 hover:bg-navy-750'
                }`}
              >
                {t('lang_en')}
              </button>
            </div>
          </div>

          {/* ========================================================= */}
          {/* (Thêm các khối cài đặt khác vào đây khi mở rộng sau này) */}
          {/* ========================================================= */}
        </div>

        {/* Footer Modal */}
        <div className="flex justify-end px-5 py-3 border-t border-navy-800 bg-navy-850">
          <button
            type="button"
            onClick={() => setIsSettingsOpen(false)}
            className="px-4 py-1.5 text-sm rounded bg-navy-700 hover:bg-navy-600 text-slate-200 transition font-medium"
          >
            {t('close')}
          </button>
        </div>
      </div>
    </div>
  );
};
