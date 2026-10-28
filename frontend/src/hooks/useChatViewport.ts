import { useLayoutEffect, useState, type RefObject } from 'react'

const MIN_HEIGHT = 240

/**
 * Fits the chat to the part of the screen the user can actually see, and stops the page itself
 * from scrolling while the chat is open, so only the message list scrolls.
 *
 * iOS Safari doesn't shrink the page when the keyboard opens; it slides the page up underneath
 * it, which makes the whole page scrollable. Swipes then move the page (header, chat and all)
 * instead of the message list, and the offset can linger after the keyboard closes. Sizing the
 * chat to the visual viewport and pinning the window at the top prevents both.
 *
 * Returns the height for {@code ref}'s element, in pixels (undefined until measured).
 */
export function useChatViewport(ref: RefObject<HTMLElement | null>): number | undefined {
  const [height, setHeight] = useState<number>()

  useLayoutEffect(() => {
    const root = document.documentElement
    const viewport = window.visualViewport
    root.classList.add('scroll-locked')

    function update() {
      if (window.scrollY !== 0) window.scrollTo(0, 0)
      const element = ref.current
      if (!element) return
      const visibleHeight = viewport?.height ?? window.innerHeight
      const visibleBottom = (viewport?.offsetTop ?? 0) + visibleHeight
      // With the keyboard open, the bottom tab bar is hidden behind it, so don't leave room for it.
      const keyboardOpen = window.innerHeight - visibleHeight > 120
      const tabBar = document.querySelector<HTMLElement>('[data-bottom-nav]')
      const tabBarHeight =
        tabBar && !keyboardOpen && getComputedStyle(tabBar).display !== 'none' ? tabBar.offsetHeight : 0
      const gap = window.innerWidth >= 768 ? 24 : 12
      const top = element.getBoundingClientRect().top
      setHeight(Math.max(MIN_HEIGHT, Math.floor(visibleBottom - top - tabBarHeight - gap)))
    }

    update()
    viewport?.addEventListener('resize', update)
    viewport?.addEventListener('scroll', update)
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update)
    return () => {
      viewport?.removeEventListener('resize', update)
      viewport?.removeEventListener('scroll', update)
      window.removeEventListener('resize', update)
      window.removeEventListener('scroll', update)
      root.classList.remove('scroll-locked')
    }
  }, [ref])

  return height
}
