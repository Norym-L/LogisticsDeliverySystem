import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import './styles/theme-colors.css'
import './styles/milk-tokens.css'
import './styles/milk-focus.css'
import './styles/milk-hover.css'
import './styles/app.css'
import './styles/tysha-fonts.css'
import './styles/tysha.css'
import './styles/sf.css'
import './styles/track-map.css'

import { initTheme } from './lib/theme-controller'
import { initPressFeedback } from './lib/press-controller'
import { initFocusBorder } from './lib/focus-controller'
import { initHoverRipple } from './lib/hover-controller'

initTheme()

// 共享控制器只注册一次，避免每个组件各自计时
const disposePress = initPressFeedback()
const disposeFocus = initFocusBorder()
const disposeHover = initHoverRipple()

createApp(App).use(router).mount('#app')

if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    disposePress()
    disposeFocus()
    disposeHover()
  })
}
