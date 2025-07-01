<template>
  <button @click="handleCreateConfigButtonClicked" class="bg-blue-500 text-white py-1 px-3 mb-4 rounded hover:bg-blue-600 disabled:opacity-50">
    Stvori Novu Konfiguraciju
  </button>
  <div class="card relative">
    <div class="overflow-y-auto" style="max-height: 400px;">
      <ul class="w-full">
        <li
          v-for="config in configs"
          :key="config.id"
          class="group cursor-pointer hover:bg-gray-100 p-2 relative"
          @click="navigateToItem(config)"
        >
          {{ config.name }}
        </li>
      </ul>
    </div>
    <div v-if="errorMessage" class="text-red-600 mt-2 text-center">
      {{ errorMessage }}
    </div>
    <CreatePlagConfigComponent
      :isOpen="isPlagConfigModalOpen"
      @close="closePlagConfigModal"
    />
  </div>
</template>

<script setup lang="ts">
import { Run } from '@/model/Run.ts'
import { onMounted, ref } from 'vue'
import { store } from '@/stores/store.ts'
import { getPlagConfigsForUser } from '@/api/apicalls.ts'
import { router } from '@/router'
import CreatePlagConfigComponent from '@/components/CreatePlagConfigComponent.vue'

const configs = ref<PlagConfig[]>([])
const errorMessage = ref('')
const isPlagConfigModalOpen = ref(false);

function navigateToItem(result: Run) {
  router.push({ name: 'OverviewView', params: { resultHash: result.resultHash } });
}

function handleCreateConfigButtonClicked() {
  isPlagConfigModalOpen.value = true;
}

function closePlagConfigModal() {
  isPlagConfigModalOpen.value = false;
}

onMounted(async () => {
  let userName = store().state.userName;
  try {
    configs.value = await getPlagConfigsForUser(userName);
  } catch (error) {
    errorMessage.value = 'Error loading results: ' + error.message;
  }
});
</script>

<style scoped>
.card {
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
</style>
