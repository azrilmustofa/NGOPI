import React, { useState, useEffect } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Sidebar, type NavTab } from './components/Sidebar';
import { Header } from './components/Header';
import { DashboardPage } from './pages/DashboardPage';
import { OrdersPage } from './pages/OrdersPage';
import { MenuPage } from './pages/MenuPage';
import { TablesPage } from './pages/TablesPage';
import { StaffPage } from './pages/StaffPage';
import { SettingsPage } from './pages/SettingsPage';
import { LoginPage } from './pages/LoginPage';
import { supabase } from './lib/supabase';

const AdminLayout: React.FC = () => {
  const { user, loading } = useAuth();
  const [currentTab, setCurrentTab] = useState<NavTab>('dashboard');
  const [activeOrdersCount, setActiveOrdersCount] = useState(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  // Fetch active orders count for badge
  const updateBadgeCount = async () => {
    try {
      const { data } = await supabase
        .from('customer_order')
        .select('id, status');

      if (data) {
        const count = data.filter((o: any) =>
          ['PAID', 'ACCEPTED', 'PREPARING', 'READY'].includes(o.status)
        ).length;
        setActiveOrdersCount(count);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    updateBadgeCount();
    const interval = setInterval(updateBadgeCount, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await updateBadgeCount();
    setRefreshKey((k) => k + 1);
    setTimeout(() => setIsRefreshing(false), 500);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-stone-900 flex items-center justify-center text-white">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-4 border-amber-600 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm font-medium text-stone-400">Memuat Kopi Inggil Admin...</span>
        </div>
      </div>
    );
  }

  if (!user) {
    return <LoginPage />;
  }

  const getPageInfo = (): { title: string; subtitle: string } => {
    switch (currentTab) {
      case 'dashboard':
        return {
          title: 'Ringkasan & Metrik Kafe',
          subtitle: 'Performa penjualan, omzet harian, dan ringkasan antrean masuk.',
        };
      case 'orders':
        return {
          title: 'Antrean & Riwayat Pesanan',
          subtitle: 'Monitor pesanan real-time dari pelanggan dan kelola status penyajian.',
        };
      case 'menu':
        return {
          title: 'Manajemen Katalog Menu',
          subtitle: 'Daftar produk, pengaturan harga, status stok habis/tersedia, dan kategori.',
        };
      case 'tables':
        return {
          title: 'Manajemen Meja & Kode QR',
          subtitle: 'Daftar nomor meja serta pencetakan kode QR stiker dine-in.',
        };
      case 'staff':
        return {
          title: 'Akses & Akun Staf',
          subtitle: 'Kelola akun barista, kasir, dan administrator Kopi Inggil.',
        };
      case 'settings':
        return {
          title: 'Pengaturan Operasional & Biaya',
          subtitle: 'Konfigurasi pajak restoran (PB1), biaya layanan, dan koneksi Supabase.',
        };
      default:
        return { title: 'Admin', subtitle: '' };
    }
  };

  const { title, subtitle } = getPageInfo();

  return (
    <div className="flex min-h-screen bg-stone-100 font-sans">
      <Sidebar
        currentTab={currentTab}
        onSelectTab={setCurrentTab}
        ordersCount={activeOrdersCount}
      />

      <div className="flex-1 flex flex-col min-w-0">
        <Header
          title={title}
          subtitle={subtitle}
          onRefresh={handleRefresh}
          isRefreshing={isRefreshing}
        />

        <main className="flex-1 overflow-y-auto" key={refreshKey}>
          {currentTab === 'dashboard' && <DashboardPage onNavigate={setCurrentTab} />}
          {currentTab === 'orders' && <OrdersPage />}
          {currentTab === 'menu' && <MenuPage />}
          {currentTab === 'tables' && <TablesPage />}
          {currentTab === 'staff' && <StaffPage />}
          {currentTab === 'settings' && <SettingsPage />}
        </main>
      </div>
    </div>
  );
};

export default function App() {
  return (
    <AuthProvider>
      <AdminLayout />
    </AuthProvider>
  );
}
