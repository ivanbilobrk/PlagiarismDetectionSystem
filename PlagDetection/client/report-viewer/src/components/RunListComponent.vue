<template>
  <div class="card relative">
    <div class="overflow-y-auto" style="max-height: 400px;">
      <ul class="w-full">
        <li
          v-for="run in runs"
          :key="run.id"
          class="group cursor-pointer hover:bg-gray-100 p-2 relative"
          :class="{ 'cursor-not-allowed': run.isJplagRunning || run.error }"
          @click="!run.error && props.runDetails.get(run.resultHash)?.finished && navigateToItem(run)"
          @mouseenter="showErrorTooltip($event, run.error)"
          @mouseleave="hideErrorTooltip"
        >
          <div class="flex items-center justify-between">
            <div>
              {{ run.resultName }}
              <div
                v-if="!props.runDetails.get(run.resultHash)?.finished && props.runDetails.get(run.resultHash)"
                class="status-counters text-sm mt-1"
              >
                Parsirano: {{ props.runDetails.get(run.resultHash)?.processedParsings }} /
                {{ props.runDetails.get(run.resultHash)?.submissionTotal }}<br />
                Normalizirano: {{ props.runDetails.get(run.resultHash)?.processedNormalizations }} /
                {{ props.runDetails.get(run.resultHash)?.submissionTotal }}<br />
                Uspoređeno: {{ props.runDetails.get(run.resultHash)?.processedComparisons }} /
                {{ props.runDetails.get(run.resultHash)?.comparisonTotal }}
              </div>
              <span
                v-if="props.runDetails.get(run.resultHash)?.failedToParseSubmissions"
                class="badge badge-warning cursor-pointer ml-2"
                @click.stop="showFailedModal(run, 'parse')"
              >
                Neuspješno parsirane predaje: {{ props.runDetails.get(run.resultHash)?.failedToParseSubmissions?.length || 0 }}
              </span>
              <span
                v-if="props.runDetails.get(run.resultHash)?.failedToCompareSubmissions"
                class="badge badge-warning cursor-pointer ml-2"
                @click.stop="showFailedModal(run, 'compare')"
              >
                Neuspješne usporedbe: {{ props.runDetails.get(run.resultHash)?.failedToCompareSubmissions?.length || 0 }}
              </span>
              <button
                v-if="!props.runDetails.get(run.resultHash)?.finished"
                class="badge badge-info cursor-pointer ml-2"
                @click.stop="showProcessingModal(run)"
              >
                Trenutno se obrađuje ({{ props.runDetails.get(run.resultHash)?.currentlyProcessingSubmissions?.length || 0 }})
              </button>
            </div>
            <div>
              <font-awesome-icon
                v-if="currentlySuspendedRuns && currentlySuspendedRuns.some(plagRun => plagRun.plagRunHash == run.resultHash && plagRun.suspended)"
                v-tooltip="'Ova obrada je trenutno pauzirana te će se nastaviti tijekom noći.'"
                :icon="['fas', 'exclamation-triangle']"
                class="text-warning ml-2"
              />
              <font-awesome-icon
                v-if="!props.runDetails.get(run.resultHash)?.finished && !currentlySuspendedRuns.some(plagRun => plagRun.plagRunHash === run.resultHash && plagRun.suspended)"
                :icon="['fas', 'spinner']"
                class="fa-spin ml-2"
              />
              <font-awesome-icon
                :icon="['fas', 'trash']"
                class="delete-icon absolute right-10 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 text-red-500 hover:text-red-700"
                @click.stop="deleteResult(run.resultHash, run.resultName)"
              />
              <font-awesome-icon
                v-if="run.error"
                :icon="['fas', 'exclamation-triangle']"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-red-500"
              />
            </div>
          </div>
        </li>
      </ul>
    </div>
    <div v-if="errorMessage" class="text-red-600 mt-2 text-center">
      {{ errorMessage }}
    </div>
    <div
      v-if="tooltipVisible"
      :style="tooltipStyle"
      class="error-tooltip fixed p-2 bg-red-100 text-red-700 border border-red-400 rounded shadow-lg z-50"
    >
      {{ tooltipContent }}
    </div>

    <!-- FAILED SUBMISSIONS MODAL -->
    <div
      v-if="failedModalVisible"
      class="fixed inset-0 flex items-center justify-center z-50 bg-black bg-opacity-40"
      @click.self="failedModalVisible = false"
    >
      <div class="bg-white rounded-lg p-6 max-w-3xl w-full max-h-[90vh] shadow-lg relative overflow-hidden">
        <h2 class="text-lg font-semibold mb-2">
          <span v-if="failedModalType === 'parse'">Neuspješno parsirana rješenja</span>
          <span v-else>Cannot Compare Submissions</span>
          <span class="float-right cursor-pointer text-xl leading-none" @click="failedModalVisible = false">&times;</span>
        </h2>
        <div class="max-h-[60vh] overflow-y-auto border-t border-b py-2 mt-2 mb-2">
          <ul>
            <li
              v-for="sub in failedModalType === 'parse'
                ? props.runDetails.get(failedModalRun?.resultHash!!)?.failedToParseSubmissions
                : props.runDetails.get(failedModalRun?.resultHash!!)?.failedToCompareSubmissions"
              :key="failedModalType === 'parse' ? sub.submissionName : sub.firstSubmissionName + '-' + sub.secondSubmissionName"
              class="break-all text-sm py-1 border-b last:border-transparent"
            >
              <div v-if="failedModalType === 'parse'">
                <div class="font-semibold">{{ sub.submissionName }}</div>
                <div class="text-xs text-gray-600 whitespace-pre-wrap">
                  {{ sub.reason }}
                </div>
              </div>
              <div v-else>
                <div class="font-semibold">
                  {{ sub.firstSubmissionName }} ↔ {{ sub.secondSubmissionName }}
                </div>
                <div class="text-xs text-gray-600 whitespace-pre-wrap">
                  {{ sub.reason }}
                </div>
              </div>
            </li>

          </ul>
        </div>
        <button class="mt-2 bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded" @click="failedModalVisible = false">
          Close
        </button>
      </div>
    </div>

    <!-- CURRENTLY PROCESSING MODAL -->
    <div
      v-if="processingModalVisible"
      class="fixed inset-0 flex items-center justify-center z-50 bg-black bg-opacity-40"
      @click.self="processingModalVisible = false"
    >
      <div class="bg-white rounded-lg p-6 max-w-lg w-full shadow-lg relative">
        <h2 class="text-lg font-semibold mb-2">
          Trenutno se obrađuju prijave
          <span class="float-right cursor-pointer text-xl leading-none" @click="processingModalVisible = false">&times;</span>
        </h2>
        <div class="max-h-80 overflow-y-auto border-t border-b py-2 mt-2 mb-2">
          <ul>
            <li
              v-for="item in processingModalItems"
              :key="item"
              class="break-all text-sm py-1 border-b last:border-transparent"
            >
              {{ item }}
            </li>
          </ul>
        </div>
        <button class="mt-2 bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded" @click="processingModalVisible = false">
          Close
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Run } from '@/model/Run.ts'
import { defineProps, ref } from 'vue'
import { store } from '@/stores/store.ts'
import { deleteResultByUserNameAndResultName } from '@/api/apicalls.ts'
import { router } from '@/router'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import type { PlagSuspendedStatus } from '@/model/PlagSuspendedStatus.ts'
import type { PlagRunDetails } from '@/model/PlagRunDetails.ts'

