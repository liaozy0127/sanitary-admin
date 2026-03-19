<template>
  <div class="print-config-page">
    <el-card style="max-width: 500px">
      <template #header>
        <span>打印设置</span>
      </template>
      <el-form ref="formRef" :model="formData" label-width="100px" v-loading="loading">
        <el-form-item label="工厂名称" prop="factoryName">
          <el-input v-model="formData.factoryName" placeholder="打印单据上显示的工厂名称" />
        </el-form-item>
        <el-form-item label="制单人" prop="makerName">
          <el-input v-model="formData.makerName" placeholder="打印单据上的制单人签名" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPrintConfig, updatePrintConfig } from '@/api/config'

const loading = ref(false)
const saving = ref(false)
const formRef = ref(null)

const formData = reactive({
  factoryName: '',
  makerName: ''
})

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getPrintConfig()
    const data = res.data || res
    formData.factoryName = data.factoryName || ''
    formData.makerName = data.makerName || ''
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await updatePrintConfig({ factoryName: formData.factoryName, makerName: formData.makerName })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.print-config-page { padding: 8px; }
</style>
