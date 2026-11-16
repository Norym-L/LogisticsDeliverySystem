export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface LoginView {
  id: number
  username: string
  name: string
  role: 'PATH_ADMIN' | 'STATION_ADMIN' | 'POSTMAN'
  stationId: number | null
}

export interface StationView {
  id: number
  code: string
  name: string
}

export interface OrderBrief {
  orderNo: string
  state: string
  fromStation: string | null
  toStation: string | null
  createTime: string
}

export interface RoutePlan {
  stations: StationView[]
  totalKm: number
}

export interface CreatedOrder {
  orderNo: string
  state: string
  approveDeadline: string
}

export interface PostmanInfo {
  id: number
  username: string
  name: string
  phone: string
  stationId: number | null
  stationName: string | null
  status: number
  activeTasks: number
  doneTasks: number
}

export interface PostmanTask {
  orderNo: string
  orderState: string
  seq: number
  forwardStationCount: number
  fromStationName: string
  toStationName: string
  tripNo: string | null
  departTime: string | null
  arriveTime: string | null
  legStatus: string
  lastMile: boolean
  senderName: string
  receiverName: string
  receiverPhone: string
  goodsDesc: string | null
}

export const STATE_LABEL: Record<string, string> = {
  WAITING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  SENDING: '运送中',
  DELIVERING: '派送中',
  RECEIVED: '已签收',
  REFUSED: '已拒收'
}

export function stateLabel(state: string): string {
  return STATE_LABEL[state] ?? state
}

export function stateTone(state: string): string {
  if (state === 'WAITING') return 'tone-wait'
  if (state === 'REJECTED' || state === 'REFUSED') return 'tone-bad'
  if (state === 'RECEIVED') return 'tone-ok'
  return 'tone-run'
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}
