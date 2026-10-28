<template>
  <!-- 课程卡片：管理端「课程列表」、教师端「我的课程」、学生端「加入课程」共用同一套视觉
       clickable=true 时整张卡片可点击（鼠标手型 + 上浮），使用方直接在组件上写 @click 即可；
       卡片内的按钮 / 勾选框区域已阻止冒泡，不会误触发卡片点击。 -->
  <div class="course-card" :class="{ 'course-card--clickable': clickable }">
    <!-- 封面：由「课程管理」上传；未设置时用渐变底色 + 课程名首字占位 -->
    <div class="course-card__cover">
      <img v-if="course.cover" :src="course.cover" :alt="course.courseName" />
      <span v-else class="course-card__cover-text">{{ (course.courseName || '课').slice(0, 1) }}</span>
      <!-- 左上角附加区：如批量删除时的勾选框，由使用方按需填充（点击不触发卡片跳转） -->
      <div v-if="selectable" class="course-card__select" @click.stop>
        <slot name="select" />
      </div>
      <!-- 右上角角标：管理端/教师端显示课程类型，学生端显示加入状态 -->
      <span v-if="badge" class="course-card__badge" :class="`badge--${badgeType}`">{{ badge }}</span>
    </div>

    <div class="course-card__body">
      <h3 class="course-card__name" :title="course.courseName">{{ course.courseName }}</h3>

      <!-- 信息行：默认「授课教师」，教师端可换成「课程编码」 -->
      <div class="course-card__info">
        <span class="course-card__info-label">{{ infoLabel }}</span>
        <span class="course-card__info-value" :title="infoText">{{ infoText }}</span>
      </div>

      <!-- 简介固定两行，超出省略，悬停可看完整内容 -->
      <p class="course-card__desc" :title="course.description || ''">
        {{ course.description || '该课程暂无简介' }}
      </p>

      <div class="course-card__meta">
        <slot name="meta" />
      </div>

      <!-- 操作区：按钮点击不冒泡到卡片，避免「点按钮也跳详情页」 -->
      <div class="course-card__ops" @click.stop>
        <slot name="ops" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 课程数据（id / courseName / courseType / courseCode / cover / description / teacherNames / studentCount 等） */
  course: { type: Object, required: true },
  /** 右上角角标文案，空则不显示角标 */
  badge: { type: String, default: '' },
  /** 角标配色：open（蓝）/ joined（绿）/ pending（橙）/ closed（灰） */
  badgeType: { type: String, default: 'open' },
  /** 信息行标签，默认「授课教师」 */
  infoLabel: { type: String, default: '授课教师' },
  /** 信息行内容，留空时回退到课程数据的 teacherNames */
  infoValue: { type: String, default: '' },
  /** 是否在封面左上角显示勾选附加区（批量操作模式），内容由 #select 插槽提供 */
  selectable: { type: Boolean, default: false },
  /** 整张卡片是否可点击（进入课程信息页），点击事件由使用方通过 @click 监听 */
  clickable: { type: Boolean, default: false }
})

const infoText = computed(() => props.infoValue || props.course.teacherNames || '暂未安排')
</script>

<style scoped>
.course-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  transition: box-shadow 0.2s, transform 0.2s;
}
.course-card:hover {
  box-shadow: 0 8px 20px rgba(31, 41, 55, 0.1);
  transform: translateY(-2px);
}
/* 整卡可点击：管理端「课程列表」/ 教师端「我的课程」点击卡片进入课程信息页 */
.course-card--clickable {
  cursor: pointer;
}
.course-card--clickable:hover {
  border-color: #c6e2ff;
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.16);
}
/* 封面：固定高度，无封面时用渐变底 + 首字占位 */
.course-card__cover {
  position: relative;
  height: 122px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, #409eff, #79bbff);
}
.course-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.course-card__cover-text {
  font-size: 34px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
  letter-spacing: 2px;
}
/* 左上角附加区：用白色底衬托勾选框，保证在深色封面上也看得清 */
.course-card__select {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  display: flex;
  align-items: center;
  padding: 0 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
}
.course-card__badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 18px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
}
.badge--joined {
  background: rgba(103, 194, 58, 0.92);
}
.badge--pending {
  background: rgba(230, 162, 60, 0.92);
}
.badge--closed {
  background: rgba(144, 147, 153, 0.92);
}
.badge--open {
  background: rgba(64, 158, 255, 0.92);
}
.course-card__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
}
.course-card__name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-card__info {
  display: flex;
  gap: 6px;
  font-size: 12px;
  color: #606266;
}
.course-card__info-label {
  flex: none;
  color: #909399;
}
.course-card__info-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 简介固定两行高度，行数不足也不跳动 */
.course-card__desc {
  margin: 0;
  min-height: 38px;
  font-size: 12px;
  line-height: 1.6;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.course-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
}
.course-card__meta > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-card__meta :deep(b) {
  color: #409eff;
}
.course-card__ops {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;
  padding-top: 4px;
}
/* 用 gap 控制间距，去掉 Element Plus 相邻按钮的默认左外边距，避免卡片换行错位 */
.course-card__ops :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
