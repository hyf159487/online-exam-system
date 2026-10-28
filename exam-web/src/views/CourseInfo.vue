<template>
  <div v-loading="loading" class="course-info-page">
    <!-- 顶部：返回列表（管理端 → 课程列表，教师端 → 我的课程） -->
    <div class="page-header">
      <el-button link type="primary" class="back-btn" @click="goBack">← {{ backText }}</el-button>
      <span class="page-sub">{{ pageSub }}</span>
    </div>

    <template v-if="course">
      <!-- 课程基础信息：封面 + 概况 + 编辑入口 -->
      <el-card shadow="never" class="base-card">
        <div class="base">
          <div class="base__cover">
            <img v-if="course.cover" :src="course.cover" :alt="course.courseName" />
            <span v-else class="base__cover-text">{{ (course.courseName || '课').slice(0, 1) }}</span>
          </div>

          <div class="base__info">
            <div class="base__title-row">
              <h2 class="base__title">{{ course.courseName }}</h2>
              <el-tag v-if="course.courseType" type="info" effect="plain">{{ course.courseType }}</el-tag>
              <el-tag effect="light">{{ course.courseCode || '未设编码' }}</el-tag>
            </div>

            <p class="base__desc">{{ course.description || '该课程暂无简介' }}</p>

            <div class="base__meta">
              <div class="meta-item">
                <span class="meta-label">任课教师</span>
                <span class="meta-value">{{ course.teacherNames || '暂未安排' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">课程编码</span>
                <span class="meta-value">{{ course.courseCode || '-' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">开课时间</span>
                <span class="meta-value">{{ formatTime(course.startTime) || '长期开放' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">报名截止</span>
                <span class="meta-value">{{ formatTime(course.enrollDeadline) || '长期开放' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">创建时间</span>
                <span class="meta-value">{{ formatTime(course.createTime) || '-' }}</span>
              </div>
            </div>

            <!-- 任课教师联系卡：悬停查看账号与联系方式 -->
            <div v-if="teachers.length" class="teacher-chips">
              <span class="teacher-chips__label">任课教师</span>
              <el-popover v-for="t in teachers" :key="t.id" trigger="hover" placement="top" width="240">
                <template #reference>
                  <span class="teacher-chip">{{ t.realName || t.username }}</span>
                </template>
                <div class="teacher-card">
                  <div class="teacher-card__name">{{ t.realName || t.username }}</div>
                  <div class="teacher-card__row">登录账号：{{ t.username || '-' }}</div>
                  <div class="teacher-card__row">手机号：{{ t.phone || '未填写' }}</div>
                  <div class="teacher-card__row">邮箱：{{ t.email || '未填写' }}</div>
                </div>
              </el-popover>
            </div>
          </div>

          <div class="base__ops">
            <el-button type="primary" :icon="Edit" @click="openEditDialog">编辑课程信息</el-button>
            <el-button
              v-for="entry in quickEntries"
              :key="entry.label"
              plain
              @click="router.push(entry.to)"
            >{{ entry.label }}</el-button>
          </div>
        </div>

        <!-- 课程建设数据：与「课程详情」口径一致 -->
        <div class="stat-row">
          <div class="stat-card stat-blue">
            <div class="stat-num">{{ stats.questionCount }}</div>
            <div class="stat-label">试题数</div>
          </div>
          <div class="stat-card stat-purple">
            <div class="stat-num">{{ stats.paperCount }}</div>
            <div class="stat-label">试卷数</div>
          </div>
          <div class="stat-card stat-green">
            <div class="stat-num">{{ stats.examCount }}</div>
            <div class="stat-label">考试数</div>
          </div>
          <div class="stat-card stat-orange">
            <div class="stat-num">{{ stats.knowledgeCount }}</div>
            <div class="stat-label">知识点</div>
          </div>
        </div>
      </el-card>

      <!-- 课程内容：介绍 / 资料 / 作业 / 试卷 / 考试 / 知识点，与学生端「课程详情」同源，均按本课程过滤 -->
      <el-card shadow="never" class="content-card">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <!-- Tab1 课程介绍：详细介绍 / 学习目标 / 适用人群 / 课程大纲（学生端课程详情 Tab1 的数据源） -->
          <el-tab-pane label="课程介绍" name="intro">
            <div class="tab-toolbar">
              <span class="tab-tip">这 4 项内容会展示在学生端「课程详情 → 课程介绍」</span>
              <el-button type="primary" plain size="small" :icon="Edit" @click="openEditDialog">编辑</el-button>
            </div>

            <div class="intro-wrap">
              <!-- 课程类型：与卡片角标、标题后的标签同源，学生端「课程介绍」顶部同样展示 -->
              <div class="intro-type">
                <span class="intro-type__label">课程类型</span>
                <el-tag :type="course.courseType ? 'primary' : 'info'" effect="light" size="small">
                  {{ courseTypeText }}
                </el-tag>
              </div>

              <template v-if="hasIntroContent">
                <div v-if="course.intro" class="intro-block">
                  <h4 class="intro-block__title">课程介绍</h4>
                  <p class="intro-block__text">{{ course.intro }}</p>
                </div>
                <div v-if="course.learningObjectives" class="intro-block">
                  <h4 class="intro-block__title">学习目标</h4>
                  <p class="intro-block__text">{{ course.learningObjectives }}</p>
                </div>
                <div v-if="course.targetAudience" class="intro-block">
                  <h4 class="intro-block__title">适用人群</h4>
                  <p class="intro-block__text">{{ course.targetAudience }}</p>
                </div>
                <div v-if="syllabusLines.length" class="intro-block">
                  <h4 class="intro-block__title">课程大纲</h4>
                  <ol class="syllabus">
                    <li v-for="(line, idx) in syllabusLines" :key="idx">{{ line }}</li>
                  </ol>
                </div>
              </template>

              <!-- 未填写详细介绍时保留空态引导，课程类型仍可见 -->
              <el-empty v-else :image-size="90" description="该课程暂未填写课程介绍，点击「编辑」补充" />
            </div>
          </el-tab-pane>

          <!-- Tab2 课程资料：与「学习资料管理」同源，学生端在「课程详情 → 课程资料」查看 -->
          <el-tab-pane label="课程资料" name="resource">
            <div class="tab-toolbar">
              <span class="tab-tip">
                <template v-if="tabLoaded.resource">共 {{ tabTotal.resource }} 份资料，</template>学生加入课程后可在「课程详情 → 课程资料」在线预览与下载
              </span>
              <el-button link type="primary" @click="refreshTab">刷新</el-button>
              <el-button type="primary" plain size="small" @click="openManage('resource')">资料管理</el-button>
            </div>

            <el-table v-loading="tabLoading.resource" :data="tabData.resource" class="tab-table" stripe border
              max-height="420" empty-text="该课程暂无学习资料">
              <el-table-column label="资料名称" width="220">
                <template #default="{ row }">
                  <el-tooltip :content="resourceFullName(row)" placement="top" :show-after="200">
                    <span class="cell-name">
                      {{ row.resourceName }}<span v-if="row.description" class="cell-desc">（{{ row.description }}）</span>
                    </span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="100" align="center">
                <template #default="{ row }">{{ resourceTypeText(row.fileType) }}</template>
              </el-table-column>
              <el-table-column prop="fileSizeText" label="大小" width="90" align="center" />
              <el-table-column label="上传教师" width="110" align="center">
                <template #default="{ row }">{{ row.uploadTeacherName || '-' }}</template>
              </el-table-column>
              <el-table-column label="浏览/下载" width="100" align="center">
                <template #default="{ row }">{{ row.viewCount || 0 }} / {{ row.downloadCount || 0 }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="上传时间" min-width="185" align="center" class-name="nowrap-cell" />
              <el-table-column label="操作" width="120" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.fileType === 'link'" type="primary" link @click="openResourceLink(row)">去学习</el-button>
                  <template v-else>
                    <el-button v-if="row.fileType === 'pdf'" type="primary" link @click="previewResource(row)">预览</el-button>
                    <el-button type="primary" link @click="downloadResource(row)">下载</el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab3 课程作业：与「发布作业」同源，管理员端只读（作业由任课教师发布与批改） -->
          <el-tab-pane label="课程作业" name="homework">
            <div class="tab-toolbar">
              <span class="tab-tip">
                <template v-if="isTeacher">
                  <template v-if="tabLoaded.homework">共 {{ tabTotal.homework }} 份作业，</template>学生加入课程后可在「作业列表」在线作答或下载附件
                </template>
                <template v-else>作业由任课教师在教师端发布与批改，管理员端仅可查看本课程作业清单</template>
              </span>
              <el-button link type="primary" @click="refreshTab">刷新</el-button>
              <!-- 作业管理（新增 / 编辑 / 发布 / 批改）属教师业务：管理员端只读查看本课程作业 -->
              <el-button v-if="isTeacher" type="primary" plain size="small" @click="openManage('homework')">发布 / 管理作业</el-button>
            </div>

            <el-table v-loading="tabLoading.homework" :data="tabData.homework" class="tab-table" stripe border
              max-height="420" empty-text="该课程暂无作业">
              <el-table-column label="作业名称" width="220">
                <template #default="{ row }">
                  <el-tooltip :content="homeworkFullName(row)" placement="top" :show-after="200">
                    <span class="cell-name">
                      {{ row.paperName }}<span v-if="row.description" class="cell-desc">（{{ row.description }}）</span>
                    </span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column label="作业形式" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.attachmentPath ? 'warning' : 'primary'" effect="plain">
                    {{ row.attachmentPath ? '附件作业' : '在线作业' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="题目数" width="90" align="center">
                <template #default="{ row }">{{ row.questionCount || '—' }}</template>
              </el-table-column>
              <el-table-column label="总分" width="90" align="center">
                <template #default="{ row }">{{ row.totalScore != null ? row.totalScore : '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="homeworkStatusTag(row.status)" effect="light">
                    {{ homeworkStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="已练人数" width="100" align="center">
                <template #default="{ row }">{{ row.practiceUserCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="创建教师" width="110" align="center">
                <template #default="{ row }">{{ row.createTeacherName || '-' }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="创建时间" min-width="185" align="center" class-name="nowrap-cell" />
            </el-table>
          </el-tab-pane>

          <!-- Tab4 课程试卷：与「试卷管理」同源（管理员端试卷管理已下线，仅可查看） -->
          <el-tab-pane label="课程试卷" name="paper">
            <div class="tab-toolbar">
              <span class="tab-tip">
                <template v-if="isTeacher">
                  <template v-if="tabLoaded.paper">共 {{ tabTotal.paper }} 张试卷，</template>在「试卷管理」中组卷、发布后即可安排考试
                </template>
                <template v-else>试卷由任课教师在教师端维护，管理员端仅可查看本课程试卷清单</template>
              </span>
              <el-button link type="primary" @click="refreshTab">刷新</el-button>
              <!-- 试卷管理（组卷 / 发布 / 归档）属教师业务：管理员端只读查看本课程试卷 -->
              <el-button v-if="isTeacher" type="primary" plain size="small" @click="openManage('paper')">试卷管理</el-button>
            </div>

            <el-table v-loading="tabLoading.paper" :data="tabData.paper" class="tab-table" stripe border
              max-height="420" empty-text="该课程暂无试卷">
              <el-table-column prop="paperName" label="试卷名称" width="220" show-overflow-tooltip />
              <el-table-column label="组卷方式" width="110" align="center">
                <template #default="{ row }">{{ paperTypeText(row.paperType) }}</template>
              </el-table-column>
              <el-table-column label="题目数" width="90" align="center">
                <template #default="{ row }">{{ row.questionCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="总分" width="90" align="center">
                <template #default="{ row }">{{ row.totalScore != null ? row.totalScore : '-' }}</template>
              </el-table-column>
              <el-table-column label="时长" width="90" align="center">
                <template #default="{ row }">{{ row.duration }} 分钟</template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="paperStatusTag(row.status)" effect="light">
                    {{ paperStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="创建人" width="110" align="center">
                <template #default="{ row }">{{ row.creatorName || '-' }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="创建时间" min-width="185" align="center" class-name="nowrap-cell" />
            </el-table>
          </el-tab-pane>

          <!-- Tab5 课程考试：与「考试管理」同源 -->
          <el-tab-pane label="课程考试" name="exam">
            <div class="tab-toolbar">
              <span class="tab-tip">
                <template v-if="isTeacher">
                  <template v-if="tabLoaded.exam">共 {{ tabTotal.exam }} 场考试，</template>考试发布后学生可在「我的考试」进入作答
                </template>
                <template v-else>考试由任课教师安排与发布，管理员端仅可查看本课程考试清单</template>
              </span>
              <el-button link type="primary" @click="refreshTab">刷新</el-button>
              <!-- 考试管理（安排 / 发布 / 监控）属教师业务：管理员端只读查看本课程考试 -->
              <el-button v-if="isTeacher" type="primary" plain size="small" @click="openManage('exam')">考试管理</el-button>
            </div>

            <el-table v-loading="tabLoading.exam" :data="tabData.exam" class="tab-table" stripe border
              max-height="420" empty-text="该课程暂无考试">
              <el-table-column prop="examName" label="考试名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="paperName" label="试卷" min-width="160" show-overflow-tooltip />
              <el-table-column label="题数" width="80" align="center">
                <template #default="{ row }">{{ row.questionCount || '-' }}</template>
              </el-table-column>
              <el-table-column label="总分" width="80" align="center">
                <template #default="{ row }">{{ row.totalScore || '-' }}</template>
              </el-table-column>
              <el-table-column label="时长" width="90" align="center">
                <template #default="{ row }">{{ row.duration }} 分钟</template>
              </el-table-column>
              <el-table-column label="考试时间" min-width="300" class-name="nowrap-cell">
                <template #default="{ row }">
                  {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="examStatusTag(row)" effect="light">{{ examStatusText(row) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab6 课程知识点：与「知识点管理」同源（知识点写入仅管理员，教师可查看） -->
          <el-tab-pane label="课程知识点" name="knowledge">
            <div class="tab-toolbar">
              <span class="tab-tip">
                <template v-if="tabLoaded.knowledge">共 {{ tabTotal.knowledge }} 个知识点，</template>试题录入时按知识点绑定，供学情分析统计掌握情况
              </span>
              <el-button link type="primary" @click="refreshTab">刷新</el-button>
              <el-button type="primary" plain size="small" @click="openManage('knowledge')">知识点管理</el-button>
            </div>

            <el-table v-loading="tabLoading.knowledge" :data="tabData.knowledge" class="tab-table" stripe border
              max-height="420" empty-text="该课程暂无知识点">
              <el-table-column prop="pointName" label="知识点名称" width="220" show-overflow-tooltip />
              <el-table-column prop="knowledgeCode" label="知识点编码" width="150" align="center">
                <template #default="{ row }">{{ row.knowledgeCode || '-' }}</template>
              </el-table-column>
              <el-table-column label="备注" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">{{ row.remark || '-' }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="创建时间" min-width="185" align="center" class-name="nowrap-cell" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!--
        课程内容管理入口：不跳转到独立管理页，直接在当前页以抽屉打开对应模块。
        courseId 传给模块后，模块内的课程下拉会被锁定为本课程（embedded 模式）。
      -->
      <el-drawer
        v-model="manageVisible.resource"
        :title="MANAGE_TITLES.resource"
        size="82%"
        destroy-on-close
        @closed="reloadTabAfterManage('resource')"
      >
        <StudyResourceList :course-id="courseId" embedded />
      </el-drawer>

      <el-drawer
        v-if="isTeacher"
        v-model="manageVisible.homework"
        :title="MANAGE_TITLES.homework"
        size="82%"
        destroy-on-close
        @closed="reloadTabAfterManage('homework')"
      >
        <PracticePaperList :course-id="courseId" embedded />
      </el-drawer>

      <el-drawer
        v-if="isTeacher"
        v-model="manageVisible.paper"
        :title="MANAGE_TITLES.paper"
        size="82%"
        destroy-on-close
        @closed="reloadTabAfterManage('paper')"
      >
        <PaperList :course-id="courseId" embedded />
      </el-drawer>

      <el-drawer
        v-if="isTeacher"
        v-model="manageVisible.exam"
        :title="MANAGE_TITLES.exam"
        size="82%"
        destroy-on-close
        @closed="reloadTabAfterManage('exam')"
      >
        <ExamList :course-id="courseId" embedded />
      </el-drawer>

      <el-drawer
        v-model="manageVisible.knowledge"
        :title="MANAGE_TITLES.knowledge"
        size="82%"
        destroy-on-close
        @closed="reloadTabAfterManage('knowledge')"
      >
        <KnowledgePointList :course-id="courseId" embedded :readonly="!isAdmin" />
      </el-drawer>
    </template>

    <el-empty v-else-if="!loading" :image-size="110" description="课程不存在或已被删除">
      <el-button type="primary" @click="goBack">返回</el-button>
    </el-empty>

    <!-- 编辑课程信息：管理员可改课程编码与任课教师，教师仅维护介绍类信息 -->
    <el-dialog v-model="dialogVisible" title="编辑课程信息" width="560px" @closed="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" placeholder="如 数据结构" maxlength="50" />
        </el-form-item>

        <!-- 课程编码参与题库/试卷/考试关联，教师端只读 -->
        <el-form-item v-if="isAdmin" label="课程编码" prop="courseCode">
          <el-input v-model="form.courseCode" placeholder="如 CS202（唯一，用于题库/试卷关联）" maxlength="20" />
        </el-form-item>
        <el-form-item v-else label="课程编码">
          <el-input :model-value="course?.courseCode || '未设置'" disabled />
          <div class="form-tip">课程编码由管理员维护，如需修改请联系管理员</div>
        </el-form-item>

        <el-form-item v-if="isAdmin" label="任课教师" prop="teacherIds">
          <el-select
            v-model="form.teacherIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择任课教师（可多选，可留空）"
            style="width: 100%"
          >
            <el-option
              v-for="t in teacherOptions"
              :key="t.id"
              :label="`${t.realName}（${t.username}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="课程类型" prop="courseType">
          <el-select
            v-model="form.courseType"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="请选择或手动填写课程类型"
            style="width: 100%"
          >
            <el-option v-for="type in courseTypes" :key="type" :label="type" :value="type" />
          </el-select>
        </el-form-item>

        <el-form-item label="课程简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2"
            placeholder="卡片上的简短简介，选填" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item label="详细介绍" prop="intro">
          <el-input v-model="form.intro" type="textarea" :rows="4"
            placeholder="课程完整介绍（学生端课程详情页展示），与上方卡片简介区分，选填"
            maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="学习目标" prop="learningObjectives">
          <el-input v-model="form.learningObjectives" type="textarea" :rows="2"
            placeholder="学完本课程后能掌握什么，选填" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="适用人群" prop="targetAudience">
          <el-input v-model="form.targetAudience" type="textarea" :rows="2"
            placeholder="如：计算机专业二年级学生 / 零基础自学者，选填" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="课程大纲" prop="syllabus">
          <el-input v-model="form.syllabus" type="textarea" :rows="4"
            placeholder="每行一个章节，例如：&#10;第一章 绪论&#10;第二章 线性表" maxlength="2000" show-word-limit />
        </el-form-item>

        <el-form-item label="课程封面">
          <div class="cover-field">
            <div class="cover-preview">
              <img v-if="form.cover" :src="form.cover" alt="课程封面" />
              <span v-else class="cover-preview__tip">暂无封面</span>
            </div>
            <div class="cover-actions">
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                accept="image/jpeg,image/png,image/gif,image/webp,image/bmp"
                :on-change="handleCoverChange"
              >
                <el-button size="small" :icon="Upload" :loading="coverUploading">
                  {{ form.cover ? '更换封面' : '上传封面' }}
                </el-button>
              </el-upload>
              <el-button v-if="form.cover" size="small" link type="danger" @click="form.cover = ''">移除</el-button>
              <span class="cover-tip">jpg/png/gif/webp/bmp，不超过 5MB；留空时显示默认封面</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="开课时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选填，学生端展示课程何时开始"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报名截止" prop="enrollDeadline">
          <el-date-picker
            v-model="form.enrollDeadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选填，留空表示长期开放报名"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Edit, Upload } from '@element-plus/icons-vue'
import { getCourseDetail, updateCourse, uploadCourseCover } from '@/api/course'
import { getMyCourseDetail, updateMyCourse } from '@/api/myCourse'
import { getUserListByRole } from '@/api/sysUser'
import { downloadStudyResource, getStudyResourceList, viewStudyResource } from '@/api/studyResource'
import { listPracticePaper } from '@/api/practicePaper'
import { getPaperList } from '@/api/examPaper'
import { getExamPage } from '@/api/exam'
import { getKnowledgePointPage } from '@/api/knowledgePoint'
import { useAuthStore } from '@/stores/auth'

/**
 * 课程内容的管理模块体积较大（各自带筛选、表格、弹窗），
 * 这里按需异步加载：只有点了「资料管理 / 作业管理 / 试卷管理 / 考试管理 / 知识点管理」才去拉对应 chunk。
 */
const StudyResourceList = defineAsyncComponent(() => import('@/views/studyResource/StudyResourceList.vue'))
const PracticePaperList = defineAsyncComponent(() => import('@/views/PracticePaperList.vue'))
const PaperList = defineAsyncComponent(() => import('@/views/PaperList.vue'))
const ExamList = defineAsyncComponent(() => import('@/views/ExamList.vue'))
const KnowledgePointList = defineAsyncComponent(() => import('@/views/knowledgePoint/KnowledgePointList.vue'))

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 管理员（role=2）走课程管理接口，可改课程编码与任课教师；教师走「我的课程」接口，只维护介绍类信息
const isAdmin = computed(() => authStore.userInfo?.role === 2)
// 教师端：作业 / 试卷 / 考试 / 知识点都在本页以抽屉内嵌管理；管理员端这些入口只做只读展示
const isTeacher = computed(() => authStore.userInfo?.role === 1)
const isTeacherSide = computed(() => route.name === 'TeacherCourseInfo')
const courseId = Number(route.params.courseId)

const loading = ref(false)
const course = ref(null)
const teachers = ref([])
const stats = reactive({ questionCount: 0, paperCount: 0, examCount: 0, knowledgeCount: 0 })

const backText = computed(() => (isTeacherSide.value ? '返回我的课程' : '返回课程列表'))
const pageSub = computed(() => (isAdmin.value
  ? '课程信息 · 可在此查看并修改课程全部信息（含课程编码与任课教师）'
  : '课程信息 · 可在此查看并修改本人任教课程的介绍信息（课程编码由管理员维护）'))

/**
 * 快捷入口：课程内的资料 / 作业 / 试卷 / 考试 / 知识点已并入下方「课程内容」Tab，
 * 这里只保留教师侧的教务操作（学生名单、加入申请审核）。
 */
const quickEntries = computed(() => (isTeacher.value
  ? [
      { label: '学生名单', to: { path: '/my-courses', query: { courseId } } },
      { label: '申请审核', to: { path: '/course/applies', query: { courseId } } }
    ]
  : []))

/** 课程类型（专业课 / 公共课 等），未设置时归为「未分类」，与课程卡片角标保持一致 */
const courseTypeText = computed(() => course.value?.courseType || '未分类')

const hasIntroContent = computed(() => Boolean(
  course.value?.intro || course.value?.learningObjectives
  || course.value?.targetAudience || course.value?.syllabus
))

/** 课程大纲：后端按「每行一个章节」存储，这里逐行渲染成有序列表 */
const syllabusLines = computed(() => String(course.value?.syllabus || '')
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter(Boolean))

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 加载课程信息：管理员 / 教师分别调用各自权限下的详情接口 */
async function loadDetail() {
  if (!courseId || Number.isNaN(courseId)) {
    ElMessage.error('课程参数有误')
    goBack()
    return
  }
  loading.value = true
  try {
    const res = isTeacherSide.value
      ? await getMyCourseDetail(courseId)
      : await getCourseDetail(courseId)
    const data = res.data || {}
    course.value = data.course || null
    teachers.value = data.teachers || []
    stats.questionCount = data.questionCount ?? 0
    stats.paperCount = data.paperCount ?? 0
    stats.examCount = data.examCount ?? 0
    stats.knowledgeCount = data.knowledgeCount ?? 0
  } catch {
    // 无权查看 / 课程已删除：拦截器已提示，这里展示空态
    course.value = null
    teachers.value = []
  } finally {
    loading.value = false
  }
}

/** 返回列表：优先回退历史（保留列表页的筛选与页码），没有历史时按角色回到对应菜单 */
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push(isTeacherSide.value ? '/my-courses' : '/courses')
  }
}

// ============ 课程内容 Tab（资料 / 作业 / 试卷 / 考试 / 知识点）============
const activeTab = ref('intro')
/** 各 Tab 的列表数据：切到该 Tab 才加载，避免进页面就并发 5 个请求 */
const tabData = reactive({ resource: [], homework: [], paper: [], exam: [], knowledge: [] })
/** 各 Tab 的总条数（后端 total，用于「共 N 条」提示） */
const tabTotal = reactive({ resource: 0, homework: 0, paper: 0, exam: 0, knowledge: 0 })
/** 已加载标记（含空结果）：避免重复请求；请求失败时会重置以便重试 */
const tabLoaded = reactive({ resource: false, homework: false, paper: false, exam: false, knowledge: false })
const tabLoading = reactive({ resource: false, homework: false, paper: false, exam: false, knowledge: false })

/**
 * 课程内的管理入口：不跳转到独立管理页，而是在当前页用抽屉打开对应模块，
 * 并把本课程 courseId 传下去（模块内课程下拉会被锁定为本课程，所见即本课程数据）。
 */
const manageVisible = reactive({ resource: false, homework: false, paper: false, exam: false, knowledge: false })
const MANAGE_TITLES = {
  resource: '课程资料管理',
  homework: '课程作业管理',
  paper: '课程试卷管理',
  exam: '课程考试管理',
  knowledge: '课程知识点管理'
}
function openManage(name) {
  manageVisible[name] = true
}

/** 抽屉关闭后刷新对应 Tab，把管理过程中的增删改同步回课程信息页 */
function reloadTabAfterManage(name) {
  tabLoaded[name] = false
  if (activeTab.value === name) loadTabData(name)
}

/** 加载某个 Tab 的课程数据（统一按 courseId 过滤，课程内数据量有限，取前 100 条） */
async function loadTabData(name) {
  if (!name || name === 'intro' || tabLoaded[name]) return
  tabLoaded[name] = true
  tabLoading[name] = true
  const params = { courseId, pageNum: 1, pageSize: 100 }
  try {
    let res
    if (name === 'resource') {
      res = await getStudyResourceList(params)
    } else if (name === 'homework') {
      res = await listPracticePaper(params)
    } else if (name === 'paper') {
      res = await getPaperList(params)
    } else if (name === 'exam') {
      res = await getExamPage(params)
    } else if (name === 'knowledge') {
      res = await getKnowledgePointPage(params)
    }
    tabData[name] = res?.data?.records || []
    tabTotal[name] = Number(res?.data?.total ?? tabData[name].length)
  } catch {
    // 已登录/有权限时正常返回；失败（如无权限）由拦截器提示，这里重置标记以便切回重试
    tabData[name] = []
    tabTotal[name] = 0
    tabLoaded[name] = false
  } finally {
    tabLoading[name] = false
  }
}

function handleTabChange(name) {
  loadTabData(name)
}

/** 手动刷新当前 Tab：课程内数据可能在别的管理页刚改过 */
function refreshTab() {
  const name = activeTab.value
  if (!name || name === 'intro') return
  tabLoaded[name] = false
  loadTabData(name)
}

// ============ Tab 内的展示文案 ============
/** 资料类型中文名（与「学习资料管理」页保持一致） */
const RESOURCE_TYPE_LABEL = {
  pdf: 'PDF', word: 'Word', ppt: 'PPT', excel: 'Excel', text: '文本',
  compress: '压缩包', image: '图片', video: '视频', audio: '音频',
  link: '外部链接', other: '其他'
}
const resourceTypeText = (t) => RESOURCE_TYPE_LABEL[t] || t || '-'

/** 课程资料悬浮提示：完整资料名称 + 备注（列宽有限，超长时截取显示） */
function resourceFullName(row) {
  return row.description ? `${row.resourceName}（${row.description}）` : (row.resourceName || '-')
}

/** 课程作业悬浮提示：完整作业名称 + 备注 */
function homeworkFullName(row) {
  return row.description ? `${row.paperName}（${row.description}）` : (row.paperName || '-')
}

/** 作业状态：0草稿 1已发布（学生可见） 2停用 */
function homeworkStatusText(status) {
  return { 0: '草稿', 1: '已发布', 2: '已停用' }[status] || '未知'
}
function homeworkStatusTag(status) {
  return status === 1 ? 'success' : 'info'
}

/** 试卷组卷方式：1手动 2随机 3AI 智能 */
function paperTypeText(type) {
  return { 1: '手动组卷', 2: '随机组卷', 3: 'AI 智能组卷' }[type] || '-'
}
/** 试卷状态：0草稿 1已发布 2已归档 */
function paperStatusText(status) {
  return { 0: '草稿', 1: '已发布', 2: '已归档' }[status] || '未知'
}
function paperStatusTag(status) {
  if (status === 1) return 'success'
  return status === 2 ? 'warning' : 'info'
}

/** 考试状态：status=0 已撤回，否则看运行时状态（0未开始 1进行中 2已结束） */
function examStatusText(row) {
  if (row.status === 0) return '已撤回'
  if (row.runtimeStatus === 0) return '未开始'
  if (row.runtimeStatus === 1) return '进行中'
  return '已结束'
}
function examStatusTag(row) {
  return row.runtimeStatus === 1 ? 'success' : 'info'
}

/** 课程资料：外部链接（站外教学视频等）新窗口打开，同时记一次浏览 */
async function openResourceLink(row) {
  if (!row.filePath) {
    ElMessage.error('链接地址缺失')
    return
  }
  try {
    await viewStudyResource(row.id)
  } catch (e) { /* 统计失败不阻断打开 */ }
  window.open(row.filePath, '_blank', 'noopener,noreferrer')
}

/**
 * 课程资料：PDF 在线预览。
 * 直接打开后端静态地址由浏览器流式加载，不走 axios，避免大文件被整体读进内存；
 * 打开前记一次浏览（记录失败不阻断预览），与「学习资料管理」页保持一致。
 */
async function previewResource(row) {
  if (!row.filePath) {
    ElMessage.error('预览失败：文件路径缺失')
    return
  }
  try {
    await viewStudyResource(row.id)
  } catch (e) { /* 忽略统计失败 */ }
  window.open(row.filePath, '_blank')
}

/** 课程资料：下载到本地 */
async function downloadResource(row) {
  try {
    const res = await downloadStudyResource(row.id)
    const blob = res.data || res
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.resourceName || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}

// ============ 编辑课程信息 ============
const dialogVisible = ref(false)
const submitting = ref(false)
const coverUploading = ref(false)
const formRef = ref(null)
const teacherOptions = ref([])
const courseTypes = ['专业课', '公共课', '选修课']
const form = reactive({
  courseName: '',
  courseCode: '',
  courseType: '',
  description: '',
  cover: '',
  startTime: '',
  enrollDeadline: '',
  teacherIds: [],
  intro: '',
  learningObjectives: '',
  targetAudience: '',
  syllabus: ''
})
const rules = computed(() => (isAdmin.value
  ? {
      courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
      courseCode: [{ required: true, message: '请输入课程编码', trigger: 'blur' }]
    }
  : {
      courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }]
    }))

function openEditDialog() {
  const c = course.value || {}
  form.courseName = c.courseName || ''
  form.courseCode = c.courseCode || ''
  form.courseType = c.courseType || ''
  form.description = c.description || ''
  form.cover = c.cover || ''
  form.startTime = c.startTime ? String(c.startTime).replace('T', ' ').slice(0, 19) : ''
  form.enrollDeadline = c.enrollDeadline ? String(c.enrollDeadline).replace('T', ' ').slice(0, 19) : ''
  form.teacherIds = c.teacherIds || (teachers.value || []).map(t => t.id)
  form.intro = c.intro || ''
  form.learningObjectives = c.learningObjectives || ''
  form.targetAudience = c.targetAudience || ''
  form.syllabus = c.syllabus || ''
  dialogVisible.value = true
}

function resetForm() {
  form.courseName = ''
  form.courseCode = ''
  form.courseType = ''
  form.description = ''
  form.cover = ''
  form.startTime = ''
  form.enrollDeadline = ''
  form.teacherIds = []
  form.intro = ''
  form.learningObjectives = ''
  form.targetAudience = ''
  form.syllabus = ''
  coverUploading.value = false
  formRef.value?.clearValidate?.()
}

/**
 * 课程封面：选中图片后立即上传（/api/course/cover，教师与管理员均可用），
 * 返回路径先暂存在表单里，点「确定」保存课程信息时才真正写库。
 */
async function handleCoverChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  coverUploading.value = true
  try {
    const res = await uploadCourseCover(raw)
    form.cover = res.data || ''
    ElMessage.success('封面已上传，保存后生效')
  } catch (e) {
    console.error('封面上传失败:', e)
  } finally {
    coverUploading.value = false
  }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = {
        courseName: form.courseName.trim(),
        courseType: form.courseType || null,
        description: form.description ? form.description.trim() : null,
        // 封面：留空传空串（后端按"显式清空"处理），已回填时原样提交
        cover: form.cover || '',
        startTime: form.startTime || null,
        enrollDeadline: form.enrollDeadline || null,
        intro: form.intro ? form.intro.trim() : null,
        learningObjectives: form.learningObjectives ? form.learningObjectives.trim() : null,
        targetAudience: form.targetAudience ? form.targetAudience.trim() : null,
        syllabus: form.syllabus ? form.syllabus.trim() : null
      }
      if (isAdmin.value) {
        // 管理员：走课程管理接口，可同时维护课程编码与任课教师
        payload.courseCode = form.courseCode.trim()
        payload.teacherIds = form.teacherIds
        await updateCourse(courseId, payload)
      } else {
        // 教师：课程编码与任课教师由管理员维护，后端会忽略这两个字段
        await updateMyCourse(courseId, payload)
      }
      ElMessage.success('课程信息已更新')
      dialogVisible.value = false
      await loadDetail()
    } catch (e) {
      console.error('保存课程信息失败:', e)
    } finally {
      submitting.value = false
    }
  })
}

onMounted(async () => {
  await loadDetail()
  // 管理员的编辑表单需要教师下拉（教师端不提供任课教师编辑）
  if (isAdmin.value) {
    try {
      const res = await getUserListByRole(1)
      teacherOptions.value = res.data || []
    } catch (e) {
      console.error('加载教师列表失败:', e)
    }
  }
})
</script>

<style scoped>
.course-info-page {
  padding: 4px;
  min-height: 200px;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.back-btn {
  font-size: 14px;
}
.page-sub {
  font-size: 12px;
  color: #909399;
}

/* ============ 顶部课程基础信息区 ============ */
.base-card {
  margin-bottom: 12px;
  border-radius: 12px;
}
.base {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.base__cover {
  position: relative;
  flex: none;
  width: 200px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 10px;
  background: linear-gradient(135deg, #409eff, #79bbff);
}
.base__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.base__cover-text {
  font-size: 34px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
}
.base__info {
  flex: 1;
  min-width: 0;
}
.base__title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.base__title {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
  color: #1f2937;
}
.base__desc {
  margin: 8px 0 10px;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}
.base__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 6px;
  font-size: 13px;
}
.meta-item {
  display: flex;
  gap: 8px;
  min-width: 0;
}
.meta-label {
  flex: none;
  width: 72px;
  color: #909399;
}
.meta-value {
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.teacher-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.teacher-chips__label {
  font-size: 13px;
  color: #909399;
}
.teacher-chip {
  padding: 2px 10px;
  border: 1px solid #d9ecff;
  border-radius: 12px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}
.teacher-chip:hover {
  background: #d9ecff;
}
.teacher-card__name {
  font-weight: 600;
  margin-bottom: 6px;
}
.teacher-card__row {
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}
/* 操作区：编辑课程信息 + 快捷入口，纵向排列居中 */
.base__ops {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-self: center;
}
.base__ops :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* ============ 课程建设数据 ============ */
.stat-row {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #ebeef5;
}
.stat-card {
  flex: 1;
  border-radius: 10px;
  padding: 14px 8px;
  text-align: center;
  color: #fff;
}
.stat-blue   { background: linear-gradient(135deg, #60a5fa, #3b82f6); }
.stat-purple { background: linear-gradient(135deg, #a78bfa, #8b5cf6); }
.stat-green  { background: linear-gradient(135deg, #34d399, #10b981); }
.stat-orange { background: linear-gradient(135deg, #fbbf24, #f59e0b); }
.stat-num {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  font-size: 13px;
  opacity: 0.9;
  margin-top: 4px;
}

/* ============ 课程内容 Tab（介绍 / 资料 / 作业 / 试卷 / 考试 / 知识点）============ */
.content-card {
  border-radius: 12px;
}
.content-card :deep(.el-tabs__content) {
  padding-top: 4px;
}
/* 每个 Tab 的工具栏：左侧说明，右侧「刷新 / 去管理」 */
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.tab-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.tab-tip {
  flex: 1;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}
/* 课程内容 Tab 的表格：表头与内容统一居中（与「知识点管理」页一致） */
.tab-table :deep(.el-table__header th.el-table__cell),
.tab-table :deep(.el-table__body td.el-table__cell) {
  text-align: center;
}
/* 单元格内层容器（含 show-overflow-tooltip 的单行文本）同样居中 */
.tab-table :deep(.el-table__cell > .cell) {
  text-align: center;
}
/* 时间列强制单行：避免「日期 + 时分秒」被折成两行 */
.tab-table :deep(.nowrap-cell .cell) {
  white-space: nowrap;
}
/* 名称单元格：名称与备注同行显示，超长截取；悬浮时由 el-tooltip 显示完整名称。
   这里刻意左对齐：居中 + 截断会把名称开头也裁掉，只剩结尾和省略号，反而看不全。 */
.cell-name {
  display: block;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  cursor: default;
}
/* 同行跟在名称后的备注：灰色小字，不再单独占一行 */
.cell-desc {
  margin-left: 2px;
  font-size: 12px;
  color: #909399;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.card-header__title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.card-header__tip {
  flex: 1;
  font-size: 12px;
  color: #909399;
}
.intro-wrap {
  padding: 2px;
}
/* 课程介绍顶部的课程类型（专业课 / 公共课 等） */
.intro-type {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.intro-type__label {
  font-size: 13px;
  color: #909399;
}
.intro-block + .intro-block {
  margin-top: 18px;
}
.intro-block__title {
  margin: 0 0 8px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.intro-block__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}
.syllabus {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: #606266;
}

/* ============ 编辑表单：封面与提示 ============ */
.form-tip {
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
}
.cover-field {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.cover-preview {
  flex: none;
  width: 132px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  background: #f7f8fa;
}
.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-preview__tip {
  font-size: 12px;
  color: #c0c4cc;
}
.cover-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.cover-actions :deep(.el-upload) {
  display: inline-block;
}
.cover-tip {
  flex-basis: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

/* 窄屏：信息区改为竖向排布 */
@media (max-width: 900px) {
  .base {
    flex-direction: column;
  }
  .base__ops {
    flex-direction: row;
    align-self: flex-start;
  }
  .base__meta {
    grid-template-columns: 1fr;
  }
  .stat-row {
    flex-wrap: wrap;
  }
  .stat-card {
    flex: 1 1 40%;
  }
  .tab-toolbar {
    flex-wrap: wrap;
  }
  .tab-tip {
    flex-basis: 100%;
  }
}
</style>
