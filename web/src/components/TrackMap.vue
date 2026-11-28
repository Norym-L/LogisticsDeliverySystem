<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { RouteNode, StationView, TrackPosition } from '@/api/types'
import { AmapLoadError, amapJsApiKey, loadAmap } from '@/lib/amap-loader'

const props = defineProps<{
  nodes: RouteNode[]
  position?: TrackPosition | null
  fromStation?: StationView | null
  toStation?: StationView | null
}>()

const container = ref<HTMLDivElement | null>(null)
const mapError = ref('')

const modeClass = computed(() => `is-${(props.position?.mode ?? 'not-shipped').toLowerCase().replace(/_/g, '-')}`)

/** 高德 JS API 使用 [经度, 纬度]，注意不要按 [纬度, 经度] 传入。 */
type LngLatTuple = [number, number]
type MapOverlay = AMap.Marker | AMap.Polyline

let map: AMap.Map | null = null
let overlays: MapOverlay[] = []
let disposed = false

function toLngLat(latitude?: number | null, longitude?: number | null): LngLatTuple | null
{
  if (latitude == null || longitude == null) return null
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
  return [longitude, latitude]
}

function stationLngLat(station?: StationView | null): LngLatTuple | null
{
  return station ? toLngLat(station.latitude, station.longitude) : null
}

function nodeLngLat(node: RouteNode): LngLatTuple | null
{
  return toLngLat(node.latitude, node.longitude)
}

