import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Symbol } from '../components/BrandMark'
import { supabase } from '../lib/supabase'

type Kind = 'users'|'content'|'listings'|'orders'|'reports'|'announcements'|'releases'|'audit'
const config:Record<Kind,{eyebrow:string;title:string;description:string;action:string;columns:string[]}>={
  users:{eyebrow:'IDENTITY / ACCESS',title:'用户',description:'账户状态、角色和近期活动。管理员授权只在服务端生效。',action:'邀请用户',columns:['用户','角色','状态','最近活动']},
  content:{eyebrow:'COMMUNITY / CONTENT',title:'帖子与评论',description:'按风险和时间处理内容，不让审核离开上下文。',action:'筛选内容',columns:['内容','作者','风险','发布时间']},
  listings:{eyebrow:'MARKET / LISTINGS',title:'商品',description:'检查商品状态、价格和媒体，保留已售内容历史。',action:'导出列表',columns:['商品','卖家','状态','价格']},
  orders:{eyebrow:'MARKET / ORDERS',title:'订单',description:'从创建到完成的节点轨迹，以及需要人工介入的争议。',action:'导出订单',columns:['订单','买卖双方','进度','金额']},
  reports:{eyebrow:'TRUST / SAFETY',title:'举报审核',description:'高风险优先，所有处理动作都写入不可变审计轨迹。',action:'审核规则',columns:['举报对象','举报人','风险','提交时间']},
  announcements:{eyebrow:'OPERATIONS / NOTICE',title:'公告',description:'面向不同用户群发布、定时和撤回校园公告。',action:'新建公告',columns:['标题','受众','状态','发布时间']},
  releases:{eyebrow:'DELIVERY / RELEASE',title:'版本发布',description:'Android 构建、校验和、灰度范围与强制升级策略。',action:'新建版本',columns:['版本','构建','状态','发布时间']},
  audit:{eyebrow:'SECURITY / AUDIT',title:'审计日志',description:'按操作者、资源与动作检索服务端审计事件。',action:'导出日志',columns:['操作者','动作','结果','发生时间']},
}

const samples:Record<Kind,string[][]>={
  users:[['林屿 / lin@example.edu','student','正常','2 分钟前'],['陈默 / chen@example.edu','moderator','正常','18 分钟前'],['匿名账户 A19','student','受限','1 小时前']],
  content:[['图书馆三楼学习搭子','匿名同学','低','12 分钟前'],['二手群外链评论','林屿','中','36 分钟前'],['课程资料分享','陈默','低','1 小时前']],
  listings:[['机械键盘 87 键','北区学生','在售','¥168'],['线性代数教材','数学系同学','在售','¥28'],['折叠露营椅','南苑 5 栋','已售','¥55']],
  orders:[['CA-0822-031','林屿 / 北区学生','待交付','¥168'],['CA-0821-116','陈默 / 数学系同学','已完成','¥28'],['CA-0820-098','匿名 A19 / 南苑 5 栋','争议中','¥55']],
  reports:[['帖子 #P-3821','用户 L17','高','8 分钟前'],['商品 #G-992','匿名用户','中','21 分钟前'],['评论 #C-811','用户 R04','低','1 小时前']],
  announcements:[['新学期数据同步说明','全部用户','已发布','今天 08:00'],['图书馆服务调整','北校区','定时','明天 07:30'],['市场交易提醒','市场用户','草稿','—']],
  releases:[['1.2.0','120 / a8f2','待发布','—'],['1.1.4','114 / e921','已发布','8月12日'],['1.1.3','113 / b20d','已归档','7月28日']],
  audit:[['moderator@campus.ai','APPROVE_REPORT','成功','2 分钟前'],['admin@campus.ai','PUBLISH_NOTICE','成功','18 分钟前'],['system','SYNC_ORDER','重试','26 分钟前']],
}

