import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  frameworkZhSidebar: [
    'intro',
    {
      type: 'category',
      label: '快速开始',
      items: [
        'guide/quick-start',
        'guide/minimal-integration',
        'guide/sample-quickstart',
        'guide/app-scaffold',
        'guide/app-scaffold-starter-project-guide',
        'guide/default-implementation-vs-sample',
      ],
    },
    {
      type: 'category',
      label: '参考手册',
      items: [
        'reference/bom-and-starter',
        'reference/core-modules',
        'reference/metastore-extension',
        'reference/override-default-implementations',
      ],
    },
    {
      type: 'category',
      label: 'ORM',
      items: [
        'orm/overview',
        'orm/annotations',
        'orm/fluent-dsl',
        'orm/datasource-extension',
      ],
    },
    {
      type: 'category',
      label: 'MQL',
      items: ['mql/overview', 'mql/usage'],
    },
    {
      type: 'category',
      label: '运行时',
      items: ['runtime/platform-web-runtime', 'runtime/security-context-lifecycle'],
    },
    {
      type: 'category',
      label: '设计时',
      items: ['designer/platform-desginer'],
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
      label: 'API',
      items: ['api/reference', 'api/srvexplain-catalog'],
    },
    {
      type: 'category',
      label: '运维与治理',
      items: ['operations/document-governance', 'operations/runtime-designer-deployment'],
    },
  ],
};

export default sidebars;
