<template>
  <div class="container mx-auto p-4 scrollable-container" >
    <div v-if="errorMessage" class="mb-4 p-4 bg-red-100 text-red-700 border border-red-400 rounded">
      {{ errorMessage }}
    </div>
    <div class="mb-6 border p-4 rounded-lg shadow-lg bg-white">
      <h2 class="text-2xl font-semibold mb-2 flex items-center">
        <i class="fas fa-file-alt mr-2"></i> {{ config.plagConfig.name }}
      </h2>
      <p><strong>Ime predmeta:</strong> {{ config.plagConfig.subjectName }}</p>
      <p><strong>Resursi se uzimaju unazad:</strong> {{ config.plagConfig.resourcesTTL }} godina</p>
      <p><strong>URL za povlačenje resursa:</strong> {{ config.plagConfig.clientBackendURL }}</p>
      <p><strong>Vrsta osvježavanja resursa:</strong> {{ translateScheduleType(config.plagConfig.scheduleType) }}</p>
      <div>
        <h3 class="font-semibold mt-4 flex items-center">
          <i class="fas fa-project-diagram mr-2"></i> Projekti na predmetu
        </h3>
        <ul>
          <li v-for="project in groupedResources" :key="project.name" class="mb-2">
            <details :key="project.name" class="bg-gray-100 p-2 rounded">
              <summary :key="project.name" class="cursor-pointer flex items-center">
                <i class="fas fa-folder-open mr-2"></i> {{ project.name }}
                <div v-if="project.resultHashes.some(hash =>currentlySuspendedRuns.some(run => run.plagRunHash === hash && run.suspended === true))" class="ml-auto">
                  <font-awesome-icon :icon="['fas', 'exclamation-triangle']" class="text-warning ml-2" v-tooltip="'Ova obrada je trenutno pauzirana te će se nastaviti tijekom noći.'" />
                </div>
                <div v-else-if="jPlagRunStatus[project.name]" class="ml-auto">
                  <font-awesome-icon :icon="['fas', 'spinner']" class="fa-spin mr-1" />
                  <span>Trenutno se obavlja provjera plagijata</span>
                </div>
                <div v-else-if="Object.entries(resourceStatuses).some(([key, value]) => value === true && Object.values(project.resources).some(resource => resource.path === key))" class="ml-auto">
                  <font-awesome-icon :icon="['fas', 'spinner']" class="fa-spin mr-1" />
                  <span>Trenutno se obavlja ažuriranje resursa</span>
                </div>
                <button v-else class="ml-auto text-green-500 hover:text-green-700 flex items-center" @click.stop="startProject(project.name)">
                  <font-awesome-icon :icon="['fas', 'play']" class="mr-1" />
                  <span>Pokreni provjeru plagijata</span>
                </button>
              </summary>
              <p><strong>Nastavci datoteka koje se uzimaju u obzir prilikom detekcije plagijata:</strong> {{ Array.from(project.suffixes).join(', ') }}</p>
              <p><strong>Jezici:</strong> {{ Array.from(project.languages).join(', ') }}</p>
              <div>
                <h4 class="font-semibold mt-2 flex items-center">
                  <i class="fas fa-database mr-2"></i> Resursi
                </h4>
                <button class="bg-blue-500 text-white py-1 mb-2 px-3 rounded hover:bg-blue-600" @click="handleUploadButtonClicked">
                  Prenesi nove resurse za usporedbu
                </button>
                <i>
                  Broj Resursa: {{ config.numberOfSubmissions[project.name] }}
                </i>
                <div v-for="(resources, year) in project.groupedResources" :key="year">
                  <h5 class="font-semibold mt-2">{{ year }}</h5>
                  <ul>
                    <li v-for="resource in resources" :key="resource.path" class="mb-1">
                      <details :key="resource.path" class="bg-gray-200 p-2 rounded">
                        <summary :key="resource.path" class="cursor-pointer flex items-center">
                          <i class="fas fa-file-code mr-2"></i> {{ getResourceName(resource, project.name) }}
                          <span class="ml-auto flex items-center">
                            <font-awesome-icon
                              v-if="resource.exceptionMessage"
                              v-tooltip="resource.exceptionMessage"
                              :icon="['fas', 'exclamation-circle']"
                              class="text-yellow-500 mr-2 cursor-help"
                            />
                            <div v-if="project.resultHashes.some(hash =>currentlySuspendedRuns.some(run => run.plagRunHash === hash && run.suspended === true))" class="ml-auto">
                              <font-awesome-icon :icon="['fas', 'exclamation-triangle']" class="text-warning ml-2" v-tooltip="'Ova obrada je trenutno pauzirana te će se nastaviti tijekom noći.'" />
                            </div>
                            <div v-else-if="jPlagRunStatus[project.name]" class="flex items-center">
                              <font-awesome-icon :icon="['fas', 'spinner']" class="fa-spin mr-1" />
                              <span>Trenutno se obavlja provjera plagijata</span>
                            </div>
                            <div v-else-if="resourceStatuses[resource.path]" class="flex items-center">
                              <font-awesome-icon :icon="['fas', 'spinner']" class="fa-spin mr-1" />
                              <span>Ažuriranje...</span>
                            </div>
                            <div v-else-if="!resource.exceptionMessage" class="flex items-center">
                              <font-awesome-icon :icon="['fas', 'check']" class="text-green-500 mr-1" />
                              <span>Resurs ažuriran</span>
                              <font-awesome-icon
                                v-if="resource.resourceType != ResourceType.ZIP"
                                :icon="['fas', 'sync']"
                                class="text-blue-500 ml-2 cursor-pointer"
                                title="Kliknite za ručno ažuriranje resursa"
                                @click="manuallyUpdateResource(resource.path, project.name, config.plagConfig.name)"
                              />
                              <font-awesome-icon
                                :icon="['fas', 'trash']"
                                class="ml-2 cursor-pointer text-gray-500 hover:text-red-500"
                                @click="deleteResource(resource.path, project.name, config.plagConfig.name)"
                              />
                            </div>
                          </span>
                        </summary>
                        <p><strong>Tip Resursa:</strong> {{ resource.resourceType }}</p>
                        <p><strong>Zadnje ažuriranje:</strong> {{ new Date(resource.lastUpdate).toLocaleDateString() }}</p>
                        <p><strong>Akademska godina:</strong> {{ resource.academicYear }}</p>
                        <p v-if="resource.resourceType === 'GIT'">
                          <strong>Repo URL:</strong> <a :href="resource.repoURL" class="text-blue-500 hover:underline">{{ resource.repoURL }}</a><br>
                          <strong>Branch:</strong> {{ resource.branch }}
                        </p>
                      </details>
                    </li>
                  </ul>
                </div>
                <ManuallyAddResourcesComponent :config-name="config.plagConfig.name" :project-name="project.name" :subject-name="config.plagConfig.subjectName" :is-open="isResourceModalOpen" @close="closeLoadResourcesModal" @update-nodes="updateNodes"/>
              </div>
              <div>
                <details class="bg-gray-200 p-2 rounded mt-4" @toggle="handleToggle">
                  <summary class="cursor-pointer flex items-center">
                    <i class="fas fa-list mr-2"></i> Rezultati detekcije plagijata
                  </summary>
                  <RunListComponent :runs="resultsForProjects[project.name]" :run-details="runDetails" :currently-suspended-runs="currentlySuspendedRuns" @result-deleted="handleResultDeleted" />
                </details>
              </div>
            </details>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { defineProps } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { library } from '@fortawesome/fontawesome-svg-core';
