<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ArrowLeftOutlined, RocketOutlined, SaveOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import Modeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import {
  deployLcProcess,
  getLcProcess,
  updateLcProcess,
  type LcProcessStatus
} from '@/api/lc-process'

// ============ 内置 BPMN 模板（原样嵌入；注意 \${} 为字面量，避免模板字符串插值） ============

/** 模板A 请假审批：start → 主管审批(\${approver}) → end */
const TEMPLATE_LEAVE = `<?xml version="1.0" encoding="UTF-8"?><bpmn2:definitions xmlns:bpmn2="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" id="defs_leave" targetNamespace="http://gencode.local/process"><bpmn2:process id="leave_approval" name="请假审批" isExecutable="true"><bpmn2:startEvent id="start" name="开始"/><bpmn2:sequenceFlow id="f1" sourceRef="start" targetRef="boss"/><bpmn2:userTask id="boss" name="主管审批" flowable:assignee="\${approver}"/><bpmn2:sequenceFlow id="f2" sourceRef="boss" targetRef="end"/><bpmn2:endEvent id="end" name="结束"/></bpmn2:process><bpmndi:BPMNDiagram id="di"><bpmndi:BPMNPlane id="plane" bpmnElement="leave_approval"><bpmndi:BPMNShape id="start_s" bpmnElement="start"><dc:Bounds x="160" y="160" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="boss_s" bpmnElement="boss"><dc:Bounds x="260" y="140" width="100" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="end_s" bpmnElement="end"><dc:Bounds x="420" y="160" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNEdge id="f1_e" bpmnElement="f1"><di:waypoint x="196" y="178"/><di:waypoint x="260" y="178"/></bpmndi:BPMNEdge><bpmndi:BPMNEdge id="f2_e" bpmnElement="f2"><di:waypoint x="360" y="178"/><di:waypoint x="420" y="178"/></bpmndi:BPMNEdge></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn2:definitions>`

/** 模板B 两级审批：start → 主管审批(\${approver}) → 部门负责人审批(\${approver2}) → end */
const TEMPLATE_TWO_LEVEL = `<?xml version="1.0" encoding="UTF-8"?><bpmn2:definitions xmlns:bpmn2="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" id="defs_leave_two" targetNamespace="http://gencode.local/process"><bpmn2:process id="leave_two_level" name="两级审批" isExecutable="true"><bpmn2:startEvent id="start" name="开始"/><bpmn2:sequenceFlow id="f1" sourceRef="start" targetRef="boss"/><bpmn2:userTask id="boss" name="主管审批" flowable:assignee="\${approver}"/><bpmn2:sequenceFlow id="f2" sourceRef="boss" targetRef="dept"/><bpmn2:userTask id="dept" name="部门负责人审批" flowable:assignee="\${approver2}"/><bpmn2:sequenceFlow id="f3" sourceRef="dept" targetRef="end"/><bpmn2:endEvent id="end" name="结束"/></bpmn2:process><bpmndi:BPMNDiagram id="di"><bpmndi:BPMNPlane id="plane" bpmnElement="leave_two_level"><bpmndi:BPMNShape id="start_s" bpmnElement="start"><dc:Bounds x="160" y="160" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="boss_s" bpmnElement="boss"><dc:Bounds x="300" y="140" width="100" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="dept_s" bpmnElement="dept"><dc:Bounds x="460" y="140" width="100" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="end_s" bpmnElement="end"><dc:Bounds x="620" y="160" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNEdge id="f1_e" bpmnElement="f1"><di:waypoint x="196" y="178"/><di:waypoint x="300" y="178"/></bpmndi:BPMNEdge><bpmndi:BPMNEdge id="f2_e" bpmnElement="f2"><di:waypoint x="400" y="178"/><di:waypoint x="460" y="178"/></bpmndi:BPMNEdge><bpmndi:BPMNEdge id="f3_e" bpmnElement="f3"><di:waypoint x="560" y="178"/><di:waypoint x="620" y="178"/></bpmndi:BPMNEdge></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn2:definitions>`

/** 内置模板下拉选项 */
const TEMPLATES = [
  { value: 'leave', label: '模板A：请假审批（单级）' },
  { value: 'twoLevel', label: '模板B：两级审批' }
]

/**
 * Flowable moddle 扩展（最小集）：把 flowable:assignee 声明为 UserTask 扩展属性，
 * 使 bpmn-js 编辑后 saveXML 能原样回写 flowable:assignee，否则序列化会静默丢弃该属性。
 */
const FLOWABLE_MODDLE = {
  name: 'Flowable',
  uri: 'http://flowable.org/bpmn',
  prefix: 'flowable',
  types: [
    {
      name: 'UserTask',
      extends: ['bpmn:UserTask'],
      properties: [{ name: 'assignee', isAttr: true, type: 'String' }]
    }
  ]
}

function errMsg(err: unknown): string {
  return err instanceof Error ? err.message : String(err)
}

// ============ 流程基础信息 ============

const route = useRoute()
const router = useRouter()
const processId = String(route.params.id ?? '')

const loading = ref(false)
const saving = ref(false)
const deploying = ref(false)
const defName = ref('')
const defCategory = ref<string | null>(null)
const defRemark = ref<string | null>(null)
const publishVersion = ref(0)
const status = ref<LcProcessStatus>(0)

// ============ bpmn-js 设计器 ============

const canvasRef = ref<HTMLDivElement | null>(null)
let modeler: Modeler | null = null

/** 当前图相对服务端是否有未保存改动 */
const dirty = ref(false)
/** 服务端最近一次保存的原始 XML（无改动时直接用它保存，保护 flowable:assignee） */
let originalXml = ''

const templateKey = ref<string | undefined>(undefined)

