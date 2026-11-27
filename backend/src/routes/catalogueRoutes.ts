import { Router } from 'express';
import { z } from 'zod';
import { parseHttpRange } from '../lib/range.js';
import { supabase } from '../lib/supabase.js';
import { requireUserAuth } from '../middleware/auth.js';
import {
  TelegramMediaNotFoundError,
  TelegramMediaSizeLimitError,
  TelegramUnavailableError,
  telegramService,
} from '../services/telegramService.js';
import {
  type DbCategory,
  type DbMovie,
  type DbWatchHistory,
  toApiMovieDto,
} from '../types/models.js';

export const catalogueRouter = Router();

const uuidSchema = z.string().uuid();

async function loadCategoryMap(): Promise<Map<string, DbCategory>> {
  const { data } = await supabase
    .from('categories')
    .select('*')
    .order('sort_order', { ascending: true })
    .returns<DbCategory[]>();

  const map = new Map<string, DbCategory>();
  for (const cat of data ?? []) {
    map.set(cat.id, cat);
  }
  return map;
}

/**
 * GET /api/categories
 */
catalogueRouter.get('/categories', requireUserAuth, async (_req, res) => {
  try {
    const { data, error } = await supabase
      .from('categories')
      .select('id, name, slug, sort_order')
      .order('sort_order', { ascending: true });

    if (error) {
      res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load categories.' });
      return;
    }

    res.status(200).json({ categories: data ?? [] });
  } catch (err) {
    console.error('GET /api/categories error:', err);
    res.status(500).json({ code: 'INTERNAL_ERROR', message: 'Unexpected server error.' });
  }
});

/**
 * GET /api/movies?category_id=&q=
 * Returns { movies, featured, trending, recently_added } matching Android MoviesListResponse.
 */
catalogueRouter.get('/movies', requireUserAuth, async (req, res) => {
  try {
    const rawCategoryId = typeof req.query.category_id === 'string' ? req.query.category_id.trim() : '';
    const rawQuery = typeof req.query.q === 'string' ? req.query.q.trim() : '';

    if (rawCategoryId && !uuidSchema.safeParse(rawCategoryId).success) {
      res.status(400).json({ code: 'INVALID_CATEGORY', message: 'Invalid category_id parameter.' });
      return;
    }

    const categoryMap = await loadCategoryMap();

    let query = supabase
      .from('movies')
      .select('*')
      .eq('is_published', true)
      .order('created_at', { ascending: false });

    if (rawCategoryId) {
      query = query.eq('category_id', rawCategoryId);
    }

    if (rawQuery) {
      // Safe parameterized ilike filter via Supabase client
      const sanitized = rawQuery.replace(/[%_,()]/g, ' ').trim();
      if (sanitized.length > 0) {
        query = query.ilike('title', `%${sanitized}%`);
      }
    }

    const { data, error } = await query.returns<DbMovie[]>();
    if (error) {
      console.error('GET /api/movies DB error:', error);
      res.status(500).json({ code: 'DB_ERROR', message: 'Failed to fetch movie library.' });
      return;
    }

    const movies = (data ?? []).map((row) => toApiMovieDto(row, categoryMap));
    const featured = movies.find((m) => m.is_featured) ?? movies[0] ?? null;
    const trending = movies.filter((m) => m.is_trending);
    const recentlyAdded = movies.filter((m) => m.is_recently_added);

    res.status(200).json({
      movies,
      featured,
      trending,
      recently_added: recentlyAdded,
    });
  } catch (err) {
    console.error('GET /api/movies error:', err);
    res.status(500).json({ code: 'INTERNAL_ERROR', message: 'Unexpected server error.' });
  }
});

/**
 * GET /api/movies/:id
 * Returns { movie, is_favorite, watch_history } matching Android MovieDetailResponse.
 */
