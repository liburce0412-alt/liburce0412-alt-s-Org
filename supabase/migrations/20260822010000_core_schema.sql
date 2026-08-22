begin;

create extension if not exists pgcrypto;
create schema if not exists extensions;
create extension if not exists pg_trgm with schema extensions;

do $$ begin create type public.app_role as enum ('student','moderator','admin','super_admin'); exception when duplicate_object then null; end $$;
do $$ begin create type public.sync_state as enum ('local_only','pending','synced','conflict','failed'); exception when duplicate_object then null; end $$;
do $$ begin create type public.order_status as enum ('pending_payment','paid','meeting','completed','cancelled','disputed'); exception when duplicate_object then null; end $$;
do $$ begin create type public.moderation_status as enum ('pending','approved','rejected','removed'); exception when duplicate_object then null; end $$;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  handle text unique,
  display_name text not null default 'CampusAI 用户',
  avatar_path text,
  cover_path text,
  bio text not null default '',
  role public.app_role not null default 'student',
  is_blocked boolean not null default false,
  level integer not null default 1 check (level between 1 and 99),
  experience integer not null default 0 check (experience >= 0),
  streak_days integer not null default 0 check (streak_days >= 0),
  settings jsonb not null default '{"theme":"system","motion":"on","renderQuality":"auto","spectraEnvironment":"original","sound":true}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.time_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  client_id uuid not null default gen_random_uuid(),
  title text not null check (char_length(title) between 1 and 120),
  category text not null default '其他',
  description text not null default '',
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  duration_seconds integer generated always as (greatest(0, extract(epoch from (ends_at - starts_at))::integer)) stored,
  version integer not null default 1 check (version > 0),
  sync_state public.sync_state not null default 'synced',
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, client_id),
  check (ends_at >= starts_at)
);

create table if not exists public.time_goals (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade,
  date date not null, target_minutes integer not null check (target_minutes between 1 and 1440),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(), unique(user_id,date)
);

create table if not exists public.focus_sessions (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade,
  preset_minutes integer not null check (preset_minutes in (25,50,90)), elapsed_seconds integer not null default 0 check (elapsed_seconds >= 0),
  category text not null default '专注', status text not null default 'running' check (status in ('running','paused','completed','cancelled')),
  started_at timestamptz not null default now(), ended_at timestamptz, created_at timestamptz not null default now()
);

create table if not exists public.course_schedules (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade,
  client_id uuid not null default gen_random_uuid(), name text not null check(char_length(name) between 1 and 160), weekday smallint not null check(weekday between 1 and 7),
  start_minute smallint not null check(start_minute between 0 and 1439), end_minute smallint not null check(end_minute between 1 and 1440),
  location text not null default '', teacher text not null default '', weeks text not null default '', source_hash text not null,
  version integer not null default 1, deleted_at timestamptz, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  unique(user_id,client_id), unique(user_id,source_hash), check(end_minute>start_minute)
);

create table if not exists public.achievement_definitions (
  id text primary key, name text not null, description text not null, icon_key text not null, criteria jsonb not null default '{}'::jsonb, points integer not null default 0
);
create table if not exists public.achievements (
  user_id uuid not null references public.profiles(id) on delete cascade, achievement_id text not null references public.achievement_definitions(id) on delete cascade,
  unlocked_at timestamptz not null default now(), progress jsonb not null default '{}'::jsonb, primary key(user_id,achievement_id)
);

create table if not exists public.posts (
  id uuid primary key default gen_random_uuid(), author_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 5000), topic text, tags text[] not null default '{}', media_paths text[] not null default '{}',
  is_anonymous boolean not null default false, moderation_status public.moderation_status not null default 'pending', like_count integer not null default 0,
  comment_count integer not null default 0, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz
);
create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(), post_id uuid not null references public.posts(id) on delete cascade,
  author_id uuid not null references public.profiles(id) on delete cascade, parent_id uuid references public.comments(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000), moderation_status public.moderation_status not null default 'pending',
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz
);
create table if not exists public.post_likes (
  post_id uuid not null references public.posts(id) on delete cascade, user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(post_id,user_id)
);
create table if not exists public.post_bookmarks (
  post_id uuid not null references public.posts(id) on delete cascade, user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(post_id,user_id)
);

create table if not exists public.listings (
  id uuid primary key default gen_random_uuid(), seller_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (char_length(title) between 1 and 160), description text not null default '', price_cents integer not null check (price_cents >= 0),
  original_price_cents integer check (original_price_cents >= 0), category text not null, condition text not null, location text not null default '',
  media_paths text[] not null default '{}', status text not null default 'active' check (status in ('draft','active','reserved','sold','withdrawn','removed')),
  moderation_status public.moderation_status not null default 'pending', created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.favorites (
  listing_id uuid not null references public.listings(id) on delete cascade, user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(listing_id,user_id)
);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(), listing_id uuid references public.listings(id) on delete set null,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade, user_id uuid not null references public.profiles(id) on delete cascade,
  last_read_at timestamptz, created_at timestamptz not null default now(), primary key(conversation_id,user_id)
);
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(), conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade, body text not null default '', media_path text,
  client_id uuid not null default gen_random_uuid(), created_at timestamptz not null default now(), deleted_at timestamptz,
  unique(sender_id,client_id), check (body <> '' or media_path is not null)
);

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(), listing_id uuid not null references public.listings(id), buyer_id uuid not null references public.profiles(id),
  seller_id uuid not null references public.profiles(id), price_cents integer not null check(price_cents >= 0), status public.order_status not null default 'pending_payment',
  version integer not null default 1, created_at timestamptz not null default now(), updated_at timestamptz not null default now(), completed_at timestamptz,
  check (buyer_id <> seller_id)
);

