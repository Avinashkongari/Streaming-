import { Router } from 'express';
import { z } from 'zod';
import { supabase } from '../lib/supabase.js';
import { requireUserAuth } from '../middleware/auth.js';
import {
  type DbCategory,
  type DbMovie,
  type DbWatchHistory,
  toApiMovieDto,
} from '../types/models.js';

export const userRouter = Router();

const uuidSchema = z.string().uuid();

const addFavoriteSchema = z.object({
  movie_id: z.string().uuid(),
});

const watchHistorySchema = z.object({
  movie_id: z.string().uuid(),
  position_ms: z.number().int().min(0),
  duration_ms: z.number().int().min(0),
});

async function buildFavoritesPayload(userId: string) {
  const [{ data: favRows, error: favErr }, { data: categories }] = await Promise.all([
    supabase
      .from('favorites')
      .select('id, user_id, movie_id, created_at')
      .eq('user_id', userId)
      .order('created_at', { ascending: false }),
    supabase
      .from('categories')
      .select('*')
      .order('sort_order', { ascending: true })
      .returns<DbCategory[]>(),
  ]);

  if (favErr) throw favErr;

  const movieIds = (favRows ?? []).map((f) => f.movie_id);
  const categoryMap = new Map<string, DbCategory>();
  for (const c of categories ?? []) categoryMap.set(c.id, c);

  let movieMap = new Map<string, ReturnType<typeof toApiMovieDto>>();
  if (movieIds.length > 0) {
    const { data: movieRows } = await supabase
      .from('movies')
      .select('*')
      .in('id', movieIds)
      .eq('is_published', true)
      .returns<DbMovie[]>();

    for (const row of movieRows ?? []) {
      movieMap.set(row.id, toApiMovieDto(row, categoryMap));
    }
  }

  const favorites = (favRows ?? [])
    .filter((f) => movieMap.has(f.movie_id))
    .map((f) => ({
      id: f.id,
      user_id: f.user_id,
      movie_id: f.movie_id,
      created_at: f.created_at,
      movie: movieMap.get(f.movie_id) ?? null,
    }));

  const movies = favorites.map((f) => f.movie).filter((m): m is NonNullable<typeof m> => m !== null);

  return { favorites, movies };
}

/**
 * GET /api/user/favorites
 */
userRouter.get('/favorites', requireUserAuth, async (req, res) => {
  try {
    const payload = await buildFavoritesPayload(req.userAuth!.user.id);
    res.status(200).json(payload);
  } catch (err) {
    console.error('GET /api/user/favorites error:', err);
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load My List.' });
  }
});

/**
 * POST /api/user/favorites
 */
userRouter.post('/favorites', requireUserAuth, async (req, res) => {
  const parsed = addFavoriteSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Valid movie_id UUID is required.' });
    return;
  }

  try {
    const userId = req.userAuth!.user.id;
    const movieId = parsed.data.movie_id;

    const { data: movieExists } = await supabase
      .from('movies')
      .select('id')
      .eq('id', movieId)
      .eq('is_published', true)
      .maybeSingle();

    if (!movieExists) {
      res.status(404).json({ code: 'NOT_FOUND', message: 'Movie not found.' });
      return;
    }

    const { error: upsertErr } = await supabase.from('favorites').upsert(
      { user_id: userId, movie_id: movieId },
      { onConflict: 'user_id,movie_id' }
    );

    if (upsertErr) {
      res.status(500).json({ code: 'DB_ERROR', message: 'Failed to save movie to My List.' });
      return;
    }

    const payload = await buildFavoritesPayload(userId);
    res.status(200).json(payload);
  } catch (err) {
    console.error('POST /api/user/favorites error:', err);
    res.status(500).json({ code: 'INTERNAL_ERROR', message: 'Unexpected server error.' });
  }
});

/**
 * DELETE /api/user/favorites/:id
 * Note: `:id` is the `movie_id` sent by the Android client (`DELETE api/user/favorites/{id}`).
 */
userRouter.delete('/favorites/:id', requireUserAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  if (!parsedId.success) {
    res.status(400).json({ code: 'INVALID_ID', message: 'Invalid movie ID format.' });
    return;
  }

  try {
    const userId = req.userAuth!.user.id;
    const targetId = parsedId.data;

    // Supports deletion by movie_id (Android contract) or favorite row id scoped strictly to user_id
    const { error } = await supabase
      .from('favorites')
      .delete()
      .eq('user_id', userId)
      .or(`movie_id.eq.${targetId},id.eq.${targetId}`);

    if (error) {
      res.status(500).json({ code: 'DB_ERROR', message: 'Failed to remove movie from My List.' });
      return;
    }

    res.status(204).send();
  } catch (err) {
    console.error('DELETE /api/user/favorites/:id error:', err);
    res.status(500).json({ code: 'INTERNAL_ERROR', message: 'Unexpected server error.' });
  }
});

/**
 * GET /api/user/watch-history
 */
userRouter.get('/watch-history', requireUserAuth, async (req, res) => {
  try {
    const { data, error } = await supabase
      .from('watch_history')
      .select('*')
      .eq('user_id', req.userAuth!.user.id)
      .order('updated_at', { ascending: false })
      .returns<DbWatchHistory[]>();

    if (error) {
      res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load watch history.' });
      return;
    }

    res.status(200).json(
      (data ?? []).map((row) => ({
        id: row.id,
        user_id: row.user_id,
        movie_id: row.movie_id,
        position_ms: Number(row.position_ms),
        duration_ms: Number(row.duration_ms),
        updated_at: row.updated_at,
      }))
    );
  } catch (err) {
    console.error('GET /api/user/watch-history error:', err);
    res.status(500).json({ code: 'INTERNAL_ERROR', message: 'Unexpected server error.' });
  }
});

/**
 * POST /api/user/watch-history
 */
userRouter.post('/watch-history', requireUserAuth, async (req, res) => {
  const parsed = watchHistorySchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Invalid watch history payload.' });
    return;
  }

  try {
    const userId = req.userAuth!.user.id;
    const { movie_id, position_ms, duration_ms } = parsed.data;

    const { data, error } = await supabase
      .from('watch_history')
      .upsert(
        {
          user_id: userId,
          movie_id,
          position_ms,
          duration_ms,
          updated_at: new Date().toISOString(),
        },
        { onConflict: 'user_id,movie_id' }
      )
      .select('*')
      .single<DbWatchHistory>();

    if (error || !data) {
      res.status(500).json({ code: 'DB_ERROR', message: 'Failed to save watch progress.' });
      return;
    }

    res.status(200).json({
      id: data.id,
      user_id: data.user_id,
      movie_id: data.movie_id,
      position_ms: Number(data.position_ms),
      duration_ms: Number(data.duration_ms),
      updated_at: data.updated_at,
    });
  } catch (err) {
    console.error('POST /api/user/watch-history error:', err);
    res.status(500).json({ code: 'INTERNAL_ERROR', message: 'Unexpected server error.' });
  }
});
