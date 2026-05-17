export type PlanType = 'FREE' | 'PREMIUM' | 'PRO';
export type ProjectStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED';
export type ProductBrand = 'IKEA' | 'AMAZON' | 'LEROY_MERLIN' | 'ACTION' | 'OTHER';
export type ProductCategory = 'SOFA' | 'TABLE' | 'CHAIR' | 'LAMP' | 'CARPET' | 'PLANT' | 'CURTAIN' | 'SHELF' | 'DESK' | 'BED' | 'DECORATION' | 'OTHER';

export type DecorationStyle =
  | 'SCANDINAVIAN'
  | 'MODERN_LUXURY'
  | 'MINIMALIST'
  | 'JAPANESE_ZEN'
  | 'ARABIC_MODERN'
  | 'GAMER_SETUP'
  | 'COZY'
  | 'INDUSTRIAL'
  | 'SMART_OFFICE'
  | 'DEVELOPER_SETUP';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName?: string;
  avatarUrl?: string;
  plan: PlanType;
  planExpiry?: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface Generation {
  id: string;
  resultImageUrl?: string;
  processingTimeMs?: number;
  createdAt?: string;
  status: ProjectStatus;
  progress?: number;
  errorMessage?: string;
}

export interface Project {
  id: string;
  name: string;
  originalImageUrl: string;
  status: ProjectStatus;
  style: DecorationStyle;
  budget?: number;
  createdAt: string;
  generation?: Generation;
}

export interface Product {
  id: string;
  name: string;
  description?: string;
  category: ProductCategory;
  brand: ProductBrand;
  price?: number;
  currency: string;
  productUrl?: string;
  affiliateUrl?: string;
  imageUrl?: string;
  inStock: boolean;
}

export interface Quota {
  plan: PlanType;
  dailyUsed: number;
  dailyLimit: number;
  remaining: number;
  resetsAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface StyleOption {
  key: DecorationStyle;
  label: string;
  emoji: string;
  description: string;
  color: string;
}

export const STYLE_OPTIONS: StyleOption[] = [
  { key: 'SCANDINAVIAN', label: 'Scandinave', emoji: '🪵', description: 'Bois clair, blanc, plantes', color: '#E8F5E9' },
  { key: 'MODERN_LUXURY', label: 'Modern Luxury', emoji: '✨', description: 'Marbre, or, velours', color: '#FFF8E1' },
  { key: 'MINIMALIST', label: 'Minimaliste', emoji: '◽', description: 'Épuré, neutre, fonctionnel', color: '#F5F5F5' },
  { key: 'JAPANESE_ZEN', label: 'Japonais Zen', emoji: '🎋', description: 'Bois naturel, bambou, harmonie', color: '#E8F5E9' },
  { key: 'ARABIC_MODERN', label: 'Arabe Moderne', emoji: '🕌', description: 'Géométrie, dorures, richesse', color: '#FFF3E0' },
  { key: 'GAMER_SETUP', label: 'Gamer Setup', emoji: '🎮', description: 'RGB, noir, multi-écrans', color: '#1A1A2E' },
  { key: 'COZY', label: 'Cozy', emoji: '🕯️', description: 'Chaud, textiles doux, lumière', color: '#FBE9E7' },
  { key: 'INDUSTRIAL', label: 'Industriel', emoji: '🏭', description: 'Béton, métal, brique', color: '#ECEFF1' },
  { key: 'SMART_OFFICE', label: 'Smart Office', emoji: '💼', description: 'Ergonomique, épuré, pro', color: '#E3F2FD' },
  { key: 'DEVELOPER_SETUP', label: 'Dev Setup', emoji: '💻', description: 'Bureau senior, monitoring, RGB', color: '#1A1A2E' },
];
