// Thành phần chính: Bố cục Sidebar + Header + Chọn trang theo state page
import React, { useState, useEffect } from 'react';
import {
  LayoutDashboard,
  Shield,
  Sliders,
  Bell,
  Cpu,
  Settings as SettingsIcon,
  AlertTriangle,
  X,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';
import { StoreProvider, useStore } from './store';
import { StatusBadge } from './components/StatusBadge';
import { SettingsModal } from './components/SettingsModal';
import { Toast } from './components/Toast';
import { Overview } from './pages/Overview';
import { Fence } from './pages/Fence';
import { Sensitivity } from './pages/Sensitivity';
import { Alerts } from './pages/Alerts';
import { Devices } from './pages/Devices';
import { PageId } from './types';
import { t, TranslationKey } from './i18n';

const SIDEBAR_STORAGE_KEY = 'smart_fence_sidebar_collapsed';

const AppContent: React.FC = () => {
  const {
    page,
    setPage,
    language,
    securityStatus,
    currentTime,
    unresolvedCount,
    unresolvedDangerCount,
    dangerBannerDismissed,
    dismissDangerBanner,
    setIsSettingsOpen,
  } = useStore();

  // Trạng thái thu gọn sidebar lưu localStorage
  const [isCollapsed, setIsCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem(SIDEBAR_STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(SIDEBAR_STORAGE_KEY, String(isCollapsed));
    } catch {
      // Bỏ qua lỗi storage
    }
  }, [isCollapsed]);

  // Danh mục 5 trang trên Sidebar (mỗi mục chỉ 1 tên theo ngôn ngữ đang chọn)
  const navItems: { id: PageId; icon: React.ElementType; titleKey: TranslationKey }[] = [
    { id: 'overview', icon: LayoutDashboard, titleKey: 'nav_overview' },
    { id: 'fence', icon: Shield, titleKey: 'nav_fence' },
    { id: 'sensitivity', icon: Sliders, titleKey: 'nav_sensitivity' },
    { id: 'alerts', icon: Bell, titleKey: 'nav_alerts' },
    { id: 'devices', icon: Cpu, titleKey: 'nav_devices' },
  ];

  return (
    <div className="flex h-screen bg-navy-950 text-slate-100 overflow-hidden font-sans select-none min-w-[1280px]">
      {/* ========================================================= */}
      {/* SIDEBAR THU GỌN / MỞ RỘNG (72px / 240px) */}
      {/* ========================================================= */}
      <aside
        className={`${
          isCollapsed ? 'w-[72px]' : 'w-60'
        } bg-navy-900 border-r border-navy-800 flex flex-col justify-between shrink-0 transition-[width] duration-300 ease-in-out`}
      >
        <div>
          {/* Logo & nút thu gọn */}
          <div
            className={`p-4 border-b border-navy-800 flex items-center ${
              isCollapsed ? 'justify-center flex-col space-y-3' : 'justify-between space-x-2'
            }`}
          >
            {!isCollapsed ? (
              <div className="flex items-center space-x-3 overflow-hidden min-w-0">
                <div className="w-9 h-9 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400 shrink-0">
                  <Shield className="w-5 h-5" />
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-extrabold tracking-wider text-white truncate">
                    {t('app_title')}
                  </div>
                  <div className="text-[10px] text-slate-400 truncate">
                    {t('app_subtitle')}
                  </div>
                </div>
              </div>
            ) : (
              <div
                className="w-9 h-9 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400 shrink-0"
                title={`${t('app_title')} - ${t('app_subtitle')}`}
              >
                <Shield className="w-5 h-5" />
              </div>
            )}

            <button
              type="button"
              onClick={() => setIsCollapsed(!isCollapsed)}
              className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-navy-850 transition shrink-0"
              title={isCollapsed ? t('expand_sidebar') : t('collapse_sidebar')}
              aria-label={isCollapsed ? t('expand_sidebar') : t('collapse_sidebar')}
            >
              {isCollapsed ? (
                <PanelLeftOpen className="w-5 h-5" />
              ) : (
                <PanelLeftClose className="w-5 h-5" />
              )}
            </button>
          </div>

          {/* Danh sách mục chuyển trang */}
          <nav className="p-3 space-y-1.5">
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = page === item.id;
              const itemTitle = t(item.titleKey);

              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setPage(item.id)}
                  title={itemTitle}
                  className={`w-full flex items-center ${
                    isCollapsed ? 'justify-center px-0 py-3' : 'px-3 py-2.5'
                  } rounded-lg text-left transition ${
                    isActive
                      ? 'bg-blue-600 text-white font-medium shadow'
                      : 'text-slate-400 hover:bg-navy-850 hover:text-slate-200'
                  }`}
                >
                  <Icon
                    className={`w-5 h-5 shrink-0 ${
                      isActive ? 'text-white' : 'text-slate-400'
                    }`}
                  />
                  {!isCollapsed && (
                    <span className="ml-3 text-xs font-medium truncate">{itemTitle}</span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        {/* Footer Sidebar */}
        <div
          className={`p-3 border-t border-navy-800 text-[11px] text-slate-500 flex items-center ${
            isCollapsed ? 'justify-center' : 'justify-between'
          }`}
        >
          {isCollapsed ? (
            <span title={t('simulated_data_badge')}>v1.0</span>
          ) : (
            <>
              <span>v1.0.0</span>
              <span className="px-2 py-0.5 rounded bg-navy-800 text-slate-400 border border-navy-700">
                {t('simulated_data_badge')}
              </span>
            </>
          )}
        </div>
      </aside>

      {/* ========================================================= */}
      {/* KHU VỰC NỘI DUNG CHÍNH (Header + Nội dung trang) */}
      {/* ========================================================= */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Header trên cùng */}
        <header className="h-16 bg-navy-900 border-b border-navy-800 px-6 flex items-center justify-between shrink-0">
          <div className="flex items-center space-x-4">
            <span className="text-sm font-semibold text-slate-200">{t('app_title')}</span>
            <StatusBadge status={securityStatus} />
            <span className="text-[11px] text-slate-400 border-l border-navy-700 pl-4">
              {t('updated_at')} <span className="font-mono text-slate-200">{currentTime}</span>
            </span>
          </div>

          <div className="flex items-center space-x-3">
            {/* Chuông cảnh báo có số lượng chưa xử lý */}
            <button
              type="button"
              onClick={() => setPage('alerts')}
              className="relative p-2 rounded-lg bg-navy-850 border border-navy-800 text-slate-300 hover:text-white hover:bg-navy-800 transition"
              title={t('nav_alerts')}
            >
              <Bell className="w-4 h-4" />
              {unresolvedCount > 0 && (
                <span className="absolute -top-1 -right-1 flex h-4 min-w-[16px] px-1 items-center justify-center rounded-full bg-red-600 text-[10px] font-bold text-white">
                  {unresolvedCount}
                </span>
              )}
            </button>

            {/* Bánh răng cài đặt */}
            <button
              type="button"
              onClick={() => setIsSettingsOpen(true)}
              className="p-2 rounded-lg bg-navy-850 border border-navy-800 text-slate-300 hover:text-white hover:bg-navy-800 transition"
              title={t('settings_title')}
            >
              <SettingsIcon className="w-4 h-4" />
            </button>
          </div>
        </header>

        {/* Banner đỏ nguy hiểm nếu có sự kiện nguy hiểm chưa xử lý */}
        {unresolvedDangerCount > 0 && !dangerBannerDismissed && (
          <div className="bg-red-950 border-b border-red-700 px-6 py-2.5 flex items-center justify-between text-red-200 animate-pulse">
            <div className="flex items-center space-x-2 text-xs font-semibold tracking-wide">
              <AlertTriangle className="w-4 h-4 text-red-400" />
              <span>{t('danger_banner_text')}</span>
            </div>
            <button
              type="button"
              onClick={dismissDangerBanner}
              className="inline-flex items-center px-2 py-1 rounded bg-red-900/60 hover:bg-red-800 text-red-200 text-[11px] font-medium transition"
            >
              <X className="w-3.5 h-3.5 mr-1" />
              {t('dismiss_banner')}
            </button>
          </div>
        )}

        {/* Nội dung trang theo state page */}
        <main className="flex-1 overflow-y-auto p-6 bg-navy-950">
          {page === 'overview' && <Overview />}
          {page === 'fence' && <Fence />}
          {page === 'sensitivity' && <Sensitivity />}
          {page === 'alerts' && <Alerts />}
          {page === 'devices' && <Devices />}
        </main>
      </div>

      {/* Modal Cài đặt */}
      <SettingsModal />

      {/* Thông báo Toast toàn cục */}
      <Toast />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <StoreProvider>
      <AppContent />
    </StoreProvider>
  );
};

export default App;
