<template>
  <div class="container mx-auto p-4">
    <h1 class="text-3xl font-bold mb-6 flex items-center">
      <i class="fas fa-cogs mr-2"></i> Konfiguracije
    </h1>
    <div class="config-list overflow-y-auto" style="max-height: 600px;">
      <div
        v-for="config in plagConfigs"
        :key="config.name"
        class="mb-6 border p-4 rounded-lg shadow-lg bg-white cursor-pointer"
        @click="navigateToConfig(config.name)"
      >
        <h2 class="text-2xl font-semibold mb-2 flex items-center">
          <i class="fas fa-file-alt mr-2"></i> {{ config.name }}
        </h2>
        <p><strong>Ime predmeta:</strong> {{ config.subjectName }}</p>
        <p><strong>Resursi se uzimaju unazad:</strong> {{ config.resourcesTTL }} godina</p>
        <p><strong>Backend URL za povlačenje resursa:</strong> {{ config.clientBackendURL }}</p>
        <p><strong>Vrsta osvježavanja resursa:</strong> {{ translateScheduleType(config.scheduleType) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" >
import { ref, onMounted } from 'vue';
import { store } from '@/stores/store.ts'
import { getPlagConfigsForUser } from '@/api/apicalls.ts'
import { useRouter } from 'vue-router'
const plagConfigs = ref<PlagConfig[]>([])
const router = useRouter();

function navigateToConfig(configId: string) {
  router.push(`/plagconfig/${configId}`);
}

function translateScheduleType(scheduleType: string) {
  switch (scheduleType) {
    case 'DAILY':
      return 'Dnevno';
    case 'WEEKLY':
      return 'Tjedno';
    case 'EVERY_TWO_WEEKS':
      return 'Svaka dva tjedna';
    case 'MONTHLY':
      return 'Mjesečno';
    default:
      return scheduleType;
  }
}

onMounted(async() => {
  let userName = store().state.userName;
  plagConfigs.value = await getPlagConfigsForUser(userName);
});
</script>

<style scoped>
.container {
  max-width: 800px;
}

.cursor-pointer {
  cursor: pointer;
}

.config-list {
  max-height: 600px;
  overflow-y: auto;
}
</style>

