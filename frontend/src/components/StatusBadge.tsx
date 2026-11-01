// Huy hiệu hiển thị màu sắc và trạng thái hệ thống
import React from 'react';
import { t, TranslationKey } from '../i18n';

export type BadgeVariant =
  | 'safe'
  | 'suspicious'
  | 'intrusion'
  | 'paused'
  | 'unknown'
  | 'online'
  | 'offline'
  | 'info'
  | 'warning'
  | 'danger'
  | 'unresolved'
  | 'resolved'
  | 'false_alarm';

interface StatusBadgeProps {
  status: BadgeVariant;
  labelKey?: TranslationKey;
  className?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, labelKey, className = '' }) => {
  // Lựa chọn kiểu màu sắc tương ứng theo trạng thái
  const getBadgeStyle = () => {
    switch (status) {
      case 'safe':
      case 'online':
      case 'resolved':
        return 'bg-emerald-950/80 text-emerald-400 border border-emerald-700/60';

      case 'suspicious':
      case 'warning':
        return 'bg-yellow-950/80 text-yellow-400 border border-yellow-700/60';

      case 'intrusion':
      case 'danger':
        // Cảnh báo đỏ nhấp nháy nhẹ
        return 'bg-red-950 text-red-400 border border-red-700 animate-pulse font-medium';

      case 'offline':
      case 'false_alarm':
        return 'bg-slate-800 text-slate-400 border border-slate-700';

      case 'paused':
      case 'unknown':
      case 'info':
      default:
        return 'bg-blue-950/70 text-blue-400 border border-blue-800/60';
    }
  };

  // Xác định key ngôn ngữ mặc định nếu không truyền labelKey
  const resolveKey = (): TranslationKey => {
    if (labelKey) return labelKey;
    switch (status) {
      case 'safe':
        return 'status_safe';
      case 'suspicious':
        return 'status_suspicious';
      case 'intrusion':
        return 'status_intrusion';
      case 'paused':
        return 'status_paused';
      case 'unknown':
        return 'status_unknown';
      case 'online':
        return 'status_online';
      case 'offline':
        return 'status_offline';
      case 'info':
        return 'severity_info';
      case 'warning':
        return 'severity_warning';
      case 'danger':
        return 'severity_danger';
      case 'unresolved':
        return 'status_unresolved';
      case 'resolved':
        return 'status_resolved';
      case 'false_alarm':
        return 'status_false_alarm';
    }
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium tracking-wide ${getBadgeStyle()} ${className}`}
    >
      <span className="w-1.5 h-1.5 rounded-full mr-1.5 bg-current opacity-80" />
      {t(resolveKey())}
    </span>
  );
};
