<template>
  <div v-if="isOpen" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-8 rounded shadow-lg w-1/2">
      <h2 class="text-2xl mb-4">Odaberite opciju prijenosa</h2>
      <div class="flex justify-center gap-4 mb-4">
        <button @click="selectOption('local')" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600 flex items-center">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v4a1 1 0 001 1h3m10-5h3a1 1 0 011 1v4a1 1 0 01-1 1h-3m-4 0H8m0 0V5a1 1 0 011-1h4a1 1 0 011 1v11a1 1 0 01-1 1H9a1 1 0 01-1-1v-5z"></path>
          </svg>
          Prijenos ZIP datoteke
        </button>
        <button @click="selectOption('git')" class="bg-blue-500 text-white py-1 px-3 rounded hover:bg-blue-600 flex items-center">
          <svg class="w-5 h-5 mr-1" fill="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 0C5.37 0 0 5.37 0 12c0 5.3 3.438 9.8 8.205 11.385.6.11.82-.26.82-.577v-2.17c-3.338.726-4.042-1.61-4.042-1.61-.546-1.387-1.333-1.756-1.333-1.756-1.09-.744.083-.729.083-.729 1.205.084 1.84 1.237 1.84 1.237 1.07 1.835 2.807 1.305 3.492.998.108-.775.42-1.305.762-1.605-2.665-.3-5.467-1.332-5.467-5.93 0-1.31.467-2.38 1.235-3.22-.123-.303-.535-1.523.117-3.176 0 0 1.008-.322 3.3 1.23.957-.266 1.983-.398 3.003-.403 1.02.005 2.046.137 3.003.403 2.29-1.552 3.297-1.23 3.297-1.23.653 1.653.24 2.873.118 3.176.77.84 1.233 1.91 1.233 3.22 0 4.61-2.807 5.625-5.48 5.92.43.37.823 1.102.823 2.222v3.293c0 .32.218.694.825.576C20.565 21.798 24 17.298 24 12c0-6.63-5.37-12-12-12z"/>
          </svg>
          <svg class="w-5 h-5 ml-1 mr-2" fill="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M22.5 9.75l-2.25-7.5c-.15-.45-.6-.75-1.05-.75s-.9.3-1.05.75L16.5 7.5h-9L5.85 2.25c-.15-.45-.6-.75-1.05-.75s-.9.3-1.05.75L1.5 9.75c-.15.45 0 .9.3 1.2l10.2 10.2c.3.3.75.3 1.05 0l10.2-10.2c.3-.3.45-.75.3-1.2z"/>
          </svg>
          Git
        </button>
      </div>
      <div v-if="selectedOption === 'local'">
        <UploadFileComponent @close="closeModal" @update-nodes="updateNodes" />
      </div>
      <div v-if="selectedOption === 'git'">
        <GitUploadComponent @close="closeModal" @update-nodes="updateNodes" />
      </div>
      <button @click="closeModal" class="mt-4 bg-gray-500 text-white py-1 px-3 rounded hover:bg-gray-600">
        Zatvori
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { FileNode } from '@/model/FileNode.ts';
import { defineProps, defineEmits } from 'vue';
import UploadFileComponent from '@/components/UploadFileComponent.vue';
import GitUploadComponent from '@/components/GitUploadComponent.vue';

const props = defineProps({
  isOpen: Boolean,
});

const emit = defineEmits(['close', 'update-nodes']);
const selectedOption = ref<string | null>(null);

function selectOption(option: string) {
  selectedOption.value = option;
}

function closeModal() {
  selectedOption.value = null;
  emit('close');
}

function updateNodes(newNodes: FileNode[]) {
  emit('update-nodes', newNodes);
}
</script>