import { faPlay, faSpinner, faCheck, faSync, faTrash, faExclamationCircle } from '@fortawesome/free-solid-svg-icons'
import { ResourceType } from '@/model/PlagConfig.ts';
import {
  checkIfResourceUpdating,
  startjPlagRunForConfigAndProjectAndUserName,
  checkIfJplagIsRunning,
  manuallyUpdateResourceApiCall,
  deleteResourceForProject,
  getAllRunsForConfigAndProject,
  checkRunSuspended,
  getPlagRunDetails
} from '@/api/apicalls.ts'
import { store } from '@/stores/store.ts';
import ManuallyAddResourcesComponent from '@/components/ManuallyAddResourcesComponent.vue'
import { computed } from 'vue';
import { Run } from '@/model/Run.ts';
import RunListComponent from '@/components/RunListComponent.vue'
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons/faExclamationTriangle'
import type { PlagSuspendedStatus } from '@/model/PlagSuspendedStatus.ts'
import type { PlagRunDetails } from '@/model/PlagRunDetails.ts'

library.add(faPlay, faSpinner, faCheck, faSync, faTrash, faExclamationTriangle, faExclamationCircle);

const props = defineProps<{
  config: PlagConfig;
}>();
const emit = defineEmits(['refreshConfig']);

const resourceStatuses = ref<Record<string, boolean>>({});
const jPlagRunStatus = ref<Record<string, boolean>>({});
const errorMessage = ref('');
const isResourceModalOpen = ref(false);
const resultsForProjects = ref<Record<string, Run[]>>({});
const currentlySuspendedRuns = ref<PlagSuspendedStatus[]>([]);
const runDetails = ref<Map<string, PlagRunDetails>>(new Map());

