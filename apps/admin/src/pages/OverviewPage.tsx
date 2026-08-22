import { useQuery } from '@tanstack/react-query'
import { Symbol } from '../components/BrandMark'
import { supabase } from '../lib/supabase'

const fallback = { users: 1248, records: 8361, pending: 17, orders: 286 }

export function OverviewPage() {
  const { data = fallback, isFetching } = useQuery({
    queryKey:['admin-overview'],
    queryFn: async () => {
      if (!supabase) return fallback
      const [users, records, reports, orders] = await Promise.all([
        supabase.from('profiles').select('*', { count:'exact', head:true }),
        supabase.from('time_entries').select('*', { count:'exact', head:true }),
        supabase.from('reports').select('*', { count:'exact', head:true }).eq('status','pending'),
        supabase.from('orders').select('*', { count:'exact', head:true }),
      ])
      const error = users.error ?? records.error ?? reports.error ?? orders.error
      if (error) throw error
      return { users:users.count ?? 0, records:records.count ?? 0, pending:reports.count ?? 0, orders:orders.count ?? 0 }
    },
  })

  return <>
    <div className="page-header"><div><div className="eyebrow">总览 / 今日</div><h1>校园运行脉搏</h1><p className="muted">连续指标、主趋势和待处理队列在同一条工作流中。</p></div><button className="pill-button primary"><Symbol>add</Symbol>发布公告</button></div>
    <section className="metric-strip glass strong" aria-label="关键指标">
      <Metric label="活跃用户" value={data.users.toLocaleString()} delta="本周 +8.4%" />
      <Metric label="时间记录" value={data.records.toLocaleString()} delta="今日 +612" />
      <Metric label="待审核" value={data.pending.toString()} delta="需在 2h 内处理" warn />
      <Metric label="累计订单" value={data.orders.toLocaleString()} delta="履约率 96.8%" />
    </section>
    <div className="dashboard-grid">
      <section className="panel glass strong">
        <div className="panel-header"><div><div className="eyebrow">FOCUS / 7 DAYS</div><h2>有效投入趋势</h2></div><span className="badge">{isFetching?'同步中':'实时'}</span></div>
        <div className="chart">
          <svg className="chart-line" viewBox="0 0 800 280" preserveAspectRatio="none" role="img" aria-label="过去七天有效投入总体上升"><polyline points="20,222 142,188 264,205 386,126 508,150 630,82 780,48" /></svg>
        </div>
        <div className="chart-labels"><span>周一</span><span>周二</span><span>周三</span><span>周四</span><span>周五</span><span>周六</span><span>今天</span></div>
      </section>
      <section className="panel glass strong">
        <div className="panel-header"><div><div className="eyebrow">QUEUE / PRIORITY</div><h2>待处理</h2></div><button className="icon-button" aria-label="刷新"><Symbol>refresh</Symbol></button></div>
        <div className="queue">
          <Queue icon="flag" title="举报审核" meta="7 条高风险内容" badge="紧急" kind="error" />
          <Queue icon="receipt_long" title="订单争议" meta="3 笔等待仲裁" badge="处理中" kind="warn" />
          <Queue icon="storefront" title="商品复核" meta="5 件信息不完整" badge="待处理" />
          <Queue icon="rocket_launch" title="版本发布" meta="1.2.0 已通过检查" badge="可发布" kind="ok" />
        </div>
      </section>
    </div>
  </>
}

function Metric({label,value,delta,warn=false}:{label:string;value:string;delta:string;warn?:boolean}) { return <div className="metric"><div className="eyebrow">{label}</div><div className="metric-value">{value}</div><div className="metric-delta" style={warn?{color:'var(--warning)'}:undefined}>{delta}</div></div> }
function Queue({icon,title,meta,badge,kind=''}:{icon:string;title:string;meta:string;badge:string;kind?:string}) { return <div className="queue-row"><div className="queue-icon"><Symbol>{icon}</Symbol></div><div><strong>{title}</strong><div className="muted" style={{fontSize:12}}>{meta}</div></div><span className={`badge ${kind}`}>{badge}</span></div> }
