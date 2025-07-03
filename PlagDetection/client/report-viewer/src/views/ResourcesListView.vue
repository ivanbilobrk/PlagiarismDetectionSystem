<template>
  <div class="flex flex-col items-center gap-4">
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
import { ref } from 'vue'
import CreatePlagConfigComponent from '@/components/CreatePlagConfigComponent.vue'
import { useRouter } from 'vue-router'

const isPlagConfigModalOpen = ref(false);
const router = useRouter();

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