const groupedResources = computed(() => {
  return props.config.plagConfig.projects.map(project => {
    const grouped = project.resources.reduce((acc, resource) => {
      if (!acc[resource.academicYear]) {
        acc[resource.academicYear] = [];
      }
      acc[resource.academicYear].push(resource);
      return acc;
    }, {});
    return { ...project, groupedResources: grouped };
  });
});

function handleToggle() {
  props.config.plagConfig.projects.forEach(project => {
    getJplagResultsForProject(project.name);
  });
}

async function updatePlagRunStatuses(plagRunHashes: string[]) {
  for (const plagRunHash of plagRunHashes) {
    const plagRunDetails = await getPlagRunDetails(plagRunHash)
    runDetails.value.set(plagRunHash, plagRunDetails)
  }
}

function handleResultDeleted() {
  props.config.plagConfig.projects.forEach(project => {
    getJplagResultsForProject(project.name);
  });
}

function closeLoadResourcesModal() {
  emit('refreshConfig')
  isResourceModalOpen.value = false;
}

function handleUploadButtonClicked() {
  isResourceModalOpen.value = true;
}

function getResourceName(resource: Resource, projectName: string): string {
  var startIndex = resource.path.toLowerCase().indexOf(projectName.replaceAll(' ', '-').replaceAll('/', '-').toLowerCase());
  if (startIndex === -1) {
    startIndex = resource.path.toLowerCase().indexOf(projectName.toLowerCase());
  }
  if (startIndex !== -1) {
    return resource.path.substring(startIndex);
  }
  return "";
}

function deleteResource(resourcePath: string, projectName: string, configName: string) {
  deleteResourceForProject(store().state.userName, configName, projectName, resourcePath)
    .then(() => {
      resourceStatuses.value[resourcePath] = false;
      emit('refreshConfig');
    })
    .catch((error) => {
      console.error('Error deleting resource:', error);
      errorMessage.value = `Greška prilikom brisanja resursa: ${error}`;
      setTimeout(() => {
        errorMessage.value = '';
      }, 20000);
    });
}

function translateScheduleType(scheduleType: string) {
  switch (scheduleType) {
    case 'DAILY':
      return 'Dnevno';
    case 'WEEKLY':
      return 'Tjedno';
    case 'EVERY_TWO_WEEKS':
      return 'Svaka dva tjedna';
    case 'MONTHLY':
      return 'Mjesečno';
    default:
      return scheduleType;
  }
}

