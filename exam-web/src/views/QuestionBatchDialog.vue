<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="新增题目"
    width="820px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-tabs v-model="activeTab">
      <!-- 手动录入模式 -->
      <el-tab-pane label="手动录入" name="manual">
        <div class="batch-toolbar">
          <span class="total-count">共 {{ questions.length }} 题</span>
          <el-button type="primary" plain :icon="Plus" size="small" @click="addRow">
            添加一题
          </el-button>
          <el-button
            type="warning"
            plain
            size="small"
            :loading="aiEvaluating"
            :disabled="!questions.length"
            @click="aiEvaluateAll"
          >
            🤖 AI智能评估难度
          </el-button>
          <el-button size="small" @click="clearAll" :disabled="!questions.length">
            清空全部
          </el-button>
          <el-button size="small" plain @click="expandAll(true)" :disabled="!questions.length">📂 展开全部</el-button>
          <el-button size="small" plain @click="expandAll(false)" :disabled="!questions.length">🗂 折叠全部</el-button>
          <span v-if="mountedLimit < questions.length" style="margin-left: 8px; font-size: 12px; color: var(--el-color-info)">
            ⏳ 分批渲染中 {{ mountedLimit }} / {{ questions.length }}
            <el-button link type="primary" size="small" @click="scheduleMount(true)">立即加载全部</el-button>
          </span>
          <span v-else-if="questions.length" style="margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary)">
            共 {{ questions.length }} 题 · 已展开 {{ expandedCount }} 题
          </span>
        </div>

        <div class="batch-list">
          <div v-for="(q, idx) in questions.slice(0, mountedLimit)" :key="idx" class="batch-item" :class="{ 'invalid-item': invalidIndex === idx }" :data-question-index="idx">
            <div class="batch-item-header" @click="toggleExpand(idx)">
              <span class="batch-item-index">{{ idx + 1 }}</span>
              <span class="batch-item-title">{{ q.content || '(未填写题干)' }}</span>
              <el-button
                type="danger"
                link
                size="small"
                :icon="Delete"
                @click.stop="removeRow(idx)"
                :disabled="questions.length <= 1"
              >
                删除
              </el-button>
              <span class="batch-item-arrow">{{ expanded[idx] ? '▲' : '▼' }}</span>
            </div>

            <div v-if="expanded[idx]" class="batch-item-body">
              <el-row :gutter="12">
                <el-col :span="8">
                  <el-form-item label="学科" required>
                    <el-select v-model="q.courseId" placeholder="选择学科" style="width: 100%" filterable>
                      <el-option
                        v-for="c in courseList"
                        :key="c.id"
                        :label="c.courseName"
                        :value="c.id"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="题型" required>
                    <el-select v-model="q.questionType" placeholder="选择题型" style="width: 100%" @change="(t) => handleTypeChange(idx, t)">
                      <el-option label="单选题" :value="1" />
                      <el-option label="多选题" :value="2" />
                      <el-option label="判断题" :value="3" />
                      <el-option label="填空题" :value="4" />
                      <el-option label="简答题" :value="5" />
                      <el-option label="名词解释" :value="6" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="难度" required>
                    <el-radio-group v-model="q.difficulty">
                      <el-radio-button :value="1">简单</el-radio-button>
                      <el-radio-button :value="2">中等</el-radio-button>
                      <el-radio-button :value="3">困难</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item label="题干" required>
                <el-input
                  v-model="q.content"
                  type="textarea"
                  :rows="2"
                  placeholder="请输入题干内容"
                />
              </el-form-item>

              <!-- 选项编辑器（仅单选/多选） -->
              <el-form-item
                v-if="q.questionType === 1 || q.questionType === 2"
                label="选项"
                required
              >
                <div class="options-wrapper">
                  <div v-for="(opt, oi) in q._options" :key="oi" class="option-row">
                    <el-radio
                      v-if="q.questionType === 1"
                      v-model="q._correctIdx"
                      :value="oi"
                    >
                      {{ letter(oi) }}
                    </el-radio>
                    <el-checkbox v-else v-model="opt.correct">
                      {{ letter(oi) }}
                    </el-checkbox>
                    <el-input
                      v-model="opt.text"
                      :placeholder="`选项 ${letter(oi)} 内容`"
                      style="flex: 1"
                    />
                    <el-button
                      type="danger"
                      :icon="Delete"
                      circle
                      size="small"
                      :disabled="q._options.length <= 2"
                      @click="removeOption(idx, oi)"
                    />
                  </div>
                  <el-button type="primary" plain :icon="Plus" size="small" @click="addOption(idx)">
                    添加选项
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item label="答案" required>
                <el-radio-group v-if="q.questionType === 3" v-model="q.answer">
                  <el-radio value="T">正确</el-radio>
                  <el-radio value="F">错误</el-radio>
                </el-radio-group>
                <el-input
                  v-else-if="q.questionType === 4"
                  v-model="q.answer"
                  placeholder="填空答案（多个空用 | 分隔）"
                />
                <el-input
                  v-else-if="q.questionType === 5 || q.questionType === 6"
                  v-model="q.answer"
                  type="textarea"
                  :rows="2"
                  placeholder="参考答案"
                />
                <el-input
                  v-else
                  :model-value="getSelectionAnswer(q)"
                  disabled
                  placeholder="请在上方选项中选择正确答案"
                />
              </el-form-item>

              <el-row :gutter="12">
                <el-col :span="8">
                  <el-form-item label="分值" required>
                    <el-input-number v-model="q.score" :min="0.5" :max="100" :step="0.5" />
                  </el-form-item>
                </el-col>
                <el-col :span="16">
                  <el-form-item label="解析">
                    <el-input
                      v-model="q.analysis"
                      type="textarea"
                      :rows="2"
                      placeholder="答案解析（选填）"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 文件导入模式（支持 Word 文件、Word 文本、JSON 文本） -->
      <el-tab-pane label="文件导入" name="file">
        <div class="file-import">
          <!-- 预设学科 / 预设分值 / 预设难度 -->
          <el-form-item label="预设学科" v-if="!preset?.courseId">
            <el-select v-model="batchCourseId" placeholder="选择学科（可选，解析后也可设置）" filterable style="width: 100%" clearable>
              <el-option
                v-for="c in courseList"
                :key="c.id"
                :label="c.courseName"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
          <el-row :gutter="12" style="margin-bottom: 8px">
            <el-col :span="8">
              <el-form-item label="预设分值">
                <el-input-number
                  v-model="batchScore"
                  :min="0.5"
                  :max="100"
                  :step="0.5"
                  :precision="1"
                  controls-position="right"
                  style="width: 100%"
                  placeholder="默认 2.0"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="预设难度">
                <el-select v-model="batchDifficulty" placeholder="AI 自动评估" style="width: 100%" clearable>
                  <el-option label="简单" :value="1" />
                  <el-option label="中等" :value="2" />
                  <el-option label="困难" :value="3" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8" style="display: flex; align-items: flex-end; padding-bottom: 18px">
              <el-button @click="applyPresetToAll" :disabled="!currentParsedQuestions.length || (!batchScore && !batchDifficulty && !batchCourseId)">
                批量应用到已解析
              </el-button>
            </el-col>
          </el-row>

          <!-- 按钮组：导入方式切换 + 模板下载 -->
          <div class="file-mode-switch" style="display: flex; align-items: center; gap: 12px">
            <el-radio-group v-model="fileImportMode">
              <el-radio-button value="file">📁 上传文件</el-radio-button>
              <el-radio-button value="text">📋 粘贴文本</el-radio-button>
            </el-radio-group>
            <el-dropdown trigger="click">
              <el-button>
                📥 下载导入模板
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleTemplateDownload('excel')">
                    <el-icon><Document /></el-icon>下载 Excel 模板（推荐·最稳定）
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleTemplateDownload('json')">
                    <el-icon><Document /></el-icon>下载 JSON 模板
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleTemplateDownload('txt')">
                    <el-icon><Document /></el-icon>下载 TXT 文本模板
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleTemplateDownload('word')">
                    <el-icon><Document /></el-icon>下载 Word 模板（样例参考）
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <!-- 格式说明（根据文本模式或文件类型自动切换） -->
          <div class="import-tips">
            <template v-if="fileImportMode === 'text' && textMode === 'json'">
              <p class="tips-title">JSON 导入格式说明：</p>
              <p>
                粘贴 JSON 数组批量导入题目。支持字段：
                <code>courseId</code>、<code>questionType</code>、<code>content</code>、
                <code>options</code>（数组）、<code>answer</code>、
                <code>analysis</code>、<code>difficulty</code>、<code>score</code>
              </p>
            </template>
            <template v-else-if="fileImportMode === 'file' && wordFile">
              <p class="tips-title">{{ getFileTypeLabel(wordFile.name) }} 导入格式说明：</p>
              <ol>
                <li><strong>Word (.docx)</strong>：题目以数字开头，选项以字母开头，答案标注在题干末尾</li>
                <li><strong>Excel (.xlsx/.xls)</strong>：第1行为表头（题干/题型/选项/答案/解析/分值）</li>
                <li><strong>PDF</strong>：自动提取文本内容，格式同 Word 文本</li>
                <li><strong>JSON (.json)</strong>：JSON 数组格式，字段同上</li>
                <li><strong>TXT (.txt)</strong>：就是粘贴文本的格式，也可直接上传 txt 文件</li>
              </ol>
              <p v-if="isWeakFormatFile" class="weak-format-warn">
                ⚠️ <strong>{{ getFileTypeLabel(wordFile.name) }} 为弱解析格式</strong>，Word/PDF 内的表格、图片、复杂排版可能解析失败；建议正式批量录入优先使用 Excel/JSON/TXT 模板。
              </p>
            </template>
            <template v-else>
              <p class="tips-title">题库导入格式说明：</p>
              <ol>
                <li>每道题目以数字开头：<code>1. 题干内容</code>（支持 1. / 1． / 1、）</li>
                <li>选项以字母开头：<code>A. 选项内容</code>（支持 A-F）</li>
                <li>答案标注在题干末尾：<code>（A）</code> 或 <code>(A)</code>，多选用 <code>（ABC）</code></li>
                <li>判断题答案：<code>（对）</code> / <code>（错）</code> 或 <code>（T）</code> / <code>（F）</code></li>
                <li>解析以"解析："开头</li>
                <li>系统自动识别：单选 / 多选 / 判断 / 填空 / 简答</li>
                <li>简答题推荐格式：<code>题目：xxx</code> + <code>答案：</code>（可多行，支持 ①②③ 分条）+ <code>解析：xxx</code></li>
              </ol>
            </template>
          </div>

          <!-- 文件上传区（多种格式）
                关键：:show-file-list="false" 完全禁用 el-upload 内置的文件列表渲染，
                避免它自己的 list 与我们自定义 word-file-info 合并出现"同一个文件显示两个"的双条目问题。
                所有文件展示只走 wordFileList 驱动的自定义 div（可控，永远 1 条）。 -->
          <div v-show="fileImportMode === 'file'">
            <el-upload
              ref="wordUploadRef"
              class="word-uploader"
              drag
              :auto-upload="false"
              :show-file-list="false"
              :limit="1"
              :multiple="false"
              accept=".docx,.xlsx,.xls,.pdf,.json,.txt,.png,.jpg,.jpeg"
              :on-change="handleFileChange"
              :on-exceed="() => ElMessage.warning('只能上传一个文件，已自动替换为最新选择的文件')"
            >
              <el-icon class="el-icon--upload"><Upload /></el-icon>
              <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">
                  支持格式：Word (.docx) / Excel (.xlsx/.xls) / PDF / JSON / TXT / 图片 (.png/.jpg)，文件大小不超过 20MB
                </div>
                <div class="el-upload__tip" style="color: var(--el-color-warning)">
                  💡 Word/PDF 内的图片试题暂不支持自动解析；正式批量录入建议使用 Excel/JSON/TXT 模板
                </div>
              </template>
            </el-upload>

            <!-- 自定义文件展示：单事实源 wordFile / wordFileList，绝对不会重复 -->
            <div v-if="wordFileList.length && wordFile" class="word-file-info">
              <el-icon><Document /></el-icon>
              <span class="word-file-name">{{ wordFile.name }}</span>
              <el-tag size="small" style="margin-left: 8px">{{ getFileTypeLabel(wordFile.name) }}</el-tag>
              <el-button type="danger" link size="small" @click="removeWordFile">移除</el-button>
            </div>

            <div class="import-actions">
              <el-button
                type="primary"
                :loading="wordParsing"
                :disabled="!wordFile"
                @click="parseWord"
              >
                {{ wordParsing ? '解析中...' : '解析文件' }}
              </el-button>
              <el-button
                :disabled="!wordParsedQuestions.length"
                @click="exportJson"
              >
                生成 JSON
              </el-button>
            </div>
          </div>

          <!-- 文本导入（格式说明自动切换） -->
          <div v-show="fileImportMode === 'text'">
            <el-input
              v-model="wordText"
              type="textarea"
              :rows="10"
              :placeholder="textMode === 'word' ? '从 Word/Excel/PDF 复制文本粘贴到此处，然后点击解析' : '粘贴 JSON 数组到此处，然后点击解析'"
            />
            <div class="import-actions">
              <el-button
                type="primary"
                :loading="wordTextParsing"
                :disabled="!wordText || !wordText.trim()"
                @click="parseWordText"
              >
                {{ wordTextParsing ? '解析中...' : '解析内容' }}
              </el-button>
              <el-button @click="loadWordSample">加载 {{ textMode === 'word' ? 'Word' : 'JSON' }} 示例</el-button>
              <el-button
                :disabled="!wordParsedQuestions.length"
                @click="exportJson"
              >
                生成 JSON
              </el-button>
            </div>
          </div>

          <!-- 文本格式切换（仅在文本模式显示） -->
          <div v-if="fileImportMode === 'text'" style="margin-top: 8px">
            <el-radio-group v-model="textMode" size="small">
              <el-radio-button value="word">Word 文本格式</el-radio-button>
              <el-radio-button value="json">JSON 格式</el-radio-button>
            </el-radio-group>
          </div>

          <!-- 解析报告（有解析结果时显示） -->
          <div v-if="parseReport && (parseReport.successCount || parseReport.failedCount)" class="parse-report">
            <el-alert
              :type="parseReport.failedCount > 0 ? 'warning' : 'success'"
              :closable="false"
              show-icon
            >
              <template #title>
                <span style="font-weight: 600">解析报告</span>
                <span style="margin-left: 12px">
                  ✅ 成功 <strong>{{ parseReport.successCount }}</strong> 道
                </span>
                <span v-if="parseReport.failedCount > 0" style="margin-left: 12px">
                  ❌ 失败 <strong>{{ parseReport.failedCount }}</strong> 道
                </span>
                <span style="margin-left: 12px; color: var(--el-text-color-secondary); font-size: 12px">
                  成功题目已生成草稿状态，可在下方预览、编辑后再提交审核
                </span>
              </template>
              <template v-if="parseReport.failedReasons?.length" #default>
                <el-collapse>
                  <el-collapse-item title="查看失败详情">
                    <ul style="padding-left: 16px; margin: 0">
                      <li v-for="(reason, i) in parseReport.failedReasons" :key="i" style="color: var(--el-color-danger); margin: 2px 0">{{ reason }}</li>
                    </ul>
                  </el-collapse-item>
                </el-collapse>
              </template>
            </el-alert>
          </div>

          <!-- 统一预览/编辑区域：复用手动录入的可编辑卡片模板（共享 questions / expanded 数据） -->
          <div v-if="currentParsedQuestions.length && questions.length" class="word-preview">
            <el-divider content-position="left">
              解析预览（可编辑）· 共 {{ questions.length }} 题
              <span v-if="batchCourseId" style="margin-left: 12px; font-size: 12px; color: var(--el-color-success)">
                学科：{{ courseList.find(c => c.id === batchCourseId)?.courseName }}
              </span>
              <span v-else style="margin-left: 12px; font-size: 12px; color: var(--el-color-warning)">
                ⚠️ 未设置学科，请在上方选择预设学科或每题手动设置
              </span>
              <span v-if="batchScore" style="margin-left: 12px; font-size: 12px; color: var(--el-color-info)">
                预设分值：{{ batchScore }}
              </span>
              <span v-if="batchDifficulty" style="margin-left: 12px; font-size: 12px; color: var(--el-color-info)">
                预设难度：{{ difficultyLabel(batchDifficulty) }}
              </span>
            </el-divider>
            <div class="word-preview-actions-top">
              <el-button
                type="warning"
                plain
                size="small"
                :loading="aiEvaluating"
                @click="aiEvaluateParsedQuestions"
              >
                🤖 重新用AI评估全部难度
              </el-button>
              <el-button size="small" plain @click="expandAll(true)">📂 展开全部</el-button>
              <el-button size="small" plain @click="expandAll(false)">🗂 折叠全部</el-button>
              <el-button
                type="primary"
                plain
                size="small"
                :icon="Plus"
                @click="addRow"
              >
                新增一道空白题
              </el-button>
              <!-- 分批挂载进度 + 立即加载 -->
              <span v-if="mountedLimit < questions.length" style="margin-left: 8px; font-size: 12px; color: var(--el-color-info)">
                ⏳ 分批渲染中 {{ mountedLimit }} / {{ questions.length }}
                <el-button link type="primary" size="small" @click="scheduleMount(true)">立即加载全部</el-button>
              </span>
              <span v-else style="margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary)">
                已渲染 {{ questions.length }} 题 · 已展开 {{ expandedCount }} 题
                <span v-if="questions.length > 40" style="color: var(--el-color-warning); margin-left: 4px">
                  （批量题目默认折叠，需要修改时再逐题点开）
                </span>
              </span>
            </div>
            <div class="batch-list">
              <div v-for="(q, idx) in questions.slice(0, mountedLimit)" :key="idx" class="batch-item" :class="{ 'invalid-item': invalidIndex === idx }" :data-question-index="idx">
                <div class="batch-item-header" @click="toggleExpand(idx)">
                  <span class="batch-item-index">{{ idx + 1 }}</span>
                  <span class="batch-item-title">{{ q.content || '(未填写题干)' }}</span>
                  <el-tag
                    size="small"
                    :type="difficultyTagType(q.difficulty)"
                    style="margin-right: 6px"
                  >
                    {{ typeLabel(q.questionType) }} · {{ difficultyLabel(q.difficulty) }}
                  </el-tag>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    :icon="Delete"
                    @click.stop="removeRow(idx)"
                    :disabled="questions.length <= 1"
                  >
                    删除
                  </el-button>
                  <span class="batch-item-arrow">{{ expanded[idx] ? '▲' : '▼' }}</span>
                </div>

                <!-- v-if：折叠时绝不挂载 el-select/el-radio-group/el-form-item 等重控件，避免 90+ 张同卡 → 页面卡死 -->
                <div v-if="expanded[idx]" class="batch-item-body">
                  <el-row :gutter="12">
                    <el-col :span="8">
                      <el-form-item label="学科" required>
                        <el-select v-model="q.courseId" placeholder="选择学科" style="width: 100%" filterable>
                          <el-option
                            v-for="c in courseList"
                            :key="c.id"
                            :label="c.courseName"
                            :value="c.id"
                          />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="题型" required>
                        <el-select v-model="q.questionType" placeholder="选择题型" style="width: 100%" @change="(t) => handleTypeChange(idx, t)">
                          <el-option label="单选题" :value="1" />
                          <el-option label="多选题" :value="2" />
                          <el-option label="判断题" :value="3" />
                          <el-option label="填空题" :value="4" />
                          <el-option label="简答题" :value="5" />
                          <el-option label="名词解释" :value="6" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="难度" required>
                        <el-radio-group v-model="q.difficulty">
                          <el-radio-button :value="1">简单</el-radio-button>
                          <el-radio-button :value="2">中等</el-radio-button>
                          <el-radio-button :value="3">困难</el-radio-button>
                        </el-radio-group>
                      </el-form-item>
                    </el-col>
                  </el-row>

                  <el-form-item label="题干" required>
                    <el-input
                      v-model="q.content"
                      type="textarea"
                      :rows="2"
                      placeholder="请输入题干内容"
                    />
                  </el-form-item>

                  <!-- 选项编辑器（仅单选/多选） -->
                  <el-form-item
                    v-if="q.questionType === 1 || q.questionType === 2"
                    label="选项"
                    required
                  >
                    <div class="options-wrapper">
                      <div v-for="(opt, oi) in q._options" :key="oi" class="option-row">
                        <el-radio
                          v-if="q.questionType === 1"
                          v-model="q._correctIdx"
                          :value="oi"
                        >
                          {{ letter(oi) }}
                        </el-radio>
                        <el-checkbox v-else v-model="opt.correct">
                          {{ letter(oi) }}
                        </el-checkbox>
                        <el-input
                          v-model="opt.text"
                          :placeholder="`选项 ${letter(oi)} 内容`"
                          style="flex: 1"
                        />
                        <el-button
                          type="danger"
                          :icon="Delete"
                          circle
                          size="small"
                          :disabled="q._options.length <= 2"
                          @click="removeOption(idx, oi)"
                        />
                      </div>
                      <el-button type="primary" plain :icon="Plus" size="small" @click="addOption(idx)">
                        添加选项
                      </el-button>
                    </div>
                  </el-form-item>

                  <el-form-item label="答案" required>
                    <el-radio-group v-if="q.questionType === 3" v-model="q.answer">
                      <el-radio value="T">正确</el-radio>
                      <el-radio value="F">错误</el-radio>
                    </el-radio-group>
                    <el-input
                      v-else-if="q.questionType === 4"
                      v-model="q.answer"
                      placeholder="填空答案（多个空用 | 分隔）"
                    />
                    <el-input
                      v-else-if="q.questionType === 5 || q.questionType === 6"
                      v-model="q.answer"
                      type="textarea"
                      :rows="2"
                      placeholder="参考答案（支持 ①②③ 分条）"
                    />
                    <el-input
                      v-else
                      :model-value="getSelectionAnswer(q)"
                      disabled
                      placeholder="请在上方选项中选择正确答案"
                    />
                  </el-form-item>

                  <el-row :gutter="12">
                    <el-col :span="8">
                      <el-form-item label="分值" required>
                        <el-input-number v-model="q.score" :min="0.5" :max="100" :step="0.5" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="16">
                      <el-form-item label="解析">
                        <el-input
                          v-model="q.analysis"
                          type="textarea"
                          :rows="2"
                          placeholder="答案解析（选填）"
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        提交
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { Delete, Plus, Upload, Document, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { batchCreateQuestions, importFileQuestions, parseTextQuestions, aiEvaluateDifficulty, downloadQuestionTemplate } from '@/api/question'
import { getCourseList } from '@/api/course'

const props = defineProps({
  modelValue: Boolean,
  /** 预设信息（如从随机组卷跳转的学科/题型） */
  preset: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const submitting = ref(false)
const activeTab = ref('manual')
const courseList = ref([])
/** AI 难度评估加载状态 */
const aiEvaluating = ref(false)

/** 题目列表，每项是一份可序列化的表单数据 */
const questions = ref([])
/** 控制展开/折叠 */
/** 折叠展开状态（题目级，index -> true/false） */
const expanded = ref([])
const invalidIndex = ref(-1)

/**
 * 分批挂载上限：避免 90+ 道题一口气挂载 90 张编辑卡片把 Chrome 主线程卡死 30s+。
 * 默认先渲染前 BATCH_SIZE 条，每 FRAME_MS ms 再追加一批（拆到多个 Event Loop）。
 */
const BATCH_SIZE = 15
const FRAME_MS = 60
const mountedLimit = ref(BATCH_SIZE)
let mountTimer = null

/** 启动/重启分帧挂载调度器，把 mountedLimit 逐步推进到 questions.value.length */
function scheduleMount(all = false) {
  if (mountTimer) { clearTimeout(mountTimer); mountTimer = null }
  const total = questions.value.length
  if (all) {
    mountedLimit.value = total
    return
  }
  if (total <= BATCH_SIZE) {
    mountedLimit.value = total
    return
  }
  mountedLimit.value = BATCH_SIZE
  const step = () => {
    const next = Math.min(mountedLimit.value + BATCH_SIZE, total)
    mountedLimit.value = next
    if (next < total) {
      mountTimer = setTimeout(step, FRAME_MS)
    } else {
      mountTimer = null
    }
  }
  mountTimer = setTimeout(step, FRAME_MS)
}

/** 展开全部 / 折叠全部 */
function expandAll(flag) {
  expanded.value = questions.value.map(() => !!flag)
}

/** 已展开的题数 */
const expandedCount = computed(() => expanded.value.filter(Boolean).length)
/** JSON 导入文本（兼容旧字段，实际统一使用 wordText） */
const jsonText = ref('')
const jsonParsedQuestions = ref([])

/** 文件导入相关 */
const wordFile = ref(null)
/** el-upload 受控 fileList：始终只保留最后一个文件（limit=1），作为唯一事实源 */
const wordFileList = ref([])
const wordUploadRef = ref(null)
const wordParsing = ref(false)
const wordParsedQuestions = ref([])
const fileImportMode = ref('file') // 'file' | 'text'
const textMode = ref('word') // 'word' | 'json' — 文本模式下的子类型
const wordText = ref('')
const wordTextParsing = ref(false)
const batchCourseId = ref(null)
/** 预设分值（覆盖解析题目的 score） */
const batchScore = ref(2.0)
/** 预设难度（null 表示不覆盖，保留 AI 自动评估结果） */
const batchDifficulty = ref(null)
/** 解析报告（successCount / failedCount / failedReasons） */
const parseReport = ref(null)

/** 获取文件类型标签 */
function getFileTypeLabel(filename) {
  if (!filename) return '未知'
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  return {
    'docx': 'Word 文档',
    'xlsx': 'Excel 表格',
    'xls': 'Excel 表格',
    'pdf': 'PDF 文档',
    'json': 'JSON 文件',
    'png': '图片文件',
    'jpg': '图片文件',
    'jpeg': '图片文件',
    'gif': '图片文件',
    'bmp': '图片文件'
  }[ext] || ext.toUpperCase() + ' 文件'
}

/** 当前预览的题目列表（仍保留，用于顶部标题统计） */
const currentParsedQuestions = computed(() => {
  // 文件模式 + 文本模式统一使用 wordParsedQuestions
  return wordParsedQuestions.value
})

/**
 * 把后端解析结果（Question 原生对象）转成前端可编辑格式，赋值给 questions
 * - 解析 options JSON → _options 数组 [{text, correct}]
 * - 单选 answer "A" → _correctIdx = 0
 * - 多选 answer "ABD" → 给 _options[i].correct 打标
 * - 判断/填空/简答 保持 answer
 * - 保留 courseId / difficulty / score / analysis
 */
function syncParsedToEditable() {
  const source = wordParsedQuestions.value || []
  if (!source.length) return

  const imported = source.map((item) => {
    // 解析 options
    let options = []
    try {
      options = item.options ? JSON.parse(item.options) : []
    } catch {
      options = []
    }
    // 如果 options 是对象形式（[{text,correct}]）→ 兼容
    if (options.length && typeof options[0] === 'object' && options[0] !== null) {
      options = options.map((o) => o.text ?? '')
    }
    const courseId = item.courseId || batchCourseId.value || props.preset?.courseId || null
    const q = {
      courseId,
      questionType: item.questionType || 1,
      content: item.content || '',
      answer: item.answer || '',
      analysis: item.analysis || '',
      difficulty: Number(item.difficulty) || 2,
      score: Number(item.score) || 2.0,
      _options: options.map((text) => ({ text: String(text ?? ''), correct: false })),
      _correctIdx: 0
    }
    // 回显正确项：单选
    if (q.questionType === 1 && q.answer) {
      q._correctIdx = q.answer.charCodeAt(0) - 65
      if (q._correctIdx < 0 || q._correctIdx >= q._options.length) q._correctIdx = 0
    }
    // 多选
    if (q.questionType === 2 && q.answer) {
      const corrects = (q.answer || '').split('').map((s) => s.trim()).filter(Boolean)
      q._options.forEach((o, i) => {
        o.correct = corrects.includes(letter(i))
      })
    }
    // 确保至少 2 个选项
    while ((q.questionType === 1 || q.questionType === 2) && q._options.length < 2) {
      q._options.push({ text: '', correct: false })
    }
    return q
  })

  questions.value = imported
  // 默认展开策略：>40 题默认全折叠（避免一口气挂载 90+ 表单卡崩浏览器）；≤40 题默认展开
  expanded.value = imported.length > 40
    ? imported.map(() => false)
    : imported.map(() => true)
  // 分帧挂载：先画前 15 条，再每 60ms 追加 15 条
  scheduleMount(false)
}

/** 判断当前上传文件是否是弱解析格式（Word/PDF） */
const isWeakFormatFile = computed(() => {
  if (!wordFile.value) return false
  const ext = (wordFile.value.name.split('.').pop() || '').toLowerCase()
  return ['docx', 'pdf'].includes(ext)
})

/** 下载导入模板 */
async function handleTemplateDownload(type) {
  try {
    // request 拦截器对 responseType=blob 会返回原始 response（data 是 blob）
    const response = await downloadQuestionTemplate(type)
    // data 可能是 Blob（拦截器成功放行时 response.data）；也可能 response 就是 blob
    const blob = (response && response.data instanceof Blob) ? response.data
      : (response instanceof Blob ? response : null)
    if (!blob) {
      ElMessage.error('下载失败：响应格式不正确')
      return
    }
    // 从响应 headers 提取 Content-Disposition 中的文件名
    const headers = response.headers || {}
    const disposition = headers['content-disposition'] || headers['Content-Disposition'] || ''
    let filename = { excel: 'Excel文件题目模板.xlsx', json: 'JSON题目模板.json', txt: 'TXT文本题目模板.txt', word: 'Word文件题目模板.docx' }[type] || `题目模板_${type}`
    const match = disposition.match(/filename\*=UTF-8''(.+?)(?:;|$)/) || disposition.match(/filename="?(.+?)"?(?:;|$)/)
    if (match && match[1]) {
      try {
        filename = decodeURIComponent(match[1])
      } catch {
        filename = match[1]
      }
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`已下载 ${type.toUpperCase()} 模板：${filename}`)
  } catch (err) {
    console.error('模板下载失败:', err)
    const msg = err?.message || err?.msg || '模板下载失败，请检查后端是否启动'
    ElMessage.error(msg)
  }
}

/** 将预设学科/分值/难度批量应用到已解析题目 */
function applyPresetToAll() {
  if (!wordParsedQuestions.value.length) return
  let applied = 0
  wordParsedQuestions.value.forEach((q) => {
    if (batchCourseId.value) { q.courseId = batchCourseId.value; applied++ }
    if (batchScore.value) { q.score = batchScore.value; applied++ }
    if (batchDifficulty.value) { q.difficulty = batchDifficulty.value; applied++ }
  })
  // 同步刷新可编辑 questions 的值（如果用户没直接改可编辑卡片，用 wordParsedQuestions 的新值覆盖）
  if (applied > 0) syncParsedToEditable()
  ElMessage.success(`已将预设批量应用到 ${wordParsedQuestions.value.length} 道题目`)
}

// 预设学科变化时，如果已解析题目，同步更新
watch(batchCourseId, (newVal) => {
  let needSync = false
  if (wordParsedQuestions.value.length && newVal) {
    wordParsedQuestions.value.forEach((q) => { q.courseId = newVal })
    needSync = true
  }
  if (jsonParsedQuestions.value.length && newVal) {
    jsonParsedQuestions.value.forEach((q) => { q.courseId = newVal })
    needSync = true
  }
  if (needSync) syncParsedToEditable()
})

// 预设分值 / 预设难度变化 → 同步刷新可编辑卡片
watch([batchScore, batchDifficulty], () => {
  if (!wordParsedQuestions.value.length) return
  if (batchScore.value) {
    wordParsedQuestions.value.forEach((q) => { q.score = batchScore.value })
  }
  if (batchDifficulty.value) {
    wordParsedQuestions.value.forEach((q) => { q.difficulty = batchDifficulty.value })
  }
  syncParsedToEditable()
})

/** 字母映射 */
function letter(idx) {
  return String.fromCharCode(65 + idx)
}

/** 题型标签 */
function typeLabel(type) {
  const map = { 1: '单选', 2: '多选', 3: '判断', 4: '填空', 5: '简答', 6: '名词解释' }
  return map[type] || '未知'
}

/** 难度标签文字 */
function difficultyLabel(d) {
  if (d === 1) return '简单'
  if (d === 3) return '困难'
  return '中等'
}

/** 难度标签颜色 */
function difficultyTagType(d) {
  if (d === 1) return 'success'
  if (d === 3) return 'danger'
  return 'warning'
}

/** 解析 options JSON 数组为字符串数组 */
function parseOptionsArray(optionsStr) {
  try {
    return JSON.parse(optionsStr || '[]')
  } catch {
    return []
  }
}

/** 创建一道空白题目 */
function createBlankQuestion(preset = null) {
  const q = {
    courseId: preset?.courseId ?? null,
    questionType: preset?.questionType ?? 1,
    content: '',
    _options: [
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false },
      { text: '', correct: false }
    ],
    _correctIdx: 0,
    answer: '',
    analysis: '',
    difficulty: 2,
    score: 2.0
  }
  // 判断题默认答案
  if (q.questionType === 3) q.answer = 'T'
  return q
}

/** 添加一行 */
function addRow() {
  questions.value.push(createBlankQuestion(props.preset))
  expanded.value.push(true)
}

/** 删除一行 */
function removeRow(idx) {
  questions.value.splice(idx, 1)
  expanded.value.splice(idx, 1)
}

/** 清空全部 */
function clearAll() {
  questions.value = [createBlankQuestion(props.preset)]
  expanded.value = [true]
  scheduleMount(true)
}

/** 折叠切换 */
function toggleExpand(idx) {
  expanded.value[idx] = !expanded.value[idx]
}

/** 提交校验失败后定位到对应题目 */
async function focusInvalidQuestion(idx) {
  invalidIndex.value = idx
  expanded.value[idx] = true
  if (idx >= mountedLimit.value) scheduleMount(true)
  await nextTick()
  const item = [...document.querySelectorAll(`[data-question-index="${idx}"]`)]
    .find((candidate) => candidate.offsetParent !== null)
  item?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

/** 题型切换 */
function handleTypeChange(idx, type) {
  const q = questions.value[idx]
  if (type === 1 || type === 2) {
    if (!q._options || q._options.length === 0) {
      q._options = [
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false },
        { text: '', correct: false }
      ]
    }
    q._correctIdx = 0
    q.answer = ''
  } else if (type === 3) {
    q.answer = 'T'
  } else {
    q.answer = ''
  }
}

/** 添加选项 */
function addOption(idx) {
  questions.value[idx]._options.push({ text: '', correct: false })
}

/** 删除选项 */
function removeOption(idx, oi) {
  const q = questions.value[idx]
  q._options.splice(oi, 1)
  if (q.questionType === 1 && q._correctIdx >= q._options.length) {
    q._correctIdx = 0
  }
}

/** 获取选择类题的答案文本 */
function getSelectionAnswer(q) {
  if (q.questionType === 1) {
    return q._correctIdx != null ? letter(q._correctIdx) : ''
  }
  if (q.questionType === 2) {
    const selected = q._options
      .map((o, i) => (o.correct ? letter(i) : null))
      .filter(Boolean)
    return selected.join(',')
  }
  return ''
}

/** 校验单题 */
function validateOne(q, idx) {
  if (!q.courseId) return `第 ${idx + 1} 题：请选择学科`
  if (!q.questionType) return `第 ${idx + 1} 题：请选择题型`
  if (!q.content || !q.content.trim()) return `第 ${idx + 1} 题：题干不能为空`
  if (!q.difficulty) return `第 ${idx + 1} 题：请选择难度`
  if (!q.score || q.score <= 0) return `第 ${idx + 1} 题：请输入分值`

  if (q.questionType === 1 || q.questionType === 2) {
    const emptyOpt = q._options.find((o) => !o.text || !o.text.trim())
    if (emptyOpt) return `第 ${idx + 1} 题：请填写所有选项内容`
    const ans = getSelectionAnswer(q)
    if (!ans) return `第 ${idx + 1} 题：请选择正确答案`
  } else if (!q.answer || !q.answer.trim()) {
    return `第 ${idx + 1} 题：答案不能为空`
  }
  return null
}

/** 将前端数据转为后端可序列化的结构 */
function serializeOne(q) {
  const data = {
    courseId: q.courseId,
    questionType: q.questionType,
    content: q.content,
    answer: q.answer,
    analysis: q.analysis || '',
    difficulty: q.difficulty,
    score: q.score
  }
  if (q.questionType === 1 || q.questionType === 2) {
    data.options = JSON.stringify(q._options.map((o) => o.text))
    data.answer = getSelectionAnswer(q)
  } else {
    data.options = null
  }
  return data
}

/** 加载示例（根据当前文本模式） */
function loadSample() {
  if (textMode.value === 'json') {
    loadJsonSample()
  } else {
    loadWordSample()
  }
}

/** 加载示例 JSON */
function loadJsonSample() {
  wordText.value = JSON.stringify(
    [
      {
        courseId: 1,
        questionType: 1,
        content: 'Java 中用于创建对象的关键字是？',
        options: ['new', 'class', 'object', 'create'],
        answer: 'A',
        analysis: 'new 关键字用于创建对象实例',
        difficulty: 1,
        score: 2
      },
      {
        courseId: 1,
        questionType: 3,
        content: 'Java 是编译型语言。',
        options: null,
        answer: 'T',
        analysis: 'Java 源代码编译成字节码',
        difficulty: 1,
        score: 1
      },
      {
        courseId: 1,
        questionType: 5,
        content: '简述面向对象的三大特性。',
        options: null,
        answer: '封装、继承、多态',
        analysis: '',
        difficulty: 2,
        score: 5
      }
    ],
    null,
    2
  )
  fileImportMode.value = 'text'
  textMode.value = 'json'
}

/** 加载 Word 文本示例（题库导入，涵盖单选 / 多选 / 判断 / 填空 / 简答五种题型） */
function loadWordSample() {
  wordText.value = `Java 基础

1、Java 中用于创建对象的关键字是（B）
A. class
B. new
C. object
D. create
解析：new 关键字用于创建对象实例，调用构造方法。

2、以下哪些是 Java 的基本数据类型？（ACD）
A. int
B. String
C. double
D. char
E. List
解析：String 和 List 属于引用类型，int、double、char 是基本数据类型。

3、Java 是编译型语言。（对）
解析：Java 源代码先编译成字节码，再由 JVM 解释执行。

4、面向对象的三大特性包括______、继承和多态。
答案：封装
解析：面向对象三大特性：封装、继承、多态，封装是隐藏内部细节的基础特性。

5、题目：简述什么是多态，并说明其两种实现方式。
答案：
①方法重写（Override）：子类重新实现父类方法，运行时体现多态。
②方法重载（Overload）：同一个类中方法名相同、参数列表不同，编译时体现多态。
解析：多态是指同一接口有不同的实现方式，是面向对象的核心特性之一。`
  fileImportMode.value = 'text'
  textMode.value = 'word'
  ElMessage.info('已加载示例文本，点击"解析内容"即可预览')
}

/** 旧的前端 JSON 解析函数（不再使用，统一走后端 parseText type=json） */
function _unused_parseJson() {
  // 已废弃
}

/** 统一确认导入（所有格式统一使用 WordImport 逻辑） */
function confirmImport() {
  confirmWordImport()
}

/** 导出当前解析的题目为 JSON 文件 */
function exportJson() {
  const questions = currentParsedQuestions.value
  if (!questions.length) {
    ElMessage.warning('没有可导出的题目')
    return
  }
  
  // 转换为导出格式
  const exportData = questions.map((q) => {
    const item = {
      courseId: q.courseId || batchCourseId.value || null,
      questionType: q.questionType,
      content: q.content,
      answer: q.answer || '',
      analysis: q.analysis || '',
      difficulty: q.difficulty || 2,
      score: q.score || 2.0
    }
    // 选择题需要导出选项
    if (q.questionType === 1 || q.questionType === 2) {
      try {
        const opts = q.options ? JSON.parse(q.options) : []
        item.options = opts
      } catch {
        item.options = []
      }
    }
    return item
  })
  
  // 生成 JSON 文件
  const jsonStr = JSON.stringify(exportData, null, 2)
  const blob = new Blob([jsonStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  
  // 触发下载
  const link = document.createElement('a')
  link.href = url
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  link.download = `questions_${timestamp}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  
  ElMessage.success(`已导出 ${exportData.length} 道题目为 JSON 文件`)
}

/** 记录"上一次成功选入的文件签名"（name + size），用于 Element Plus on-change 同一次选择 fire 两次的去重防抖 */
const lastFileKey = ref('')
const lastFileTime = ref(0)

/**
 * 文件选择 / 变更（el-upload on-change 回调）
 * - show-file-list=false：el-upload 自己不再渲染文件列表，完全由 wordFile / wordFileList 控制展示
 * - 同一次选择 Element Plus 可能 fire on-change 两次（status='selecting' 和 status='ready'），用 name+size 防抖去重
 * - name+size 相同 + 距上次 <500ms → 视为同一次重复事件，直接丢弃，绝不出现同一个文件显示两个
 */
function handleFileChange(uploadFile /* , fileList = [] */) {
  const file = uploadFile?.raw || uploadFile
  if (!file) {
    wordFileList.value = []
    wordFile.value = null
    lastFileKey.value = ''
    return
  }
  // ===== 1. ext / size 合法性校验 =====
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  const allowedExts = ['docx', 'xlsx', 'xls', 'pdf', 'json', 'txt', 'png', 'jpg', 'jpeg', 'gif', 'bmp']
  if (!allowedExts.includes(ext)) {
    ElMessage.error(`不支持的文件格式：.${ext}。支持：docx/xlsx/xls/pdf/json/txt/png/jpg/jpeg`)
    try { wordUploadRef.value?.clearFiles?.() } catch { /* ignore */ }
    wordFileList.value = []
    wordFile.value = null
    lastFileKey.value = ''
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件过大（>20MB），请压缩后再上传')
    try { wordUploadRef.value?.clearFiles?.() } catch { /* ignore */ }
    wordFileList.value = []
    wordFile.value = null
    lastFileKey.value = ''
    return
  }

  // ===== 2. 同文件重复事件去重：同一个 name + size 且距上次 <500ms，视为 Element Plus 重复 fire，直接丢弃 =====
  const key = `${file.name}@${file.size}`
  const now = Date.now()
  if (key === lastFileKey.value && now - lastFileTime.value < 500) return
  lastFileKey.value = key
  lastFileTime.value = now

  // ===== 3. 清掉之前可能残留的，确保永远只有 1 个 =====
  try { wordUploadRef.value?.clearFiles?.() } catch { /* ignore */ }
  // 把当前上传文件塞回去（show-file-list=false 时 clearFiles 不影响我们的自定义显示，但保持组件内部干净）
  try {
    if (wordUploadRef.value?.submit) { /* auto-upload=false 时无需 submit */ }
  } catch { /* ignore */ }

  wordFile.value = file
  // 自定义展示用的 wordFileList：保持统一构造，name/uid 保证显示稳定
  wordFileList.value = [{
    name: file.name,
    size: file.size,
    raw: file,
    uid: uploadFile?.uid || (file.uid = file.uid || Date.now())
  }]
}

/** 移除 Word 文件 */
function removeWordFile() {
  try { wordUploadRef.value?.clearFiles?.() } catch { /* ignore */ }
  wordFile.value = null
  wordFileList.value = []
  wordParsedQuestions.value = []
  parseReport.value = null
  lastFileKey.value = ''
  lastFileTime.value = 0
}

/** 计算两个字符串的相似度（基于公共子串比例） */
function calcSimilarity(str1, str2) {
  if (!str1 || !str2) return 0
  const s1 = str1.toLowerCase().trim()
  const s2 = str2.toLowerCase().trim()
  // 完全包含则高相似度
  if (s1.includes(s2) || s2.includes(s1)) {
    return Math.min(s1.length, s2.length) / Math.max(s1.length, s2.length) + 0.3
  }
  // 计算最长公共子串
  const len1 = s1.length, len2 = s2.length
  const dp = Array(len1 + 1).fill(0).map(() => Array(len2 + 1).fill(0))
  let maxLen = 0
  for (let i = 1; i <= len1; i++) {
    for (let j = 1; j <= len2; j++) {
      if (s1[i - 1] === s2[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1
        maxLen = Math.max(maxLen, dp[i][j])
      }
    }
  }
  return (2 * maxLen) / (len1 + len2)
}

/** 根据标题匹配相似学科并自动选择 */
async function tryAutoMatchCourse(title) {
  if (!title || !courseList.value?.length) {
    console.log('[智能匹配] 跳过：title为空或学科列表为空', { title, courseCount: courseList.value?.length })
    return
  }
  console.log('[智能匹配] 开始匹配，标题:', title, '学科列表:', courseList.value.map(c => c.courseName))
  
  let bestMatch = null
  let bestScore = 0
  for (const course of courseList.value) {
    const score = calcSimilarity(title, course.courseName)
    console.log(`[智能匹配] "${title}" vs "${course.courseName}" = ${score.toFixed(3)}`)
    if (score > bestScore) {
      bestScore = score
      bestMatch = course
    }
  }
  
  console.log(`[智能匹配] 最佳匹配: ${bestMatch?.courseName}, 分数: ${bestScore.toFixed(3)}`)
  
  // 相似度超过 0.4 则认为匹配成功（降低阈值提高命中率）
  if (bestMatch && bestScore > 0.4) {
    try {
      await ElMessageBox.confirm(
        `检测到文档标题「${title}」与学科「${bestMatch.courseName}」相似，是否自动选择该学科？`,
        '智能匹配',
        { confirmButtonText: '是，自动选择', cancelButtonText: '否，手动选择', type: 'info' }
      )
      batchCourseId.value = bestMatch.id
      // 同步设置已解析题目的 courseId
      if (wordParsedQuestions.value.length) {
        wordParsedQuestions.value.forEach((q) => { q.courseId = bestMatch.id })
      }
      ElMessage.success(`已自动选择学科：${bestMatch.courseName}`)
    } catch {
      // 用户取消，不做任何操作
    }
  } else {
    // 没有找到足够相似的学科时，提示用户
    ElMessage.info(`标题「${title}」未能匹配到已有学科，请手动选择`)
  }
}

/** 解析文件 */
async function parseWord() {
  if (!wordFile.value) return
  const courseId = batchCourseId.value || props.preset?.courseId
  try {
    wordParsing.value = true
    const res = await importFileQuestions(wordFile.value, courseId)
    console.log('[解析文件] 后端返回:', res)
    const data = res.data || {}
    const questions = Array.isArray(data) ? data : (data.questions || [])
    const title = Array.isArray(data) ? '' : (data.title || '')
    // 解析报告字段（后端 WordParseResult 扩展）
    const successCount = data.successCount ?? questions.length
    const failedCount = data.failedCount ?? 0
    const failedReasons = data.failedReasons || []

    wordParsedQuestions.value = questions
    // 保存解析报告
    parseReport.value = { successCount, failedCount, failedReasons }

    // 应用预设分值/难度（如果有设置）
    if (batchScore.value) {
      wordParsedQuestions.value.forEach((q) => { q.score = batchScore.value })
    }
    if (batchDifficulty.value) {
      wordParsedQuestions.value.forEach((q) => { q.difficulty = batchDifficulty.value })
    }

    if (courseId) {
      wordParsedQuestions.value.forEach((q) => { q.courseId = courseId })
    }

    // 汇总提示
    let summaryMsg = `解析完成：成功 ${successCount} 道`
    if (failedCount > 0) summaryMsg += `，失败 ${failedCount} 道`
    summaryMsg += '，成功题目已生成草稿状态'
    if (failedCount > 0 && isWeakFormatFile.value) {
      summaryMsg += '（Word/PDF 为弱解析格式，建议使用 Excel/JSON/TXT 模板以获得更好的解析效果）'
    }

    if (courseId) {
      ElMessage.success(summaryMsg)
    } else if (title) {
      ElMessage.success(summaryMsg)
      tryAutoMatchCourse(title)
    } else {
      ElMessage.info(summaryMsg + '，请为题目设置学科')
    }

    // === 解析结果转可编辑卡片，供用户直接编辑 ===
    syncParsedToEditable()
  } catch (err) {
    console.error('文件解析失败:', err)
    const rawMsg = err?.response?.data?.msg || err?.message || err?.msg || '解析失败'
    let tip = String(rawMsg)
    if (tip.includes('timeout') || tip.includes('超时') || err?.code === 'ECONNABORTED') {
      tip = '⏱ 解析超时。① 请确认后端已执行 mvn clean compile 并重启 Spring Boot；② 大模型服务可能网络不通（系统已自动降级，5 秒内会出结果，本次重试即可）；③ 文件过大请拆分成多个小文件。'
    } else if (tip.includes('Network Error') || err?.code === 'ERR_NETWORK') {
      tip = '❌ 网络错误：请确认后端服务已启动（默认端口）并检查浏览器控制台 /api/question/importFile 请求状态。'
    } else if (tip.includes('500') || tip.includes('Internal Server Error')) {
      tip += '（请查看后端控制台日志以获得具体堆栈）'
    }
    ElMessage.error(tip)
  } finally {
    wordParsing.value = false
  }
}

/** 解析粘贴的文本（Word 或 JSON） */
async function parseWordText() {
  if (!wordText.value || !wordText.value.trim()) return
  const courseId = batchCourseId.value || props.preset?.courseId
  try {
    wordTextParsing.value = true
    const type = textMode.value // 'word' | 'json'
    const res = await parseTextQuestions(type, wordText.value, courseId)
    console.log('[解析文本] 后端返回:', res)
    const data = res.data || {}
    console.log('[解析文本] data:', data, 'title:', data.title, 'questions count:', data.questions?.length)

    // 兼容处理：可能是旧格式（直接返回数组）或新格式（返回对象）
    const questions = Array.isArray(data) ? data : (data.questions || [])
    const title = Array.isArray(data) ? '' : (data.title || '')
    // 解析报告字段
    const successCount = Array.isArray(data) ? questions.length : (data.successCount ?? questions.length)
    const failedCount = Array.isArray(data) ? 0 : (data.failedCount ?? 0)
    const failedReasons = Array.isArray(data) ? [] : (data.failedReasons || [])

    wordParsedQuestions.value = questions
    parseReport.value = { successCount, failedCount, failedReasons }

    // 应用预设分值/难度
    if (batchScore.value) {
      wordParsedQuestions.value.forEach((q) => { q.score = batchScore.value })
    }
    if (batchDifficulty.value) {
      wordParsedQuestions.value.forEach((q) => { q.difficulty = batchDifficulty.value })
    }

    if (courseId) {
      wordParsedQuestions.value.forEach((q) => { q.courseId = courseId })
    }

    // 汇总提示
    let summaryMsg = `解析完成：成功 ${successCount} 道`
    if (failedCount > 0) summaryMsg += `，失败 ${failedCount} 道`
    summaryMsg += '，成功题目已生成草稿状态'

    if (courseId) {
      ElMessage.success(summaryMsg)
    } else if (title) {
      ElMessage.success(summaryMsg)
      // 尝试自动匹配学科
      tryAutoMatchCourse(title)
    } else {
      ElMessage.info(summaryMsg + '，请为题目设置学科')
    }

    // === 解析结果转可编辑卡片，供用户直接编辑 ===
    syncParsedToEditable()
  } catch (err) {
    console.error('文本解析失败:', err)
    const rawMsg = err?.response?.data?.msg || err?.message || err?.msg || '解析失败'
    let tip = String(rawMsg)
    if (tip.includes('timeout') || tip.includes('超时') || err?.code === 'ECONNABORTED') {
      tip = '⏱ 解析超时。① 请确认后端已执行 mvn clean compile 并重启 Spring Boot；② 大模型服务可能网络不通（系统已自动降级，5 秒内会出结果，本次重试即可）；③ 内容过大请分批粘贴。'
    } else if (tip.includes('Network Error') || err?.code === 'ERR_NETWORK') {
      tip = '❌ 网络错误：请确认后端服务已启动（默认端口）并检查浏览器控制台 /api/question/parseText 请求状态。'
    } else if (tip.includes('500') || tip.includes('Internal Server Error')) {
      tip += '（请查看后端控制台日志以获得具体堆栈）'
    }
    ElMessage.error(tip)
  } finally {
    wordTextParsing.value = false
  }
}

/** 确认导入 Word 解析结果到批量列表 */
async function confirmWordImport() {
  if (!wordParsedQuestions.value.length) return

  // 将后端返回的 Question 对象转换为前端可编辑的格式
  const imported = wordParsedQuestions.value.map((item) => {
    // 解析 options
    let options = []
    try {
      options = item.options ? JSON.parse(item.options) : []
    } catch {
      options = []
    }
    // 优先使用题目自带的 courseId，否则使用批量设置的学科
    const courseId = item.courseId || batchCourseId.value || null
    const q = {
      courseId,
      questionType: item.questionType,
      content: item.content,
      answer: item.answer || '',
      analysis: item.analysis || '',
      difficulty: item.difficulty || 2,
      score: item.score || 2.0,
      _options: options.map((text) => ({ text, correct: false })),
      _correctIdx: 0
    }
    // 根据 answer 回显正确项
    if (item.questionType === 1 && item.answer) {
      q._correctIdx = item.answer.charCodeAt(0) - 65
    } else if (item.questionType === 2 && item.answer) {
      // 后端返回 "ABD" 格式，拆分为 ['A', 'B', 'D']
      const corrects = (item.answer || '').split('').map((s) => s.trim()).filter(Boolean)
      q._options.forEach((o, i) => {
        o.correct = corrects.includes(letter(i))
      })
    }
    return q
  })

  // 检查不完整的题目
  const incompleteList = []
  imported.forEach((q, idx) => {
    const reasons = []
    if (!q.content || !q.content.trim()) reasons.push('题干为空')
    if (!q.answer || !q.answer.trim()) reasons.push('答案为空')
    if (q.questionType === 1 || q.questionType === 2) {
      if (q._options.length < 2) reasons.push('选项不足')
      else if (q._options.some((o) => !o.text || !o.text.trim())) reasons.push('选项有空值')
    }
    if (reasons.length > 0) {
      incompleteList.push({ index: idx, reasons, content: q.content || '(无题干)' })
    }
  })

  // 如果有不完整的题目，弹出确认框
  if (incompleteList.length > 0) {
    try {
      const listHtml = incompleteList
        .map((item, i) => {
          const reasonsText = item.reasons.join('、')
          const contentPreview = item.content.length > 20 ? item.content.slice(0, 20) + '...' : item.content
          return `${i + 1}. 第${item.index + 1}题「${contentPreview}」：${reasonsText}`
        })
        .join('<br>')

      await ElMessageBox.confirm(
        `检测到 <strong>${incompleteList.length}</strong> 道题目不完整：<br><br>${listHtml}<br><br>` +
        `点击 <strong>确定</strong> 删除不完整题目，保留 ${imported.length - incompleteList.length} 道完整题目；<br>` +
        `点击 <strong>取消</strong> 保留所有题目（需手动补充）。`,
        '题目不完整提示',
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: '删除不完整题目',
          cancelButtonText: '保留全部（手动补充）',
          type: 'warning'
        }
      )
      // 用户选择删除不完整题目
      const validImported = imported.filter((_, idx) => !incompleteList.some((i) => i.index === idx))
      questions.value = validImported
      expanded.value = validImported.length > 40 ? validImported.map(() => false) : validImported.map(() => true)
      scheduleMount(false)
      ElMessage.info(`已删除 ${incompleteList.length} 道不完整题目，保留 ${validImported.length} 题`)
    } catch {
      // 用户选择保留所有题目
      questions.value = imported
      expanded.value = imported.length > 40 ? imported.map(() => false) : imported.map(() => true)
      scheduleMount(false)
      ElMessage.info(`已保留 ${imported.length} 题，其中 ${incompleteList.length} 题不完整，请手动补充`)
    }
  } else {
    // 所有题目完整，直接导入
    questions.value = imported
    expanded.value = imported.length > 40 ? imported.map(() => false) : imported.map(() => true)
    scheduleMount(false)
    ElMessage.success(`已导入 ${imported.length} 题，请检查后提交`)
  }

  activeTab.value = 'manual'
  wordParsedQuestions.value = []
  try { wordUploadRef.value?.clearFiles?.() } catch { /* ignore */ }
  wordFile.value = null
  wordFileList.value = []
  lastFileKey.value = ''
  lastFileTime.value = 0

  const noCourseCount = questions.value.filter((q) => !q.courseId).length
  if (noCourseCount > 0 && incompleteList.length === 0) {
    ElMessage.warning(`其中 ${noCourseCount} 题未设置学科，请补充后提交`)
  }
}

// ==================== AI 难度评估 ====================

/**
 * 将前端题目数据（含 _options/_correctIdx 等私有字段）转换为后端 Question 格式
 * 用于发送给 AI 评估接口
 */
function toBackendQuestion(q) {
  const courseName = courseList.value?.find((c) => c.id === q.courseId)?.courseName || ''
  const data = {
    courseId: q.courseId || null,
    courseName,
    questionType: q.questionType || 1,
    content: q.content || '',
    answer: q.answer || '',
    analysis: q.analysis || ''
  }
  // 选择类题：把 _options 数组转成 JSON 字符串（与后端一致）
  if (q.questionType === 1 || q.questionType === 2) {
    const opts = (q._options || []).map((o) => (o && o.text ? o.text : ''))
    data.options = JSON.stringify(opts)
  } else {
    data.options = null
  }
  return data
}

/**
 * 手动录入批量列表：一键AI评估所有题目的难度
 */
async function aiEvaluateAll() {
  if (!questions.value.length) {
    ElMessage.warning('当前没有题目')
    return
  }
  // 过滤出有题干的题目（避免无内容的空题污染请求）
  const valid = questions.value.filter((q) => q.content && q.content.trim())
  if (!valid.length) {
    ElMessage.warning('请先填写题干内容，再进行AI评估')
    return
  }
  try {
    aiEvaluating.value = true
    const payload = questions.value.map(toBackendQuestion)
    const results = await evaluateDifficultyByBatches(payload)
    let updateCount = 0
    for (let i = 0; i < questions.value.length && i < results.length; i++) {
      const r = results[i]
      if (r && r.difficulty) {
        questions.value[i].difficulty = r.difficulty
        updateCount++
      }
    }
    const llmCount = results.filter((r) => r && r.source === 'llm').length
    const modeMsg = llmCount > 0
      ? `大模型${llmCount}题 / 本地规则${results.length - llmCount}题`
      : '本地规则引擎（配置 API Key 可启用大模型更精准评估）'
    ElMessage.success(`已更新 ${updateCount} 道题的难度（${modeMsg}），您仍可手动微调`)
  } catch (err) {
    console.error('AI难度评估失败:', err)
    const msg = err?.response?.data?.msg || err?.message || '评估失败'
    ElMessage.error('AI难度评估失败：' + msg)
  } finally {
    aiEvaluating.value = false
  }
}

// AI 难度评估接口单次最多 200 道（后端 QuestionController 限制），这里自动分批评估，
// 返回顺序与输入 payload 完全一致的合并结果，避免大批量题目被一次请求拒绝。
// 每批取 50 道：后端会再按 10 题一批调大模型，50 题约 5 次调用、耗时几十秒内，低于前端 60s 超时
const AI_EVAL_BATCH_SIZE = 50
async function evaluateDifficultyByBatches(payload) {
  const results = []
  for (let i = 0; i < payload.length; i += AI_EVAL_BATCH_SIZE) {
    const batch = payload.slice(i, i + AI_EVAL_BATCH_SIZE)
    const res = await aiEvaluateDifficulty(batch)
    const data = res.data || []
    results.push(...data)
    // 长度不足时补 null，保证 results 与 payload 下标一一对应，避免后续题目错位
    for (let j = data.length; j < batch.length; j++) results.push(null)
  }
  return results
}

/**
 * 文件导入预览区：重新用AI评估已解析题目的难度
 * （后端已在解析时自动评估一次，此方法允许用户不满意时重新评估）
 */
async function aiEvaluateParsedQuestions() {
  if (!wordParsedQuestions.value.length) return
  try {
    aiEvaluating.value = true
    // 解析结果中的 Question 字段名已与后端一致（options 为 JSON 字符串），补充 courseName
    const payload = wordParsedQuestions.value.map((q) => {
      const courseName = courseList.value?.find((c) => c.id === (q.courseId || batchCourseId.value))?.courseName || ''
      return {
        courseId: q.courseId || batchCourseId.value || null,
        courseName,
        questionType: q.questionType || 1,
        content: q.content || '',
        options: q.options || null,
        answer: q.answer || '',
        analysis: q.analysis || ''
      }
    })
    const results = await evaluateDifficultyByBatches(payload)
    for (let i = 0; i < wordParsedQuestions.value.length && i < results.length; i++) {
      const r = results[i]
      if (r && r.difficulty) {
        wordParsedQuestions.value[i].difficulty = r.difficulty
      }
    }
    const llmCount = results.filter((r) => r && r.source === 'llm').length
    const modeMsg = llmCount > 0
      ? `大模型${llmCount}题 / 本地规则${results.length - llmCount}题`
      : '本地规则引擎（配置 API Key 可启用大模型更精准评估）'
    ElMessage.success(`已重新评估 ${results.length} 道题难度（${modeMsg}）`)
  } catch (err) {
    console.error('AI难度重新评估失败:', err)
    const msg = err?.response?.data?.msg || err?.message || '评估失败'
    ElMessage.error('AI难度评估失败：' + msg)
  } finally {
    aiEvaluating.value = false
  }
}

/** 提交：两个 Tab 共享 questions 数组，统一走 validateOne + serializeOne
 *  - 文件导入 Tab（解析结果）：导入后草稿状态（auditStatus = 0），不自动提交审核
 *  - 手动录入 Tab：提交审核（由后端 QuestionController.batch 默认行为控制，这里也显式写 0 作草稿）
 */
async function handleSubmit() {
  if (!questions.value.length) {
    ElMessage.warning('请至少添加一道题目')
    return
  }
  // 校验
  for (let i = 0; i < questions.value.length; i++) {
    const err = validateOne(questions.value[i], i)
    if (err) {
      ElMessage.error(err)
      await focusInvalidQuestion(i)
      return
    }
  }
  // 如果文件导入 Tab，auditStatus 固定写 0（草稿），不自动提交审核
  const asDraft = activeTab.value === 'file'
  const payload = questions.value.map((q) => ({ ...serializeOne(q), auditStatus: asDraft ? 0 : (serializeOne(q).auditStatus ?? 0) }))
  try {
    submitting.value = true
    const res = await batchCreateQuestions(payload)
    const ids = res.data || []
    const questionsWithIds = ids.map((id, idx) => ({
      id,
      title: payload[idx]?.content?.slice(0, 30) || '（无题干）',
      payload: payload[idx]
    }))
    if (asDraft) {
      ElMessage.success(`✅ 成功导入 ${ids.length} 道题目（草稿状态），请在「我的试题」中编辑后再批量提交审核`)
    } else {
      ElMessage.success(`成功批量新增 ${ids.length} 道题目（草稿状态），请在「我的试题」中提交审核`)
    }
    emit('success', { ids, count: ids.length, questions: questionsWithIds })
    emit('update:modelValue', false)
  } catch (err) {
    console.error('批量提交失败:', err)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  getCourseList()
    .then((res) => {
      courseList.value = res.data || []
    })
    .catch((err) => console.error('获取学科列表失败:', err))

  // 初始化为一道空白题
  questions.value = [createBlankQuestion(props.preset)]
  expanded.value = [true]
  scheduleMount(true)
})

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      // 打开时重置
      questions.value = [createBlankQuestion(props.preset)]
      expanded.value = [true]
      scheduleMount(true)
      activeTab.value = 'manual'
      jsonText.value = ''
      jsonParsedQuestions.value = []
      // 文件上传：同时清 wordFile / wordFileList / el-upload 内部文件列表
      try { wordUploadRef.value?.clearFiles?.() } catch { /* ignore */ }
      wordFile.value = null
      wordFileList.value = []
      lastFileKey.value = ''
      lastFileTime.value = 0
      wordParsedQuestions.value = []
      parseReport.value = null
      batchCourseId.value = props.preset?.courseId || null
      fileImportMode.value = 'file'
      textMode.value = 'word'
      wordText.value = ''
      wordTextParsing.value = false
    }
  }
)
</script>

<style scoped>
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.total-count {
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.batch-list {
  max-height: 50vh;
  overflow-y: auto;
}

.batch-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  margin-bottom: 10px;
  overflow: hidden;
}

.batch-item.invalid-item {
  border-color: var(--el-color-danger);
  box-shadow: 0 0 0 2px var(--el-color-danger-light-8);
}

.batch-item-header {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  cursor: pointer;
  gap: 8px;
}

.batch-item-index {
  font-weight: 600;
  color: var(--el-color-primary);
  min-width: 28px;
}

.batch-item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.batch-item-arrow {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.batch-item-body {
  padding: 12px;
}

.options-wrapper {
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.option-row :deep(.el-input) {
  flex: 1;
}

.json-import {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.json-tip {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.json-tip code {
  background: var(--el-fill-color);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
}

.json-actions {
  display: flex;
  gap: 8px;
}

/* 文件导入样式 */
.file-import {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.file-mode-switch {
  margin-bottom: 8px;
}

.import-tips {
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  padding: 12px 16px;
  font-size: 13px;
  line-height: 1.8;
}

.import-tips ol {
  padding-left: 20px;
  margin: 4px 0;
}

.import-tips li {
  margin: 2px 0;
}

.import-tips code {
  background: var(--el-bg-color);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 12px;
}

.tips-title {
  font-weight: 500;
  margin: 0 0 8px 0;
  color: var(--el-text-color-primary);
}

.weak-format-warn {
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.parse-report {
  margin-top: 4px;
}

.parse-report :deep(.el-collapse-item__content) {
  max-height: 300px;
  overflow-y: auto;
}

.word-uploader {
  width: 100%;
}

.word-file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.word-file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-actions {
  display: flex;
  gap: 8px;
}

.import-tip-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.word-preview-list {
  max-height: 300px;
  overflow-y: auto;
}

.word-preview-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 8px;
}

.word-preview-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.word-preview-score {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.word-preview-content {
  font-weight: 500;
  margin-bottom: 6px;
  line-height: 1.6;
}

.word-preview-options {
  padding-left: 8px;
}

.word-preview-option {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
}

.word-preview-meta {
  display: flex;
  gap: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 6px;
  flex-wrap: wrap;
  white-space: pre-line;
}

.word-preview-actions {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}

.word-preview-actions-top {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
</style>
