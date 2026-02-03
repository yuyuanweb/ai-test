import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLoginModalStore = defineStore('loginModal', () => {
  const visible = ref(false)
  const activeTab = ref<'login' | 'register'>('login')

  const openModal = (tab: 'login' | 'register' = 'login') => {
    activeTab.value = tab
    visible.value = true
  }

  const closeModal = () => {
    visible.value = false
  }

  return {
    visible,
    activeTab,
    openModal,
    closeModal,
  }
})
