begin;

create or replace function public.claim_ai_request(max_requests integer) returns integer
language plpgsql security definer set search_path=public as $$
declare next_count integer;
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode='28000'; end if;
  if max_requests<1 or max_requests>100 then raise exception 'invalid_quota'; end if;
  insert into ai_usage_daily(user_id,day,request_count) values(auth.uid(),current_date,1)
  on conflict(user_id,day) do update set request_count=ai_usage_daily.request_count+1
  where ai_usage_daily.request_count<max_requests
  returning request_count into next_count;
  if next_count is null then raise exception 'daily_ai_quota_exhausted' using errcode='P0001'; end if;
  return next_count;
end $$;

create or replace function public.record_ai_usage(add_input_tokens integer, add_output_tokens integer, add_cost_micros bigint) returns void
language plpgsql security definer set search_path=public as $$
begin
  if auth.uid() is null then raise exception 'authentication_required' using errcode='28000'; end if;
  insert into ai_usage_daily(user_id,day,input_tokens,output_tokens,cost_micros)
  values(auth.uid(),current_date,least(greatest(add_input_tokens,0),1000000),least(greatest(add_output_tokens,0),1000000),least(greatest(add_cost_micros,0),1000000000))
  on conflict(user_id,day) do update set
    input_tokens=ai_usage_daily.input_tokens+excluded.input_tokens,
    output_tokens=ai_usage_daily.output_tokens+excluded.output_tokens,
    cost_micros=ai_usage_daily.cost_micros+excluded.cost_micros;
end $$;

revoke all on function public.claim_ai_request(integer), public.record_ai_usage(integer,integer,bigint) from public,anon;
grant execute on function public.claim_ai_request(integer) to authenticated;
grant execute on function public.record_ai_usage(integer,integer,bigint) to authenticated;

commit;
