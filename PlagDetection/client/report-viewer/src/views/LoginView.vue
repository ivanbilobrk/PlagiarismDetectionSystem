<template>
  <div class="login-container p-4">
    <h1 class="text-2xl font-bold mb-4">Login</h1>
    <input
      v-model="inputUsername"
      type="text"
      placeholder="Enter your username"
      class="border rounded p-2 w-full mb-4"
    />
    <button class="bg-blue-500 text-white px-4 py-2 rounded" @click="login">
      Login
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { store } from '@/stores/store';
import { useRouter, useRoute } from 'vue-router';

const inputUsername = ref('');
const router = useRouter();
const route = useRoute();

function login() {
  if (inputUsername.value.trim()) {
    store().state.userName = inputUsername.value.trim();
    const redirectPath = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    router.push(redirectPath);
  }
}
</script>

<style scoped>
.login-container {
  max-width: 400px;
  margin: 50px auto;
  text-align: center;
}
</style>
