/**
 * Database row shapes & Android-compatible API response DTOs.
 * Field names in API DTOs strictly match `AxionModels.kt` in the Android client.
 */

export interface DbUser {
  id: string;
  display_name: string;
  status: 'active' | 'suspended' | 'revoked';
  device_id: string;
  device_bound: boolean;
  created_at: string;
  last_seen_at: string;
}

export interface DbAccessCode {
  id: string;
  code_hash: string;
  masked_code: string;
  label: string | null;
  status: 'active' | 'claimed' | 'disabled' | 'expired';
  claimed_by_user_id: string | null;
  claimed_at: string | null;
  expires_at: string | null;
  single_user_locked: boolean;
  created_at: string;
}

export interface DbCategory {
  id: string;
  name: string;
  slug: string;
  sort_order: number;
  created_at: string;
}

export interface DbMovie {
  id: string;
  title: string;
  description: string | null;
  category_id: string | null;
  extra_categories: string[] | null;
  release_year: number | null;
  duration_seconds: number;
  file_size_bytes: number | null;
  mime_type: string;
  quality_label: string;
  maturity_rating: string;
  is_featured: boolean;
  is_trending: boolean;
  is_recently_added: boolean;
  is_published: boolean;
  telegram_channel_id: string;
  telegram_message_id: number;
  has_telegram_thumbnail: boolean;
  poster_override_url: string | null;
  backdrop_override_url: string | null;
  created_at: string;
  updated_at: string;
}

export interface DbWatchHistory {
  id: string;
  user_id: string;
  movie_id: string;
  position_ms: number;
  duration_ms: number;
  updated_at: string;
}

export interface ApiMovieDto {
  id: string;
  title: string;
  description: string | null;
  thumbnail_url: string | null;
  poster_url: string | null;
  backdrop_url: string | null;
  category_id: string | null;
  category_name: string | null;
  categories: string[];
  release_year: number | null;
  duration_seconds: number;
  file_size_bytes: number | null;
  quality_label: string;
  maturity_rating: string;
  is_featured: boolean;
  is_trending: boolean;
  is_recently_added: boolean;
  created_at: string;
  has_telegram_thumbnail: boolean;
}

/**
 * Serializes a database movie row into the Android client DTO.
 * Strips all private Telegram channel IDs and message IDs!
 */
export function toApiMovieDto(
  row: DbMovie,
  categoryMap: Map<string, DbCategory>
): ApiMovieDto {
  const primaryCategory = row.category_id ? categoryMap.get(row.category_id) : undefined;
  const combinedCategories = Array.from(
    new Set(
      [
        ...(primaryCategory ? [primaryCategory.name] : []),
        ...(row.extra_categories ?? []),
      ].filter((c) => c.trim().length > 0)
    )
  );

  const backendThumbPath = row.has_telegram_thumbnail
    ? `/api/movies/${row.id}/thumbnail`
    : null;

  return {
    id: row.id,
    title: row.title,
    description: row.description,
    thumbnail_url: backendThumbPath,
    poster_url: row.poster_override_url ?? backendThumbPath,
    backdrop_url: row.backdrop_override_url ?? backendThumbPath,
    category_id: row.category_id,
    category_name: primaryCategory?.name ?? null,
    categories: combinedCategories,
    release_year: row.release_year,
    duration_seconds: Number(row.duration_seconds ?? 0),
    file_size_bytes: row.file_size_bytes !== null ? Number(row.file_size_bytes) : null,
    quality_label: row.quality_label,
    maturity_rating: row.maturity_rating,
    is_featured: row.is_featured,
    is_trending: row.is_trending,
    is_recently_added: row.is_recently_added,
    created_at: row.created_at,
    has_telegram_thumbnail: row.has_telegram_thumbnail,
  };
}
