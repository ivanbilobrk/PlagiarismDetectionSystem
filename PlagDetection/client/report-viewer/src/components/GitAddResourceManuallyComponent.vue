<template>
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50" @click.self="closeModal">
    <div class="bg-white p-8 rounded shadow-lg w-1/2">
      <h2 class="text-2xl mb-4">Prijenos GitHub/GitLab Repozitorija</h2>
      <form @submit.prevent="handleSubmit">
        <div class="mb-4">
          <label for="resourceName" class="block text-sm font-medium text-gray-700">Akademska godina</label>
          <input type="text" v-model="academicYear" id="resourceName" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm" />
        </div>
        <div class="mb-4">
          <label for="resourceName" class="block text-sm font-medium text-gray-700">Github/GitLab URL</label>
          <input type="text" v-model="resourceURL" id="resourceURL" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm" />
        </div>
        <div class="mb-4">
          <label for="resourceName" class="block text-sm font-medium text-gray-700">Ime Grane</label>
          <input type="text" v-model="branchName" id="branchName" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm" />
        </div>
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700">Filter - nazivi datoteka koje želite izostaviti iz usporedbe</label>
          <input type="text" v-model="tagsInput" placeholder="Unesite nazive odvojene zarezom" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm" />
          <div class="flex flex-wrap mt-2">
            <span v-for="(tag, index) in filters" :key="index" class="bg-blue-100 text-blue-800 text-sm font-medium mr-2 mb-2 px-2.5 py-0.5 rounded">
              {{ tag }}
              <button type="button" @click="removeTag(index)" class="ml-1 text-red-500 hover:text-red-700">x</button>
            </span>
          </div>
        </div>
        <div v-if="errorMessage" class="mb-4 text-red-500">
          {{ errorMessage }}
        </div>
        <div v-if="!canSubmit" class="mb-4 text-red-500">
          Molimo unesite URL repozitorija, akademsku godinu i naziv grane.
        </div>
        <div class="flex justify-end">
          <button type="button" @click="closeModal" class="mr-2 bg-gray-500 text-white py-1 px-3 rounded hover:bg-gray-600" :disabled="isLoading">
            Zatvori
          </button>
          <button type="submit" :disabled="!canSubmit || isLoading" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600 disabled:opacity-50">
            Prenesi
          </button>
        </div>
      </form>
      <div v-if="isLoading" class="absolute inset-0 bg-white bg-opacity-75 flex items-center justify-center">
        <svg class="animate-spin h-8 w-8 text-blue-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
        </svg>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, defineProps } from 'vue'
import { defineEmits } from 'vue';
import { addGitResourceToProject } from '@/api/apicalls.ts'
import { store } from '@/stores/store';

const emit = defineEmits(['close', 'update-nodes']);

const branchName = ref('');
const resourceURL = ref('');
const filters = ref([]);
const tagsInput = ref('');
const errorMessage = ref('');
const academicYear = ref('');
const isLoading = ref(false);

const props = defineProps({
  configName: String,
  projectName: String,
  subjectName: String
});

watch(tagsInput, (newValue) => {
  filters.value = newValue.split(',').map(tag => tag.trim()).filter(tag => tag !== '');
});

const canSubmit = computed(() => {
  return academicYear.value.trim() !== '' && resourceURL.value.trim() !== '' && branchName.value.trim() !== '';
});

function closeModal() {
  emit('close');
}

function isValidAcademicYear(year: string): boolean {
  const regex = /^\d{4}-\d{4}$/;
  if (!regex.test(year)) return false;
  const [startYear, endYear] = year.split('-').map(Number);
  return endYear === startYear + 1;
}

async function handleSubmit() {
  if (!isValidAcademicYear(academicYear.value)) {
    errorMessage.value = 'Molimo unesite akademsku godinu u formatu YYYY-YYYY, gde je druga godina za jednu veća od prve.';
    return;
  }

  isLoading.value = true;
  try {
    await addGitResourceToProject(resourceURL.value, branchName.value, store().state.userName, filters.value, props.configName || '', props.projectName || '', academicYear.value, props.subjectName || '');
    errorMessage.value = '';
    emit('update-nodes');
    closeModal();
    academicYear.value = '';
    filters.value = [];
    tagsInput.value = '';
    errorMessage.value = '';
    closeModal()
  } catch (error) {
    console.error(error);
    errorMessage.value = `Došlo je do greške prilikom prijenosa datoteke. Molimo pokušajte ponovo. ${error}`;
  } finally {
    isLoading.value = false;
  }
}

function removeTag(index) {
  filters.value.splice(index, 1);
  tagsInput.value = filters.value.join(', ');
}
</script>
