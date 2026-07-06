import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  frameworkZhSidebar: [
    'intro',
    {
      type: 'category',
      label: '快速开始',
      items: [
        {
          type: 'doc',
          id: 'guide/quick-start',
          label: '快速开始',
        },
        {
          type: 'doc',
          id: 'guide/minimal-integration',
          label: '新项目最小接入',
        },
        {
          type: 'doc',
          id: 'guide/default-implementation-vs-sample',
          label: '默认实现与 Sample 定位',
        },
        {
          type: 'doc',
          id: 'guide/sample-quickstart',
          label: 'Sample Quickstart',
        },
        {
          type: 'category',
          label: 'Scaffold QuickStart',
          items: [
            {
              type: 'doc',
              id: 'guide/app-scaffold-starter-project-guide',
              label: '项目接入',
            },
            {
              type: 'doc',
              id: 'guide/require-table',
              label: 'RequireTable',
            },
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '平台部署',
      items: [
        {
          type: 'doc',
          id: 'operations/runtime-designer-deployment',
          label: '普通部署',
        },
        {
          type: 'doc',
          id: 'operations/docker-deployment',
          label: 'Docker部署',
        },
      ],
    },
    {
      type: 'category',
      label: 'ORM库',
      items: [
        {
          type: 'doc',
          id: 'orm/overview',
          label: 'ORM 总览',
        },
        {
          type: 'doc',
          id: 'orm/annotations',
          label: '注解说明',
        },
        {
          type: 'doc',
          id: 'orm/fluent-dsl',
          label: 'FluentDSL',
        },
        {
          type: 'doc',
          id: 'orm/event-features',
          label: '事件特性',
        },
      ],
    },
    {
      type: 'category',
      label: '动态数据源',
      items: [
        {
          type: 'doc',
          id: 'dynamic-datasource/overview',
          label: '动态数据源能力',
        },
      ],
    },
    {
      type: 'category',
      label: '插件机制',
      items: [
        {
          type: 'doc',
          id: 'plugin-mechanism/overview',
          label: '概览',
        },
        {
          type: 'doc',
          id: 'plugin-mechanism/development',
          label: '定义与开发',
        },
        {
          type: 'doc',
          id: 'plugin-mechanism/lifecycle',
          label: '加载、启停与卸载',
        },
        {
          type: 'doc',
          id: 'plugin-mechanism/repository',
          label: '插件仓库配置',
        },
      ],
    },
    {
      type: 'category',
      label: '系统配置',
      items: [
        {
          type: 'doc',
          id: 'system-config/overview',
          label: '主配置与导入机制',
        },
        {
          type: 'doc',
          id: 'system-config/workflow',
          label: 'Workflow 模块',
        },
        {
          type: 'doc',
          id: 'system-config/seata',
          label: 'Seata 模块',
        },
        {
          type: 'doc',
          id: 'system-config/oss',
          label: 'OSS 模块',
        },
        {
          type: 'doc',
          id: 'system-config/package',
          label: 'Package 模块',
        },
        {
          type: 'doc',
          id: 'system-config/sc',
          label: 'SC 模块',
        },
        {
          type: 'doc',
          id: 'system-config/auth',
          label: 'Auth 模块',
        },
        {
          type: 'doc',
          id: 'system-config/market',
          label: 'Market 模块',
        },
        {
          type: 'doc',
          id: 'system-config/message',
          label: 'Message 模块',
        },
        {
          type: 'doc',
          id: 'system-config/weixin-work',
          label: '企业微信模块',
        },
        {
          type: 'doc',
          id: 'system-config/elasticsearch',
          label: 'Elasticsearch 模块',
        },
        {
          type: 'doc',
          id: 'system-config/monitor',
          label: 'Monitor 模块',
        },
      ],
    },
    {
      type: 'category',
      label: '文件处理',
      items: [
        {
          type: 'doc',
          id: 'file-processing/upload',
          label: '文件上传',
        },
        {
          type: 'doc',
          id: 'file-processing/download',
          label: '文件下载',
        },
      ],
    },
    {
      type: 'category',
      label: 'MQL语法',
      items: [
        {
          type: 'doc',
          id: 'mql/overview',
          label: '概览',
        },
        {
          type: 'doc',
          id: 'mql/usage',
          label: '语法与用法',
        },
      ],
    },
    {
      type: 'category',
      label: '安全性',
      items: [
        {
          type: 'doc',
          id: 'runtime/security-context-lifecycle',
          label: 'SecurityContext 生命周期',
        },
        {
          type: 'doc',
          id: 'authentication/security-authentication',
          label: '认证鉴权',
        },
      ],
    },
    {
      type: 'category',
      label: '可扩展性',
      items: [
        {
          type: 'doc',
          id: 'reference/metastore-extension',
          label: 'MetaStore 扩展',
        },
        {
          type: 'doc',
          id: 'orm/datasource-extension',
          label: 'ORM / 数据源扩展',
        },
        {
          type: 'doc',
          id: 'reference/security-provider-extension',
          label: '安全 Provider 扩展',
        },
        {
          type: 'doc',
          id: 'reference/override-default-implementations',
          label: '覆盖默认实现',
        },
      ],
    },
    {
      type: 'category',
      label: '平台能力',
      items: [
        {
          type: 'doc',
          id: 'platform-capabilities/global-context',
          label: '全局上下文',
        },
        {
          type: 'doc',
          id: 'platform-capabilities/event-bus',
          label: '事件总线',
        },
        {
          type: 'doc',
          id: 'platform-capabilities/sse-subscription',
          label: 'SSE订阅推送',
        },
        {
          type: 'doc',
          id: 'platform-capabilities/traffic-tagging',
          label: '流量染色',
        },
      ],
    },
    {
      type: 'category',
      label: '参考手册',
      items: [
        {
          type: 'doc',
          id: 'reference/bom-and-starter',
          label: 'BOM 与 Starter',
        },
        {
          type: 'doc',
          id: 'reference/startup-process',
          label: '启动过程',
        },
        {
          type: 'doc',
          id: 'reference/core-modules',
          label: '核心模块说明',
        },
        {
          type: 'doc',
          id: 'operations/document-governance',
          label: '文档治理',
        },
      ],
    },
    {
      type: 'category',
      label: '架构设计',
      items: [
        {
          type: 'doc',
          id: 'runtime/platform-web-runtime',
          label: 'PlatformWebRuntime',
        },
        {
          type: 'doc',
          id: 'designer/platform-desginer',
          label: 'PlatformDesginer',
        },
      ],
    },
    {
      type: 'category',
      label: '统一认证',
      items: [
        'authentication/overview',
        'authentication/architecture',
        'authentication/lite-login-integration',
      ],
    },
    {
      type: 'category',
      label: 'API参考',
      items: ['api/reference', 'api/srvexplain-catalog'],
    },
  ],
};

export default sidebars;
