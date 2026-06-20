<script setup lang="ts">
import { computed } from 'vue';
import { useDroneStore } from '../store/drone';
import type { RiskAssessment } from '../types';

const store = useDroneStore();

const riskColors: Record<RiskAssessment['overallRisk'], string> = {
  safe: 'bg-green-500',
  caution: 'bg-yellow-500',
  warning: 'bg-orange-500',
  critical: 'bg-red-600',
};

const riskLabels: Record<RiskAssessment['overallRisk'], string> = {
  safe: '安全',
  caution: '注意',
  warning: '警告',
  critical: '危险',
};

const currentRisk = computed(() => store.riskAssessment);
const suggestions = computed(() => store.rerouteSuggestions);
const hasSuggestions = computed(() => suggestions.value.length > 0);

function getConflictSeverityColor(severity: string) {
  switch (severity) {
    case 'critical': return 'text-red-400';
    case 'high': return 'text-orange-400';
    case 'medium': return 'text-yellow-400';
    default: return 'text-gray-400';
  }
}

function handlePreview(suggestion: typeof suggestions.value[0]) {
  store.previewReroute(suggestion);
}

function handleApply(suggestion: typeof suggestions.value[0]) {
  store.applyReroute(suggestion);
}

function handleCancelPreview() {
  store.cancelReroutePreview();
}

function formatDistance(meters: number) {
  if (meters >= 1000) {
    return (meters / 1000).toFixed(2) + ' km';
  }
  return meters.toFixed(0) + ' m';
}

function formatTime(seconds: number) {
  if (seconds >= 60) {
    return (seconds / 60).toFixed(1) + ' min';
  }
  return seconds.toFixed(0) + ' s';
}
</script>

