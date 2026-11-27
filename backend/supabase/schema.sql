-- =============================================================================
-- AXION STREAM — SUPABASE POSTGRESQL SCHEMA
-- Run this SQL inside your Supabase Dashboard -> SQL Editor.
-- All tables enforce Row Level Security (RLS) with zero public anon exposure;
-- the authorized backend accesses Supabase exclusively via SUPABASE_SERVICE_ROLE_KEY.
-- =============================================================================

create extension if not exists "pgcrypto";

-- 1. USERS
create table if not exists public.users (
    id uuid primary key default gen_random_uuid(),
    display_name text not null,
    status text not null default 'active' check (status in ('active', 'suspended', 'revoked')),
    device_id text not null,
    device_bound boolean not null default true,
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

create index if not exists idx_users_device_id on public.users (device_id);

-- 2. ACCESS CODES (One-time, single-user locked)
create table if not exists public.access_codes (
    id uuid primary key default gen_random_uuid(),
    code_hash text not null unique,
    masked_code text not null,
    label text,
    status text not null default 'active' check (status in ('active', 'claimed', 'disabled', 'expired')),
    claimed_by_user_id uuid references public.users(id) on delete set null,
    claimed_at timestamptz,
    expires_at timestamptz,
    single_user_locked boolean not null default true,
    created_at timestamptz not null default now()
);

create index if not exists idx_access_codes_status on public.access_codes (status);
create index if not exists idx_access_codes_claimed_user on public.access_codes (claimed_by_user_id);

-- 3. USER SESSIONS (Opaque token hashes stored server-side)
create table if not exists public.user_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    access_code_id uuid not null references public.access_codes(id) on delete cascade,
    token_hash text not null unique,
    device_id text not null,
    platform text not null default 'android',
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    last_used_at timestamptz not null default now()
);

create index if not exists idx_user_sessions_user_id on public.user_sessions (user_id);
create index if not exists idx_user_sessions_active on public.user_sessions (token_hash) where revoked_at is null;