create table if not exists public.announcements (
  id uuid primary key default gen_random_uuid(), title text not null, body text not null, audience jsonb not null default '{"all":true}'::jsonb,
  status text not null default 'draft' check(status in ('draft','scheduled','published','withdrawn')), publish_at timestamptz,
  author_id uuid references public.profiles(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(), reporter_id uuid not null references public.profiles(id), target_type text not null,
  target_id uuid not null, reason text not null, details text not null default '', content_digest text not null default '', status text not null default 'pending',
  assignee_id uuid references public.profiles(id), resolution text, created_at timestamptz not null default now(), resolved_at timestamptz
);
create table if not exists public.app_releases (
  id uuid primary key default gen_random_uuid(), version_code integer unique not null, version_name text not null, notes text not null,
  apk_url text not null, checksum_sha256 text not null, rollout_percent integer not null default 100 check(rollout_percent between 0 and 100),
  is_force_update boolean not null default false, status text not null default 'draft', published_by uuid references public.profiles(id), created_at timestamptz not null default now(), published_at timestamptz
);
create table if not exists public.audit_logs (
  id bigint generated always as identity primary key, actor_id uuid references public.profiles(id), action text not null, resource_type text not null,
  resource_id text, result text not null, metadata jsonb not null default '{}'::jsonb, ip_hash text, created_at timestamptz not null default now()
);
create table if not exists public.ai_reports (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade, mode text not null check(mode in ('fast','deep')),
  title text not null, summary text not null, messages jsonb not null default '[]'::jsonb, context jsonb not null default '{}'::jsonb,
  model text not null, input_tokens integer, output_tokens integer, cost_micros bigint, created_at timestamptz not null default now()
);
create table if not exists public.ai_usage_daily (
  user_id uuid not null references public.profiles(id) on delete cascade, day date not null default current_date,
  request_count integer not null default 0, input_tokens bigint not null default 0, output_tokens bigint not null default 0, cost_micros bigint not null default 0,
  primary key(user_id,day)
);

create index if not exists time_entries_user_starts_idx on public.time_entries(user_id,starts_at desc) where deleted_at is null;
create index if not exists course_schedules_user_day_idx on public.course_schedules(user_id,weekday,start_minute) where deleted_at is null;
create index if not exists focus_sessions_user_started_idx on public.focus_sessions(user_id,started_at desc);
create index if not exists posts_feed_idx on public.posts(created_at desc) where deleted_at is null;
create index if not exists posts_body_trgm_idx on public.posts using gin(body extensions.gin_trgm_ops);
create index if not exists comments_post_idx on public.comments(post_id,created_at) where deleted_at is null;
create index if not exists comments_author_idx on public.comments(author_id);
create index if not exists post_likes_user_idx on public.post_likes(user_id,created_at desc);
create index if not exists post_bookmarks_user_idx on public.post_bookmarks(user_id,created_at desc);
create index if not exists listings_feed_idx on public.listings(status,created_at desc);
create index if not exists listings_seller_idx on public.listings(seller_id,created_at desc);
create index if not exists listings_title_trgm_idx on public.listings using gin(title extensions.gin_trgm_ops);
create index if not exists favorites_user_idx on public.favorites(user_id,created_at desc);
create index if not exists conversation_members_user_idx on public.conversation_members(user_id,created_at desc);
create index if not exists messages_conversation_idx on public.messages(conversation_id,created_at) where deleted_at is null;
create index if not exists messages_sender_idx on public.messages(sender_id);
create index if not exists orders_buyer_idx on public.orders(buyer_id,created_at desc);
create index if not exists orders_seller_idx on public.orders(seller_id,created_at desc);
create index if not exists orders_listing_idx on public.orders(listing_id);
create index if not exists reports_queue_idx on public.reports(status,created_at);
create index if not exists reports_reporter_idx on public.reports(reporter_id,created_at desc);
create index if not exists reports_assignee_idx on public.reports(assignee_id) where assignee_id is not null;
create index if not exists announcements_author_idx on public.announcements(author_id) where author_id is not null;
create index if not exists releases_publisher_idx on public.app_releases(published_by) where published_by is not null;
create index if not exists audit_logs_created_idx on public.audit_logs(created_at desc);
create index if not exists audit_logs_actor_idx on public.audit_logs(actor_id,created_at desc) where actor_id is not null;

commit;
