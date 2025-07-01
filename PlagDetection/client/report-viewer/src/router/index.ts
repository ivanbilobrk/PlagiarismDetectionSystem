import { createRouter, createWebHistory } from 'vue-router'
import ComparisonViewWrapper from '@/viewWrapper/ComparisonViewWrapper.vue'
import ErrorView from '@/views/ErrorView.vue'
import InformationViewWrapper from '@/viewWrapper/InformationViewWrapper.vue'
import ClusterViewWrapper from '@/viewWrapper/ClusterViewWrapper.vue'
import OldVersionRedirectView from '@/views/OldVersionRedirectView.vue'
import LoginView from '@/views/LoginView.vue'
import ResourcesListView from '@/views/ResourcesListView.vue'
import { store } from '@/stores/store'
import FileUploadView from '@/views/FileUploadView.vue'
import OverviewView from '@/views/OverviewView.vue'
import PlagConfigListView from '@/views/PlagConfigListView.vue'
import PlagConfigDetailView from '@/views/PlagConfigDetailView.vue'

/**
 * The router is used to navigate between the different views of the application.
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
     path: "/plagconfigs",
      name: "PlagConfigListView",
      component: PlagConfigListView,
    },
    {
      path: "/plagconfig/:plagConfigName",
      name: "PlagConfigDetailView",
      component: PlagConfigDetailView,
      props: route => ({ plagConfigName: route.params.plagConfigName })
    },
    {
      path: '/resources',
      name: 'ResourcesListView',
      component: ResourcesListView
    },
    {
      path: '/temp',
      name: 'FileUploadView',
      component: FileUploadView
    },
    {
      path: '/login',
      name: 'LoginView',
      component: LoginView
    },
    {
      path: '/overview/:resultHash',
      name: 'OverviewView',
      component: OverviewView,
      props: route => ({ resultHash: route.params.resultHash })
    },
    {
      path: '/comparison/:searchKey',
      name: 'ComparisonView',
      component: ComparisonViewWrapper,
      props: true
    },
    {
      path: '/error/:message/:to?/:routerInfo?',
      name: 'ErrorView',
      component: ErrorView,
      props: true
    },
    {
      path: '/cluster/:clusterIndex',
      name: 'ClusterView',
      component: ClusterViewWrapper,
      props: true
    },
    {
      path: '/info',
      name: 'InfoView',
      component: InformationViewWrapper
    },
    {
      path: '/old/:version',
      name: 'OldVersionRedirectView',
      component: OldVersionRedirectView,
      props: true
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/error/Could not find the requested page/FileUploadView/Back to file upload'
    }
  ]
})

router.beforeEach((to, from, next) => {
  if (store().state.userName.length === 0) {
    if (to.name !== 'LoginView') {
      next({ name: 'LoginView', query: { redirect: to.fullPath } });
    } else {
      next();
    }
  } else {
    next();
  }
});

function redirectOnError(
  error: Error,
  prefix: string = '',
  redirectRoute: string = 'ResourcesListView',
  redirectRouteTitle: string = 'Back to file upload'
) {
  console.error(error)
  router.push({
    name: 'ErrorView',
    params: {
      message: prefix + (error.message ?? error),
      to: redirectRoute,
      routerInfo: redirectRouteTitle
    }
  })
}

let hasHadRouterError = false
router.onError((error) => {
  if (hasHadRouterError) {
    return alert('An error occurred while routing. Please reload the page.')
  }
  hasHadRouterError = true
  redirectOnError(error, 'An error occurred while routing. Please reload the page.\n')
})

export { router, redirectOnError }
