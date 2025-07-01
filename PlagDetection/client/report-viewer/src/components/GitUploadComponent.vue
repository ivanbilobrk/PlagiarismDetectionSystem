<template>
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-8 rounded shadow-lg w-1/2">
      <h2 class="text-2xl mb-4">Prijenos GitHub/GitLab Repozitorija</h2>
      <form @submit.prevent="handleSubmit">
        <div class="mb-4">
          <label for="resourceName" class="block text-sm font-medium text-gray-700">Ime Resursa</label>
          <input type="text" v-model="resourceName" id="resourceName" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm" />
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
          Molimo unesite ime resursa i URL repozitorija.
        </div>
        <div class="flex justify-end">
          <button type="button" @click="closeModal" class="mr-2 bg-gray-500 text-white py-1 px-3 rounded hover:bg-gray-600">
            Zatvori
          </button>
          <button type="submit" :disabled="!canSubmit" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600 disabled:opacity-50">
            Prenesi
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { defineEmits } from 'vue';
import { loadFileStructure, uploadGitRepo } from '@/api/apicalls.ts'
import { store } from '@/stores/store';

const emit = defineEmits(['close', 'update-nodes']);

const branchName = ref('');
const resourceURL = ref('');
const resourceName = ref('');
const filters = ref([]);
const tagsInput = ref('');
const errorMessage = ref('');

watch(tagsInput, (newValue) => {
  filters.value = newValue.split(',').map(tag => tag.trim()).filter(tag => tag !== '');
});

const canSubmit = computed(() => {
  return resourceName.value.trim() !== '' && resourceURL.value.trim() !== '' && branchName.value.trim() !== '';
});

function closeModal() {
  emit('close');
}

async function handleSubmit() {
  try {
    await uploadGitRepo(store().state.userName, resourceName.value, branchName.value, resourceURL.value, filters.value);
    errorMessage.value = '';
    let updatedFileStructure = await loadFileStructure(store().state.userName);
    emit('update-nodes', updatedFileStructure);
    closeModal();
    resourceName.value = '';
    filters.value = [];
    tagsInput.value = '';
    errorMessage.value = '';
  } catch (error) {
    console.error(error);
    errorMessage.value = `Došlo je do greške prilikom prijenosa datoteke. Molimo pokušajte ponovo. ${error}`;
  }
}

function removeTag(index) {
  filters.value.splice(index, 1);
  tagsInput.value = filters.value.join(', ');
}
</script>
