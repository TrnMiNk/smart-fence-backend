// Component hiển thị thông báo Toast góc dưới màn hình
import React from 'react';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';
import { useStore } from '../store';
import { t, TranslationKey } from '../i18n';

export const Toast: React.FC = () => {
  const { toasts, removeToast } = useStore();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-5 right-5 z-50 flex flex-col space-y-2 pointer-events-none">
      {toasts.map((item) => {
        const isError = item.type === 'error';
        const isInfo = item.type === 'info';

        return (
          <div
            key={item.id}
            className={`pointer-events-auto flex items-center px-4 py-3 rounded-lg shadow-lg border text-sm max-w-sm ${
              isError
                ? 'bg-red-950/95 border-red-700 text-red-200'
                : isInfo
                ? 'bg-blue-950/95 border-blue-700 text-blue-200'
                : 'bg-emerald-950/95 border-emerald-700 text-emerald-200'
            }`}
          >
            {isError ? (
              <AlertTriangle className="w-5 h-5 mr-3 shrink-0 text-red-400" />
            ) : isInfo ? (
              <Info className="w-5 h-5 mr-3 shrink-0 text-blue-400" />
            ) : (
              <CheckCircle2 className="w-5 h-5 mr-3 shrink-0 text-emerald-400" />
            )}

            <span className="flex-1 mr-2">{t(item.messageKey as TranslationKey)}</span>

            <button
              onClick={() => removeToast(item.id)}
              className="p-1 rounded hover:bg-black/20 text-slate-300 transition"
              aria-label={t('close')}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        );
      })}
    </div>
  );
};
