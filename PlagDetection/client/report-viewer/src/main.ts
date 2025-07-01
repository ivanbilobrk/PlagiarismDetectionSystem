import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import { router } from './router'
import VueVirtualScroller from 'vue-virtual-scroller'
import 'highlight.js/lib/common'
import PrimeVue from "primevue/config";
import { library } from '@fortawesome/fontawesome-svg-core'
import { faTrash } from '@fortawesome/free-solid-svg-icons'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'

import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'

import './style.css'
import Noir from '../src/presets/Noir.js';

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueVirtualScroller)
library.add(faTrash)

app.use(PrimeVue, {
  theme: {
    preset: Noir,
    options: {
      prefix: 'p',
      darkModeSelector: '.p-dark',
      cssLayer: false,
    }
  }
});

app.config.errorHandler = (err, vm, info) => {
  console.error(err)
  console.error(info)
  alert('An unhandled error occurred. Please check the console for more details.')
}

app.component('font-awesome-icon', FontAwesomeIcon)
app.mount('#app')
