<template>
  <div v-if="isOpen" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50" @click.self="closeModal">
    <div class="bg-white p-8 rounded shadow-lg w-3/4" @click.stop>
      <h2 class="text-2xl mb-4">Odaberite opcije usporedbe</h2>
      <div class="mb-4">
        <label for="resourceName" class="block text-sm font-medium text-gray-700">Ime Usporedbe</label>
        <input type="text" v-model="resultName" id="resultName" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm" />
      </div>
      <div class="mb-4">
        <label class="block text-sm font-medium text-gray-700">Odaberite programski jezik</label>
        <select v-model="selectedLanguage" @change="addLanguage" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm">
          <option value="" disabled selected>Odaberite jezik</option>
          <option v-for="language in languages" :key="language" :value="language">{{ language }}</option>
        </select>
        <div class="flex flex-wrap mt-2">
          <span v-for="(language, index) in selectedLanguages" :key="index" class="bg-blue-100 text-blue-800 text-sm font-medium mr-2 mb-2 px-2.5 py-0.5 rounded">
            {{ language }}
            <button type="button" @click="removeLanguage(index)" class="ml-1 text-red-500 hover:text-red-700">x</button>
          </span>
        </div>

        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700"
          >Sufiksi - Datoteke Koje Želite Uključiti u Usporedbu</label
          >
          <input
            type="text"
            v-model="suffixInput"
            placeholder="Unesite nazive odvojene zarezom"
            class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
          />
          <div class="mt-2 flex flex-wrap">
          <span
            v-for="(suffix, index) in suffixes"
            :key="index"
            class="mb-2 mr-2 rounded bg-blue-100 px-2.5 py-0.5 text-sm font-medium text-blue-800"
          >
            {{ suffix }}
            <button
              type="button"
              @click="removeSuffix(index)"
              class="ml-1 text-red-500 hover:text-red-700"
            >
              x
            </button>
          </span>
          </div>
        </div>

      </div>
      <ul class="list-disc list-inside mb-4">
        <li v-for="(path, index) in filePaths" :key="index" class="flex items-center justify-between text-gray-700 mb-4">
          <span class="flex-1 break-words">{{ path }}</span>
          <div class="flex space-x-2">
            <button @click="removePath(index)" class="text-red-500 hover:text-red-700 active:text-red-800">X</button>
            <button @click="selectOption(index, 'STUDENTS')" :class="{'bg-blue-700 text-white': typeOfResource[index] === 'STUDENTS', 'bg-gray-200 text-gray-700': typeOfResource[index] !== 'STUDENTS'}" class="py-1 px-2 rounded hover:bg-gray-300 active:bg-gray-400">Students</button>
            <button @click="selectOption(index, 'WHITELIST')" :class="{'bg-blue-700 text-white': typeOfResource[index] === 'WHITELIST', 'bg-gray-200 text-gray-700': typeOfResource[index] !== 'WHITELIST'}" class="py-1 px-2 rounded hover:bg-gray-300 active:bg-gray-400">Basecode</button>
            <button @click="selectOption(index, 'OLD_SUBMISSION')" :class="{'bg-blue-700 text-white': typeOfResource[index] === 'OLD_SUBMISSION', 'bg-gray-200 text-gray-700': typeOfResource[index] !== 'OLD_SUBMISSION'}" class="py-1 px-2 rounded hover:bg-gray-300 active:bg-gray-400">Oldsubmission</button>
            <div class="flex items-center justify-center">
              <span>Koristi kao rješenje jednog studenta?</span>
              <input v-model="isSingleSolution[index]" type="checkbox" class="ml-2" />
            </div>
          </div>
        </li>
      </ul>
      <div v-if="errorMessage" class="text-red-600 mt-2 text-center">
        {{ errorMessage }}
      </div>
      <button @click="runComparison" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600 active:bg-blue-700 mt-4">
        Pokreni usporedbu
      </button>
      <button @click="closeModal" class="mt-4 ml-4 bg-gray-500 text-white py-1 px-3 rounded hover:bg-gray-600 active:bg-gray-700">
        Zatvori
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineProps, defineEmits, watch, ref } from 'vue';
import { extractDataStrings } from '@/utils/FileTraversal.ts';
import { loadFileStructure, runJplag } from '@/api/apicalls.ts'
import { store } from '@/stores/store.ts';
import type { JPlagSolutionRun } from '@/model/JPlagSolutionRun.ts';