-- 4. ADMIN SESSIONS (Strictly isolated from user_sessions)
create table if not exists public.admin_sessions (
    id uuid primary key default gen_random_uuid(),
    admin_email text not null,
    token_hash text not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_admin_sessions_active on public.admin_sessions (token_hash) where revoked_at is null;

-- 5. CATEGORIES
create table if not exists public.categories (
    id uuid primary key default gen_random_uuid(),
    name text not null unique,
    slug text not null unique,
    sort_order integer not null default 0,
    created_at timestamptz not null default now()
);

-- 6. MOVIES (Private Telegram message reference stays server-side only)
create table if not exists public.movies (
    id uuid primary key default gen_random_uuid(),
    title text not null,
    description text,
    category_id uuid references public.categories(id) on delete set null,
    extra_categories text[] not null default '{}',
    release_year integer,
    duration_seconds bigint not null default 0,
    file_size_bytes bigint,
    mime_type text not null default 'video/mp4',
    quality_label text not null default 'HD',
    maturity_rating text not null default '16+',
    is_featured boolean not null default false,
    is_trending boolean not null default false,
    is_recently_added boolean not null default true,
    is_published boolean not null default true,
    telegram_channel_id text not null,
    telegram_message_id bigint not null,
    has_telegram_thumbnail boolean not null default true,
    poster_override_url text,
    backdrop_override_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (telegram_channel_id, telegram_message_id)
);

create index if not exists idx_movies_published_created on public.movies (is_published, created_at desc);
create index if not exists idx_movies_category_id on public.movies (category_id);
create index if not exists idx_movies_title_lower on public.movies (lower(title));

-- 7. FAVORITES ("My List")
create table if not exists public.favorites (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    movie_id uuid not null references public.movies(id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (user_id, movie_id)
);

create index if not exists idx_favorites_user_id on public.favorites (user_id, created_at desc);

-- 8. WATCH HISTORY (Resume playback)
create table if not exists public.watch_history (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    movie_id uuid not null references public.movies(id) on delete cascade,
    position_ms bigint not null default 0 check (position_ms >= 0),
    duration_ms bigint not null default 0 check (duration_ms >= 0),
    updated_at timestamptz not null default now(),
    unique (user_id, movie_id)
);

create index if not exists idx_watch_history_user_updated on public.watch_history (user_id, updated_at desc);

-- =============================================================================
-- ROW LEVEL SECURITY (Zero-Trust: block direct public anon table access)
-- =============================================================================
alter table public.users enable row level security;
alter table public.access_codes enable row level security;
alter table public.user_sessions enable row level security;
alter table public.admin_sessions enable row level security;
alter table public.categories enable row level security;
alter table public.movies enable row level security;
alter table public.favorites enable row level security;
alter table public.watch_history enable row level security;

-- =============================================================================
-- ATOMIC ONE-TIME ACCESS CODE CLAIM RPC (Prevents concurrent claim races)
-- =============================================================================
create or replace function public.claim_access_code(
    p_code_hash text,
    p_device_id text,
    p_platform text default 'android'
)
returns table (
    outcome text,
    error_code text,
    error_message text,
    user_id uuid,
    access_code_id uuid
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_code public.access_codes%rowtype;
    v_user public.users%rowtype;
    v_new_user_id uuid;
begin
    select *
    into v_code
    from public.access_codes
    where code_hash = p_code_hash
    for update;

    if not found then
        return query select 'ERROR', 'INVALID_CODE', 'Invalid access code.', null::uuid, null::uuid;
        return;
    end if;

    if v_code.status = 'disabled' then
        return query select 'ERROR', 'CODE_DISABLED', 'This access code has been disabled.', null::uuid, v_code.id;
        return;
    end if;

    if v_code.status = 'expired' or (v_code.expires_at is not null and v_code.expires_at <= now()) then
        update public.access_codes set status = 'expired' where id = v_code.id;
        return query select 'ERROR', 'CODE_EXPIRED', 'This access code has expired.', null::uuid, v_code.id;
        return;
    end if;

    if v_code.claimed_by_user_id is not null or v_code.status = 'claimed' then
        select * into v_user from public.users where id = v_code.claimed_by_user_id;
        if not found then
            return query select 'ERROR', 'CODE_ALREADY_CLAIMED', 'This access code has already been claimed by another user.', null::uuid, v_code.id;
            return;
        end if;

        if v_user.status <> 'active' then
            return query select 'ERROR', 'CODE_DISABLED', 'The account bound to this access code is suspended or revoked.', null::uuid, v_code.id;
            return;
        end if;

        if v_user.device_id = p_device_id then
            update public.users set last_seen_at = now() where id = v_user.id;
            return query select 'OK', null::text, null::text, v_user.id, v_code.id;
            return;
        end if;

        return query select 'ERROR', 'CODE_ALREADY_CLAIMED', 'This access code has already been claimed by another user.', null::uuid, v_code.id;
        return;
    end if;

    -- Unclaimed active code: atomically create single-user account and bind code
    insert into public.users (display_name, status, device_id, device_bound, created_at, last_seen_at)
    values (
        coalesce(nullif(trim(v_code.label), ''), 'Member ' || right(v_code.masked_code, 4)),
        'active',
        p_device_id,
        true,
        now(),
        now()
    )
    returning id into v_new_user_id;

    update public.access_codes
    set status = 'claimed',
        claimed_by_user_id = v_new_user_id,
        claimed_at = now()
    where id = v_code.id;

    return query select 'OK', null::text, null::text, v_new_user_id, v_code.id;
end;
$$;

revoke all on function public.claim_access_code(text, text, text) from public;
grant execute on function public.claim_access_code(text, text, text) to service_role;

-- Seed default categories (safe idempotent upsert)
insert into public.categories (name, slug, sort_order) values
    ('Action', 'action', 1),
    ('Sci-Fi', 'sci-fi', 2),
    ('Thriller', 'thriller', 3),
    ('Drama', 'drama', 4),
    ('Fantasy', 'fantasy', 5),
    ('Documentary', 'documentary', 6)
on conflict (slug) do nothing;
