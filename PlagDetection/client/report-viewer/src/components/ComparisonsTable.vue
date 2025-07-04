<template>
  <div class="flex flex-col">
    <ComparisonTableFilter
      v-model:search-string="combinedSearchString"
      :enable-cluster-sorting="clusters != undefined"
      :header="header"
    />
    <div class="flex justify-left gap-x-2 p-1 bg-gray-100 dark:bg-gray-800 mt-9">
      <div class="w-1/3">
        <label>Avg ≥ {{ avgThreshold }}%</label>
        <input
          v-model.number="avgThreshold"
          type="range"
          min="0"
          max="100"
          class="w-3/4 h-6"
          @input="updateSearchString"
        />
      </div>
      <div class="w-1/3">
        <label>Max ≥ {{ maxThreshold }}%</label>
        <input
          v-model.number="maxThreshold"
          type="range"
          min="0"
          max="100"
          class="w-3/4 h-6"
          @input="updateSearchString"
        />
      </div>
    </div>
    <div class="flex flex-col overflow-hidden">
      <div class="font-bold">
        <!-- Header -->
        <div class="tableRow">
          <div class="tableCellNumber"></div>
          <div class="tableCellName items-center">Submissions in Comparison</div>
          <div class="tableCellSimilarity !flex-col">
            <div>Similarity</div>
            <div class="flex w-full flex-row">
              <ToolTipComponent class="flex-1" :direction="displayClusters ? 'top' : 'left'">
                <template #default>
                  <p class="w-full text-center">
                    {{ metricToolTips[MetricType.AVERAGE].shortName }}
                  </p>
                </template>
                <template #tooltip>
                  <p class="whitespace-pre text-sm">
                    {{ metricToolTips[MetricType.AVERAGE].tooltip }}
                  </p>
                </template>
              </ToolTipComponent>
              <ToolTipComponent class="flex-1" :direction="displayClusters ? 'top' : 'left'">
                <template #default>
                  <p class="w-full text-center">
                    {{ metricToolTips[MetricType.MAXIMUM].shortName }}
                  </p>
                </template>
                <template #tooltip>
                  <p class="whitespace-pre text-sm">
                    {{ metricToolTips[MetricType.MAXIMUM].tooltip }}
                  </p>
                </template>
              </ToolTipComponent>
            </div>
          </div>
          <div v-if="displayClusters" class="tableCellCluster items-center">Cluster</div>
        </div>
      </div>
      <!-- Body -->
      <div class="flex flex-grow flex-col overflow-hidden">
        <DynamicScroller
          v-if="topComparisons.length > 0"
          ref="dynamicScroller"
          :items="displayedComparisons"
          :min-item-size="48"
        >
          <template #default="{ item, index, active }">
            <DynamicScrollerItem
              :item="item"
              :active="active"
              :size-dependencies="[
                item.firstSubmissionId,
                item.secondSubmissionId,
                store().isAnonymous(item.firstSubmissionId),
                store().isAnonymous(item.secondSubmissionId)
              ]"
              :data-index="index"
            >
              <!-- Row -->
              <div
                class="tableRow"
                :class="{
                  'bg-container-secondary-light dark:bg-container-secondary-dark': item.id % 2 == 1,
                  'bg-blue-500 !bg-opacity-30': isHighlightedRow(item)
                }"
              >
                <RouterLink
                  :to="{
                      name: 'ComparisonView',
                      params: { searchKey: `${item.firstSubmissionId}-${item.secondSubmissionId}` }
                    }"
                  class="flex flex-grow cursor-pointer flex-row">
                  <!-- Index in sorted list -->
                  <div class="tableCellNumber">
                    <div class="w-full text-center">{{ item.sortingPlace + 1 }}</div>
                  </div>
                  <!-- Names -->
                  <div class="tableCellName">
                    <NameElement :id="item.firstSubmissionId" :name="item.firstSubmissionId" class="h-full w-1/2 px-2" />
                    <NameElement :id="item.secondSubmissionId" :name="item.secondSubmissionId" class="h-full w-1/2 px-2" />
                  </div>
                  <!-- Similarities -->
                  <div class="tableCellSimilarity">
                    <div class="w-1/2">
                      {{ (item.similarities[MetricType.AVERAGE] * 100).toFixed(2) }}%
                    </div>
                    <div class="w-1/2">
                      {{ (item.similarities[MetricType.MAXIMUM] * 100).toFixed(2) }}%
                    </div>
                  </div>
                </RouterLink>
                <!-- Clusters -->
                <div v-if="displayClusters" class="tableCellCluster flex !flex-col items-center">
                  <RouterLink
                    v-if="item.clusterIndex >= 0"
                    :to="{
                      name: 'ClusterView',
                      params: { clusterIndex: item.clusterIndex }
                    }"
                    class="flex w-full justify-center text-center"
                  >
                    <ToolTipComponent
                      class="w-fit"
                      direction="left"
                      :tool-tip-container-will-be-centered="true"
                    >
                      <template #default>
                        {{ clusters?.[item.clusterIndex].members?.length }}
                        <FontAwesomeIcon
                          :icon="['fas', 'user-group']"
                          :style="{ color: clusterIconColors[item.clusterIndex] }"
                        />
                        {{
                          (
                            (clusters?.[item.clusterIndex].averageSimilarity as number) * 100
                          ).toFixed(2)
                        }}%
                      </template>
                      <template #tooltip>
                        <p class="whitespace-nowrap text-sm">
                          {{ clusters?.[item.clusterIndex].members?.length }} submissions in cluster
                          with average similarity of
                          {{
                            (
                              (clusters?.[item.clusterIndex].averageSimilarity as number) * 100
                            ).toFixed(2)
                          }}%
                        </p>
                      </template>
                    </ToolTipComponent>
                  </RouterLink>
                </div>
              </div>
            </DynamicScrollerItem>
          </template>
          <template #after>
            <slot name="footer"></slot>
          </template>
        </DynamicScroller>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Cluster } from '@/model/Cluster'
