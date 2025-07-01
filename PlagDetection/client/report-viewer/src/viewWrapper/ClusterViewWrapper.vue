<template>
  <div>
    <ClusterView v-if="cluster" :cluster="cluster" :top-comparisons="topComparisons" />
    <div
      v-else
      class="absolute bottom-0 left-0 right-0 top-0 flex flex-col items-center justify-center"
    >
      <LoadingCircle class="mx-auto" />
    </div>

    <VersionRepositoryReference />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ClusterView from '@/views/ClusterView.vue'
import LoadingCircle from '@/components/LoadingCircle.vue'
import VersionRepositoryReference from '@/components/VersionRepositoryReference.vue'
import { store } from '@/stores/store.ts';

const props = defineProps({
  clusterIndex: {
    type: String,
    required: true
  }
})

const clusterIndex = computed(() => parseInt(props.clusterIndex))

const cluster = store().state.clusters[clusterIndex.value]
const topComparisons = store().state.topComparisons

</script>
