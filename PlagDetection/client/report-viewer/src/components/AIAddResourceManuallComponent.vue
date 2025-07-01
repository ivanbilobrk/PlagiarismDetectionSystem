<template>
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50" @click.self="closeModal">
    <div class="bg-white p-8 rounded shadow-lg w-1/2">
      <h2 class="text-2xl mb-4">Prijenos GitHub/GitLab Repozitorija</h2>
      <form @submit.prevent="handleSubmit">
        <div class="mb-4">
          <label for="taskDescription" class="block text-sm font-medium text-gray-700">Tekst Zadatka</label>
          <textarea v-model="taskDescription" id="taskDescription" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm resize-y" rows="4"></textarea>
        </div>
        <div class="mb-4">
          <label for="programmingLanguage" class="block text-sm font-medium text-gray-700">Programski Jezik</label>
          <select v-model="programmingLanguage" id="programmingLanguage" class="mt-1 block w-full border border-gray-300 rounded-md shadow-sm">
            <option value="" disabled>Odaberite jezik</option>
            <option value="javascript">JavaScript</option>
            <option value="python">Python</option>
            <option value="java">Java</option>
            <option value="csharp">C#</option>
            <!-- Dodajte druge jezike prema potrebi -->
          </select>
        </div>
        <div v-if="errorMessage" class="mb-4 text-red-500">
          {{ errorMessage }}
        </div>
        <div v-if="!canSubmit" class="mb-4 text-red-500">
          Molimo unesite URL repozitorija, akademsku godinu, naziv grane, tekst zadatka i odaberite programski jezik.
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
import { ref, computed, defineProps } from 'vue'
import { defineEmits } from 'vue';
import { addAIResourceToTheProject } from '@/api/apicalls.ts'
import { store } from '@/stores/store';

const emit = defineEmits(['close', 'update-nodes']);
const errorMessage = ref('');
const taskDescription = ref('');
const programmingLanguage = ref('');
const isLoading = ref(false);

const props = defineProps({
  configName: String,
  projectName: String,
  subjectName: String
});

const canSubmit = computed(() => {
  return taskDescription.value.trim() !== '' &&
    programmingLanguage.value.trim() !== '';
});

function closeModal() {
  emit('close');
}

async function handleSubmit() {
  isLoading.value = true;
  try {
    await addAIResourceToTheProject(
      store().state.userName,
      props.configName,
      props.projectName,
      props.subjectName,
      programmingLanguage.value,
      taskDescription.value,
    );
    errorMessage.value = '';
    emit('update-nodes');
    closeModal();
    taskDescription.value = '';
    programmingLanguage.value = '';
    errorMessage.value = '';
    closeModal();
  } catch (error) {
    console.error(error);
    errorMessage.value = `Došlo je do greške prilikom prijenosa datoteke. Molimo pokušajte ponovo. ${error}`;
  } finally {
    isLoading.value = false;
  }
}
</script>
