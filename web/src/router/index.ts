import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import ContentDetailView from '../views/ContentDetailView.vue'
import GenerateView from '../views/GenerateView.vue'
import LoginView from '../views/LoginView.vue'
import ProjectDetailView from '../views/ProjectDetailView.vue'
import ProjectsView from '../views/ProjectsView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', component: ProjectsView },
    { path: '/projects/:id', component: ProjectDetailView },
    { path: '/projects/:id/generate', component: GenerateView },
    { path: '/contents/:id', component: ContentDetailView }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.isAuthed) {
    return '/login'
  }
  if (to.path === '/login' && auth.isAuthed) {
    return '/'
  }
})
