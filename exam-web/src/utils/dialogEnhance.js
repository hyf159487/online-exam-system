/**
 * 全局增强 Element Plus 的 el-dialog
 * 功能：1) 拖拽标题栏  2) 右下角缩放  3) 双击/按钮最大化与还原  4) 限制初始高度不超出视口
 *
 * 实现方式：事件委托 + MutationObserver，无需改动任何现有组件代码。
 */

/** 注入全局弹窗增强样式 */
function injectDialogStyle() {
  if (document.getElementById('dialog-enhance-style')) return
  const style = document.createElement('style')
  style.id = 'dialog-enhance-style'
  style.textContent = `
    /* 弹窗上下左右都居中（flex 容器中 margin:auto 可居中，内容超高时可滚动不裁切） */
    .el-overlay-dialog {
      display: flex;
    }
    .el-overlay-dialog .el-dialog {
      margin: auto;
    }

    /* 限制弹窗最大高度，防止内容过长超出屏幕 */
    .el-dialog {
      max-height: 90vh !important;
      display: flex !important;
      flex-direction: column !important;
    }
    .el-dialog__header {
      flex-shrink: 0;
      padding-right: 104px;
    }
    .el-dialog__body {
      overflow-y: auto;
      flex: 1;
    }
    .el-dialog__footer {
      flex-shrink: 0;
    }

    /* 右下角缩放手柄 */
    .dialog-resize-handle {
      position: absolute;
      right: 0;
      bottom: 0;
      width: 20px;
      height: 20px;
      cursor: se-resize;
      z-index: 10;
    }
    .dialog-resize-handle::after {
      content: '';
      position: absolute;
      right: 4px;
      bottom: 4px;
      width: 0;
      height: 0;
      border-left: 6px solid transparent;
      border-top: 6px solid #909399;
      pointer-events: none;
    }

    /* 最大化/还原按钮：与关闭按钮同尺寸，紧贴其左侧 */
    .dialog-max-btn {
      position: absolute;
      top: 0;
      right: 48px;
      width: 48px;
      height: 48px;
      padding: 0;
      margin: 0;
      background: transparent;
      border: none;
      outline: none;
      cursor: pointer;
      color: var(--el-color-info);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      z-index: 10;
    }
    .dialog-max-btn:hover {
      color: var(--el-color-primary);
    }
    .dialog-max-btn svg {
      display: block;
    }
    .dialog-max-btn .max-icon,
    .dialog-max-btn .restore-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }
  `
  document.head.appendChild(style)
}

/** 切换最大化/还原状态 */
function toggleMaximize(dialogEl, btnEl) {
  if (dialogEl._maximized) {
    // 还原
    dialogEl.style.width = dialogEl._prevWidth
    dialogEl.style.height = dialogEl._prevHeight
    dialogEl.style.transform = dialogEl._prevTransform
    dialogEl.style.margin = dialogEl._prevMargin
    dialogEl.style.borderRadius = dialogEl._prevBorderRadius
    const body = dialogEl.querySelector('.el-dialog__body')
    if (body && dialogEl._prevBodyMaxHeight !== undefined) {
      body.style.maxHeight = dialogEl._prevBodyMaxHeight
    }
    dialogEl._maximized = false
    updateMaxBtnIcon(btnEl, false)
  } else {
    // 最大化：保存当前状态
    dialogEl._prevWidth = dialogEl.style.width || ''
    dialogEl._prevHeight = dialogEl.style.height || ''
    dialogEl._prevTransform = dialogEl.style.transform || ''
    dialogEl._prevMargin = dialogEl.style.margin || ''
    dialogEl._prevBorderRadius = dialogEl.style.borderRadius || ''
    const body = dialogEl.querySelector('.el-dialog__body')
    dialogEl._prevBodyMaxHeight = body ? body.style.maxHeight : ''

    dialogEl.style.width = '100vw'
    dialogEl.style.height = '100vh'
    dialogEl.style.margin = '0'
    dialogEl.style.transform = 'translate(0, 0)'
    dialogEl.style.borderRadius = '0'
    if (body) body.style.maxHeight = 'none'
    dialogEl._maximized = true
    updateMaxBtnIcon(btnEl, true)
  }
}

