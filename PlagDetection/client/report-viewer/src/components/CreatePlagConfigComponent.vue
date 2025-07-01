<template>
  <div
    v-if="isOpen"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50"
    @click.self="closeModal"
  >
    <div class="w-3/4 rounded bg-white p-8 shadow-lg" @click.stop>
      <h2 class="mb-4 text-2xl">Konfiguracija Plagijata</h2>

      <div class="mb-4">
        <label for="configName" class="block text-sm font-medium text-gray-700"
        >Naziv Konfiguracije</label
        >
        <input
          type="text"
          v-model="configName"
          id="configName"
          class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
        />
      </div>

      <!-- NOVI AUTOCOMPLETE -->
      <div class="mb-4 relative">
        <label for="subjectName" class="block text-sm font-medium text-gray-700"
        >Naziv Predmeta</label
        >
        <input
          type="text"
          v-model="subjectQuery"
          id="subjectName"
          autocomplete="off"
          class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
          @input="onSubjectInput"
          :readonly="!!subjectId"
        />
        <ul
          v-if="subjectResults.length && !subjectId"
          class="absolute z-10 w-full bg-white border border-gray-300 rounded mt-1 max-h-48 overflow-y-auto"
          style="list-style:none;padding:0;margin:0;"
        >
          <li
            v-for="subject in subjectResults"
            :key="subject.id + subject.year"
            class="px-3 py-2 cursor-pointer hover:bg-blue-100"
            @click="selectSubject(subject)"
          >
            {{ subject.name }} <span class="text-gray-400 text-xs">[{{ subject.id }}]</span>
          </li>
        </ul>
        <!-- PRIKAZ ODABRANOG, MOGUĆNOST UKLANJANJA -->
        <div v-if="subjectId" class="mt-2 text-xs text-green-800 bg-green-100 px-2 py-1 rounded inline-flex items-center">
          Odabrano: {{ subjectQuery }} (subjectId: {{ subjectId }})
          <button @click="clearSubject" class="ml-2 text-red-500 font-bold cursor-pointer" title="Obriši odabir">×</button>
        </div>
      </div>
      <!-- KRAJ AUTOCOMPLETE -->

      <!-- ostalo ne mijenjaj -->
      <div class="mb-4">
        <label for="yearsConsidered" class="block text-sm font-medium text-gray-700"
        >Broj Godina za Preuzimanje Resursa</label
        >
        <input
          v-model.number="yearsConsidered"
          type="number"
          id="yearsConsidered"
          min="1"
          max="5"
          class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
          :class="{ 'border-red-500': numberOfYearsError }"
          @input="validateNumberOfYears"
        />
        <span v-if="numberOfYearsError" class="text-sm text-red-600"
        >Broj godina mora biti između 1 i 5.</span
        >
      </div>

      <div class="mb-4">
        <label class="block text-sm font-medium text-gray-700"
        >Sufiksi - Datoteke Koje Želite Uključiti u Usporedbu</label
        >
        <input
          type="text"
          v-model="suffixInput"
          placeholder="Unesite nazive odvojene zarezom"
          class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
        />
        <div class="mt-2 flex flex-wrap">
          <span
            v-for="(suffix, index) in suffixes"
            :key="index"
            class="mb-2 mr-2 rounded bg-blue-100 px-2.5 py-0.5 text-sm font-medium text-blue-800"
          >
            {{ suffix }}
            <button
              type="button"
              @click="removeSuffix(index)"
              class="ml-1 text-red-500 hover:text-red-700"
            >
              x
            </button>
          </span>
        </div>
      </div>
      <div class="mb-4">
        <label class="block text-sm font-medium text-gray-700"
        >Datoteke koje želite izostaviti iz usporedbe</label
        >
        <input
          type="text"
          v-model="disallowedDileInput"
          placeholder="Unesite nazive odvojene zarezom"
          class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
        />
        <div class="mt-2 flex flex-wrap">
          <span
            v-for="(file, index) in disallowedFiles"
            :key="index"
            class="mb-2 mr-2 rounded bg-blue-100 px-2.5 py-0.5 text-sm font-medium text-blue-800"
          >
            {{ file }}
            <button
              type="button"
              @click="removeFile(index)"
              class="ml-1 text-red-500 hover:text-red-700"
            >
              x
            </button>
          </span>
        </div>
      </div>
      <div class="mb-4">
        <label class="block text-sm font-medium text-gray-700">Odaberite programski jezik</label>
        <select
          v-model="selectedLanguage"
          @change="addLanguage"
          class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
        >
          <option value="" disabled selected>Odaberite jezik</option>
          <option v-for="language in languages" :key="language" :value="language">
            {{ language }}
          </option>
        </select>
        <div class="mt-2 flex flex-wrap">
          <span
            v-for="(language, index) in selectedLanguages"
            :key="index"
            class="mb-2 mr-2 rounded bg-blue-100 px-2.5 py-0.5 text-sm font-medium text-blue-800"
          >
            {{ language }}
            <button
              type="button"
              @click="removeLanguage(index)"
              class="ml-1 text-red-500 hover:text-red-700"
            >
              x
            </button>
          </span>
        </div>
      </div>
      <div class="mb-4">
        <label for="updateSchedule" class="block text-sm font-medium text-gray-700"
        >Vrsta Rasporeda Ažuriranja Resursa</label
        >
        <select
          v-model="updateSchedule"
          id="updateSchedule"
          class="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
        >
          <option value="DAILY">Dnevno</option>
          <option value="WEEKLY">Tjedno</option>
          <option value="EVERY_TWO_WEEKS">Svaka dva tjedna</option>
          <option value="MONTHLY">Mjesečno</option>
        </select>
      </div>
      <div v-if="errorMessage" class="mt-2 text-center text-red-600">
        {{ errorMessage }}
      </div>
      <button
        @click="saveConfiguration"
        class="mt-4 rounded bg-blue-500 px-3 py-1 text-white hover:bg-blue-600 active:bg-blue-700"
      >
        Spremi Konfiguraciju
      </button>
      <button
        @click="closeModal"
        class="ml-4 mt-4 rounded bg-gray-500 px-3 py-1 text-white hover:bg-gray-600 active:bg-gray-700"
      >
        Zatvori
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineEmits, defineProps, ref, watch } from 'vue'
import { createPlagConfig } from '@/api/apicalls.ts'
import { store } from '@/stores/store'
import { useRouter } from 'vue-router'