<template>
  <div class="fixed bottom-4 left-4 z-40 w-96 max-w-[calc(100vw-2rem)]">
    <!-- Risk Assessment Panel -->
    <div
      v-if="currentRisk && currentRisk.overallRisk !== 'safe'"
      :class="[
        'p-4 rounded-lg shadow-xl mb-3 text-white',
        riskColors[currentRisk.overallRisk]
      ]"
    >
      <div class="flex items-center justify-between mb-2">
        <div class="flex items-center gap-2">
          <span class="text-2xl">
            {{ currentRisk.overallRisk === 'critical' ? '🚨' : currentRisk.overallRisk === 'warning' ? '⚠️' : '🔶' }}
          </span>
          <div>
            <h3 class="font-bold text-sm">当前风险等级</h3>
            <div class="text-2xl font-bold">{{ riskLabels[currentRisk.overallRisk] }}</div>
          </div>
        </div>
        <div class="text-right">
          <div class="text-xs opacity-80">风险分值</div>
          <div class="text-3xl font-bold">{{ currentRisk.riskScore.toFixed(0) }}</div>
        </div>
      </div>

      <!-- Risk Meter -->
      <div class="bg-black/30 rounded-full h-2 mb-3 overflow-hidden">
        <div
          class="h-full bg-white transition-all duration-500"
          :style="{ width: `${Math.min(100, currentRisk.riskScore)}%` }"
        />
      </div>

      <!-- Warnings -->
      <div v-if="currentRisk.warnings.length > 0" class="mb-3">
        <h4 class="text-xs font-semibold mb-1 opacity-90">风险警告</h4>
        <ul class="text-xs space-y-1">
          <li v-for="(warning, idx) in currentRisk.warnings" :key="idx" class="flex items-start gap-1">
            <span>•</span>
            <span>{{ warning }}</span>
          </li>
        </ul>
      </div>

      <!-- Conflict Zones -->
      <div v-if="currentRisk.conflictZones.length > 0">
        <h4 class="text-xs font-semibold mb-2 opacity-90">冲突区域</h4>
        <div class="space-y-2">
          <div
            v-for="zone in currentRisk.conflictZones"
            :key="zone.zoneId"
            class="bg-black/20 rounded p-2 text-xs"
          >
            <div class="flex justify-between items-center mb-1">
              <span class="font-medium">{{ zone.zoneName }}</span>
              <span :class="getConflictSeverityColor(zone.severity)" class="font-bold">
                {{ zone.severity === 'critical' ? '严重' : zone.severity === 'high' ? '高' : '中' }}
              </span>
            </div>
            <div class="flex justify-between opacity-80">
              <span>距离: {{ formatDistance(zone.distance) }}</span>
              <span>影响航点: {{ zone.affectedWaypointIndices.length }}个</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Reroute Suggestions Panel -->
    <div
      v-if="hasSuggestions"
      class="bg-slate-800 rounded-lg shadow-xl overflow-hidden"
    >
      <div class="bg-green-600 px-4 py-2 flex items-center gap-2">
        <span class="text-xl">🛤️</span>
        <div class="flex-1">
          <h3 class="font-bold text-white text-sm">改线建议</h3>
          <p class="text-xs text-green-100">检测到 {{ suggestions.length }} 条可行的替代航线</p>
        </div>
        <span
          v-if="store.selectedSuggestion"
          class="text-xs bg-white/20 px-2 py-1 rounded text-white"
        >
          预览中
        </span>
      </div>

      <div class="p-3 space-y-3 max-h-80 overflow-y-auto">
        <div
          v-for="suggestion in suggestions"
          :key="suggestion.id"
          :class="[
            'p-3 rounded-lg border-2 transition-all cursor-pointer',
            store.selectedSuggestion?.id === suggestion.id
              ? 'border-green-500 bg-green-900/30'
              : 'border-slate-600 bg-slate-700/50 hover:border-slate-500'
          ]"
          @click="handlePreview(suggestion)"
        >
          <div class="flex items-start justify-between mb-2">
            <div>
              <h4 class="font-bold text-white text-sm">{{ suggestion.description }}</h4>
              <p class="text-xs text-slate-400 mt-0.5">{{ suggestion.reason }}</p>
            </div>
            <div
              v-if="suggestion.riskReduction > 50"
              class="bg-green-600 text-white text-xs font-bold px-2 py-0.5 rounded"
            >
              ✅ 推荐
            </div>
          </div>

          <!-- Stats Grid -->
          <div class="grid grid-cols-3 gap-2 mb-3">
            <div class="bg-slate-800/50 rounded p-2 text-center">
              <div class="text-[10px] text-slate-400">风险降低</div>
              <div class="text-green-400 font-bold text-sm">-{{ suggestion.riskReduction.toFixed(0) }}%</div>
            </div>
            <div class="bg-slate-800/50 rounded p-2 text-center">
              <div class="text-[10px] text-slate-400">距离变化</div>
              <div :class="suggestion.savedDistance >= 0 ? 'text-green-400' : 'text-red-400'" class="font-bold text-sm">
                {{ suggestion.savedDistance >= 0 ? '-' : '+' }}{{ formatDistance(Math.abs(suggestion.savedDistance)) }}
              </div>
            </div>
            <div class="bg-slate-800/50 rounded p-2 text-center">
              <div class="text-[10px] text-slate-400">时间变化</div>
              <div :class="suggestion.savedTime >= 0 ? 'text-green-400' : 'text-red-400'" class="font-bold text-sm">
                {{ suggestion.savedTime >= 0 ? '-' : '+' }}{{ formatTime(Math.abs(suggestion.savedTime)) }}
              </div>
            </div>
          </div>

          <!-- Waypoint info -->
          <div class="flex items-center justify-between text-xs text-slate-400 mb-3">
            <span>航点数: {{ suggestion.suggestedWaypoints.length }}</span>
            <span>生成时间: {{ new Date(suggestion.createdAt).toLocaleTimeString('zh-CN') }}</span>
          </div>

          <!-- Action Buttons -->
          <div class="flex gap-2">
            <button
              v-if="store.selectedSuggestion?.id !== suggestion.id"
              @click.stop="handlePreview(suggestion)"
              class="flex-1 px-3 py-1.5 bg-slate-600 hover:bg-slate-500 text-white text-xs font-medium rounded transition"
            >
              👁️ 预览航线
            </button>
            <button
              v-else
              @click.stop="handleCancelPreview"
              class="flex-1 px-3 py-1.5 bg-slate-600 hover:bg-slate-500 text-white text-xs font-medium rounded transition"
            >
              ✕ 取消预览
            </button>
            <button
              @click.stop="handleApply(suggestion)"
              class="flex-1 px-3 py-1.5 bg-green-600 hover:bg-green-500 text-white text-xs font-medium rounded transition"
            >
              ✅ 应用改线
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.max-h-80 {
  max-height: 20rem;
}

.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: #1e293b;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background: #475569;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}
</style>
