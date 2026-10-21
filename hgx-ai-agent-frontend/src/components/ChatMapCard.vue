<template>
  <div class="chat-map-card">
    <div class="chat-map-header">
      <span class="chat-map-title">{{ bundle?.title || '空间地图' }}</span>
      <button type="button" class="chat-map-toggle" @click="collapsed = !collapsed">
        {{ collapsed ? '展开地图' : '隐藏地图' }}
      </button>
    </div>
    <div v-show="!collapsed" class="chat-map-body">
      <div ref="mapEl" class="chat-map-container"></div>
      <div v-if="error" class="chat-map-overlay">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getMapQueryResult } from '../services/api';
import { wgs84ToGcj02, geojsonToGcj02 } from '../services/coords';

const props = defineProps({
  mapId: {
    type: String,
    required: true,
  },
});

const mapEl = ref(null);
const bundle = ref(null);
const error = ref('');
const collapsed = ref(false);

let map = null;
let loaded = false;

async function load() {
  if (loaded || collapsed.value) {
    return;
  }
  loaded = true;
  try {
    const { data } = await getMapQueryResult(props.mapId);
    bundle.value = data;
    await nextTick();
    renderMap();
  } catch (e) {
    error.value = e?.response?.status === 404 ? '地图结果已过期，请重新提问生成。' : '地图加载失败';
  }
}

function renderMap() {
  if (!mapEl.value || !bundle.value) {
    return;
  }
  map = L.map(mapEl.value, { zoomControl: true, attributionControl: false });
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    { subdomains: ['1', '2', '3', '4'], maxZoom: 18 }
  ).addTo(map);

  const allBounds = [];

  // 目标地点高亮（纠偏到 GCJ-02 与高德底图对齐）
  const { center, targetName } = bundle.value;
  const gcjCenter = center ? wgs84ToGcj02(center.lon, center.lat) : null;
  if (gcjCenter) {
    L.circleMarker([gcjCenter[1], gcjCenter[0]], {
      radius: 10,
      color: '#d4380d',
      weight: 3,
      fillColor: '#ff7a45',
      fillOpacity: 0.85,
    })
      .addTo(map)
      .bindPopup(`<strong>${targetName || '目标地点'}</strong>`)
      .openPopup();
    allBounds.push([gcjCenter[1], gcjCenter[0]]);
  }

  // 周边圈层
  if (gcjCenter && bundle.value.radiusKm) {
    L.circle([gcjCenter[1], gcjCenter[0]], {
      radius: bundle.value.radiusKm * 1000,
      color: '#2f80ed',
      weight: 1,
      dashArray: '6 6',
      fillOpacity: 0.04,
    }).addTo(map);
  }

  // 各图层要素
  for (const layer of bundle.value.layers || []) {
    L.geoJSON(geojsonToGcj02(layer.geojson), {
      style: () => ({ color: layer.color, weight: 2, fillOpacity: 0.25 }),
      pointToLayer: (feature, latlng) =>
        L.circleMarker(latlng, {
          radius: 7,
          color: layer.color,
          weight: 2,
          fillColor: layer.color,
          fillOpacity: 0.75,
        }),
      onEachFeature: (feature, lyr) => {
        const props = feature.properties || {};
        const rows = Object.entries(props)
          .map(([k, v]) => `<tr><th>${esc(k)}</th><td>${esc(String(v))}</td></tr>`)
          .join('');
        lyr.bindPopup(
          `<div class="map-popup"><strong>${esc(props.name || layer.name)}</strong><table>${rows}</table></div>`,
          { maxWidth: 300 }
        );
      },
    }).addTo(map);
    const transformed = geojsonToGcj02(layer.geojson);
    for (const f of transformed?.features || []) {
      const coords = centroidOf(f.geometry);
      if (coords) {
        allBounds.push(coords);
      }
    }
  }

  if (allBounds.length > 1) {
    map.fitBounds(allBounds, { padding: [24, 24] });
  } else if (gcjCenter) {
    map.setView([gcjCenter[1], gcjCenter[0]], 13);
  }
}

function centroidOf(geometry) {
  if (!geometry || !geometry.coordinates) {
    return null;
  }
  const flat = JSON.stringify(geometry.coordinates).match(/-?\d+\.?\d*/g)?.map(Number) || [];
  let sx = 0, sy = 0, n = 0;
  for (let i = 0; i + 1 < flat.length; i += 2) {
    sx += flat[i];
    sy += flat[i + 1];
    n++;
  }
  return n ? [sy / n, sx / n] : null;
}

function esc(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

watch(collapsed, async (val) => {
  if (!val) {
    await nextTick();
    if (map) {
      map.invalidateSize();
    } else {
      loaded = false;
      await load();
    }
  }
});

watch(mapEl, () => load(), { immediate: true });

onBeforeUnmount(() => {
  if (map) {
    map.remove();
    map = null;
  }
});
</script>
