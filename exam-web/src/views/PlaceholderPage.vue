<template>
  <div class="placeholder-page">
    <el-card shadow="never" class="placeholder-card">
      <div class="placeholder-content">
        <el-icon class="placeholder-icon"><InfoFilled /></el-icon>
        <h2 class="placeholder-title">{{ title }}</h2>
        <p class="placeholder-desc">{{ description || '该功能模块正在规划开发中，敬请期待。' }}</p>
        <el-tag type="info" effect="plain">功能开发中</el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'

const route = useRoute()
const title = computed(() => route.meta?.title || '页面')
const description = computed(() => route.meta?.description || '')
</script>

<style scoped>
.placeholder-page {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 40px 0;
}

.placeholder-card {
  width: 100%;
  max-width: 720px;
  border-radius: 8px;
}

.placeholder-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 48px 24px;
  gap: 16px;
}

.placeholder-icon {
  font-size: 56px;
  color: #909399;
}

.placeholder-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.placeholder-desc {
  margin: 0;
  font-size: 14px;
  color: #909399;
  line-height: 1.6;
}
</style>
