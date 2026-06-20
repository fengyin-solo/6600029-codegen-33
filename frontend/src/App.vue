<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import MapView from './components/MapView.vue';
import TerrainProfile from './components/TerrainProfile.vue';
import FlightStats from './components/FlightStats.vue';
import NotificationToast from './components/NotificationToast.vue';
import RerouteSuggestion from './components/RerouteSuggestion.vue';
import { useDroneStore } from './store/drone';

const store = useDroneStore();

const testScenarios = [
  { name: '测试1: 机场附近临时管制', lat: 39.92, lng: 116.42, radius: 4000, type: 'restricted' as const },
  { name: '测试2: 大型活动管制', lat: 39.90, lng: 116.40, radius: 2500, type: 'airport' as const },
  { name: '测试3: 军事演习区域', lat: 39.88, lng: 116.43, radius: 3500, type: 'military' as const },
];

onMounted(() => {
  store.loadMockData();
  store.startNotificationPolling();
});

onUnmounted(() => {
  store.stopNotificationPolling();
});

function handlePlanRoute() {
  if (store.waypoints.length < 2) return;
  const first = store.waypoints[0];
  const last = store.waypoints[store.waypoints.length - 1];
  store.planRoute([first.lat, first.lng], [last.lat, last.lng]);
  setTimeout(() => store.assessCurrentRisk(), 100);
}

async function triggerTestNotification(scenario: typeof testScenarios[0]) {
  await store.addTemporaryNoFlyZone({
    name: scenario.name,
    lat: scenario.lat,
    lng: scenario.lng,
    radius: scenario.radius,
    type: scenario.type,
  });
}

function handleManualRiskAssess() {
  store.assessCurrentRisk();
  store.fetchRerouteSuggestions();
}
</script>

