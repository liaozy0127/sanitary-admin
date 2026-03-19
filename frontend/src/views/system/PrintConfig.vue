<template>
  <div class="print-config-page">
    <el-card style="max-width: 720px">
      <template #header>
        <span>打印设置</span>
      </template>
      <el-form ref="formRef" :model="formData" label-width="120px" v-loading="loading">

        <!-- 公司基本信息 -->
        <div class="section-title">公司基本信息</div>
        <el-form-item label="工厂名称">
          <el-input v-model="formData.factoryName" placeholder="旧版工厂名称（兼容）" />
        </el-form-item>
        <el-form-item label="公司名称">
          <el-input v-model="formData.printCompanyName" placeholder="发货单上显示的公司名称" />
        </el-form-item>
        <el-form-item label="电话/传真">
          <el-input v-model="formData.printCompanyPhone" placeholder="如 0750-2766036" />
        </el-form-item>
        <el-form-item label="公司地址">
          <el-input v-model="formData.printCompanyAddress" placeholder="公司详细地址" />
        </el-form-item>
        <el-form-item label="联系人1">
          <el-input v-model="formData.printContact1" placeholder="如 廖总：13536094788" />
        </el-form-item>
        <el-form-item label="联系人2">
          <el-input v-model="formData.printContact2" placeholder="如 仓管：13672842611" />
        </el-form-item>

        <!-- 排产单设置 -->
        <div class="section-title">签名栏设置（排产单 / 发货单共用）</div>
        <el-form-item label="排产单标题">
          <el-input v-model="formData.printTitleProduction" placeholder="排产单打印文档主标题" />
        </el-form-item>
        <el-form-item label="签名栏1">
          <el-input v-model="formData.printSignature1Label" placeholder="排产单：生产班长；发货单：收货单位" />
        </el-form-item>
        <el-form-item label="签名栏2">
          <el-input v-model="formData.printSignature2Label" placeholder="如 仓管" />
        </el-form-item>
        <el-form-item label="签名栏3">
          <el-input v-model="formData.printSignature3Label" placeholder="排产单第3栏，如 签名" />
        </el-form-item>

        <!-- 发货单设置 -->
        <div class="section-title">发货单打印</div>
        <el-form-item label="发货单标题">
          <el-input v-model="formData.printTitleDelivery" placeholder="发货单打印文档主标题" />
        </el-form-item>
        <el-form-item label="制单人标签">
          <el-input v-model="formData.printMakerLabel" placeholder="如 制单人" />
        </el-form-item>
        <el-form-item label="制单人">
          <el-input v-model="formData.makerName" placeholder="打印单据上的制单人签名" />
        </el-form-item>
        <el-form-item label="发货单备注">
          <el-input v-model="formData.printDeliveryRemark" type="textarea" :rows="4"
            placeholder="发货单左侧竖排备注内容，多行用换行分隔" />
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
  makerName: '',
  printTitleProduction: '',
  printTitleDelivery: '',
  printCompanyName: '',
  printCompanyPhone: '',
  printCompanyAddress: '',
  printContact1: '',
  printContact2: '',
  printSignature1Label: '',
  printSignature2Label: '',
  printSignature3Label: '',
  printMakerLabel: '',
  printDeliveryRemark: ''
})

const loadConfig = async () => {
  loading.value = true
  try {
    const res = await getPrintConfig()
    const data = res.data || res
    Object.keys(formData).forEach(key => {
      if (data[key] !== undefined) formData[key] = data[key]
    })
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    await updatePrintConfig({ ...formData })
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.print-config-page { padding: 8px; }
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #409EFF;
  border-left: 3px solid #409EFF;
  padding-left: 8px;
  margin: 16px 0 12px 0;
}
</style>
