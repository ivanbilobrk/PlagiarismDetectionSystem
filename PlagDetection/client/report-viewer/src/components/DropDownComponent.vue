<template>
  <div class="w-full max-w-md">
    <button @click="toggle" class="w-full bg-blue-500 text-white py-2 px-4 rounded">
      {{ title }}
    </button>
    <div v-show="isOpen" class="mt-2 p-4 border rounded shadow">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  title: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['toggle']);

const isOpen = ref(false);

function toggle() {
  isOpen.value = !isOpen.value;
  emit('toggle', isOpen.value);
}
</script>