catalogueRouter.get('/movies/:id', requireUserAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  if (!parsedId.success) {
    res.status(400).json({ code: 'INVALID_ID', message: 'Invalid movie ID format.' });
    return;
  }

  try {
    const movieId = parsedId.data;
    const userId = req.userAuth!.user.id;

    const [{ data: movieRow, error: movieErr }, categoryMap, { data: favRow }, { data: historyRow }] =
      await Promise.all([
        supabase
          .from('movies')
          .select('*')
          .eq('id', movieId)
          .eq('is_published', true)
          .maybeSingle<DbMovie>(),
        loadCategoryMap(),
        supabase
          .from('favorites')
          .select('id')
          .eq('user_id', userId)
          .eq('movie_id', movieId)
          .maybeSingle(),
        supabase
          .from('watch_history')
          .select('*')
          .eq('user_id', userId)
          .eq('movie_id', movieId)
          .maybeSingle<DbWatchHistory>(),
      ]);

    if (movieErr) {
      res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load movie details.' });
      return;
    }

    if (!movieRow) {
      res.status(404).json({ code: 'NOT_FOUND', message: 'Movie not found.' });
      return;
    }

    res.status(200).json({
      movie: toApiMovieDto(movieRow, categoryMap),
      is_favorite: Boolean(favRow),
      watch_history: historyRow
        ? {
            id: historyRow.id,
            user_id: historyRow.user_id,
            movie_id: historyRow.movie_id,
            position_ms: Number(historyRow.position_ms),
            duration_ms: Number(historyRow.duration_ms),
            updated_at: historyRow.updated_at,
          }
        : null,
    });
  } catch (err) {
    console.error('GET /api/movies/:id error:', err);
    res.status(500).json({ code: 'INTERNAL_ERROR', message: 'Unexpected server error.' });
  }
});

/**
 * GET /api/movies/:id/thumbnail
 * Securely proxies the automatically retrieved Telegram video thumbnail for authenticated users.
 */
catalogueRouter.get('/movies/:id/thumbnail', requireUserAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  if (!parsedId.success) {
    res.status(400).json({ code: 'INVALID_ID', message: 'Invalid movie ID.' });
    return;
  }

  try {
    const { data: movieRow, error } = await supabase
      .from('movies')
      .select('id, telegram_channel_id, telegram_message_id, has_telegram_thumbnail, is_published')
      .eq('id', parsedId.data)
      .eq('is_published', true)
      .maybeSingle();

    if (error || !movieRow) {
      res.status(404).json({ code: 'NOT_FOUND', message: 'Movie not found.' });
      return;
    }

    if (!telegramService.isConfigured()) {
      res.status(503).json({
        code: 'TELEGRAM_UNCONFIGURED',
        message: 'Telegram media backend is not configured.',
      });
      return;
    }

    const thumb = await telegramService.getVideoThumbnail(
      movieRow.telegram_channel_id,
      Number(movieRow.telegram_message_id)
    );

    if (!thumb) {
      res.status(404).json({ code: 'THUMBNAIL_NOT_FOUND', message: 'No Telegram thumbnail available.' });
      return;
    }

    res.setHeader('Content-Type', thumb.contentType);
    res.setHeader('Content-Length', thumb.buffer.length.toString());
    res.setHeader('Cache-Control', 'private, max-age=3600');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.status(200).send(thumb.buffer);
  } catch (err) {
    if (err instanceof TelegramUnavailableError) {
      res.status(503).json({ code: 'STREAM_BACKEND_UNAVAILABLE', message: err.message });
      return;
    }
    if (err instanceof TelegramMediaNotFoundError) {
      res.status(404).json({ code: 'MEDIA_NOT_FOUND', message: err.message });
      return;
    }
    console.error('GET /api/movies/:id/thumbnail error:', err);
    res.status(502).json({ code: 'THUMBNAIL_FETCH_ERROR', message: 'Failed to retrieve thumbnail.' });
  }
});

/**
 * GET /api/movies/:id/stream
 * Progressive HTTP Range (206 Partial Content) streaming directly from private Telegram channel via MTProto.
 * - Requires valid user session Bearer token.
 * - Never writes full videos to server disk.
 * - Immediately cancels Telegram download when client disconnects or seeks.
 */