import type { ComparisonListElement } from '@/model/ComparisonListElement'
import { type PropType, watch, computed, ref, type Ref, onMounted } from 'vue'
import { store } from '@/stores/store'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { library } from '@fortawesome/fontawesome-svg-core'
import { faUserGroup } from '@fortawesome/free-solid-svg-icons'
import { generateHues } from '@/utils/ColorUtils'
import ToolTipComponent from './ToolTipComponent.vue'
import { MetricType, metricToolTips } from '@/model/MetricType'
import NameElement from './NameElement.vue'
import ComparisonTableFilter from './ComparisonTableFilter.vue'
library.add(faUserGroup)

const props = defineProps({
  topComparisons: {
    type: Array<ComparisonListElement>,
    required: true
  },
  clusters: {
    type: Array<Cluster>,
    required: false,
    default: undefined
  },
  header: {
    type: String,
    default: 'Top Comparisons:'
  },
  highlightedRowIds: {
    type: Object as PropType<{ firstId: string; secondId: string }>,
    required: false,
    default: undefined
  }
})

const avgThreshold = ref(0)
const maxThreshold = ref(0)
const combinedSearchString = ref('')

function updateSearchString() {
  combinedSearchString.value = `avg>=${avgThreshold.value} and max>=${maxThreshold.value}`
}

const displayedComparisons = computed(() => {
  const comparisons = getFilteredComparisons(getSortedComparisons(Array.from(props.topComparisons)))
  let index = 1
  comparisons.forEach((c) => {
    c.id = index++
  })
  return comparisons
})

function getFilteredComparisons(comparisons: ComparisonListElement[]) {
  const searches = combinedSearchString.value
    .trim()
    .toLowerCase()
    .split(' and ')
    .map(s => s.trim())

  if (searches.length === 0) {
    return comparisons
  }

  const searchesWithoutMetric = searches.filter(s => !s.includes('>=') && !s.includes('>') && !s.includes('<') && !s.includes('<='))

  const metricConditions: Record<MetricType, string[]> = {
    [MetricType.AVERAGE]: [],
    [MetricType.MAXIMUM]: []
  }

  searches.forEach(s => {
    const [metric, condition] = s.split('>=')
    if (metric && condition) {
      if (metric.includes('avg')) {
        metricConditions[MetricType.AVERAGE].push(`>=${condition}`)
      } else if (metric.includes('max')) {
        metricConditions[MetricType.MAXIMUM].push(`>=${condition}`)
      }
    }
  })


  return comparisons.filter(c => {
    const metricsMet = Object.entries(metricConditions)
      .every(([metric, conditions]) => {
        if (conditions.length === 0) return true
        const metricType = metric as MetricType
        return conditions.some(condition =>
          evaluateMetricCondition(c.similarities[metricType], condition)
        )
      })
    if (searchesWithoutMetric.length === 0) return metricsMet
    const comparisonContainsSearchPart = searchesWithoutMetric.some(s =>
      c.firstSubmissionId.toLowerCase().includes(s.toLowerCase()) ||
      c.secondSubmissionId.toLowerCase().includes(s.toLowerCase())
    );
    return metricsMet && comparisonContainsSearchPart
  })
}

