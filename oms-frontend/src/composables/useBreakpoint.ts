import { ref, onMounted, onUnmounted } from 'vue'

const MOBILE_MAX = 768
const TABLET_MAX = 992

function getMatches() {
  if (typeof window === 'undefined') return { mobile: false, tablet: false }
  return {
    mobile: window.matchMedia(`(max-width: ${MOBILE_MAX}px)`).matches,
    tablet: window.matchMedia(`(max-width: ${TABLET_MAX}px)`).matches
  }
}

/**
 * 响应式断点：移动端 768px、平板 992px
 * isMobile: 手机/小平板；isTablet: 大平板/小桌面（含 mobile）
 */
export function useBreakpoint() {
  const isMobile = ref(getMatches().mobile)
  const isTablet = ref(getMatches().tablet)

  function update() {
    const m = getMatches()
    isMobile.value = m.mobile
    isTablet.value = m.tablet
  }

  let qMobile: MediaQueryList | null = null
  let qTablet: MediaQueryList | null = null

  onMounted(() => {
    qMobile = window.matchMedia(`(max-width: ${MOBILE_MAX}px)`)
    qTablet = window.matchMedia(`(max-width: ${TABLET_MAX}px)`)
    qMobile.addEventListener('change', update)
    qTablet.addEventListener('change', update)
    update()
  })

  onUnmounted(() => {
    if (qMobile) qMobile.removeEventListener('change', update)
    if (qTablet) qTablet.removeEventListener('change', update)
  })

  return { isMobile, isTablet }
}
