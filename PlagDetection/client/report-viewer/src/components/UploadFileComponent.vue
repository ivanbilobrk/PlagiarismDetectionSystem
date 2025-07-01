<template>
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-8 rounded shadow-lg w-1/2">
      <h2 class="text-2xl mb-4">Prijenos Datoteka</h2>
      <form @submit.prevent="handleSubmit">
        <div class="mb-4">
          <label for="resourceName" class="block text-sm font-medium text-gray-700">Ime Resursa</label>
          <input type="text" v-model="resourceName" id="resourceName" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm" />
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
        <div class="mb-4">
          <div v-if="droppedFile === null">
            <label class="block text-sm font-medium text-gray-700">Prenesi ZIP Datoteku</label>
            <div class="flex h-40 items-center justify-center border-2 border-dashed border-gray-300 rounded-md"
                 @dragover.prevent
                 @drop.prevent="handleDrop">
              <p>Ispusti ZIP ovdje</p>
            </div>
          </div>
          <div v-if="droppedFile" class="mt-4 flex items-center">
            <svg class="w-6 h-6 text-gray-500 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v4a1 1 0 001 1h3m10-5h3a1 1 0 011 1v4a1 1 0 01-1 1h-3m-4 0H8m0 0V5a1 1 0 011-1h4a1 1 0 011 1v11a1 1 0 01-1 1H9a1 1 0 01-1-1v-5z"></path>
            </svg>
            <span class="text-gray-700">{{ droppedFile.name }}</span>
          </div>
        </div>
        <div v-if="errorMessage" class="mb-4 text-red-500">
          {{ errorMessage }}
        </div>
        <div v-if="!canSubmit" class="mb-4 text-red-500">
          Molimo unesite ime resursa i prenesite ZIP datoteku prije nego što nastavite.
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
import { uploadFile, loadFileStructure } from '@/api/apicalls.ts';
import { store } from '@/stores/store';

const emit = defineEmits(['close', 'update-nodes']);

const resourceName = ref('');
const filters = ref([]);
const tagsInput = ref('');
const droppedFile = ref(null);
const errorMessage = ref('');

watch(tagsInput, (newValue) => {
  filters.value = newValue.split(',').map(tag => tag.trim()).filter(tag => tag !== '');
});

const canSubmit = computed(() => {
  return resourceName.value.trim() !== '' && droppedFile.value !== null;
});

function closeModal() {
  emit('close');
}

async function handleSubmit() {
  try {
    await uploadFile(droppedFile.value, filters.value, store().state.userName, resourceName.value);
    errorMessage.value = '';
    let updatedFileStructure = await loadFileStructure(store().state.userName);
    emit('update-nodes', updatedFileStructure);
    closeModal();
    resourceName.value = '';
    filters.value = [];
    tagsInput.value = '';
    droppedFile.value = null;
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

function handleDrop(event) {
  const file = event.dataTransfer.files[0];
  if (file) {
    if (file.name.endsWith('.zip')) {
      droppedFile.value = file;
      errorMessage.value = '';
    } else {
      errorMessage.value = 'Molimo prenesite samo ZIP datoteke.';
      droppedFile.value = null;
    }
  }
}
</script>
