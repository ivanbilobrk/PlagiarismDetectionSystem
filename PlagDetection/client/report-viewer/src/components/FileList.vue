<template>
  <div class="card relative">
    <div class="absolute top-2 right-2 flex space-x-2">
      <button :disabled="!hasSelection" @click="handleDeleteButtonClicked" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600 disabled:opacity-50">
        Obriši označeno
      </button>
      <button @click="handleUploadButtonClicked" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600">
        Prenesi nove resurse za usporedbu
      </button>
      <button :disabled="!hasSelection" @click="handleStartComparissonButtonClicked" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600 disabled:opacity-50">
        Pokreni usporedbu s označenim resursima
      </button>
    </div>
    <div class="pt-24">
      <Tree v-model:selectionKeys="selectedKeys" selectionMode="multiple" :value="nodes" :filter="true" filterMode="lenient" class="w-full"></Tree>
    </div>
    <div v-if="errorMessage" class="text-red-600 mt-2 text-center">
      {{ errorMessage }}
    </div>
    <LoadResourceComponent :isOpen="isResourceModalOpen" @close="closeLoadResourcesModal" @update-nodes="updateNodes" />
    <StartComparisonComponent :isOpen="isComparisonModalOpen" v-model:selectedKeys="selectedKeys" @close="closeComparisonModal" />
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import Tree from 'primevue/tree';
import { store } from '@/stores/store';
import { extractDataStrings, removeNodesByKey } from '@/utils/FileTraversal.ts';
import LoadResourceComponent from '@/components/LoadResourceComponent.vue';
import StartComparisonComponent from '@/components/StartComparisonComponent.vue';

const props = defineProps({
  loadFileStructure: Function,
  deleteFile: Function,
});

const nodes = ref(null);
const selectedKeys = ref(null);
const isResourceModalOpen = ref(false);
const isComparisonModalOpen = ref(false);
const errorMessage = ref("")

const hasSelection = computed(() => {
  return selectedKeys.value !== null && Object.keys(selectedKeys.value).length > 0;
});

onMounted(async () => {
  let userName = store().state.userName;
  let fileStructure = await props.loadFileStructure(userName);
  nodes.value = fileStructure;
});

async function handleDeleteButtonClicked() {
  let selectedFiles = selectedKeys._rawValue;
  let fileData = extractDataStrings(nodes.value, selectedFiles);
  let filePathsToDelete = fileData.map(fileData => fileData.data);
  let fileKeys = fileData.map(fileData => fileData.key);
  nodes.value = removeNodesByKey(nodes.value, fileKeys);
  selectedKeys.value = null;
  try {
    await props.deleteFile(filePathsToDelete);
  } catch (error) {
    errorMessage.value = `Greška prilikom brisanja resursa! ${error}`;
  }
}

function handleStartComparissonButtonClicked() {
  const fileKeysObj = selectedKeys.value || {}
  const fileKeys = Object.keys(fileKeysObj)

  let triggerError = false
  fileKeys.forEach(key => {
    if (key.length === 1) {
      triggerError = true
    }
  })
  if (triggerError) {
    errorMessage.value = "Označili ste vrhovni direktorij git ili local, molimo označite pojedinačne prenesene resurse!"
  } else {
    errorMessage.value = ""
    isComparisonModalOpen.value = true
  }
}

function handleUploadButtonClicked() {
  isResourceModalOpen.value = true;
}

function closeLoadResourcesModal() {
  isResourceModalOpen.value = false;
}

function closeComparisonModal() {
  selectedKeys.value = null;
  isComparisonModalOpen.value = false;
}

function updateNodes(newNodes) {
  nodes.value = newNodes;
}
</script>