const props = defineProps<{
  isOpen: boolean;
  selectedKeys: any;
}>();

const suffixes = ref([])
const errorMessage = ref('');
const resultName = ref('');
const emit = defineEmits(['close', 'update-nodes']);
const selectedKeys = ref(null);
const filePaths = ref<string[]>([]);
const typeOfResource = ref<string[]>([]);
const isSingleSolution = ref<boolean[]>([]);
const languages = ref(['Java', 'Python', 'C++', 'JavaScript', 'TypeScript']);
const selectedLanguage = ref('');
const selectedLanguages = ref<string[]>([]);
const suffixInput = ref('')
const languagesMapping = {
  "TypeScript": ".ts, .js",
  "JavaScript": ".js",
  "Python": ".py",
  "Java": ".java",
  "C++": ".cpp",
}

function removeSuffix(index: number) {
  suffixes.value.splice(index, 1)
  suffixInput.value = suffixes.value.join(', ')
}

watch(suffixInput, (newValue) => {
  suffixes.value = newValue
    .split(',')
    .map((suffix) => suffix.trim())
    .filter((suffix) => suffix !== '')
})

watch(() => props.selectedKeys, async (newVal) => {
  if (newVal) {
    selectedKeys.value = newVal;

    try {
      let fileStructure = await loadFileStructure(store().state.userName);
      let fileData = extractDataStrings(fileStructure, selectedKeys.value);
      filePaths.value = fileData.map(fileData => fileData.data);
      typeOfResource.value = filePaths.value.map(() => 'STUDENTS');
      isSingleSolution.value = filePaths.value.map(() => false);
    } catch (error) {
      errorMessage.value = `Greška prilikom dohvaćanja strukture datoteka! ${error}`;
    }
  }
});

function removePath(index: number) {
  filePaths.value.splice(index, 1);
  typeOfResource.value.splice(index, 1);
  isSingleSolution.value.splice(index, 1);
}

function selectOption(index: number, option: string) {
  if (option === 'WHITELIST' && typeOfResource.value.includes('WHITELIST')) {
    alert('Only one element can be selected as basecode.');
    return;
  }
  typeOfResource.value[index] = option;
}

function runComparison() {
  let jPlagSolutionRuns: JPlagSolutionRun[] = typeOfResource.value.map((option, index) => {
    let solutionPath = filePaths.value[index];
    let typeOfResource = option;
    return {
      solutionPath,
      typeOfResource,
      singleSolution: isSingleSolution.value[index],
    } as JPlagSolutionRun;
  });

  try {
    runJplag(store().state.userName, resultName.value, [...selectedLanguages.value, 'text'].map(lang => lang.toLowerCase()), jPlagSolutionRuns, suffixes.value);
    closeModal()
  } catch (e) {
    errorMessage.value = `Greška prilikom pokretanja usporedbe! ${e}`;
  }
}

function closeModal() {
  errorMessage.value = '';
  resultName.value = '';
  filePaths.value = [];
  typeOfResource.value = [];
  isSingleSolution.value = [];
  selectedLanguage.value = '';
  selectedLanguages.value = [];
  suffixes.value = [];
  suffixInput.value = '';
  selectedKeys.value = null;
  emit('close');
}

function addLanguage() {
  if (selectedLanguage.value && !selectedLanguages.value.includes(selectedLanguage.value)) {
    selectedLanguages.value.push(selectedLanguage.value);
    selectedLanguage.value = '';
  }
}

function removeLanguage(index: number) {
  selectedLanguages.value.splice(index, 1);
}
</script>