<template>
  <div class="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
    <!-- Header -->
    <header class="bg-slate-900 border-b border-slate-800 px-6 py-3 flex items-center justify-between">
      <h1 class="text-lg font-bold text-sky-400">
        🛸 无人机 3D 航线规划与地形避障
      </h1>
      <div class="flex items-center gap-4">
        <div
          v-if="store.riskAssessment && store.riskAssessment.overallRisk !== 'safe'"
          :class="[
            'px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1',
            store.riskAssessment.overallRisk === 'critical' ? 'bg-red-600 animate-pulse' :
            store.riskAssessment.overallRisk === 'warning' ? 'bg-orange-500' : 'bg-yellow-500'
          ]"
        >
          <span>{{ store.riskAssessment.overallRisk === 'critical' ? '🚨' : store.riskAssessment.overallRisk === 'warning' ? '⚠️' : '🔶' }}</span>
          <span>风险: {{ store.riskAssessment.overallRisk === 'critical' ? '危险' : store.riskAssessment.overallRisk === 'warning' ? '警告' : '注意' }}</span>
          <span class="opacity-80">{{ store.riskAssessment.riskScore.toFixed(0) }}</span>
        </div>
        <div class="text-xs text-slate-500">
          航点: {{ store.waypoints.length }} |
          禁区: {{ store.noFlyZones.length }} |
          通知: {{ store.unacknowledgedNotifications.length }}
        </div>
      </div>
    </header>

    <!-- Main content -->
    <div class="flex flex-1 overflow-hidden">
      <!-- Map area -->
      <div class="flex-1 flex flex-col" style="width: 70%">
        <div class="flex-1 relative">
          <MapView />
        </div>

        <!-- Bottom terrain profile -->
        <div class="p-2 bg-slate-900 border-t border-slate-800">
          <TerrainProfile />
        </div>
      </div>

      <!-- Right sidebar -->
      <div class="w-[30%] min-w-[280px] bg-slate-900 border-l border-slate-800 p-3 flex flex-col gap-3 overflow-y-auto">
        <!-- Algorithm selector -->
        <div class="bg-slate-800 rounded-lg p-3">
          <h3 class="text-xs font-semibold text-slate-300 mb-2">规划算法</h3>
          <div class="flex gap-2">
            <label class="flex-1 cursor-pointer">
              <input
                type="radio"
                :value="'astar'"
                v-model="store.selectedAlgorithm"
                class="hidden peer"
              />
              <div class="text-center py-1.5 rounded text-xs font-medium peer-checked:bg-sky-700 peer-checked:text-white bg-slate-700 text-slate-400 transition">
                A* 搜索
              </div>
            </label>
            <label class="flex-1 cursor-pointer">
              <input
                type="radio"
                :value="'rrt'"
                v-model="store.selectedAlgorithm"
                class="hidden peer"
              />
              <div class="text-center py-1.5 rounded text-xs font-medium peer-checked:bg-sky-700 peer-checked:text-white bg-slate-700 text-slate-400 transition">
                RRT 随机树
              </div>
            </label>
          </div>
        </div>

        <!-- Actions -->
        <div class="bg-slate-800 rounded-lg p-3 space-y-2">
          <h3 class="text-xs font-semibold text-slate-300 mb-2">操作</h3>
          <button
            @click="handlePlanRoute"
            :disabled="store.waypoints.length < 2"
            class="w-full py-2 rounded text-xs font-medium bg-green-700 text-white hover:bg-green-600 disabled:opacity-40 disabled:cursor-not-allowed transition"
          >
            🧭 规划航线
          </button>
          <button
            @click="store.simulateFlight()"
            :disabled="store.isSimulating || store.waypoints.length < 2"
            class="w-full py-2 rounded text-xs font-medium bg-amber-700 text-white hover:bg-amber-600 disabled:opacity-40 disabled:cursor-not-allowed transition"
          >
            {{ store.isSimulating ? '飞行中...' : '▶ 模拟飞行' }}
          </button>

          <!-- Progress bar -->
          <div v-if="store.isSimulating || store.simProgress > 0" class="space-y-1">
            <div class="flex justify-between text-[10px] text-slate-400">
              <span>模拟进度</span>
              <span>{{ store.simProgress }}%</span>
            </div>
            <div class="w-full bg-slate-700 rounded-full h-2">
              <div
                class="h-2 rounded-full transition-all bg-amber-500"
                :style="{ width: store.simProgress + '%' }"
              />
            </div>
          </div>

          <button
            @click="store.clearRoute()"
            class="w-full py-2 rounded text-xs font-medium bg-red-800 text-white hover:bg-red-700 transition"
          >
            🗑 清除航线
          </button>
        </div>

        <!-- Emergency Test Section -->
        <div class="bg-slate-800 rounded-lg p-3 space-y-2">
          <h3 class="text-xs font-semibold text-slate-300 mb-2">🚨 突发禁飞测试</h3>
          <p class="text-[10px] text-slate-400 mb-2">点击下方按钮模拟收到突发禁飞通知</p>
          <div class="space-y-1.5">
            <button
              v-for="scenario in testScenarios"
              :key="scenario.name"
              @click="triggerTestNotification(scenario)"
              class="w-full py-1.5 px-2 rounded text-[10px] font-medium bg-red-900/60 hover:bg-red-800 text-white text-left transition flex items-center justify-between"
            >
              <span>{{ scenario.name }}</span>
              <span class="text-red-300">R{{ scenario.radius }}m</span>
            </button>
          </div>
          <button
            @click="handleManualRiskAssess"
            :disabled="store.waypoints.length < 2"
            class="w-full py-1.5 rounded text-[10px] font-medium bg-slate-700 hover:bg-slate-600 text-slate-300 transition disabled:opacity-40"
          >
            🔍 手动评估风险
          </button>
        </div>

        <!-- Flight stats -->
        <FlightStats />
      </div>
    </div>

    <!-- Notification Toast -->
    <NotificationToast />

    <!-- Reroute Suggestion Panel -->
    <RerouteSuggestion />
  </div>
</template>