catalogueRouter.get('/movies/:id/stream', requireUserAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  if (!parsedId.success) {
    res.status(400).json({ code: 'INVALID_ID', message: 'Invalid movie ID.' });
    return;
  }

  if (!telegramService.isConfigured()) {
    res.status(503).json({
      code: 'STREAM_BACKEND_UNAVAILABLE',
      message: 'Telegram streaming backend is not configured on this server.',
    });
    return;
  }

  try {
    const { data: movieRow, error } = await supabase
      .from('movies')
      .select('id, title, mime_type, telegram_channel_id, telegram_message_id, is_published')
      .eq('id', parsedId.data)
      .eq('is_published', true)
      .maybeSingle();

    if (error || !movieRow) {
      res.status(404).json({ code: 'NOT_FOUND', message: 'Movie not found.' });
      return;
    }

    const resolved = await telegramService.resolveVideoMessage(
      movieRow.telegram_channel_id,
      Number(movieRow.telegram_message_id)
    );

    const totalSize = resolved.metadata.fileSizeBytes;
    if (totalSize <= 0) {
      res.status(404).json({ code: 'EMPTY_MEDIA', message: 'Video media has zero bytes.' });
      return;
    }

    const parsedRange = parseHttpRange(req.headers.range, totalSize);
    if (parsedRange === 'INVALID') {
      res.setHeader('Content-Range', `bytes */${totalSize}`);
      res.status(416).json({
        code: 'RANGE_NOT_SATISFIABLE',
        message: 'Requested byte range is outside video bounds.',
      });
      return;
    }

    const { start, end, isPartial } = parsedRange;
    const chunkLength = end - start + 1;
    const mimeType = resolved.metadata.mimeType || movieRow.mime_type || 'video/mp4';

    res.status(isPartial ? 206 : 200);
    res.setHeader('Content-Type', mimeType);
    res.setHeader('Accept-Ranges', 'bytes');
    res.setHeader('Content-Length', chunkLength.toString());
    if (isPartial) {
      res.setHeader('Content-Range', `bytes ${start}-${end}/${totalSize}`);
    }
    // Enforce inline progressive streaming & prevent permanent download caching
    res.setHeader('Content-Disposition', 'inline');
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    res.setHeader('X-Content-Type-Options', 'nosniff');

    const abortController = new AbortController();
    req.on('close', () => {
      if (!res.writableEnded) {
        abortController.abort();
      }
    });

    for await (const chunk of telegramService.streamVideoByteRange(
      resolved.document,
      start,
      end,
      abortController.signal
    )) {
      if (abortController.signal.aborted || res.destroyed) {
        break;
      }

      const canContinue = res.write(chunk);
      if (!canContinue) {
        await new Promise<void>((resolve) => {
          const onDrain = () => {
            cleanup();
            resolve();
          };
          const onClose = () => {
            cleanup();
            resolve();
          };
          const cleanup = () => {
            res.off('drain', onDrain);
            res.off('close', onClose);
          };
          res.once('drain', onDrain);
          res.once('close', onClose);
        });
      }
    }

    if (!res.writableEnded && !res.destroyed) {
      res.end();
    }
  } catch (err) {
    if (res.headersSent) {
      res.destroy();
      return;
    }
    if (err instanceof TelegramUnavailableError) {
      res.status(503).json({ code: 'STREAM_BACKEND_UNAVAILABLE', message: err.message });
      return;
    }
    if (err instanceof TelegramMediaNotFoundError) {
      res.status(404).json({ code: 'MEDIA_NOT_FOUND', message: err.message });
      return;
    }
    if (err instanceof TelegramMediaSizeLimitError) {
      res.status(413).json({ code: 'MEDIA_TOO_LARGE', message: err.message });
      return;
    }
    console.error('GET /api/movies/:id/stream error:', err);
    res.status(502).json({
      code: 'STREAM_ERROR',
      message: 'Failed to stream authorized video from media backend.',
    });
  }
});
