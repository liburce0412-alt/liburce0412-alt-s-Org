begin;

-- Import is additive and preserves original UUIDs wherever relationships rely
-- on them. The quarantined source tables remain untouched for rollback/audit.
do $$
begin
  if to_regclass('legacy.profiles_20260822') is not null then
    execute $import$
      insert into public.profiles(id,display_name,avatar_path,bio,role,is_blocked,created_at,updated_at)
      select p.id,
             coalesce(nullif(p.nickname,''),nullif(split_part(p.email,'@',1),''),'CampusAI 用户'),
             p.avatar_url,
             coalesce(p.bio,''),
             case when p.role in ('moderator','admin','super_admin') then p.role::public.app_role else 'student'::public.app_role end,
             coalesce(p.is_blocked,false),
             coalesce(p.created_at,now()),
             coalesce(p.created_at,now())
      from legacy.profiles_20260822 p
      join auth.users u on u.id=p.id
      on conflict(id) do nothing
    $import$;
  end if;
end $$;

-- Existing Auth users may never have received a profile because the legacy
-- trigger was missing or broken. Backfill without trusting user metadata for
-- authorization; role always starts as student.
insert into public.profiles(id,display_name,role,created_at,updated_at)
select u.id,
       coalesce(nullif(u.raw_user_meta_data->>'display_name',''),nullif(split_part(u.email,'@',1),''),'CampusAI 用户'),
       'student'::public.app_role,
       coalesce(u.created_at,now()),
       now()
from auth.users u
on conflict(id) do nothing;

do $$
begin
  if to_regclass('legacy.posts_20260822') is not null then
    execute $import$
      insert into public.posts(id,author_id,body,media_paths,moderation_status,like_count,comment_count,created_at,updated_at)
      select p.id,p.user_id,p.content,
             array(select jsonb_array_elements_text(coalesce(p.images,'[]'::jsonb))),
             'approved'::public.moderation_status,
             greatest(coalesce(p.like_count,0),0),greatest(coalesce(p.comment_count,0),0),
             coalesce(p.created_at,now()),coalesce(p.created_at,now())
      from legacy.posts_20260822 p
      join public.profiles owner on owner.id=p.user_id
      on conflict(id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.comments_20260822') is not null then
    execute $import$
      insert into public.comments(id,post_id,author_id,body,moderation_status,created_at,updated_at)
      select c.id,c.post_id,c.user_id,c.content,'approved'::public.moderation_status,
             coalesce(c.created_at,now()),coalesce(c.created_at,now())
      from legacy.comments_20260822 c
      join public.posts p on p.id=c.post_id
      join public.profiles owner on owner.id=c.user_id
      on conflict(id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.likes_20260822') is not null then
    execute $import$
      insert into public.post_likes(post_id,user_id,created_at)
      select distinct l.post_id,l.user_id,coalesce(l.created_at,now())
      from legacy.likes_20260822 l
      join public.posts p on p.id=l.post_id
      join public.profiles owner on owner.id=l.user_id
      on conflict(post_id,user_id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.favorites_20260822') is not null then
    execute $import$
      insert into public.post_bookmarks(post_id,user_id,created_at)
      select distinct f.post_id,f.user_id,coalesce(f.created_at,now())
      from legacy.favorites_20260822 f
      join public.posts p on p.id=f.post_id
      join public.profiles owner on owner.id=f.user_id
      where f.post_id is not null
      on conflict(post_id,user_id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.products_20260822') is not null then
    execute $import$
      insert into public.listings(id,seller_id,title,description,price_cents,category,condition,media_paths,status,moderation_status,created_at,updated_at)
      select p.id,p.seller_id,p.title,coalesce(p.description,''),greatest(round(p.price*100)::integer,0),
             '其他','良好',array(select jsonb_array_elements_text(coalesce(p.image_urls,'[]'::jsonb))),
             case when p.status in ('active','reserved','sold','withdrawn') then p.status else 'withdrawn' end,
             'approved'::public.moderation_status,coalesce(p.created_at,now()),coalesce(p.created_at,now())
      from legacy.products_20260822 p
      join public.profiles seller on seller.id=p.seller_id
      on conflict(id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.orders_20260822') is not null then
    execute $import$
      insert into public.orders(id,listing_id,buyer_id,seller_id,price_cents,status,created_at,updated_at)
      select o.id,o.product_id,o.buyer_id,o.seller_id,l.price_cents,
             case o.status when 'paid' then 'paid'::public.order_status when 'meeting' then 'meeting'::public.order_status
               when 'completed' then 'completed'::public.order_status when 'cancelled' then 'cancelled'::public.order_status
               when 'disputed' then 'disputed'::public.order_status else 'pending_payment'::public.order_status end,
             coalesce(o.created_at,now()),coalesce(o.created_at,now())
      from legacy.orders_20260822 o
      join public.listings l on l.id=o.product_id
      join public.profiles buyer on buyer.id=o.buyer_id
      join public.profiles seller on seller.id=o.seller_id
      where o.buyer_id<>o.seller_id
      on conflict(id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.conversations_20260822') is not null then
    execute $import$
      insert into public.conversations(id,created_at,updated_at)
      select c.id,coalesce(c.created_at,now()),coalesce(c.created_at,now())
      from legacy.conversations_20260822 c on conflict(id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.conversation_participants_20260822') is not null then
    execute $import$
      insert into public.conversation_members(conversation_id,user_id)
      select distinct cp.conversation_id,cp.user_id
      from legacy.conversation_participants_20260822 cp
      join public.conversations c on c.id=cp.conversation_id
      join public.profiles owner on owner.id=cp.user_id
      on conflict(conversation_id,user_id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.messages_20260822') is not null then
    execute $import$
      insert into public.messages(id,conversation_id,sender_id,body,created_at)
      select m.id,m.conversation_id,m.sender_id,coalesce(m.content,''),coalesce(m.created_at,now())
      from legacy.messages_20260822 m
      join public.conversations c on c.id=m.conversation_id
      join public.profiles sender on sender.id=m.sender_id
      where coalesce(m.content,'')<>''
      on conflict(id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.reports_20260822') is not null then
    execute $import$
      insert into public.reports(id,reporter_id,target_type,target_id,reason,details,status,created_at)
      select r.id,r.reporter_id,'profile',r.target_user_id,coalesce(r.reason,'未说明'),coalesce(r.details,''),
             case when r.status in ('pending','resolved','rejected') then r.status else 'pending' end,
             coalesce(r.created_at,now())
      from legacy.reports_20260822 r
      join public.profiles reporter on reporter.id=r.reporter_id
      where r.target_user_id is not null
      on conflict(id) do nothing
    $import$;
  end if;

  if to_regclass('legacy.announcements_20260822') is not null then
    execute $import$
      insert into public.announcements(title,body,audience,status,publish_at,created_at,updated_at)
      select a.title,a.content,jsonb_build_object('all',true,'legacyAuthor',coalesce(a.author_name,'')),
             'published',coalesce(a.created_at,now()),coalesce(a.created_at,now()),coalesce(a.created_at,now())
      from legacy.announcements_20260822 a
    $import$;
  end if;
end $$;

revoke all on all tables in schema legacy from public, anon, authenticated;
revoke all on all sequences in schema legacy from public, anon, authenticated;

commit;
