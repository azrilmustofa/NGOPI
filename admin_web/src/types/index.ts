export type OrderStatus =
  | 'AWAITING_PAYMENT'
  | 'PAID'
  | 'ACCEPTED'
  | 'PREPARING'
  | 'READY'
  | 'COMPLETED'
  | 'CANCELLED';

export interface CustomerOrder {
  id: string;
  public_id: string;
  table_id: string;
  owner_uid?: string;
  checkout_key?: string;
  status: OrderStatus;
  subtotal_rp: number;
  tax_rp: number;
  service_charge_rp: number;
  total_rp: number;
  cancellation_reason?: string | null;
  created_at: string;
}

export interface OrderItem {
  id: string;
  order_id: string;
  menu_item_id: string;
  name_snapshot: string;
  unit_price_rp: number;
  quantity: number;
  note?: string | null;
  line_total_rp: number;
  options?: OrderItemOption[];
}

export interface OrderItemOption {
  id?: string;
  order_item_id?: string;
  menu_option_id?: string;
  option_group_name_snapshot?: string;
  option_name_snapshot?: string;
  price_delta_rp: number;
}

export interface CafeTable {
  id: string;
  label: string;
  is_active: boolean;
  qr_image_url?: string | null;
}

export interface MenuCategory {
  id: string;
  name: string;
  display_order: number;
  is_active: boolean;
  created_at?: string;
}

export interface MenuItem {
  id: string;
  category_id: string;
  name: string;
  description: string;
  photo_url: string;
  allergy_info?: string | null;
  base_price_rp: number;
  is_available: boolean;
  is_active: boolean;
  created_at?: string;
}

export interface StaffUser {
  uid: string;
  name: string;
  role: 'ADMIN' | 'BARISTA' | 'KASIR';
  is_active: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface AppSettings {
  id: string;
  tax_percentage: number;
  service_charge_percentage: number;
}
