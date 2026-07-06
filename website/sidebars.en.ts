import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  frameworkEnSidebar: [
    {
      type: 'category',
      label: '🚀 Introduction & Navigation',
      collapsible: false,
      collapsed: false,
      items: [
        'guide/developer-navigation',
      ],
    },
    {
      type: 'category',
      label: '🌱 Getting Started',
      items: [
        'guide/quick-start',
        'guide/app-scaffold-starter-project-guide',
        'guide/minimal-integration',
      ],
    },
    {
      type: 'category',
      label: '🧠 Architecture & Core Concepts',
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
      label: '🛠️ Developer Guide: Basics',
      items: [
        {
          type: 'category',
          label: 'ORM & Datasource',
          items: [
            'orm/overview',
            'orm/annotations',
            'orm/fluent-dsl',
            'orm/event-features',
            'dynamic-datasource/overview',
          ],
        },
        {
          type: 'category',
          label: 'MQL Syntax',
          items: [
            'mql/overview',
            'mql/usage',
          ],
        },
        {
          type: 'category',
          label: 'File Processing',
          items: [
            'file-processing/upload',
            'file-processing/download',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '🔌 Developer Guide: Advanced',
      items: [
        {
          type: 'category',
          label: 'Platform Capabilities',
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
          label: 'Plugin Mechanism',
          items: [
            'plugin-mechanism/overview',
            'plugin-mechanism/development',
            'plugin-mechanism/lifecycle',
            'plugin-mechanism/repository',
          ],
        },
        {
          type: 'category',
          label: 'Extensibility',
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
      label: '🔐 Independent Service: Auth',
      items: [
        'authentication/overview',
        'authentication/lite-login-integration',
        'authentication/oauth2-integration',
      ],
    },
    {
      type: 'category',
      label: '⚙️ Configuration & Deployment',
      items: [
        {
          type: 'category',
          label: 'System Module Config',
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
          label: 'Deployment Guides',
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
      label: '📚 API & References',
      items: [
        'api/reference',
        'api/srvexplain-catalog',
      ],
    },
  ],
};

export default sidebars;
