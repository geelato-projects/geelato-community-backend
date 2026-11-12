import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  frameworkSidebar: [
    {
      type: 'category',
      label: '🌐 Geelato Project',
      collapsible: false,
      collapsed: false,
      items: ['guide/developer-navigation'],
    },
    {
      type: 'category',
      label: '🧩 Geelato Framework (框架)',
      collapsible: false,
      collapsed: false,
      items: [
        {
          type: 'category',
          label: '🌱 QuickStart',
          items: [
            'guide/quick-start',
            'guide/app-scaffold-starter-project-guide',
            'guide/minimal-integration',
          ],
        },
        {
          type: 'category',
          label: '🧠 框架架构设计说明',
          items: [
            'reference/core-modules',
            'reference/startup-process',
            'runtime/platform-web-runtime',
            'designer/platform-desginer',
          ],
        },
        {
          type: 'category',
          label: '⚙️ 框架能力',
          items: [
            {
              type: 'category',
              label: '元数据',
              items: ['metadata/overview', 'reference/metastore-extension'],
            },
            {
              type: 'category',
              label: '数据源',
              items: [
                'dynamic-datasource/overview',
                'dynamic-datasource/entity-binding',
                'dynamic-datasource/configuration',
                'dynamic-datasource/switching',
                'dynamic-datasource/host-mapping',
                'dynamic-datasource/materialized-view',
                'dynamic-datasource/risingwave-engine',
                'orm/datasource-extension',
              ],
            },
            {
              type: 'category',
              label: 'ORM',
              items: ['orm/overview', 'orm/annotations', 'orm/fluent-dsl'],
            },
            {
              type: 'category',
              label: 'ORM 高级',
              items: [
                'orm/event-features',
                'reference/spi-query-filter-and-save-fill-extension',
              ],
            },
            {
              type: 'category',
              label: '脚本引擎',
              items: [
                'script/overview',
                'script/usage',
                'script/context',
                'script/kits',
                'script/custom-kit',
                'script/host-library',
                'script/debugging',
              ],
            },
            {
              type: 'category',
              label: '安全协议',
              items: [
                'security/org-user-role',
                'authentication/security-authentication',
                'runtime/security-context-lifecycle',
                'reference/security-provider-extension',
              ],
            },
            {
              type: 'category',
              label: '全局上下文',
              items: ['platform-capabilities/global-context'],
            },
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '🚀 Geelato Platform (平台)',
      collapsible: false,
      collapsed: false,
      items: [
        {
          type: 'category',
          label: 'MQL 使用和语法',
          items: ['mql/overview', 'mql/usage'],
        },
        {
          type: 'category',
          label: '文件处理',
          items: ['file-processing/upload', 'file-processing/download'],
        },
        {
          type: 'category',
          label: '事件总线与 SSE 订阅推送',
          items: ['platform-capabilities/event-bus', 'platform-capabilities/sse-subscription'],
        },
        {
          type: 'category',
          label: '流量标记',
          items: ['platform-capabilities/traffic-tagging'],
        },
        {
          type: 'category',
          label: '插件机制',
          items: [
            'plugin-mechanism/overview',
            'plugin-mechanism/development',
            'plugin-mechanism/lifecycle',
            'plugin-mechanism/repository',
          ],
        },
        {
          type: 'category',
          label: '内置维护',
          items: ['maintenance/overview'],
        },
        {
          type: 'category',
          label: '搜索引擎',
          items: ['search/overview', 'search/extension'],
        },
        {
          type: 'category',
          label: '数据归档',
          items: ['archive/overview'],
        },
        {
          type: 'category',
          label: '业务异常',
          items: ['exception/overview', 'exception/usage'],
        },
        {
          type: 'category',
          label: '业务告警',
          items: ['alert/overview', 'alert/usage'],
        },
        {
          type: 'category',
          label: '应用打包',
          items: ['packaging/overview'],
        },
        {
          type: 'category',
          label: '⚙️ 系统配置与运维',
          items: [
            {
              type: 'category',
              label: '系统模块配置',
              items: [
                'system-config/overview',
                'system-config/seata',
                'system-config/oss',
                'system-config/package',
                'system-config/sc',
                'system-config/auth',
                'system-config/market',
                'system-config/message',
                'system-config/weixin-work',
                'system-config/elasticsearch',
                'system-config/monitor',
              ],
            },
            {
              type: 'category',
              label: '平台部署指南',
              items: [
                'operations/runtime-designer-deployment',
                'operations/docker-deployment',
              ],
            },
          ],
        },
        {
          type: 'category',
          label: '📚 API 与参考手册',
          items: [
            'api/reference',
            'api/srvexplain-catalog',
            'operations/document-governance',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '🔐 Geelato AuthServer (统一认证)',
      collapsible: false,
      collapsed: false,
      items: [
        'authentication/overview',
        'authentication/lite-login-integration',
        'authentication/oauth2-integration',
        'authentication/permission-integration',
        'authentication/client-credentials-integration',
        'authentication/open-api-management',
      ],
    },
    {
      type: 'category',
      label: '📨 Geelato Message (消息中心)',
      collapsible: false,
      collapsed: false,
      items: ['message/overview', 'message/integration', 'message/integration-management'],
    },
    {
      type: 'category',
      label: '🛠️ Geelato Workflow (工作流)',
      collapsible: false,
      collapsed: false,
      items: [
        'workflow/overview',
        'workflow/api',
        'workflow/integration',
        'system-config/workflow',
      ],
    },
    {
      type: 'category',
      label: '⏰ Geelato Scheduler (调度)',
      collapsible: false,
      collapsed: false,
      items: ['scheduler/overview', 'scheduler/usage'],
    },
    {
      type: 'category',
      label: '🧩 Geelato Dyn (动态集成)',
      collapsible: false,
      collapsed: false,
      items: [
        'geelato-dyn/overview',
        'geelato-dyn/installation',
        'geelato-dyn/configuration',
        'geelato-dyn/capabilities',
        'geelato-dyn/message-dispatch',
        'geelato-dyn/callback-bus',
        'geelato-dyn/develop-module',
      ],
    },
    {
      type: 'category',
      label: '📖 帮助中心',
      collapsible: false,
      collapsed: false,
      items: ['help/glossary', 'help/faq', 'help/troubleshooting'],
    },
  ],
};

export default sidebars;
