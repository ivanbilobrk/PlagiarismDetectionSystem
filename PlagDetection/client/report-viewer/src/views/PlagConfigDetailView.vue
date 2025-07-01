<template>
  <div class="container mx-auto p-4">
    <PlagConfigDetailComponent v-if="config" :config="config" @refresh-config="reloadConfig"/>
    <p v-else>Učitavanje...</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { getPlagConfigByUsernameAndName } from '@/api/apicalls.ts'
import PlagConfigDetailComponent from '@/components/PlagConfigDetailComponent.vue';
import { store } from '@/stores/store.ts'

const route = useRoute();
const config = ref<PlagConfig | null>(null);

async function reloadConfig() {
  const plagConfigName = route.params.plagConfigName as string;
  const userName = store().state.userName;
  try {
    config.value = await getPlagConfigByUsernameAndName(userName, plagConfigName);
  } catch (error) {
    console.error('Error fetching plag config:', error);
  }
}

onMounted(async () => {
  const plagConfigName = route.params.plagConfigName as string;
  const userName = store().state.userName;
  try {
    config.value = await getPlagConfigByUsernameAndName(userName, plagConfigName);
  } catch (error) {
    console.error('Error fetching plag config:', error);
  }
});
</script>

<style scoped>
.container {
  max-width: 800px;
}
</style>