/** 更新最大化按钮图标 */
function updateMaxBtnIcon(btnEl, isMaximized) {
  if (!btnEl) return
  const maxIcon = btnEl.querySelector('.max-icon')
  const restoreIcon = btnEl.querySelector('.restore-icon')
  if (maxIcon) maxIcon.style.display = isMaximized ? 'none' : 'inline-flex'
  if (restoreIcon) restoreIcon.style.display = isMaximized ? 'inline-flex' : 'none'
  btnEl.title = isMaximized ? '还原' : '最大化'
}

/** 给 dialog 添加最大化按钮 */
function addMaxBtn(dialogEl) {
  if (dialogEl._maxBtnInited) return
  dialogEl._maxBtnInited = true

  const header = dialogEl.querySelector('.el-dialog__header')
  if (!header) return
  const closeBtn = header.querySelector('.el-dialog__headerbtn')

  const btn = document.createElement('button')
  btn.type = 'button'
  btn.className = 'dialog-max-btn'
  btn.title = '最大化'
  // 最大化图标（空心方块） / 还原图标（重叠方块）
  btn.innerHTML = `
    <span class="max-icon">
      <svg viewBox="0 0 1024 1024" width="16" height="16"><path fill="currentColor" d="M128 128h768v768H128z m64 64v640h640V192H192z"/></svg>
    </span>
    <span class="restore-icon" style="display:none">
      <svg viewBox="0 0 1024 1024" width="16" height="16"><path fill="currentColor" d="M128 256h512v512H128z m64 64v384h384V320H192z m512-192h192v192h-64V256h-128V128z m-128 128h384v384h-64V320h-256V256z"/></svg>
    </span>
  `

  btn.addEventListener('click', (e) => {
    e.stopPropagation()
    toggleMaximize(dialogEl, btn)
  })

  if (closeBtn) {
    header.insertBefore(btn, closeBtn)
    // 与关闭按钮同尺寸，紧贴其左侧，确保显示在弹窗右上角
    const closeSize = closeBtn.offsetWidth || 48
    btn.style.right = closeSize + 'px'
    btn.style.width = closeSize + 'px'
    btn.style.height = closeSize + 'px'
  } else {
    header.appendChild(btn)
    btn.style.right = '0px'
  }
}

/** 给 dialog 添加右下角缩放手柄 */
function addResizeHandle(dialogEl) {
  if (dialogEl._resizeHandleInited) return
  dialogEl._resizeHandleInited = true

  dialogEl.style.position = 'relative'
  const handle = document.createElement('div')
  handle.className = 'dialog-resize-handle'
  dialogEl.appendChild(handle)
}

/** 重置弹窗为初始状态（居中 + 默认尺寸） */
function resetDialogPosition(dialogEl) {
  if (!dialogEl) return
  dialogEl.style.transform = ''
  dialogEl.style.margin = ''
  dialogEl.style.width = ''
  dialogEl.style.height = ''
  if (dialogEl._maximized) {
    dialogEl._maximized = false
    const btn = dialogEl.querySelector('.dialog-max-btn')
    if (btn) updateMaxBtnIcon(btn, false)
  }
}

/** 判断 overlay 是否处于可见状态 */
function isOverlayVisible(overlayEl) {
  const display = overlayEl.style.display
  return display !== 'none' && display !== ''
}

/** 重置某个 overlay 内所有 dialog 的位置 */
function resetOverlayDialogs(overlayEl) {
  const dialogs = overlayEl.querySelectorAll('.el-dialog')
  dialogs.forEach(resetDialogPosition)
}

/** 初始化单个 dialog：手柄 + 按钮 + 样式 */
function enhanceDialog(dialogEl) {
  addResizeHandle(dialogEl)
  addMaxBtn(dialogEl)
}

