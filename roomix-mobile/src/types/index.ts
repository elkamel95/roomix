export type PlanType = 'FREE' | 'PREMIUM' | 'PRO';
export type ProjectStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED';
export type ProductBrand = 'IKEA' | 'AMAZON' | 'LEROY_MERLIN' | 'ACTION' | 'OTHER';
export type ProductCategory = 'SOFA' | 'TABLE' | 'CHAIR' | 'LAMP' | 'CARPET' | 'PLANT' | 'CURTAIN' | 'SHELF' | 'DESK' | 'BED' | 'DECORATION' | 'OTHER';

export type DecorationStyle =
  | 'SCANDINAVIAN' | 'MODERN_LUXURY' | 'MINIMALIST' | 'JAPANESE_ZEN'
  | 'ARABIC_MODERN' | 'GAMER_SETUP' | 'COZY' | 'INDUSTRIAL'
  | 'SMART_OFFICE' | 'DEVELOPER_SETUP'
  // Nouveaux styles premium
  | 'MODERN' | 'BOHEMIAN' | 'MID_CENTURY' | 'CONTEMPORARY'
  | 'JAPANDI' | 'VINTAGE' | 'MAXIMALIST' | 'NEOCLASSIC'
  | 'FARMHOUSE' | 'SKI_CHALET' | 'ART_DECO' | 'FRENCH_COUNTRY'
  | 'RUSTIC' | 'MEDIEVAL';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName?: string;
  avatarUrl?: string;
  plan: PlanType;
  planExpiry?: string;
  tokenBalance: number;
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

// ── Personnalisation ──────────────────────────────────────────────────────────

export type SofaColor =
  | 'beige' | 'grey' | 'white' | 'navy blue' | 'terracotta'
  | 'forest green' | 'charcoal black';

export type SofaType =
  | 'large L-shape sectional' | '3-seat sofa' | '2-seat sofa'
  | 'chaise longue' | 'sofa bed';

export type SofaMaterial = 'fabric' | 'genuine leather' | 'velvet' | 'microfiber' | 'boucle';

export type ColorPalette =
  | 'beige and white with warm neutral tones'
  | 'cool grey and silver neutral tones'
  | 'warm earthy tones — terracotta, rust, amber'
  | 'dark moody tones — charcoal, black, deep navy'
  | 'vibrant colorful palette';

export type RoomType =
  | 'living room'
  | 'bedroom'
  | 'dining room'
  | 'home office'
  | 'kitchen'
  | 'bathroom'
  | 'hallway';

export interface RoomTypeOption {
  key: RoomType;
  label: string;
  emoji: string;
}

export const ROOM_TYPE_OPTIONS: RoomTypeOption[] = [
  { key: 'living room',  label: 'Salon',             emoji: '🛋️' },
  { key: 'bedroom',      label: 'Chambre',            emoji: '🛏️' },
  { key: 'dining room',  label: 'Salle à manger',     emoji: '🍽️' },
  { key: 'home office',  label: 'Bureau',             emoji: '💼' },
  { key: 'kitchen',      label: 'Cuisine',            emoji: '🍳' },
  { key: 'bathroom',     label: 'Salle de bain',      emoji: '🚿' },
  { key: 'hallway',      label: 'Entrée / Couloir',   emoji: '🚪' },
];

export type FloorMaterial =
  | 'light oak parquet'
  | 'dark walnut parquet'
  | 'white marble tiles'
  | 'polished concrete'
  | 'beige ceramic tiles'
  | 'soft carpet';

export type WallFinish =
  | 'white paint'
  | 'light grey paint'
  | 'dark charcoal paint'
  | 'exposed brick'
  | 'polished concrete'
  | 'wood paneling';

export type TableMaterial =
  | 'natural oak wood'
  | 'white marble'
  | 'tempered glass'
  | 'black metal'
  | 'rattan'
  | 'dark walnut wood';

export type Accessory =
  | 'indoor plants'
  | 'decorative cushions'
  | 'abstract wall art'
  | 'curtains'
  | 'floor lamp'
  | 'decorative rug'
  | 'candles and vases'
  | 'bookshelf with books';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface StyleCard {
  key: DecorationStyle;
  label: string;
  tagline: string;
  emoji: string;
  gradient: [string, string];
}

