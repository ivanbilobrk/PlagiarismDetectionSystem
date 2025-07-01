<template>
  <div v-if="topComparisons" class="absolute bottom-0 left-0 right-0 top-0 flex flex-col">
    <div v-if="errorMessage" class="text-red-600 mt-2 text-center">
      {{ errorMessage }}
    </div>
    <div
      class="relative bottom-0 left-0 right-0 flex flex-grow space-x-5 px-5 pb-7 pt-5 print:flex-col print:space-x-0 print:space-y-5"
    >
      <Container class="flex max-h-0 min-h-full flex-1 flex-col print:hidden">
        <ComparisonsTable
          :top-comparisons="topComparisons"
          :clusters="clusters"
          class="min-h-0 flex-1 print:min-h-full print:flex-grow"
        >
          <template v-if="topComparisons.length < 1" #footer>
            <p class="w-full pt-1 text-center font-bold">
              Not all comparisons are shown. To see more, re-run JPlag with a higher maximum number
              argument.
            </p>
          </template>
        </ComparisonsTable>
      </Container>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, defineProps } from 'vue'
import ComparisonsTable from '@/components/ComparisonsTable.vue'
import { store } from '@/stores/store'
import Container from '@/components/ContainerComponent.vue'
import { OverviewFactory } from '@/model/factories/OverviewFactory.ts'
import type { ComparisonListElement } from '@/model/ComparisonListElement.ts'
import { getResultForResultHash } from '@/api/apicalls.ts'
import type { Cluster } from '@/model/Cluster'

const props = defineProps<{ resultHash: string }>()
const topComparisons = ref<ComparisonListElement[]>([]);
const clusters = ref<Cluster[]>([]);
const errorMessage = ref('')

document.title = `${store().state.uploadedFileName} - JPlag Report Viewer`

onMounted(async () => {
  try {
    const result = await getResultForResultHash(props.resultHash);
    clusters.value = OverviewFactory.extractClusters(result.clusters);
    store().state.clusters = clusters.value;
    topComparisons.value = OverviewFactory.extractTopComparisons(
      result.topComparisons,
      clusters.value
    ) as ComparisonListElement[];
    store().state.topComparisons = topComparisons.value;
    store().state.submissionFileIndex = result.submissionFileIndex.submission_file_indexes;
  } catch (error) {
    console.error(error);
    errorMessage.value = `Greška prilikom dohvaćanja rezultata! ${error}`;
  }
});
</script>