export function initDialogEnhance() {
  if (typeof window === 'undefined') return
  injectDialogStyle()

  let dragDialog = null
  let resizeDialog = null
  let dragStartX = 0
  let dragStartY = 0
  let dragTx = 0
  let dragTy = 0
  let resizeStartW = 0
  let resizeStartH = 0
  let resizeStartX = 0
  let resizeStartY = 0

  const onMouseMove = (e) => {
    if (dragDialog) {
      const dx = e.clientX - dragStartX
      const dy = e.clientY - dragStartY
      dragDialog.style.transform = `translate(${dragTx + dx}px, ${dragTy + dy}px)`
    }
    if (resizeDialog) {
      const newW = Math.max(400, resizeStartW + e.clientX - resizeStartX)
      const newH = Math.max(200, resizeStartH + e.clientY - resizeStartY)
      resizeDialog.style.width = newW + 'px'
      // 如果正在 resize，把 body 的 max-height 放开，让内容自适应
      const body = resizeDialog.querySelector('.el-dialog__body')
      if (body) body.style.maxHeight = 'none'
    }
  }

  const onMouseUp = (e) => {
    if (dragDialog) {
      dragTx += e.clientX - dragStartX
      dragTy += e.clientY - dragStartY
      dragDialog = null
    }
    if (resizeDialog) {
      // resize 后如果之前是最大化状态，取消最大化标记并更新按钮图标
      if (resizeDialog._maximized) {
        const btn = resizeDialog.querySelector('.dialog-max-btn')
        if (btn) updateMaxBtnIcon(btn, false)
        resizeDialog._maximized = false
      }
      resizeDialog = null
    }
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  // ========== 拖拽 & 缩放 ==========
  document.body.addEventListener('mousedown', (e) => {
    // 1) 拖拽：标题栏 mousedown（不拦截关闭/最大化按钮）
    const header = e.target.closest('.el-dialog__header')
    if (header) {
      const dialog = header.closest('.el-dialog')
      if (!dialog) return
      if (e.target.closest('.el-dialog__headerbtn')) return
      if (e.target.closest('.dialog-max-btn')) return

      header.style.cursor = 'move'

      const computed = window.getComputedStyle(dialog)
      const matrix = new DOMMatrix(computed.transform)
      dragTx = matrix.m41
      dragTy = matrix.m42

      dragDialog = dialog
      dragStartX = e.clientX
      dragStartY = e.clientY
      dialog.style.transition = 'none'
      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
      e.preventDefault()
      return
    }

    // 2) 缩放：右下角手柄 mousedown
    const handle = e.target.closest('.dialog-resize-handle')
    if (handle) {
      const dialog = handle.closest('.el-dialog')
      if (!dialog) return
      resizeDialog = dialog
      resizeStartW = dialog.offsetWidth
      resizeStartH = dialog.offsetHeight
      resizeStartX = e.clientX
      resizeStartY = e.clientY
      dialog.style.transition = 'none'
      document.addEventListener('mousemove', onMouseMove)
      document.addEventListener('mouseup', onMouseUp)
      e.preventDefault()
      e.stopPropagation()
    }
  })

  // ========== 双击标题栏：最大化 / 还原 ==========
  document.body.addEventListener('dblclick', (e) => {
    const header = e.target.closest('.el-dialog__header')
    if (!header) return
    const dialog = header.closest('.el-dialog')
    if (!dialog) return
    if (e.target.closest('.el-dialog__headerbtn')) return
    if (e.target.closest('.dialog-max-btn')) return

    const btn = dialog.querySelector('.dialog-max-btn')
    toggleMaximize(dialog, btn)
  })

  // ========== MutationObserver：动态创建的 dialog 增强 + 打开时重置居中 ==========
  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      // 1) 新增节点：增强弹窗（缩放手柄 + 最大化按钮），并重置为初始居中状态
      if (mutation.type === 'childList') {
        mutation.addedNodes.forEach((node) => {
          if (node.nodeType !== 1) return
          // 新增的是整个 overlay
          if (node.matches?.('.el-overlay')) {
            const dialogs = node.querySelectorAll('.el-dialog')
            dialogs.forEach((d) => {
              enhanceDialog(d)
              resetDialogPosition(d)
            })
            return
          }
          // 新增的是 dialog 或包含 dialog 的节点
          const dialogs = node.matches?.('.el-dialog')
            ? [node]
            : Array.from(node.querySelectorAll?.('.el-dialog') || [])
          dialogs.forEach((d) => {
            enhanceDialog(d)
            resetDialogPosition(d)
          })
        })
      }

      // 2) overlay 从隐藏变为显示：重置位置为居中
      if (mutation.type === 'attributes' && mutation.attributeName === 'style') {
        const overlay = mutation.target
        if (overlay.classList && overlay.classList.contains('el-overlay') && isOverlayVisible(overlay)) {
          resetOverlayDialogs(overlay)
        }
      }
    })
  })

  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['style'],
  })

  // 给页面已存在的 dialog 增强
  document.querySelectorAll('.el-dialog').forEach(enhanceDialog)
}
