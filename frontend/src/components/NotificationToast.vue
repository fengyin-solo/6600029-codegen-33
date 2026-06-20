<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useDroneStore } from '../store/drone';
import type { NoFlyNotification } from '../types';

const store = useDroneStore();

const showAll = ref(false);

const visibleNotifications = computed(() => {
  if (showAll.value) {
    return store.notifications;
  }
  return store.unacknowledgedNotifications;
});

const unacknowledgedCount = computed(() => store.unacknowledgedNotifications.length);

function formatTime(timestamp: number) {
  const date = new Date(timestamp);
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function getSeverityStyle(severity: NoFlyNotification['severity']) {
  switch (severity) {
    case 'critical':
      return 'bg-red-600 border-red-400';
    case 'warning':
      return 'bg-orange-500 border-orange-300';
    default:
      return 'bg-blue-500 border-blue-300';
  }
}

function getSeverityIcon(severity: NoFlyNotification['severity']) {
  switch (severity) {
    case 'critical':
      return '🚨';
    case 'warning':
      return '⚠️';
    default:
      return 'ℹ️';
  }
}

function handleAcknowledge(notificationId: string) {
  store.acknowledgeNotification(notificationId);
}

function focusOnZone(notification: NoFlyNotification) {
  const zone = notification.zone;
  const lat = zone.centerLat ?? zone.center[0];
  const lng = zone.centerLng ?? zone.center[1];
  store.mapCenter = [lat, lng];
}

let pulseInterval: ReturnType<typeof setInterval> | null = null;
const isPulsing = ref(false);

onMounted(() => {
  pulseInterval = setInterval(() => {
    if (unacknowledgedCount.value > 0) {
      isPulsing.value = !isPulsing.value;
    } else {
      isPulsing.value = false;
    }
  }, 500);
});

onUnmounted(() => {
  if (pulseInterval) clearInterval(pulseInterval);
});
</script>

<template>
  <div class="fixed top-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
    <button
      v-if="unacknowledgedCount > 0"
      @click="showAll = !showAll"
      :class="[
        'px-4 py-2 rounded-lg shadow-lg text-white font-medium text-sm transition-all',
        isPulsing ? 'ring-4 ring-red-300 scale-105' : '',
        unacknowledgedCount > 2 ? 'bg-red-600' : 'bg-orange-500'
      ]"
    >
      <span class="mr-2">🔔</span>
      {{ unacknowledgedCount }} 条新通知
      <span class="ml-2">{{ showAll ? '▲' : '▼' }}</span>
    </button>

    <TransitionGroup name="notification-list" tag="div" class="flex flex-col gap-2">
      <div
        v-for="notification in visibleNotifications"
        :key="notification.id"
        :class="[
          'p-4 rounded-lg shadow-xl border-l-4 text-white max-w-sm transition-all duration-300',
          getSeverityStyle(notification.severity),
          notification.acknowledged ? 'opacity-60' : 'animate-pulse'
        ]"
      >
        <div class="flex items-start justify-between mb-2">
          <div class="flex items-center gap-2">
            <span class="text-xl">{{ getSeverityIcon(notification.severity) }}</span>
            <div>
              <h4 class="font-bold text-sm">{{ notification.title }}</h4>
              <p class="text-xs opacity-80">{{ formatTime(notification.timestamp) }}</p>
            </div>
          </div>
          <span
            v-if="notification.acknowledged"
            class="text-xs bg-white/20 px-2 py-0.5 rounded"
          >
            已确认
          </span>
        </div>

        <p class="text-sm mb-3">{{ notification.message }}</p>

        <div class="flex items-center gap-2 text-xs bg-black/20 rounded px-2 py-1 mb-3">
          <span>📍</span>
          <span>{{ notification.zone.name }}</span>
          <span class="opacity-60">
            (半径 {{ notification.zone.radius }}m
          </span>
        </div>

        <div class="flex gap-2">
          <button
            @click="focusOnZone(notification)"
            class="flex-1 px-3 py-1.5 bg-white/20 hover:bg-white/30 rounded text-xs font-medium transition"
          >
            查看位置
          </button>
          <button
            v-if="!notification.acknowledged"
            @click="handleAcknowledge(notification.id)"
            class="flex-1 px-3 py-1.5 bg-white/25 hover:bg-white/40 rounded text-xs font-medium transition"
          >
            确认收到
          </button>
        </div>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.notification-list-enter-active,
.notification-list-leave-active {
  transition: all 0.3s ease;
}

.notification-list-enter-from {
  opacity: 0;
  transform: translateX(100px);
}

.notification-list-leave-to {
  opacity: 0;
  transform: translateX(100px);
}

.notification-list-move {
  transition: transform 0.3s ease;
}

.scale-105 {
  transform: scale(1.05);
}
</style>
