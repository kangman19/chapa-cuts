/** Must match ServiceCatalog.DEPOSIT in the backend. */
export const DEPOSIT = 1;

export type Service = {
  id: string;
  name: string;
  price: number | null;
  pricedAtShop: boolean;
};

export type Availability = {
  date: string;
  days: string[];
  slots: { time: string; available: boolean }[];
};

export type BookingStatus = 'NEW' | 'AWAITING_PAYMENT' | 'CONFIRMED' | 'FAILED' | 'EXPIRED';

export type Booking = {
  ref: string;
  status: BookingStatus;
  serviceId: string;
  serviceName: string;
  price: number | null;
  deposit: number;
  balance: number | null;
  date: string;
  slotTime: string;
  customerName: string;
  phone: string;
  note?: string;
  imageId?: string;
  paymentMethod?: 'MPESA' | 'CARD';
  checkoutUrl?: string;
  accessCode?: string;
  receipt?: string;
  message?: string;
};

export type BarberEntry = {
  time: string;
  seeded: boolean;
  ref?: string;
  customerName?: string;
  phone?: string;
  serviceId?: string;
  serviceName?: string;
  price?: number | null;
  status?: BookingStatus;
  note?: string;
  imageId?: string;
};

export type BarberDay = {
  date: string;
  days: string[];
  bookings: BarberEntry[];
};

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, init);
  } catch {
    throw new ApiError(0, "We couldn't reach the shop's server. Check your connection and try again.");
  }
  let body: any = null;
  try {
    body = await res.json();
  } catch {
    // no body, or not JSON
  }
  if (!res.ok) {
    throw new ApiError(res.status, body?.message ?? 'Something went wrong. Please try again.');
  }
  return body as T;
}

function json(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  };
}

export const api = {
  services: () => request<Service[]>('/api/services'),
  availability: (date?: string) =>
    request<Availability>(date ? `/api/availability?date=${encodeURIComponent(date)}` : '/api/availability'),
  upload: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<{ imageId: string }>('/api/uploads', { method: 'POST', body: form });
  },
  createBooking: (body: {
    serviceId: string;
    date: string;
    slotTime: string;
    customerName: string;
    phone: string;
    note?: string;
    imageId?: string;
  }) => request<Booking>('/api/bookings', json('POST', body)),
  payMpesa: (ref: string, phone: string) =>
    request<Booking>(`/api/bookings/${ref}/pay/mpesa`, json('POST', { phone })),
  payCard: (ref: string) => request<Booking>(`/api/bookings/${ref}/pay/card`, json('POST', {})),
  get: (ref: string) => request<Booking>(`/api/bookings/${encodeURIComponent(ref)}`),
  simulate: (ref: string) => request<Booking>(`/api/bookings/${ref}/simulate`, { method: 'POST' }),
  barberDay: (date?: string) =>
    request<BarberDay>(date ? `/api/barber/bookings?date=${encodeURIComponent(date)}` : '/api/barber/bookings'),
};

// ---- Formatting ------------------------------------------------------------------------------------------

const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** "Today", "Tomorrow", or the weekday name, based on where the day sits in the shop's three-day list. */
export function dayName(iso: string, days: string[]): string {
  const i = days.indexOf(iso);
  if (i === 0) return 'Today';
  if (i === 1) return 'Tomorrow';
  return WEEKDAYS[parseIso(iso).getDay()];
}

/** "Sat 13 Sep" */
export function shortDate(iso: string): string {
  const d = parseIso(iso);
  return `${WEEKDAYS[d.getDay()].slice(0, 3)} ${d.getDate()} ${MONTHS[d.getMonth()]}`;
}

/** "Today, Sat 12 Sep" */
export function dayLabel(iso: string, days: string[]): string {
  const i = days.indexOf(iso);
  return i === 0 || i === 1 ? `${dayName(iso, days)}, ${shortDate(iso)}` : shortDate(iso);
}

export function money(n: number): string {
  return `KSh ${n.toLocaleString('en-KE')}`;
}

export function prettyPhone(p: string): string {
  // 254712345678 → 0712 345 678
  const m = /^254(\d{3})(\d{3})(\d{3})$/.exec(p);
  return m ? `0${m[1]} ${m[2]} ${m[3]}` : p;
}

export function reducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
