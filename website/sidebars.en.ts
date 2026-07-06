import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  frameworkEnSidebar: [
    'intro',
    {
      type: 'category',
      label: 'Quick Start',
      items: [
        {
          type: 'doc',
          id: 'guide/quick-start',
          label: 'Quick Start',
        },
        {
          type: 'doc',
          id: 'guide/minimal-integration',
          label: 'Minimal Integration',
        },
        {
          type: 'doc',
          id: 'guide/default-implementation-vs-sample',
          label: 'Default Implementations vs Sample',
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
              id: 'guide/app-scaffold',
              label: 'Project Guide',
            },
            {
              type: 'doc',
              id: 'guide/app-scaffold-starter-project-guide',
              label: 'Starter Project Guide',
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
      label: 'Platform Deployment',
      items: [
        {
          type: 'doc',
          id: 'operations/runtime-designer-deployment',
          label: 'Standard Deployment',
        },
        {
          type: 'doc',
          id: 'operations/docker-deployment',
          label: 'Docker Deployment',
        },
      ],
    },
    {
      type: 'category',
      label: 'ORM Library',
      items: [
        {
          type: 'doc',
          id: 'orm/overview',
          label: 'ORM Overview',
        },
        {
          type: 'doc',
          id: 'orm/annotations',
          label: 'Annotations',
        },
        {
          type: 'doc',
          id: 'orm/fluent-dsl',
          label: 'Fluent DSL',
        },
        {
          type: 'doc',
          id: 'orm/event-features',
          label: 'Event Features',
        },
      ],
    },
    {
      type: 'category',
      label: 'Dynamic Datasource',
      items: [
        {
          type: 'doc',
          id: 'dynamic-datasource/overview',
          label: 'Dynamic Datasource Capability',
        },
      ],
    },
    {
      type: 'category',
      label: 'Plugin Mechanism',
      items: [
        {
          type: 'doc',
          id: 'plugin-mechanism/overview',
          label: 'Overview',
        },
        {
          type: 'doc',
          id: 'plugin-mechanism/development',
          label: 'Definition and Development',
        },
        {
          type: 'doc',
          id: 'plugin-mechanism/lifecycle',
          label: 'Loading, Start/Stop and Uninstall',
        },
        {
          type: 'doc',
          id: 'plugin-mechanism/repository',
          label: 'Plugin Repository',
        },
      ],
    },
    {
      type: 'category',
      label: 'System Configuration',
      items: [
        {
          type: 'doc',
          id: 'system-config/overview',
          label: 'Main Config and Imports',
        },
        {
          type: 'doc',
          id: 'system-config/workflow',
          label: 'Workflow Module',
        },
        {
          type: 'doc',
          id: 'system-config/seata',
          label: 'Seata Module',
        },
        {
          type: 'doc',
          id: 'system-config/oss',
          label: 'OSS Module',
        },
        {
          type: 'doc',
          id: 'system-config/package',
          label: 'Package Module',
        },
        {
          type: 'doc',
          id: 'system-config/sc',
          label: 'SC Module',
        },
        {
          type: 'doc',
          id: 'system-config/auth',
          label: 'Auth Module',
        },
        {
          type: 'doc',
          id: 'system-config/market',
          label: 'Market Module',
        },
        {
          type: 'doc',
          id: 'system-config/message',
          label: 'Message Module',
        },
        {
          type: 'doc',
          id: 'system-config/weixin-work',
          label: 'Weixin Work Module',
        },
        {
          type: 'doc',
          id: 'system-config/elasticsearch',
          label: 'Elasticsearch Module',
        },
        {
          type: 'doc',
          id: 'system-config/monitor',
          label: 'Monitor Module',
        },
      ],
    },
    {
      type: 'category',
      label: 'File Processing',
      items: [
        {
          type: 'doc',
          id: 'file-processing/upload',
          label: 'File Upload',
        },
        {
          type: 'doc',
          id: 'file-processing/download',
          label: 'File Download',
        },
      ],
    },
    {
      type: 'category',
      label: 'MQL Syntax',
      items: [
        {
          type: 'doc',
          id: 'mql/overview',
          label: 'Overview',
        },
        {
          type: 'doc',
          id: 'mql/usage',
          label: 'Syntax and Usage',
        },
      ],
    },
    {
      type: 'category',
      label: 'Security',
      items: [
        {
          type: 'doc',
          id: 'runtime/security-context-lifecycle',
          label: 'SecurityContext Lifecycle',
        },
        {
          type: 'doc',
          id: 'authentication/security-authentication',
          label: 'Authentication and Authorization',
        },
      ],
    },
    {
      type: 'category',
      label: 'Extensibility',
      items: [
        {
          type: 'doc',
          id: 'reference/metastore-extension',
          label: 'MetaStore Extension',
        },
        {
          type: 'doc',
          id: 'orm/datasource-extension',
          label: 'ORM / Datasource Extension',
        },
        {
          type: 'doc',
          id: 'reference/security-provider-extension',
          label: 'Security Provider Extension',
        },
        {
          type: 'doc',
          id: 'reference/override-default-implementations',
          label: 'Override Default Implementations',
        },
      ],
    },
    {
      type: 'category',
      label: 'Platform Capabilities',
      items: [
        {
          type: 'doc',
          id: 'platform-capabilities/global-context',
          label: 'Global Context',
        },
        {
          type: 'doc',
          id: 'platform-capabilities/event-bus',
          label: 'Event Bus',
        },
        {
          type: 'doc',
          id: 'platform-capabilities/sse-subscription',
          label: 'SSE Subscription Push',
        },
        {
          type: 'doc',
          id: 'platform-capabilities/traffic-tagging',
          label: 'Traffic Tagging',
        },
      ],
    },
    {
      type: 'category',
      label: 'Reference Manual',
      items: [
        {
          type: 'doc',
          id: 'reference/bom-and-starter',
          label: 'BOM and Starter',
        },
        {
          type: 'doc',
          id: 'reference/startup-process',
          label: 'Startup Process',
        },
        {
          type: 'doc',
          id: 'reference/core-modules',
          label: 'Core Modules',
        },
        {
          type: 'doc',
          id: 'operations/document-governance',
          label: 'Documentation Governance',
        },
      ],
    },
    {
      type: 'category',
      label: 'Architecture Design',
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
      label: 'Unified Authentication',
      items: [
        'authentication/overview',
        'authentication/architecture',
        'authentication/lite-login-integration',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: ['api/reference', 'api/srvexplain-catalog'],
    },
  ],
};

export default sidebars;
