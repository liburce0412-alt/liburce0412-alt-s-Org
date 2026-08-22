begin;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values
  ('avatars','avatars',true,5242880,array['image/jpeg','image/png','image/webp']),
  ('covers','covers',true,10485760,array['image/jpeg','image/png','image/webp']),
  ('post-media','post-media',true,15728640,array['image/jpeg','image/png','image/webp']),
  ('listing-media','listing-media',true,15728640,array['image/jpeg','image/png','image/webp']),
  ('chat-media','chat-media',false,15728640,array['image/jpeg','image/png','image/webp'])
on conflict(id) do update set file_size_limit=excluded.file_size_limit,allowed_mime_types=excluded.allowed_mime_types;

drop policy if exists public_media_read on storage.objects;
create policy public_media_read on storage.objects for select to anon,authenticated using(bucket_id in ('avatars','covers','post-media','listing-media'));
drop policy if exists owner_media_insert on storage.objects;
create policy owner_media_insert on storage.objects for insert to authenticated with check(bucket_id in ('avatars','covers','post-media','listing-media') and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists owner_media_update on storage.objects;
create policy owner_media_update on storage.objects for update to authenticated using(bucket_id in ('avatars','covers','post-media','listing-media') and (storage.foldername(name))[1]=(select auth.uid())::text) with check(bucket_id in ('avatars','covers','post-media','listing-media') and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists owner_media_delete on storage.objects;
create policy owner_media_delete on storage.objects for delete to authenticated using((bucket_id in ('avatars','covers','post-media','listing-media') and (storage.foldername(name))[1]=(select auth.uid())::text) or (select private.is_staff()));
drop policy if exists chat_media_members_read on storage.objects;
create policy chat_media_members_read on storage.objects for select to authenticated using(bucket_id='chat-media' and case when (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then (select private.is_conversation_member(((storage.foldername(name))[1])::uuid)) else false end);
drop policy if exists chat_media_members_insert on storage.objects;
create policy chat_media_members_insert on storage.objects for insert to authenticated with check(bucket_id='chat-media' and case when (storage.foldername(name))[1] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then (select private.is_conversation_member(((storage.foldername(name))[1])::uuid)) else false end and (storage.foldername(name))[2]=(select auth.uid())::text);

insert into public.achievement_definitions(id,name,description,icon_key,criteria,points) values
  ('first_light','第一束光','完成第一条有效时间记录。','node_c_first','{"timeEntries":1}',20),
  ('focus_departure','专注起航','完成一次 25、50 或 90 分钟专注。','node_c_focus','{"focusSessions":1}',30),
  ('steady_rhythm','稳定节奏','连续七天留下有效记录。','node_c_streak','{"streakDays":7}',80),
  ('hundred_hours','百小时节点','累计记录达到一百小时。','node_c_hours','{"totalMinutes":6000}',160)
on conflict(id) do update set name=excluded.name,description=excluded.description,icon_key=excluded.icon_key,criteria=excluded.criteria,points=excluded.points;

commit;