function createModeler(): void {
  if (!canvasRef.value) return
  modeler = new Modeler({
    container: canvasRef.value,
    keyboard: { bindTo: document },
    moddleExtensions: { flowable: FLOWABLE_MODDLE }
  })
}

function fitViewport(): void {
  modeler?.get<{ zoom: (a: string, b?: string) => void }>('canvas').zoom('fit-viewport', 'auto')
}

async function importXml(xml: string): Promise<void> {
  if (!modeler) return
  try {
    await modeler.importXML(xml)
    fitViewport()
  } catch (err) {
    message.error('流程图解析失败：' + errMsg(err))
  }
}

/** 选择内置模板：importXML 直接覆盖当前图，并标记为有改动 */
async function onTemplateChange(key: string): Promise<void> {
  const xml = key === 'twoLevel' ? TEMPLATE_TWO_LEVEL : TEMPLATE_LEAVE
  await importXml(xml)
  dirty.value = true
  templateKey.value = undefined
  message.success('模板已载入画布，保存后生效')
}

function onStackChanged(): void {
  dirty.value = true
}

// ============ 保存 / 部署 ============

/** 组装待保存 XML：无改动直接用原始串（避免 bpmn-js 序列化差异/丢属性），有改动才走 saveXML */
async function buildSaveXml(): Promise<string> {
  if (!dirty.value) return originalXml
  if (!modeler) return originalXml
  const res = await modeler.saveXML({ format: true })
  return res.xml
}

async function doSave(silent: boolean): Promise<boolean> {
  saving.value = true
  try {
    const xml = await buildSaveXml()
    await updateLcProcess({
      id: processId,
      name: defName.value,
      category: defCategory.value,
      bpmnXml: xml,
      remark: defRemark.value
    })
    originalXml = xml
    dirty.value = false
    if (!silent) message.success('保存成功')
    return true
  } catch (err) {
    if (!silent) message.error('保存失败：' + errMsg(err))
    return false
  } finally {
    saving.value = false
  }
}

function onSave(): void {
  void doSave(false)
}

async function onDeploy(): Promise<void> {
  deploying.value = true
  try {
    const ok = await doSave(true)
    if (!ok) return
    const res = await deployLcProcess(processId)
    publishVersion.value =
      res && res.publishVersion ? res.publishVersion : publishVersion.value + 1
    status.value = 1
    message.success(`部署成功，当前版本 v${publishVersion.value}`)
  } finally {
    deploying.value = false
  }
}

function onBack(): void {
  if (!dirty.value) {
    router.push('/lc/process')
    return
  }
  Modal.confirm({
    title: '当前改动尚未保存，确定离开？',
    okText: '离开',
    cancelText: '继续编辑',
    onOk: () => {
      router.push('/lc/process')
    }
  })
}

// ============ 生命周期 ============

onMounted(async () => {
  if (!processId) {
    message.error('缺少流程 ID')
    router.replace('/lc/process')
    return
  }
  loading.value = true
  try {
    const detail = await getLcProcess(processId)
    defName.value = detail.name
    defCategory.value = detail.category
    defRemark.value = detail.remark
    publishVersion.value = detail.publishVersion || 0
    status.value = detail.status

    createModeler()
    if (!modeler) return
    // 草稿无图时以模板A作为初始画布
    const xml = detail.bpmnXml && detail.bpmnXml.trim() ? detail.bpmnXml : TEMPLATE_LEAVE
    await importXml(xml)
    originalXml = xml
    dirty.value = false
    // 初始导入完成后再挂脏标记监听，导入本身不计为改动
    modeler.on('commandStack.changed', onStackChanged)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (modeler) {
    modeler.off('commandStack.changed', onStackChanged)
    modeler.destroy()
    modeler = null
  }
})
</script>

<template>
  <div class="designer-page">
    <!-- 顶部工具条 -->
    <div class="designer-header">
      <a-space>
        <a-button @click="onBack">
          <template #icon><arrow-left-outlined /></template>
          返回
        </a-button>
        <span class="def-name">{{ defName }}</span>
        <span v-if="defCategory" class="def-category">{{ defCategory }}</span>
        <a-tag v-if="publishVersion > 0" color="blue">v{{ publishVersion }}</a-tag>
        <a-tag v-else>未部署</a-tag>
        <a-tag v-if="status === 2" color="red">已停用</a-tag>
        <a-tag v-if="dirty" color="orange">未保存</a-tag>
      </a-space>
      <a-space>
        <a-select
          v-model:value="templateKey"
          placeholder="载入内置模板（覆盖当前图）"
          style="width: 240px"
          :options="TEMPLATES"
          @change="onTemplateChange"
        />
        <a-button v-perm="'lc:process:edit'" type="primary" :loading="saving" @click="onSave">
          <template #icon><save-outlined /></template>
          保存
        </a-button>
        <a-button v-perm="'lc:process:deploy'" :loading="deploying" @click="onDeploy">
          <template #icon><rocket-outlined /></template>
          部署
        </a-button>
      </a-space>
    </div>

    <a-spin :spinning="loading">
      <div ref="canvasRef" class="bpmn-container"></div>
    </a-spin>

    <div class="designer-tip">
      <thunderbolt-outlined /> 拖拽左侧面板元素到画布编辑；Ctrl+Z 撤销；未改动时保存将原样保留 XML（保护 flowable:assignee）
    </div>
  </div>
</template>

<style scoped>
.designer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.def-name {
  font-size: 15px;
  font-weight: 600;
}

.def-category {
  color: #999;
  font-size: 13px;
}

.bpmn-container {
  height: calc(100vh - 260px);
  min-height: 420px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}

.designer-tip {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}
</style>
