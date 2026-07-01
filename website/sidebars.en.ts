import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  frameworkEnSidebar: [
    'intro',
    {
      type: 'category',
      label: 'Guide',
      items: [
        'guide/quick-start',
        'guide/minimal-integration',
        'guide/sample-quickstart',
        'guide/app-scaffold',
        'guide/default-implementation-vs-sample',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
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
      label: 'Runtime',
      items: ['runtime/platform-web-runtime', 'runtime/security-context-lifecycle'],
    },
    {
      type: 'category',
      label: 'Designer',
      items: ['designer/platform-desginer'],
    },
    {
      type: 'category',
      label: 'Authentication',
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
      label: 'Operations',
      items: ['operations/document-governance', 'operations/runtime-designer-deployment'],
    },
  ],
};

export default sidebars;