export const STYLE_CARDS: StyleCard[] = [
  { key: 'MODERN',         label: 'Modern',           tagline: 'Clean lines · Neutral palette · Timeless',    emoji: '🏛️',  gradient: ['#3D3D3D', '#1C1C1C'] },
  { key: 'BOHEMIAN',       label: 'Bohemian',          tagline: 'Free spirit · Warm textures · Layered',       emoji: '🌸',  gradient: ['#C8794A', '#7A4020'] },
  { key: 'MINIMALIST',     label: 'Minimalist',        tagline: 'Less is more · Pure space · Serene',          emoji: '◻️',  gradient: ['#757575', '#424242'] },
  { key: 'MID_CENTURY',    label: 'Mid Century',       tagline: 'Organic forms · Warm wood · Retro',           emoji: '🪵',  gradient: ['#C8822A', '#7A4E1A'] },
  { key: 'SCANDINAVIAN',   label: 'Scandinavian',      tagline: 'Cozy hygge · Light wood · Nature',            emoji: '❄️',  gradient: ['#8A7E72', '#5C5248'] },
  { key: 'CONTEMPORARY',   label: 'Contemporary',      tagline: 'Bold accents · Fresh · Current trends',       emoji: '💎',  gradient: ['#5B7FA6', '#2E4A78'] },
  { key: 'JAPANDI',        label: 'Japandi',           tagline: 'Wabi-sabi · Natural materials · Harmony',     emoji: '🎋',  gradient: ['#9C8C7A', '#5C4E40'] },
  { key: 'VINTAGE',        label: 'Vintage',           tagline: 'Nostalgic charm · Aged patina · Character',   emoji: '🕰️',  gradient: ['#B8882A', '#6E5018'] },
  { key: 'MAXIMALIST',     label: 'Maximalist',        tagline: 'More is more · Bold colors · Layered',        emoji: '🎨',  gradient: ['#9B2355', '#5C1030'] },
  { key: 'NEOCLASSIC',     label: 'Neoclassic',        tagline: 'Symmetry · Columns · Timeless elegance',      emoji: '🏛️',  gradient: ['#A09070', '#6E6050'] },
  { key: 'FARMHOUSE',      label: 'Farmhouse',         tagline: 'Rustic charm · Shiplap · Warm comfort',       emoji: '🌾',  gradient: ['#7A9B6B', '#4A6B40'] },
  { key: 'SKI_CHALET',     label: 'Ski Chalet',        tagline: 'Alpine warmth · Stone · Wooden beams',        emoji: '⛷️',  gradient: ['#6A9ABE', '#3A6080'] },
  { key: 'ART_DECO',       label: 'Art Deco',          tagline: 'Geometric glamour · Gold · Bold symmetry',    emoji: '✦',   gradient: ['#2A2A1A', '#8B7020'] },
  { key: 'FRENCH_COUNTRY', label: 'French Country',    tagline: 'Provincial charm · Linen · Soft & romantic',  emoji: '🌹',  gradient: ['#C09090', '#8A6060'] },
  { key: 'RUSTIC',         label: 'Rustic',            tagline: 'Raw materials · Weathered wood · Earthy',     emoji: '🪨',  gradient: ['#8B5E3C', '#4A3020'] },
  { key: 'MEDIEVAL',       label: 'Medieval',          tagline: 'Stone arches · Dark grandeur · Mystical',     emoji: '🏰',  gradient: ['#2C2C54', '#14142A'] },
];

// Garde la compatibilité avec l'ancien code qui utilise STYLE_OPTIONS
export interface StyleOption {
  key: DecorationStyle;
  label: string;
  emoji: string;
  description: string;
  color: string;
}

export const STYLE_OPTIONS: StyleOption[] = STYLE_CARDS.map(c => ({
  key: c.key,
  label: c.label,
  emoji: c.emoji,
  description: c.tagline,
  color: c.gradient[0],
}));
