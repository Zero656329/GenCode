/**
 * bpmn-js 类型兜底（bpmn-js@17 官方未随包发布 .d.ts，社区 @types 已过时）。
 * 仅声明设计器实际用到的最小 API 面，其余能力通过 get(name) 取 diagram-js 服务。
 */
declare module 'bpmn-js/lib/Modeler' {
  /** 保存 XML 结果 */
  export interface BpmnSaveXMLResult {
    xml: string
  }

  /** 导入结果（含告警） */
  export interface BpmnImportXMLResult {
    warnings: unknown[]
  }

  export default class Modeler {
    constructor(options?: Record<string, unknown>)
    /** 解析并渲染 BPMN XML，解析失败时 reject */
    importXML(xml: string, options?: Record<string, unknown>): Promise<BpmnImportXMLResult>
    /** 序列化当前图为 BPMN XML */
    saveXML(options?: Record<string, unknown>): Promise<BpmnSaveXMLResult>
    /** 按 diagram-js 服务名取服务实例（canvas/commandStack 等），返回 any 由调用方自行收窄 */
    get<T = any>(name: string): T
    /** 监听事件（如 commandStack.changed） */
    on(event: string, handler: (e: unknown) => void, that?: unknown): void
    /** 取消监听 */
    off(event: string, handler?: (e: unknown) => void): void
    /** 挂载到指定容器 */
    attachTo(container: HTMLElement): void
    /** 销毁实例释放 DOM/事件 */
    destroy(): void
  }
}
