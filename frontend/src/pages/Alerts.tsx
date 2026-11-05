// Trang Cảnh báo và lịch sử: tải và xử lý sự kiện từ Backend.
import React, { useMemo, useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { Card } from '../components/Card';
import { StatusBadge, BadgeVariant } from '../components/StatusBadge';
import { t } from '../i18n';
import { useStore } from '../store';
import { resolveIntrusionEvent } from '../services/overviewService';

const normalizedStatus = (status: string): 'unresolved' | 'resolved' | null => {
  switch (status.trim().toUpperCase()) {
    case 'NEW':
    case 'UNRESOLVED':
      return 'unresolved';
    case 'RESOLVED':
      return 'resolved';
    default:
      return null;
  }
};

const eventTypeLabel = (eventType: string): string => {
  if (eventType.trim().toUpperCase() === 'LASER_BLOCKED') return t('event_laser_blocked');
  return eventType.replace(/_/g, ' ');
};

// Backend sends LocalDateTime without an offset; its values represent Vietnam local time.
const formatVietnamLocalDateTime = (value: string): string => {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?$/.exec(value);
  if (!match) return value;
  const [, year, month, day, hour, minute, second, fraction = '0'] = match;
  const milliseconds = Number(`0.${fraction}`) * 1000;
  const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour) - 7, Number(minute), Number(second), milliseconds));
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).format(date);
};

export const Alerts: React.FC = () => {
  const { events, eventsLoading: loading, eventsError: loadError, updateEvent } = useStore();
  const [actionError, setActionError] = useState<string | null>(null);
  const [resolvingId, setResolvingId] = useState<number | null>(null);
  const [filterType, setFilterType] = useState('all');
  const [filterStatus, setFilterStatus] = useState('all');

  const eventTypes = useMemo(() => [...new Set(events.map((event) => event.eventType))], [events]);
  const filteredEvents = useMemo(() => events.filter((event) => {
    if (filterType !== 'all' && event.eventType !== filterType) return false;
    if (filterStatus !== 'all' && normalizedStatus(event.eventStatus) !== filterStatus) return false;
    return true;
  }), [events, filterType, filterStatus]);

  const handleResolve = async (eventId: number) => {
    setResolvingId(eventId);
    setActionError(null);
    try {
      const updatedEvent = await resolveIntrusionEvent(eventId);
      updateEvent(updatedEvent);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : 'Không thể xử lý sự kiện.');
    } finally {
      setResolvingId(null);
    }
  };

  return (
    <div className="mx-auto max-w-[1500px] space-y-5 sm:space-y-7">
      <div>
          <p className="mb-1 text-[10px] font-semibold uppercase tracking-[0.2em] text-cyan-400">EVENT REVIEW</p><h1 className="text-2xl font-semibold tracking-tight text-white sm:text-3xl">{t('alerts_title')}</h1>
        <p className="text-xs text-slate-400 mt-0.5">{t('alerts_desc')}</p>
      </div>

      <Card>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-xs text-slate-400 font-medium mb-1.5">{t('filter_event_type')}</label>
            <select
              value={filterType}
              onChange={(event) => setFilterType(event.target.value)}
              className="w-full bg-navy-850 border border-navy-750 rounded px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-blue-500"
            >
              <option value="all">{t('all')}</option>
              {eventTypes.map((type) => <option key={type} value={type}>{eventTypeLabel(type)}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 font-medium mb-1.5">{t('filter_status')}</label>
            <select
              value={filterStatus}
              onChange={(event) => setFilterStatus(event.target.value)}
              className="w-full bg-navy-850 border border-navy-750 rounded px-3 py-2 text-xs text-slate-100 focus:outline-none focus:border-blue-500"
            >
              <option value="all">{t('all')}</option>
              <option value="unresolved">{t('status_unresolved')}</option>
              <option value="resolved">{t('status_resolved')}</option>
            </select>
          </div>
        </div>
      </Card>

      <Card>
        {actionError && <p role="alert" className="mb-3 text-xs text-red-400">{actionError}</p>}
        {loading ? (
          <div className="py-12 text-center text-slate-400 text-sm">{t('alerts_loading')}</div>
        ) : loadError ? (
          <div role="alert" className="py-12 text-center text-red-400 text-sm">{loadError}</div>
        ) : filteredEvents.length === 0 ? (
          <div className="py-12 text-center text-slate-400 text-sm">{t('no_events')}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-navy-850 text-slate-400 uppercase text-[11px] border-b border-navy-800">
                <tr>
                  <th className="py-3 px-3">{t('col_id')}</th>
                  <th className="py-3 px-3">{t('col_time')}</th>
                  <th className="py-3 px-3">{t('alert_device_component')}</th>
                  <th className="py-3 px-3">{t('col_type')}</th>
                  <th className="py-3 px-3">{t('alert_description')}</th>
                  <th className="py-3 px-3">{t('col_status')}</th>
                  <th className="py-3 px-3 text-right">{t('col_actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-navy-800/60">
                {filteredEvents.map((event) => {
                  const status = normalizedStatus(event.eventStatus);
                  const badgeStatus: BadgeVariant | null = status;
                  return (
                    <tr key={event.id} className="hover:bg-navy-850/50">
                      <td className="py-3 px-3 font-mono text-slate-300 font-semibold">{event.id}</td>
                      <td className="py-3 px-3 font-mono text-slate-400">{formatVietnamLocalDateTime(event.detectedAt)}</td>
                      <td className="py-3 px-3 font-medium text-slate-200">{event.deviceId} / {event.componentId ?? '—'}</td>
                      <td className="py-3 px-3">{eventTypeLabel(event.eventType)}</td>
                      <td className="py-3 px-3 max-w-xs truncate" title={event.description ?? undefined}>{event.description || '—'}</td>
                      <td className="py-3 px-3">
                        {badgeStatus ? <StatusBadge status={badgeStatus} /> : event.eventStatus}
                      </td>
                      <td className="py-3 px-3 text-right">
                        {status === 'unresolved' ? (
                          <button
                            type="button"
                            onClick={() => void handleResolve(event.id)}
                            disabled={resolvingId !== null}
                            className="inline-flex items-center px-2.5 py-1 rounded bg-emerald-600/20 text-emerald-400 hover:bg-emerald-600/30 border border-emerald-600/40 text-[11px] font-medium transition disabled:opacity-50 disabled:cursor-wait"
                          >
                            <CheckCircle2 className="w-3.5 h-3.5 mr-1" />
                            {resolvingId === event.id ? t('alert_resolving') : t('btn_resolve')}
                          </button>
                        ) : (
                          <span className="text-slate-500 text-[11px]">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};
