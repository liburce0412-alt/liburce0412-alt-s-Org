export function BrandMark() {
  return <span className="brand-mark" aria-label="CampusAI"><i className="brand-node"/><i className="brand-node"/><i className="brand-node"/><i className="brand-node"/><i className="brand-node"/></span>
}

const symbols: Record<string, string> = {
  add: '\ue145', blur_on: '\ue3a5', campaign: '\uef49', dashboard: '\ue871',
  fact_check: '\uf0c5', filter_list: '\ue152', flag: '\uf0c6', forum: '\ue8af',
  group: '\uea21', login: '\uea77', menu: '\ue5d2', more_horiz: '\ue5d3',
  notifications: '\ue7f5', person: '\uf0d3', policy: '\uea17', receipt_long: '\uef6e',
  refresh: '\ue5d5', rocket_launch: '\ueb9b', search: '\uef7a', sort: '\ue164',
  storefront: '\uea12',
}

export function Symbol({ children, filled = false }: { children: string; filled?: boolean }) {
  return <span className="material-symbols-rounded" aria-hidden="true" style={filled ? { fontVariationSettings: "'FILL' 1, 'wght' 450, 'GRAD' 0, 'opsz' 24" } : undefined}>{symbols[children] ?? symbols.dashboard}</span>
}
