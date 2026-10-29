import React from 'react';
import {
  LayoutDashboard,
  ClipboardList,
  Coffee,
  BadgePercent,
  QrCode,
  Users,
  Settings,
  LogOut,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export type NavTab = 'dashboard' | 'orders' | 'menu' | 'promos' | 'tables' | 'staff' | 'settings';

interface SidebarProps {
  currentTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
  ordersCount?: number;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentTab, onSelectTab, ordersCount = 0 }) => {
  const { signOut, staffUser } = useAuth();

  const navItems: { id: NavTab; label: string; icon: React.ReactNode; badge?: number }[] = [
    {
      id: 'dashboard',
      label: 'Ringkasan',
      icon: <LayoutDashboard className="w-5 h-5" />,
    },
    {
      id: 'orders',
      label: 'Antrean Pesanan',
      icon: <ClipboardList className="w-5 h-5" />,
      badge: ordersCount > 0 ? ordersCount : undefined,
    },
    {
      id: 'menu',
      label: 'Katalog Menu',
      icon: <Coffee className="w-5 h-5" />,
    },
    {
      id: 'promos',
      label: 'Promo & Diskon',
      icon: <BadgePercent className="w-5 h-5" />,
    },
    {
      id: 'tables',
      label: 'Meja & QR Code',
      icon: <QrCode className="w-5 h-5" />,
    },
    {
      id: 'staff',
      label: 'Kelola Staf',
      icon: <Users className="w-5 h-5" />,
    },
    {
      id: 'settings',
      label: 'Pengaturan',
      icon: <Settings className="w-5 h-5" />,
    },
  ];

  return (
    <aside className="w-64 bg-stone-900 text-stone-200 flex flex-col flex-shrink-0 min-h-screen border-r border-stone-800">
      {/* Brand */}
      <div className="p-6 border-b border-stone-800 flex items-center gap-3">
        <img
          src="/logo.webp"
          alt="Kopi Inggil"
          className="w-10 h-10 rounded-xl object-cover bg-[#FAF8F0] p-0.5 shadow-md shadow-amber-950/40 border border-stone-700 flex-shrink-0"
        />
        <div>
          <h1 className="font-bold text-lg text-white tracking-wide">Kopi Inggil</h1>
          <p className="text-xs text-stone-400">Panel Manajemen Admin</p>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 p-4 space-y-1.5 overflow-y-auto">
        <div className="px-3 py-2 text-xs font-semibold uppercase tracking-wider text-stone-400">
          Menu Utama
        </div>
        {navItems.map((item) => {
          const isActive = currentTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onSelectTab(item.id)}
              className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-lg text-sm font-medium transition-all ${
                isActive
                  ? 'bg-amber-600 text-white shadow-sm'
                  : 'text-stone-300 hover:bg-stone-800 hover:text-white'
              }`}
            >
              <div className="flex items-center gap-3">
                {item.icon}
                <span>{item.label}</span>
              </div>
              {item.badge !== undefined && (
                <span
                  className={`text-xs px-2 py-0.5 rounded-full font-bold ${
                    isActive ? 'bg-amber-800 text-amber-100' : 'bg-amber-500/20 text-amber-400'
                  }`}
                >
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* User profile & actions */}
      <div className="p-4 border-t border-stone-800 bg-stone-950/40">
        <div className="flex items-center justify-between mb-2">
          <div className="truncate">
            <div className="font-medium text-sm text-white truncate">
              {staffUser?.name || 'Admin'}
            </div>
            <div className="text-xs text-stone-400 capitalize">
              {staffUser?.role?.toLowerCase() || 'Administrator'}
            </div>
          </div>
        </div>
        <button
          onClick={signOut}
          className="w-full mt-2 flex items-center justify-center gap-2 px-3 py-2 text-xs font-medium text-stone-400 hover:text-red-400 hover:bg-red-950/20 rounded-lg transition-colors border border-stone-800"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span>Keluar Sesi</span>
        </button>
      </div>
    </aside>
  );
};
