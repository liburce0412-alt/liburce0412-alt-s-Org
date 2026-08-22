begin;

-- Production originally used a same-name, incompatible schema. Quarantine the
-- old tables instead of mutating or dropping them so the migration is fully
-- lossless and reconciliation can be audited before legacy cleanup.
create schema if not exists legacy;
revoke all on schema legacy from public, anon, authenticated;

do $$
declare trigger_name text;
begin
  if to_regprocedure('public.handle_new_user()') is not null then
    for trigger_name in
      select t.tgname from pg_trigger t
      where t.tgrelid='auth.users'::regclass
        and t.tgfoid=to_regprocedure('public.handle_new_user()')::oid
        and not t.tgisinternal
    loop
      execute format('drop trigger %I on auth.users',trigger_name);
    end loop;
    drop function public.handle_new_user();
  end if;
end $$;

do $$
declare
  item record;
  has_canonical_marker boolean;
  legacy_name text;
begin
  for item in
    select * from (values
      ('profiles', 'display_name'),
      ('posts', 'body'),
      ('comments', 'body'),
      ('likes', null),
      ('favorites', 'listing_id'),
      ('products', null),
      ('orders', 'listing_id'),
      ('conversations', 'updated_at'),
      ('conversation_participants', null),
      ('messages', 'body'),
      ('reports', 'target_type'),
      ('announcements', 'body')
    ) as candidates(table_name, canonical_marker)
  loop
    if to_regclass(format('public.%I', item.table_name)) is null then
      continue;
    end if;

    if item.canonical_marker is null then
      has_canonical_marker := false;
    else
      select exists(
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = item.table_name
          and column_name = item.canonical_marker
      ) into has_canonical_marker;
    end if;

    if not has_canonical_marker then
      legacy_name := item.table_name || '_20260822';
      if to_regclass(format('legacy.%I', legacy_name)) is not null then
        raise exception 'legacy quarantine target already exists: legacy.%', legacy_name;
      end if;
      execute format('alter table public.%I set schema legacy', item.table_name);
      execute format('alter table legacy.%I rename to %I', item.table_name, legacy_name);
    end if;
  end loop;
end $$;

comment on schema legacy is 'Read-only quarantine of the pre-refactor CampusAI schema; remove only after verified backup and cutover.';

commit;