const errorMessage = ref('')
const props = defineProps<{
  runs: Run[];
  runDetails: Map<string, PlagRunDetails>;
  currentlySuspendedRuns: PlagSuspendedStatus[];
}>();
const emit = defineEmits(['result-deleted'])

const tooltipVisible = ref(false)
const tooltipContent = ref('')
const tooltipStyle = ref({})
function showErrorTooltip(event: MouseEvent, error: string | null) {
  if (error) {
    tooltipContent.value = error
    tooltipStyle.value = {
      top: `${event.clientY + 10}px`,
      left: `${event.clientX + 10}px`,
    }
    tooltipVisible.value = true
  }
}
function hideErrorTooltip() {
  tooltipVisible.value = false
}

async function deleteResult(resultHash: string, runName: string) {
  try {
    await deleteResultByUserNameAndResultName(resultHash)
    emit('result-deleted', runName);
  } catch (error) {
    errorMessage.value = `Greška prilikom brisanja rezultata: ${error}`
  }
}
function navigateToItem(run: Run) {
  store().state.resultHash = run.resultHash
  router.push({ name: 'OverviewView', params: { resultHash: run.resultHash } });
}

const failedModalVisible = ref(false)
const failedModalRun = ref<Run | null>(null)
const failedModalType = ref<'parse' | 'compare'>('parse')
function showFailedModal(run: Run, type: 'parse' | 'compare') {
  failedModalRun.value = run
  failedModalType.value = type
  failedModalVisible.value = true
}

// MODAL state & methods (processing)
const processingModalVisible = ref(false)
const processingModalItems = ref<string[]>([])
function showProcessingModal(run: Run) {
  processingModalItems.value = props.runDetails.get(run.resultHash)?.currentlyProcessingSubmissions || []
  processingModalVisible.value = true
}
</script>

<style scoped>
.card {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.delete-icon {
  transition: opacity 0.2s ease;
  cursor: pointer;
  padding: 2px 5px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 4px;
}
.delete-icon:hover {
  background: rgba(245, 245, 245, 0.9);
}
.error-tooltip {
  transition: opacity 0.2s ease;
  z-index: 50;
  width: max-content;
  max-width: 300px;
}
/* BADGE */
.badge {
  display: inline-block;
  padding: 0.15em 0.5em;
  border-radius: 6px;
  font-size: 0.85em;
  color: #fff;
  background: #f59e42;
  cursor: pointer;
  margin-left: 4px;
}
.badge-warning {
  background: #f59e42;
}
.badge-info {
  background: #3490dc;
}
</style>