function evaluateMetricCondition(value: number, condition: string) {
  const match = condition.match(/([<>]=?|)(\d+)/)
  if (!match) return false
  const operator = match[1]
  const threshold = Number(match[2])
  const comparisonValue = value * 100
  switch (operator) {
    case '>': return comparisonValue > threshold
    case '<': return comparisonValue < threshold
    case '>=': return comparisonValue >= threshold
    case '<=': return comparisonValue <= threshold
    default: return comparisonValue >= threshold
  }
}

function getSortedComparisons(comparisons: ComparisonListElement[]) {
  comparisons.sort(
    (a, b) =>
      b.similarities[store().uiState.comparisonTableSortingMetric] -
      a.similarities[store().uiState.comparisonTableSortingMetric]
  )

  if (store().uiState.comparisonTableClusterSorting) {
    comparisons.sort((a, b) => b.clusterIndex - a.clusterIndex)

    comparisons.sort(
      (a, b) =>
        getClusterFor(b.clusterIndex).averageSimilarity -
        getClusterFor(a.clusterIndex).averageSimilarity
    )
  }

  let index = 0
  comparisons.forEach((c) => {
    c.sortingPlace = index++
  })
  return comparisons
}

function getClusterFor(clusterIndex: number) {
  if (clusterIndex < 0 || !props.clusters) {
    return { averageSimilarity: 0 }
  }
  return props.clusters[clusterIndex]
}

const displayClusters = props.clusters != undefined
let clusterIconHues = [] as Array<number>
const lightmodeSaturation = 80
const lightmodeLightness = 50
const lightmodeAlpha = 0.3
const darkmodeSaturation = 90
const darkmodeLightness = 65
const darkmodeAlpha = 0.6

if (props.clusters != undefined) {
  clusterIconHues = generateHues(props.clusters.length)
}

const clusterIconColors = computed(() =>
  clusterIconHues.map((h) => {
    return `hsla(${h}, ${
      store().uiState.useDarkMode ? darkmodeSaturation : lightmodeSaturation
    }%, ${
      store().uiState.useDarkMode ? darkmodeLightness : lightmodeLightness
    }%, ${store().uiState.useDarkMode ? darkmodeAlpha : lightmodeAlpha})`
  })
)

function isHighlightedRow(item: ComparisonListElement) {
  return (
    props.highlightedRowIds != undefined &&
    ((item.firstSubmissionId == props.highlightedRowIds.firstId &&
        item.secondSubmissionId == props.highlightedRowIds.secondId) ||
      (item.firstSubmissionId == props.highlightedRowIds.secondId &&
        item.secondSubmissionId == props.highlightedRowIds.firstId))
  )
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const dynamicScroller: Ref<any | null> = ref(null)

watch(
  computed(() => props.highlightedRowIds),
  (newValue, oldValue) => {
    if (
      newValue != undefined &&
      (newValue?.firstId != oldValue?.firstId || newValue?.secondId != oldValue?.secondId)
    ) {
      dynamicScroller.value?.scrollToItem(props.topComparisons.findIndex(isHighlightedRow))
    }
  }
)
</script>

<style scoped lang="postcss">
input[type="range"] {
  @apply bg-gray-200 rounded-lg appearance-none cursor-pointer dark:bg-gray-700;
}

input[type="range"]::-webkit-slider-thumb {
  @apply w-6 h-6 bg-blue-500 rounded-full appearance-none cursor-pointer; /* Increased size */
}

input[type="range"]::-moz-range-thumb {
  @apply w-6 h-6 bg-blue-500 rounded-full appearance-none cursor-pointer; /* Increased size */
}

input[type="range"]::-ms-thumb {
  @apply w-6 h-6 bg-blue-500 rounded-full appearance-none cursor-pointer; /* Increased size */
}
input[type="range"] {
  @apply h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer dark:bg-gray-700;
}
input[type="range"]::-webkit-slider-thumb {
  @apply w-4 h-4 bg-blue-500 rounded-full appearance-none cursor-pointer;
}
.tableRow {
  @apply flex flex-row text-center;
}
.tableCellNumber {
  @apply tableCell w-12 flex-shrink-0;
}
.tableCellSimilarity {
  @apply tableCell w-40 flex-shrink-0;
}
.tableCellCluster {
  @apply tableCell w-32 flex-shrink-0;
}
.tableCellName {
  @apply tableCell flex-grow;
}
.tableCell {
  @apply mx-3 flex flex-row items-center justify-center text-center;
}
</style>
