import type { SsFormItemConfig } from '@/components/SsForm'
import SsInput from '@/components/SsBase/SsInput/index.vue'

/** 基本信息配置 */
export const editModelBaseConfig: SsFormItemConfig[] = [
  {
    prop: 'name',
    label: '模型名称',
    comp: SsInput,
    compProps: { placeholder: '如 UNet-ResNet34', maxlength: 100 },
    rules: true,
  },
  {
    prop: 'version',
    label: '版本号',
    comp: SsInput,
    compProps: { placeholder: '如 1.0.0', maxlength: 50 },
  },
  {
    prop: 'description',
    label: '描述',
    comp: SsInput,
    compProps: {
      type: 'textarea',
      rows: 3,
      placeholder: '模型描述（可选）',
      maxlength: 500,
      showWordLimit: true,
    },
  },
]

/** 性能指标配置（2 列 Grid） */
export const editModelMetricConfig: SsFormItemConfig[] = [
  {
    prop: 'miou',
    label: 'mIoU',
    comp: 'ElInputNumber',
    compProps: {
      min: 0,
      max: 1,
      step: 0.01,
      precision: 4,
      controls: false,
      placeholder: '0~1',
      class: 'metric-input',
    },
    rules: [
      {
        validator: (_rule: unknown, value: number | null, callback: (err?: Error) => void) => {
          if (value != null && (value < 0 || value > 1)) callback(new Error('取值范围 0~1'))
          else callback()
        },
        trigger: 'blur',
      },
    ],
  },
  {
    prop: 'precision',
    label: 'Precision',
    comp: 'ElInputNumber',
    compProps: {
      min: 0,
      max: 1,
      step: 0.01,
      precision: 4,
      controls: false,
      placeholder: '0~1',
      class: 'metric-input',
    },
    rules: [
      {
        validator: (_rule: unknown, value: number | null, callback: (err?: Error) => void) => {
          if (value != null && (value < 0 || value > 1)) callback(new Error('取值范围 0~1'))
          else callback()
        },
        trigger: 'blur',
      },
    ],
  },
  {
    prop: 'recall',
    label: 'Recall',
    comp: 'ElInputNumber',
    compProps: {
      min: 0,
      max: 1,
      step: 0.01,
      precision: 4,
      controls: false,
      placeholder: '0~1',
      class: 'metric-input',
    },
    rules: [
      {
        validator: (_rule: unknown, value: number | null, callback: (err?: Error) => void) => {
          if (value != null && (value < 0 || value > 1)) callback(new Error('取值范围 0~1'))
          else callback()
        },
        trigger: 'blur',
      },
    ],
  },
  {
    prop: 'dice',
    label: 'Dice',
    comp: 'ElInputNumber',
    compProps: {
      min: 0,
      max: 1,
      step: 0.01,
      precision: 4,
      controls: false,
      placeholder: '0~1',
      class: 'metric-input',
    },
    rules: [
      {
        validator: (_rule: unknown, value: number | null, callback: (err?: Error) => void) => {
          if (value != null && (value < 0 || value > 1)) callback(new Error('取值范围 0~1'))
          else callback()
        },
        trigger: 'blur',
      },
    ],
  },
  {
    prop: 'fps',
    label: 'FPS',
    comp: 'ElInputNumber',
    compProps: {
      min: 0,
      step: 1,
      precision: 1,
      controls: false,
      placeholder: '帧/秒',
      class: 'metric-input',
    },
    rules: [
      {
        validator: (_rule: unknown, value: number | null, callback: (err?: Error) => void) => {
          if (value != null && value < 0) callback(new Error('不能为负数'))
          else callback()
        },
        trigger: 'blur',
      },
    ],
  },
  {
    prop: 'file_size',
    label: '文件大小 (MiB)',
    comp: 'ElInputNumber',
    compProps: {
      min: 0,
      step: 1,
      precision: 1,
      controls: false,
      placeholder: 'MB',
      class: 'metric-input',
      disabled: true,
    },
    rules: [
      {
        validator: (_rule: unknown, value: number | null, callback: (err?: Error) => void) => {
          if (value != null && value < 0) callback(new Error('不能为负数'))
          else callback()
        },
        trigger: 'blur',
      },
    ],
  },
  {
    prop: 'metric_source',
    label: '指标来源',
    comp: SsInput,
    compProps: { placeholder: '填写指标时，请说明验证集或测量方式', maxlength: 255 },
  },
]
