<template>
  <Interactable
    class="print-exact mr-2 box-border flex h-6 w-fit items-center justify-center whitespace-nowrap !rounded-2xl px-[12px] text-center hover:!border-[2px] hover:px-[11px] print:border-2"
    :class="{ '!bg-blue-800 !bg-blue-500 !bg-opacity-40': selected }"
    @click="$emit('click')"
  >
    {{ label }}
  </Interactable>
</template>

<script setup lang="ts">
import Interactable from '../InteractableComponent.vue'

defineProps({
  label: {
    type: String,
    required: true
  },
  selected: {
    type: Boolean,
    required: false,
    default: false
  }
})

defineEmits(['click'])
</script>
