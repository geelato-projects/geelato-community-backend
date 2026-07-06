import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  frameworkZhSidebar: [
    {
      type: 'category',
      label: '🌐 Geelato Project',
      collapsible: false,
      collapsed: false,
      items: [
        'guide/developer-navigation',
      ],
    },
    {
      type: 'category',
      label: '📦 Geelato Framework',
      collapsible: false,
      collapsed: false,
      items: [
        {
          type: 'category',
          label: '🌱 快速入门',
          items: [
            'guide/quick-start',
            'guide/app-scaffold-starter-project-guide',
            'guide/minimal-integration',
          ],
        },
        {
          type: 'category',
          label: '🧠 架构与核心概念',
          items: [
            'runtime/platform-web-runtime',
            'designer/platform-desginer',
            'reference/core-modules',
            'reference/startup-process',
            'reference/bom-and-starter',
          ],
        },
        {
          type: 'category',
          label: '🛠️ 开发指南：基础能力',
          items: [
            {
              type: 'category',
              label: 'ORM 与数据源',
              items: [
                'orm/overview',
                'orm/annotations',
                'orm/fluent-dsl',
                'orm/event-features',
                'dynamic-datasource/overview',
                'dynamic-datasource/host-mapping',
              ],
            },
            {
              type: 'category',
              label: 'MQL 语法',
              items: [
                'mql/overview',
                'mql/usage',
              ],
            },
            {
              type: 'category',
              label: '文件处理',
              items: [
                'file-processing/upload',
                'file-processing/download',
              ],
            },
          ],
        },
        {
          type: 'category',
          label: '🔌 开发指南：高级特性',
          items: [
            {
              type: 'category',
              label: '平台能力',
              items: [
                'platform-capabilities/global-context',
                'platform-capabilities/event-bus',
                'platform-capabilities/sse-subscription',
                'platform-capabilities/traffic-tagging',
                'runtime/security-context-lifecycle',
                'authentication/security-authentication',
              ],
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
              label: '可扩展性体系',
              items: [
                'reference/metastore-extension',
                'orm/datasource-extension',
                'reference/security-provider-extension',
                'reference/override-default-implementations',
              ],
            },
          ],
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
                'system-config/workflow',
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
            'operations/document-governance',
          ],
        },
        {
          type: 'category',
          label: '📚 API 与参考手册',
          items: [
            'api/reference',
            'api/srvexplain-catalog',
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
      ],
    },
    {
      type: 'category',
      label: '📨 Geelato Message (消息中心)',
      collapsible: false,
      collapsed: false,
      items: [
        'message/overview',
        'message/integration',
      ],
    },
  ],
};

export default sidebars;
