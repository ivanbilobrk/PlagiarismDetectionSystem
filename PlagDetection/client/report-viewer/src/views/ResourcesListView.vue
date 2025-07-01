<template>
  <div class="flex flex-col items-center gap-4">
    <Dropdown title="Resursi">
      <div class="tree-container">
        <FileList :loadFileStructure="loadFileStructure" :deleteFile="deleteFile" />
      </div>
    </Dropdown>
    <Dropdown title="Prošli rezultati" @toggle="handleDropdownToggle(2, $event)">
      <div class="tree-container">
        <RunListComponent
          v-if="runs.length > 0"
          ref="resultList"
          :runs="runs"
          :plag-run-states="plagRunStates"
          :currently-processing-submissions="currentlyProcessingSubmissions"
          @result-deleted="handleResultDeleted"
        />
        <p v-else>Nema dostupnih rezultata.</p>
      </div>
    </Dropdown>
    <button @click="handleCreateConfigButtonClicked" class="bg-blue-500 text-white py-1 px-3 mb-4 rounded hover:bg-blue-600 disabled:opacity-50">
      Stvori Konfiguraciju Automatske Detekcije Plagijata
    </button>
    <button @click="handleShowConfigsButtonClicked" class="bg-blue-500 text-white py-1 px-3 mb-4 rounded hover:bg-blue-600 disabled:opacity-50">
      Prethodne Konfiguracije Detekcije Plagijata
    </button>
    <CreatePlagConfigComponent
      :isOpen="isPlagConfigModalOpen"
      @close="closePlagConfigModal"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Dropdown from '@/components/DropDownComponent.vue';
import FileList from '@/components/FileList.vue';
import {
  loadFileStructure,
  deleteFile,
  getResults,
  getPlagRunStatuses,
  getCurrentlyProcessingSubmissionsForRun,
  checkIfBasicJplagIsRunning, checkRunSuspended
} from '@/api/apicalls.ts'
import RunListComponent from '@/components/RunListComponent.vue'
import CreatePlagConfigComponent from '@/components/CreatePlagConfigComponent.vue'
import { useRouter } from 'vue-router'
import { store } from '@/stores/store.ts'
import type { PlagRunState } from '@/model/PlagRunState.ts'

const resultList = ref(null);
const isPlagConfigModalOpen = ref(false);
const router = useRouter();
const runs = ref([]);
const plagRunStates = ref<Map<string, PlagRunState>>(new Map());
const currentlyProcessingSubmissions = ref<Map<string, string[]>>(new Map());
const jPlagRunStatus = ref<Record<string, boolean>>({});
const currentlySuspendedRuns = ref<Map<string, boolean[]>>(new Map());

async function updatePlagRunStatuses(plagRunIds: string[]) {
  for (const plagRunId of plagRunIds) {
    const plagRunState = await getPlagRunStatuses(plagRunId)
    const currentlyProcessingSubmissionsForPlagRun = await getCurrentlyProcessingSubmissionsForRun(plagRunId)
    const response = await checkIfBasicJplagIsRunning(plagRunId)
    jPlagRunStatus.value[plagRunId] = response.running
    currentlyProcessingSubmissions.value.set(plagRunId, currentlyProcessingSubmissionsForPlagRun)
    plagRunStates.value.set(plagRunId, plagRunState)
  }
}

async function refreshJplagRuns() {
  runs.value = await getResults(store().state.userName);
  const plagRunIds = runs.value.map(run => run.id);
  await updatePlagRunStatuses(plagRunIds);
  await updateCurrenltySuspendedRuns(plagRunIds)
}

async function updateCurrenltySuspendedRuns(plagRunIds: string[]) {
  for (const plagRunId of plagRunIds) {
    const currentlySuspended = await checkRunSuspended(plagRunId)
    currentlySuspendedRuns.value.set(plagRunId, currentlySuspended.running)
  }
}

onMounted(async () => {
   await refreshJplagRuns()
  setInterval(() => {
    refreshJplagRuns();
  }, 30000);
});

async function handleDropdownToggle(index, isOpen) {
  if (index === 2 && isOpen) {
    await refreshJplagRuns();
  }
}

function handleResultDeleted(resultName) {
  runs.value = runs.value.filter(result => result.resultName !== resultName);
}

function closePlagConfigModal() {
  isPlagConfigModalOpen.value = false;
}

function handleCreateConfigButtonClicked() {
  isPlagConfigModalOpen.value = true;
}

function handleShowConfigsButtonClicked() {
  router.push('/plagconfigs');
}

</script>

<style scoped>
.tree-container {
  max-height: 600px;
  overflow-y: auto;
}
</style>
