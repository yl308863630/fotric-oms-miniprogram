import { defineStore } from 'pinia'

interface TagItem {
  name: string
  path: string
  title: string
}

export const useTagStore = defineStore('tag', {
  state: () => ({
    tags: [
      { name: 'Dashboard', path: '/dashboard', title: '首页' }
    ] as TagItem[],
    activePath: '/dashboard'
  }),
  actions: {
    addTag(tag: TagItem) {
      if (!this.tags.find(item => item.path === tag.path)) {
        this.tags.push(tag)
      }
      this.activePath = tag.path
    },
    removeTag(path: string) {
      if (path === '/dashboard') return
      const index = this.tags.findIndex(item => item.path === path)
      if (index !== -1) {
        this.tags.splice(index, 1)
        if (this.activePath === path) {
          this.activePath = this.tags[this.tags.length - 1].path
        }
      }
    },
    setActivePath(path: string) {
      this.activePath = path
    }
  }
})