export function DataPage({kind}:{kind:Kind}) {
  const page=config[kind]
  const [selected,setSelected]=useState<Set<number>>(new Set())
  const [search,setSearch]=useState('')
  const {data:rows=samples[kind],isLoading,error}=useQuery({queryKey:['admin-data',kind],queryFn:()=>loadRows(kind)})
  const visibleRows=useMemo(()=>rows.filter(row=>row.some(value=>value.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()))),[rows,search])
  const toggle=(index:number)=>setSelected(current=>{const next=new Set(current);if(next.has(index))next.delete(index);else next.add(index);return next})
  return <>
    <div className="page-header"><div><div className="eyebrow">{page.eyebrow}</div><h1>{page.title}</h1><p className="muted">{page.description}</p></div><button className="pill-button primary" disabled title="写操作需在完成线上数据映射后启用"><Symbol>add</Symbol>{page.action}</button></div>
    <div className="filters"><label className="search-field glass"><Symbol>search</Symbol><input value={search} onChange={event=>setSearch(event.target.value)} placeholder="搜索当前结果" aria-label="搜索当前结果"/></label><button className="pill-button" disabled><Symbol>filter_list</Symbol>筛选</button><button className="pill-button" disabled><Symbol>sort</Symbol>最近更新</button></div>
    {error&&<div className="error-bar" role="alert">读取失败：{error instanceof Error?error.message:'未知错误'}。请检查权限或网络后刷新。</div>}
    <div className="table-panel glass strong" style={{marginTop:14}}>
      <div className="table-row header"><span/><span>{page.columns[0]}</span><span>{page.columns[1]}</span><span>{page.columns[2]}</span><span>{page.columns[3]}</span><span/></div>
      {isLoading&&<div className="empty-row muted">正在读取受保护数据…</div>}
      {!isLoading&&visibleRows.length===0&&<div className="empty-row muted">没有匹配结果。</div>}
      {visibleRows.map((row,index)=><div className="table-row" key={`${row[0]}-${index}`}>
        <input className="check" type="checkbox" checked={selected.has(index)} onChange={()=>toggle(index)} aria-label={`选择 ${row[0]}`}/>
        <strong>{row[0]}</strong><span className="muted">{row[1]}</span><span><Status value={row[2]}/></span><span className="muted">{row[3]}</span><button className="icon-button" aria-label="更多操作"><Symbol>more_horiz</Symbol></button>
      </div>)}
    </div>
    {selected.size>0&&<div className="bulk-bar glass strong"><strong>已选 {selected.size} 项</strong><button className="pill-button" disabled>标记</button><button className="pill-button primary" disabled>批量处理</button></div>}
  </>
}

function Status({value}:{value:string}) { const kind=/高|受限|争议|重试/.test(value)?'error':/中|待|定时|草稿/.test(value)?'warn':/正常|成功|完成|发布|在售/.test(value)?'ok':'';return <span className={`badge ${kind}`}>{value}</span> }

async function loadRows(kind:Kind):Promise<string[][]>{
  if(!supabase)return samples[kind]
  const query=kind==='users'?supabase.from('profiles').select('display_name,handle,role,is_blocked,updated_at'):
    kind==='content'?supabase.from('posts').select('body,author_id,moderation_status,created_at'):
    kind==='listings'?supabase.from('listings').select('title,seller_id,status,price_cents,created_at'):
    kind==='orders'?supabase.from('orders').select('id,buyer_id,seller_id,status,price_cents,created_at'):
    kind==='reports'?supabase.from('reports').select('target_type,target_id,reporter_id,status,created_at'):
    kind==='announcements'?supabase.from('announcements').select('title,audience,status,publish_at,created_at'):
    kind==='releases'?supabase.from('app_releases').select('version_name,version_code,status,published_at,created_at'):
    supabase.from('audit_logs').select('actor_id,action,result,created_at')
  const {data,error}=await query.order('created_at',{ascending:false}).limit(100)
  if(error)throw error
  return (data??[]).map((item:Record<string,unknown>)=>mapRow(kind,item))
}

function mapRow(kind:Kind,item:Record<string,unknown>):string[]{
  const date=formatDate(item.updated_at??item.publish_at??item.published_at??item.created_at)
  const short=(value:unknown)=>String(value??'—').slice(0,12)
  if(kind==='users')return [`${item.display_name??'未命名'} / ${item.handle??'—'}`,String(item.role??'student'),item.is_blocked?'受限':'正常',date]
  if(kind==='content')return [String(item.body??'').slice(0,42),short(item.author_id),statusLabel(item.moderation_status),date]
  if(kind==='listings')return [String(item.title??'未命名'),short(item.seller_id),statusLabel(item.status),money(item.price_cents)]
  if(kind==='orders')return [short(item.id),`${short(item.buyer_id)} / ${short(item.seller_id)}`,statusLabel(item.status),money(item.price_cents)]
  if(kind==='reports')return [`${item.target_type??'内容'} / ${short(item.target_id)}`,short(item.reporter_id),statusLabel(item.status),date]
  if(kind==='announcements')return [String(item.title??'未命名'),JSON.stringify(item.audience??{}).slice(0,24),statusLabel(item.status),date]
  if(kind==='releases')return [String(item.version_name??'—'),String(item.version_code??'—'),statusLabel(item.status),date]
  return [short(item.actor_id),String(item.action??'—'),statusLabel(item.result),date]
}

function money(value:unknown){const cents=Number(value);return Number.isFinite(cents)?`¥${(cents/100).toFixed(2)}`:'—'}
function formatDate(value:unknown){if(!value)return '—';const date=new Date(String(value));return Number.isNaN(date.valueOf())?'—':date.toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'})}
function statusLabel(value:unknown){const labels:Record<string,string>={pending:'待处理',approved:'已通过',rejected:'已拒绝',removed:'已移除',active:'在售',reserved:'已预订',sold:'已售',withdrawn:'已下架',pending_payment:'待付款',paid:'已付款',meeting:'待交付',completed:'已完成',cancelled:'已取消',disputed:'争议中',draft:'草稿',scheduled:'定时',published:'已发布',success:'成功',retry:'重试'};return labels[String(value)]??String(value??'—')}