const emit = defineEmits(['close'])
const errorMessage = ref('')
const configName = ref('')
const backendURL = ref('')
const suffixInput = ref('')
const disallowedDileInput = ref('')
const languages = ref(['Java', 'Python', 'C++', 'JavaScript', 'TypeScript'])
const selectedLanguage = ref('')
const selectedLanguages = ref<string[]>([])
const numberOfYearsError = ref(false)
const updateSchedule = ref('WEEKLY')
const suffixes = ref([])
const disallowedFiles = ref([])
const router = useRouter()

interface Subject {
  year: string;
  id: string;
  name: string;
  count: number;
}

const subjects: Subject[] = [
  { id: "2006", name: "Objektno orijentirano programiranje", year: "2024", count: 897 },
  { id: "2004", name: "Uvod u programiranje", year: "2024", count: 761 },
  { id: "2021", name: "Razvoj programske potpore za web", year: "2024", count: 524 },
  { id: "155", name: "Baze podataka", year: "2024", count: 487 },
  { id: "2012", name: "Napredni algoritmi i strukture podataka", year: "2024", count: 454 },
  { id: "477", name: "Napredne baze podataka", year: "2024", count: 179 },
  { id: "2022", name: "Operacijska istraživanja", year: "2024", count: 170 },
  { id: "866", name: "Informacijski sustavi", year: "2024", count: 168 },
  { id: "2017", name: "Napredni razvoj programske potpore za web", year: "2024", count: 144 },
  { id: "2020", name: "Poslovna inteligencija", year: "2024", count: 97 },
  { id: "2026", name: "Zaštita i sigurnost informacijskih sustava", year: "2024", count: 67 },
  { id: "2000", name: "Alchemy", year: "2024", count: 60 },
  { id: "2015", name: "Object Oriented Programming", year: "2024", count: 56 },
  { id: "328", name: "Razvoj primijenjene programske potpore", year: "2024", count: 54 },
  { id: "2013", name: "Introduction to Programming", year: "2024", count: 53 },
  { id: "2024", name: "Web Software Development", year: "2024", count: 34 },
  { id: "2002", name: "Databases", year: "2024", count: 30 },
  { id: "2001", name: "Programsko inženjerstvo", year: "2024", count: 29 },
  { id: "2035", name: "Ofenzivna sigurnost", year: "2024", count: 29 },
  { id: "2034", name: "Sigurnosne prijetnje na Internetu", year: "2024", count: 28 },
  { id: "2036", name: "Sigurnost operacijskih sustava i aplikacija", year: "2024", count: 28 },
  { id: "2007", name: "Natjecateljsko programiranje", year: "2024", count: 26 },
  { id: "2025", name: "Introduction to programming (COGSCI)", year: "2024", count: 17 },
  { id: "2019", name: "Development of Software Applications", year: "2024", count: 9 },
  { id: "2023", name: "Business Intelligence", year: "2024", count: 8 },
  { id: "2033", name: "Sigurnost računalnih sustava", year: "2023", count: 485 },
  { id: "2032", name: "Computational Modeling Challenge", year: "2023", count: 26 },
  { id: "2018", name: "Algorithms and Data Structures", year: "2023", count: 25 },
  { id: "2031", name: "Objektno orijentirano programiranje", year: "2023", count: 15 },
  { id: "2030", name: "Informatika (Python)", year: "2023", count: 2 },
  { id: "2011", name: "Algoritmi", year: "2022", count: 35 },
  { id: "1150", name: "Upravljanje projektima", year: "2022", count: 1 },
  { id: "2005", name: "_Poslovna inteligencija (FER2)", year: "2021", count: 55 },
  { id: "2014", name: "Hack It", year: "2020", count: 4 },
  { id: "2016", name: "Sustavi baza podataka", year: "2020", count: 3 },
  { id: "2003", name: "Information Systems", year: "2020", count: 2 },
  { id: "2008", name: "Objektno orijentirano programiranje (FER2)", year: "2019", count: 157 },
  { id: "2010", name: "Dani otvorenih vrata", year: "2019", count: 11 },
  { id: "2009", name: "Smotra", year: "2019", count: 2 },
  { id: "20", name: "Algoritmi i strukture podataka", year: "2019", count: 2 },
  { id: "2", name: "Algoritmi i strukture podataka (FER 1)", year: "2019", count: 2 },
  { id: "11", name: "Programiranje i programsko inženjerstvo", year: "2017", count: 733 }
];

