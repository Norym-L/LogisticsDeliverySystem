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
  address?: string | null
  /** GCJ-02 纬度（高德底图），来自 station.latitude；缺失时不画点 */
  latitude?: number | null
  /** GCJ-02 经度（高德底图），来自 station.longitude */
  longitude?: number | null
}

export interface OrderBrief {
  orderNo: string
  state: string
  fromStation: string | null
  toStation: string | null
  createTime: string
  approveDeadline?: string | null
  quotedPrice?: number | null
  price?: number | null
  priceConfirmed?: boolean | null
}

export interface RoutePlan {
  stations: StationView[]
  totalKm: number
}

export interface CreatedOrder {
  orderNo: string
  state: string
  approveDeadline: string
  volumeCm3?: number | null
  quotedPrice?: number | null
  price?: number | null
  priceConfirmed?: boolean | null
}

export interface PriceQuote {
  weightKg: number
  volumeCm3: number
  volumetricWeightKg: number
  chargeableWeightKg: number
  distanceKm: number
  firstKgPrice: number
  additionalKgPrice: number
  additionalWeightKg: number
  freight: number
  estimatedDays?: number | null
  orderNo?: string | null
  priceConfirmed?: boolean | null
  priceConfirmedAt?: string | null
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

export interface RouteNode {
  seq: number
  stationId: number
  stationName: string
  status: string
  departTime: string | null
  arriveTime: string | null
  tripNo: string | null
  /** GCJ-02 纬度（高德底图），来自 station.latitude */
  latitude?: number | null
  /** GCJ-02 经度（高德底图），来自 station.longitude */
  longitude?: number | null
}

/** 后端算好的地图当前位置；前端只用坐标和说明，不自己推断状态。 */
export interface TrackPosition {
  mode: 'NOT_SHIPPED' | 'AT_STATION' | 'IN_TRANSIT' | 'DELIVERING' | 'RECEIVED' | 'REFUSED' | string
  label: string
  seq: number
  stationId?: number | null
  stationName?: string | null
  latitude?: number | null
  longitude?: number | null
  fromStationId?: number | null
  fromStationName?: string | null
  toStationId?: number | null
  toStationName?: string | null
}

export interface OrderDetail {
  orderNo: string
  state: string
  senderName: string
  senderPhone: string
  receiverName: string
  receiverPhone: string
  fromStation: StationView | null
  toStation: StationView | null
  currentStation: StationView | null
  /** 包裹在地图上的当前位置；后端按状态机计算，可能为 null（历史数据无坐标时） */
  currentPosition?: TrackPosition | null
  estimatedDays?: number | null
  estimatedDeliveryAt?: string | null
  goodsDesc: string | null
  weight: number | null
  volumeCm3?: number | null
  actualWeight?: number | null
  actualVolumeCm3?: number | null
  quotedPrice?: number | null
  price?: number | null
  priceConfirmed?: boolean | null
  priceConfirmedAt?: string | null
  trajectory: RouteNode[]
  events: {
    createdAt?: string | null
    approvedAt?: string | null
    originReceivedAt?: string | null
    receivedAt?: string | null
    refusedAt?: string | null
    cancelledAt?: string | null
  }
  rejectReason?: string | null
  refuseReason?: string | null
  returnOrderNo?: string | null
  originalOrderNo?: string | null
}

export interface OrderStatus {
  orderNo: string
  state: string
  rejectReason?: string | null
  approveDeadline?: string | null
  remainingSeconds: number
}

export interface StationInboxItem {
  bucket: 'RECEIVE' | 'DEPART' | 'ARRIVE' | 'DISPATCH'
  orderNo: string
  state: string
  fromStation: string
  toStation: string
  nextStation: string | null
  currentSeq: number
  forwardStationCount: number
  tripNo: string | null
  goodsDesc: string | null
  receiverName: string
  receiverPhone: string
  weight?: number | null
  volumeCm3?: number | null
  priceConfirmed?: boolean | null
  remark?: string | null
  createTime: string
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
  weight?: number | null
  volumeCm3?: number | null
  actualWeight?: number | null
  actualVolumeCm3?: number | null
  quotedPrice?: number | null
  price?: number | null
  priceConfirmed?: boolean | null
  taskType?: 'DELIVERY' | 'RECEIVE' | null
  remark?: string | null
}

export const STATE_LABEL: Record<string, string> = {
  WAITING: '待审批',
  AWAITING_RECEIPT: '待始发收件',
  APPROVED: '待发运',
  REJECTED: '已拒绝',
  SENDING: '运送中',
  DELIVERING: '派送中',
  RECEIVED: '已签收',
  REFUSED: '已拒收',
  CANCELLED: '已取消'
}

export function stateLabel(state: string): string {
  return STATE_LABEL[state] ?? state
}

export function stateTone(state: string): string {
  if (state === 'WAITING' || state === 'AWAITING_RECEIPT' || state === 'CANCELLED') return 'tone-wait'
  if (state === 'REJECTED' || state === 'REFUSED') return 'tone-bad'
  if (state === 'RECEIVED') return 'tone-ok'
  return 'tone-run'
}

export function formatTime(value: string | null | undefined): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export function formatYuan(value: number | null | undefined): string {
  if (value == null) return '—'
  return `${value} 元`
}

export const LEG_LABEL: Record<string, string> = {
  PENDING: '未到达',
  ARRIVED: '已到站',
  DELIVERING: '派送中',
  SIGNED: '已签收',
  REFUSED: '已拒收'
}

export function legLabel(status: string | null | undefined): string {
  if (!status) return '—'
  return LEG_LABEL[status] ?? status
}

/** 用截止时间在页面上显示剩余审批时间。now 传入是为了让倒计时能跟着时钟刷新。 */
export function remainingLabel(deadline: string | null | undefined, now = Date.now()): string {
  if (!deadline) return '—'
  const ms = new Date(deadline).getTime() - now
  if (Number.isNaN(ms)) return '—'
  if (ms <= 0) return '已到截止时间'
  const total = Math.ceil(ms / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60)
    return `${hours}小时${minutes % 60}分`
  }
  return minutes > 0 ? `${minutes}分${seconds}秒` : `${seconds}秒`
}