async function startProject(projectName: string) {
  try{
    await startjPlagRunForConfigAndProjectAndUserName(store().state.userName, props.config.plagConfig.name, projectName);
    jPlagRunStatus.value[projectName] = true;
    getJplagResultsForProject(projectName);
    errorMessage.value = '';
  } catch (e) {
    errorMessage.value = `Greška prilikom pokretanja provjere plagijata: ${e}`;
    setTimeout(() => {
      errorMessage.value = '';
    }, 20000);
  }
}

async function manuallyUpdateResource(resourcePath: string, projectName: string, configName: string) {
  resourceStatuses.value[resourcePath] = true;
  try {
    await manuallyUpdateResourceApiCall(resourcePath, store().state.userName, configName, projectName)
  } catch (error) {
    console.error('Error updating resource:', error);
    errorMessage.value = `Greška prilikom ažuriranja resursa: ${error}`;
    setTimeout(() => {
      errorMessage.value = '';
    }, 20000);
    resourceStatuses.value[resourcePath] = false;
  }
}

async function checkResourceUpdating() {
  for (const project of props.config.plagConfig.projects) {
    for (const resource of project.resources) {
      try {
        const response = await checkIfResourceUpdating(store().state.userName, props.config.plagConfig.name, project.name, resource.path);
        resourceStatuses.value[resource.path] = response.updating;
      } catch (error) {
        console.error('Error checking resource status:', error);
        resourceStatuses.value[resource.path] = false;
        errorMessage.value = `Greška prilikom provjere statusa resursa: ${error}`;
        setTimeout(() => {
          errorMessage.value = '';
        }, 20000);
      }
    }
  }
}

async function checkJplagRunStatus() {
  for (const project of props.config.plagConfig.projects) {
    let isAnyRunning = false;
    for (const resultHash of project.resultHashes) {
      try {
        const response = await checkIfJplagIsRunning(resultHash);
        if (response.running) {
          isAnyRunning = true;
          break;
        }
      } catch (error) {
        console.error('Error checking jPlag run status:', error);
        errorMessage.value = `Greška prilikom provjere statusa jPlag-a: ${error}`;
        setTimeout(() => {
          errorMessage.value = '';
        }, 20000);
      }
    }
    jPlagRunStatus.value[project.name] = isAnyRunning;
  }
}

async function updateCurrenltySuspendedRuns(plagRunHashes: string[]) {
  for (const plagRunHash of plagRunHashes) {
    const currentlySuspended = await checkRunSuspended(plagRunHash)
    currentlySuspendedRuns.value.push(currentlySuspended)
  }
}

async function getJplagResultsForProject(projectName: string) {
  try {
    const response = await getAllRunsForConfigAndProject(store().state.userName, props.config.plagConfig.name, projectName);
    resultsForProjects.value[projectName] = response.map(run => ({
      ...run,
      isJplagRunning: jPlagRunStatus.value[projectName],
    }));
    const plagRunHashes = response.map(run => run.resultHash);
    await updatePlagRunStatuses(plagRunHashes);
    await updateCurrenltySuspendedRuns(plagRunHashes);
  } catch (error) {
    console.error('Error fetching jPlag results:', error);
    errorMessage.value = `Greška prilikom preuzimanja rezultata detekcije plagijata: ${error}`;
  }
}

onMounted(() => {
  checkResourceUpdating();
  checkJplagRunStatus();
  props.config.plagConfig.projects.forEach(project => {
    getJplagResultsForProject(project.name);
  });
  setInterval(() => {
    props.config.plagConfig.projects.forEach(project => {
      getJplagResultsForProject(project.name);
    });
  }, 30000);
  setInterval(checkResourceUpdating, 30000);
  setInterval(checkJplagRunStatus, 30000);
});
</script>

<style scoped>
.container {
  max-width: 800px;
}

.scrollable-container {
  max-height: 100vh;
  overflow-y: auto;
}
</style>
