begin;

insert into public.achievement_definitions(id,name,description,icon_key,criteria,points) values
  ('first_light','第一束光','完成第一条有效时间记录。','node_c_first','{"timeEntries":1}',20),
  ('focus_departure','专注起航','完成一次至少 25 分钟的专注。','node_c_focus','{"focusSessions":1}',30),
  ('steady_rhythm','稳定节奏','连续七天留下有效记录。','node_c_streak','{"streakDays":7}',80),
  ('deep_orbit','深度轨道','累计记录达到十小时。','node_c_orbit','{"totalMinutes":600}',90),
  ('time_architect','时间建筑师','完成二十五条有效时间记录。','node_c_architect','{"timeEntries":25}',100),
  ('full_spectrum','完整光谱','记录覆盖五个不同分类。','node_c_spectrum','{"categoryCount":5}',120),
  ('hundred_hours','百小时节点','累计记录达到一百小时。','node_c_hours','{"totalMinutes":6000}',160)
on conflict(id) do update set
  name=excluded.name,
  description=excluded.description,
  icon_key=excluded.icon_key,
  criteria=excluded.criteria,
  points=excluded.points;

create or replace function private.refresh_time_achievements(target_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  entry_count integer := 0;
  total_minutes integer := 0;
  focus_count integer := 0;
  category_count integer := 0;
  longest_streak integer := 0;
begin
  select
    count(*)::integer,
    coalesce(sum(duration_seconds) / 60, 0)::integer,
    count(*) filter(where category = '专注' and duration_seconds >= 1500)::integer,
    count(distinct nullif(trim(category), ''))::integer
  into entry_count,total_minutes,focus_count,category_count
  from public.time_entries
  where user_id=target_user and deleted_at is null and duration_seconds > 0;

  with days as (
    select distinct (starts_at at time zone 'Asia/Shanghai')::date as day
    from public.time_entries
    where user_id=target_user and deleted_at is null and duration_seconds > 0
  ), grouped as (
    select day,day-(row_number() over(order by day))::integer as island
    from days
  ), streaks as (
    select count(*)::integer as length from grouped group by island
  )
  select coalesce(max(length),0) into longest_streak from streaks;

  insert into public.achievements(user_id,achievement_id,progress)
  select target_user,achievement_id,progress
  from (values
    ('first_light',entry_count >= 1,jsonb_build_object('timeEntries',entry_count)),
    ('focus_departure',focus_count >= 1,jsonb_build_object('focusSessions',focus_count)),
    ('steady_rhythm',longest_streak >= 7,jsonb_build_object('streakDays',longest_streak)),
    ('deep_orbit',total_minutes >= 600,jsonb_build_object('totalMinutes',total_minutes)),
    ('time_architect',entry_count >= 25,jsonb_build_object('timeEntries',entry_count)),
    ('full_spectrum',category_count >= 5,jsonb_build_object('categoryCount',category_count)),
    ('hundred_hours',total_minutes >= 6000,jsonb_build_object('totalMinutes',total_minutes))
  ) as candidate(achievement_id,unlocked,progress)
  where unlocked
  on conflict(user_id,achievement_id) do update set progress=excluded.progress;

  update public.profiles
  set streak_days=greatest(streak_days,longest_streak),updated_at=now()
  where id=target_user;
end;
$$;

create or replace function private.on_time_entry_achievement_refresh()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.refresh_time_achievements(coalesce(new.user_id,old.user_id));
  return coalesce(new,old);
end;
$$;

drop trigger if exists time_entry_achievement_refresh on public.time_entries;
create trigger time_entry_achievement_refresh
after insert or update of category,starts_at,ends_at,deleted_at on public.time_entries
for each row execute function private.on_time_entry_achievement_refresh();

revoke all on function private.refresh_time_achievements(uuid) from public,anon,authenticated;
revoke all on function private.on_time_entry_achievement_refresh() from public,anon,authenticated;

commit;