const subjectQuery = ref('');
const subjectResults = ref<Subject[]>([]);
const subjectId = ref('');

watch(subjectQuery, val => {
  if (!val) subjectId.value = '';
})

function onSubjectInput() {
  if (subjectId.value) return;
  const value = subjectQuery.value.toLowerCase();
  if (!value) {
    subjectResults.value = [];
    return;
  }
  subjectResults.value = subjects
    .filter(s =>
      s.name.toLowerCase().includes(value)
    )
    .slice(0, 10); // do 10 prijedloga
}

function selectSubject(subject: Subject) {
  subjectQuery.value = subject.name;
  subjectId.value = subject.id;
  subjectResults.value = [];
}

function clearSubject() {
  subjectQuery.value = '';
  subjectId.value = '';
  subjectResults.value = [];
}

const yearsConsidered = ref<number>(0)
const props = defineProps<{ isOpen: boolean }>()
watch(suffixInput, (newValue) => {
  suffixes.value = newValue
    .split(',')
    .map((suffix) => suffix.trim())
    .filter((suffix) => suffix !== '')
})
watch(disallowedDileInput, (newValue) => {
  disallowedFiles.value = newValue
    .split(',')
    .map((suffix) => suffix.trim())
    .filter((suffix) => suffix !== '')
})
function removeSuffix(index) {
  suffixes.value.splice(index, 1)
  suffixInput.value = suffixes.value.join(', ')
}
function removeFile(index) {
  disallowedFiles.value.splice(index, 1)
  disallowedDileInput.value = disallowedFiles.value.join(', ')
}
function addLanguage() {
  if (selectedLanguage.value && !selectedLanguages.value.includes(selectedLanguage.value)) {
    selectedLanguages.value.push(selectedLanguage.value)
    selectedLanguage.value = ''
  }
}
function removeLanguage(index: number) {
  selectedLanguages.value.splice(index, 1)
}
async function saveConfiguration() {
  validateNumberOfYears()
  if (numberOfYearsError.value) {
    errorMessage.value = 'Molimo ispravite greške prije nego što spremite konfiguraciju.'
    return
  }
  if (!subjectId.value) {
    errorMessage.value = 'Odaberite predmet iz liste.'
    return;
  }
  try {
    await createPlagConfig(
      store().state.userName,
      configName.value,
      yearsConsidered.value,
      backendURL.value,
      suffixes.value,
      selectedLanguages.value,
      updateSchedule.value,
      disallowedFiles.value,
      subjectId.value
    )
  } catch (error) {
    errorMessage.value = `Greška prilikom spremanja konfiguracije: ${error}`
    return
  }
  await router.push(`/plagconfig/${configName.value}`)
}
function validateNumberOfYears() {
  numberOfYearsError.value =
    yearsConsidered.value === null || yearsConsidered.value < 1 || yearsConsidered.value > 5
}
function closeModal() {
  emit('close')
}
</script>
