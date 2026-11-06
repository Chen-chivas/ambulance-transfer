import { onMounted, onUnmounted } from 'vue'

/** Keeps polling local to mounted pages; the caller guards pending requests. */
export function usePolling(refresh: () => void) {
  let timer: ReturnType<typeof setInterval>
  const visible = () => { if (!document.hidden) refresh() }
  onMounted(() => {
    timer = setInterval(visible, 10000)
    document.addEventListener('visibilitychange', visible)
  })
  onUnmounted(() => { clearInterval(timer); document.removeEventListener('visibilitychange', visible) })
}