function escapeHtml(value: string): string
{
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function addOverlay(overlay: MapOverlay)
{
  if (!map) return
  map.add(overlay)
  overlays.push(overlay)
}

function stationMarker(lngLat: LngLatTuple, name: string, active: boolean): AMap.Marker
{
  return new AMap.Marker({
    position: lngLat,
    anchor: 'center',
    title: name,
    zIndex: active ? 130 : 120,
    content: `<div class="track-station-dot${active ? ' is-active' : ''}" aria-hidden="true"></div>`
  })
}

function render()
{
  if (!map) return
  if (overlays.length > 0)
  {
    map.remove(overlays)
    overlays = []
  }

  const boundPoints: LngLatTuple[] = []
  const routePoints: LngLatTuple[] = []
  const routeNodes: Array<{ node: RouteNode; lngLat: LngLatTuple }> = []

  for (const node of props.nodes)
  {
    const lngLat = nodeLngLat(node)
    if (!lngLat) continue
    routePoints.push(lngLat)
    routeNodes.push({ node, lngLat })
    boundPoints.push(lngLat)
  }

  // 已固化的相邻途经站连成折线；未来路段用虚线，已走过的路段用品牌色实线。
  if (routePoints.length >= 2)
  {
    addOverlay(new AMap.Polyline({
      path: routePoints,
      strokeColor: '#9aa0a6',
      strokeWeight: 3,
      strokeOpacity: 0.75,
      strokeStyle: 'dashed',
      strokeDasharray: [6, 8],
      lineJoin: 'round',
      lineCap: 'round',
      zIndex: 10
    }))
  }

  // 在途时把“已发运但未到站”的那一段单独标成橙色虚线，配合中点标记表达这一段正在运输。
  if (props.position?.mode === 'IN_TRANSIT')
  {
    const from = routeNodes.find(({ node }) => node.stationId === props.position?.fromStationId)
    const to = routeNodes.find(({ node }) => node.stationId === props.position?.toStationId)
    if (from && to)
    {
      addOverlay(new AMap.Polyline({
        path: [from.lngLat, to.lngLat],
        strokeColor: '#d97706',
        strokeWeight: 4,
        strokeOpacity: 0.9,
        strokeStyle: 'dashed',
        strokeDasharray: [4, 8],
        lineJoin: 'round',
        lineCap: 'round',
        zIndex: 20
      }))
    }
  }

  const positionSeq = props.position?.seq ?? 0
  if (positionSeq > 0 && props.position?.mode !== 'NOT_SHIPPED')
  {
    const passed = routeNodes
      .filter(({ node }) => node.seq <= positionSeq)
      .map(({ lngLat }) => lngLat)
    if (passed.length >= 2)
    {
      addOverlay(new AMap.Polyline({
        path: passed,
        strokeColor: '#dc1e32',
        strokeWeight: 4,
        strokeOpacity: 0.9,
        lineJoin: 'round',
        lineCap: 'round',
        zIndex: 30
      }))
    }
  }

  const activeStationId = props.position && props.position.mode !== 'IN_TRANSIT'
    ? props.position.stationId
    : null

  for (const { node, lngLat } of routeNodes)
  {
    addOverlay(stationMarker(lngLat, node.stationName, node.stationId === activeStationId))
  }

  // 还没有固化路线（待审批/已拒绝）时，至少画出起止站，避免详情页只剩一张空白底图。
  if (routeNodes.length === 0)
  {
    const present = new Set<number>()
    for (const station of [props.fromStation, props.toStation])
    {
      const lngLat = stationLngLat(station)
      if (!station || !lngLat || present.has(station.id)) continue
      present.add(station.id)
      boundPoints.push(lngLat)
      addOverlay(stationMarker(lngLat, station.name, station.id === activeStationId))
    }
  }

  let currentLngLat = toLngLat(props.position?.latitude, props.position?.longitude)
  if (!currentLngLat && props.position?.stationId != null)
  {
    const matched = routeNodes.find(({ node }) => node.stationId === props.position?.stationId)
    if (matched) currentLngLat = matched.lngLat
  }

  if (currentLngLat)
  {
    boundPoints.push(currentLngLat)
    const label = props.position?.label || '当前位置'
    // 当前位置偏东时把标签放到标记左侧，避免点击目的站时标签被地图右边缘裁掉。
    const averageLongitude = boundPoints.reduce((sum, point) => sum + point[0], 0) / boundPoints.length
    const labelLeft = boundPoints.length > 1 && currentLngLat[0] > averageLongitude
    addOverlay(new AMap.Marker({
      position: currentLngLat,
      anchor: 'center',
      title: label,
      zIndex: 1000,
      content: `<div class="track-marker ${modeClass.value}${labelLeft ? ' is-label-left' : ''}">
                  <span class="track-marker__dot"></span>
                  <span class="track-marker__label">${escapeHtml(label)}</span>
                </div>`
    }))
  }

  if (boundPoints.length === 0)
  {
    map.setZoomAndCenter(4, [110, 35])
    return
  }
  map.setFitView(overlays, false, [40, 40, 40, 40], 11)
}

function onResize()
{
  // AMap 2.0 会监听容器尺寸；保留一次容错调用，老版本没有 resize 时静默跳过。
  const resizable = map as (AMap.Map & { resize?: () => void }) | null
  resizable?.resize?.()
}

function describeMapError(error: unknown): string
{
  if (error instanceof AmapLoadError)
  {
    if (error.code === 'MISSING_KEY')
    {
      return '未配置高德地图 Key：请把 web/.env.example 复制为 web/.env.local，填写 VITE_AMAP_JS_API_KEY 后重新构建。'
    }
    if (error.code === 'TIMEOUT')
    {
      return '高德地图加载超时：请检查网络，以及 Key 是否允许当前访问域名。'
    }
  }
  return '高德地图加载失败：请检查网络，以及 Key 是否允许当前访问域名。'
}

onMounted(async () =>
{
  if (!container.value) return
  if (!amapJsApiKey())
  {
    mapError.value = describeMapError(new AmapLoadError('MISSING_KEY', '未配置高德地图 Key'))
    return
  }

  try
  {
    const AMapNS = await loadAmap()
    if (disposed || !container.value) return
    map = new AMapNS.Map(container.value, {
      viewMode: '2D',
      zoom: 4,
      center: [110, 35],
      scrollWheel: false
    })
    // 底图、logo 与版权信息由高德 JS API 默认控件提供，不要隐藏或移除。
    render()
    window.addEventListener('resize', onResize)
  }
  catch (error)
  {
    if (!disposed) mapError.value = describeMapError(error)
  }
})

watch(
  () => [props.nodes, props.position, props.fromStation, props.toStation],
  () => render(),
  { deep: true }
)

onUnmounted(() =>
{
  disposed = true
  window.removeEventListener('resize', onResize)
  map?.destroy()
  map = null
  overlays = []
})
</script>

<template>
  <div class="track-map">
    <div
      ref="container"
      class="track-map__canvas"
      role="img"
      :aria-label="position?.label ? `轨迹地图：${position.label}` : '订单轨迹地图'"
    >
      <p v-if="mapError" class="track-map__error">{{ mapError }}</p>
    </div>
    <div v-if="position" class="track-map__caption">
      <span class="track-map__caption-dot" :class="modeClass"></span>
      <b>{{ position.label }}</b>
    </div>
    <p v-else class="track-map__hint">暂无可用坐标，无法在地图上标出位置。</p>
    <p class="track-map__hint">
      站点间折线为路线示意，不是车辆实时定位。底图与坐标系由高德地图提供（GCJ-02），
      地图 logo 与版权信息由高德 JS API 默认控件保留。
    </p>
  </div>
</template>
