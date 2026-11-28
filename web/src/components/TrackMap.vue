<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import type { RouteNode, StationView, TrackPosition } from '@/api/types'

const props = defineProps<{
  nodes: RouteNode[]
  position?: TrackPosition | null
  fromStation?: StationView | null
  toStation?: StationView | null
}>()

const container = ref<HTMLDivElement | null>(null)
let map: L.Map | null = null
let layers: L.LayerGroup | null = null

const modeClass = computed(() => `is-${(props.position?.mode ?? 'not-shipped').toLowerCase().replace(/_/g, '-')}`)

type LatLngTuple = [number, number]

function toLatLng(latitude?: number | null, longitude?: number | null): LatLngTuple | null {
  if (latitude == null || longitude == null) return null
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
  return [latitude, longitude]
}

function stationLatLng(station?: StationView | null): LatLngTuple | null {
  return station ? toLatLng(station.latitude, station.longitude) : null
}

function nodeLatLng(node: RouteNode): LatLngTuple | null {
  return toLatLng(node.latitude, node.longitude)
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function addStationDot(group: L.LayerGroup, latLng: LatLngTuple, name: string, active: boolean) {
  const marker = L.circleMarker(latLng, {
    radius: active ? 8 : 5,
    color: '#dc1e32',
    weight: active ? 3 : 2,
    fillColor: '#fffdf8',
    fillOpacity: 1
  })
  marker.addTo(group)
  marker.bindTooltip(escapeHtml(name), {
    direction: 'top',
    offset: [0, -8],
    className: 'track-station-tooltip'
  })
}

function render() {
  if (!map || !layers) return
  layers.clearLayers()

  const boundPoints: L.LatLng[] = []
  const routePoints: LatLngTuple[] = []
  const routeNodes: Array<{ node: RouteNode; latLng: LatLngTuple }> = []

  for (const node of props.nodes) {
    const latLng = nodeLatLng(node)
    if (!latLng) continue
    routePoints.push(latLng)
    routeNodes.push({ node, latLng })
    boundPoints.push(L.latLng(latLng))
  }

  // 已固化的相邻途经站连成折线；未来路段用虚线，已走过的路段用品牌色实线。
  if (routePoints.length >= 2) {
    L.polyline(routePoints, {
      color: '#9aa0a6',
      weight: 3,
      opacity: 0.75,
      dashArray: '6 8'
    }).addTo(layers)
  }

  // 在途时把"已发运但未到站"的那一段单独标成橙色虚线，配合中点标记表达这一段正在运输。
  if (props.position?.mode === 'IN_TRANSIT') {
    const from = routeNodes.find(({ node }) => node.stationId === props.position?.fromStationId)
    const to = routeNodes.find(({ node }) => node.stationId === props.position?.toStationId)
    if (from && to) {
      L.polyline([from.latLng, to.latLng], {
        color: '#d97706',
        weight: 4,
        opacity: 0.9,
        dashArray: '4 8'
      }).addTo(layers)
    }
  }

  const positionSeq = props.position?.seq ?? 0
  if (positionSeq > 0 && props.position?.mode !== 'NOT_SHIPPED') {
    const passed = routeNodes
      .filter(({ node }) => node.seq <= positionSeq)
      .map(({ latLng }) => latLng)
    if (passed.length >= 2) {
      L.polyline(passed, {
        color: '#dc1e32',
        weight: 4,
        opacity: 0.9
      }).addTo(layers)
    }
  }

  const activeStationId = props.position && props.position.mode !== 'IN_TRANSIT'
    ? props.position.stationId
    : null

  for (const { node, latLng } of routeNodes) {
    addStationDot(layers, latLng, node.stationName, node.stationId === activeStationId)
  }

  // 还没有固化路线（待审批/已拒绝）时，至少画出起止站，避免详情页只剩一张空白底图。
  if (routeNodes.length === 0) {
    const present = new Set<number>()
    for (const station of [props.fromStation, props.toStation]) {
      const latLng = stationLatLng(station)
      if (!station || !latLng || present.has(station.id)) continue
      present.add(station.id)
      boundPoints.push(L.latLng(latLng))
      addStationDot(layers, latLng, station.name, station.id === activeStationId)
    }
  }

  let currentLatLng = toLatLng(props.position?.latitude, props.position?.longitude)
  if (!currentLatLng && props.position?.stationId != null) {
    const matched = routeNodes.find(({ node }) => node.stationId === props.position?.stationId)
    if (matched) currentLatLng = matched.latLng
  }

  if (currentLatLng) {
    boundPoints.push(L.latLng(currentLatLng))
    const label = props.position?.label || '当前位置'
    // 当前位置偏东时把标签放到标记左侧，避免点击目的站时标签被地图右边缘裁掉。
    const averageLongitude = boundPoints.reduce((sum, point) => sum + point.lng, 0) / boundPoints.length
    const labelLeft = boundPoints.length > 1 && currentLatLng[1] > averageLongitude
    const icon = L.divIcon({
      className: 'track-marker-host',
      html: `<div class="track-marker ${modeClass.value}${labelLeft ? ' is-label-left' : ''}">
               <span class="track-marker__dot"></span>
               <span class="track-marker__label">${escapeHtml(label)}</span>
             </div>`,
      iconSize: [0, 0],
      iconAnchor: [0, 0]
    })
    L.marker(currentLatLng, {
      icon,
      zIndexOffset: 1000,
      keyboard: false,
      title: label
    }).addTo(layers)
  }

  if (boundPoints.length === 0) {
    map.setView([35, 110], 4)
    return
  }
  map.fitBounds(L.latLngBounds(boundPoints), {
    padding: [40, 40],
    maxZoom: 11
  })
}

function onResize() {
  map?.invalidateSize()
}

onMounted(() => {
  if (!container.value) return
  map = L.map(container.value, {
    zoomControl: true,
    attributionControl: true,
    scrollWheelZoom: false
  }).setView([35, 110], 4)

  // Leaflet + OpenStreetMap；署名由 attributionControl 保留，不要移除。
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'
  }).addTo(map)

  layers = L.layerGroup().addTo(map)
  render()
  window.addEventListener('resize', onResize)
})

watch(
  () => [props.nodes, props.position, props.fromStation, props.toStation],
  () => render(),
  { deep: true }
)

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  map?.remove()
  map = null
  layers = null
})
</script>

<template>
  <div class="track-map">
    <div
      ref="container"
      class="track-map__canvas"
      role="img"
      :aria-label="position?.label ? `轨迹地图：${position.label}` : '订单轨迹地图'"
    ></div>
    <div v-if="position" class="track-map__caption">
      <span class="track-map__caption-dot" :class="modeClass"></span>
      <b>{{ position.label }}</b>
    </div>
    <p v-else class="track-map__hint">暂无可用坐标，无法在地图上标出位置。</p>
    <p class="track-map__hint">
      站点间折线为路线示意，不是车辆实时定位。底图 ©
      <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>
      contributors。
    </p>
  </div>
</template>
