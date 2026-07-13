import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const repoEditBase =
  'https://github.com/geelato-projects/geelato-community-backend/edit/master';

const config: Config = {
  title: 'Geelato Project',
  tagline: 'Enterprise Low-Code & Pro-Code Solutions',
  favicon: 'img/favicon.ico',
  future: {
    v4: true,
  },
  url: 'https://docs.geelato.cn',
  baseUrl: '/',
  organizationName: 'geelato-projects',
  projectName: 'geelato-community-backend',
  deploymentBranch: 'gh-pages',
  onBrokenLinks: 'throw',
  i18n: {
    defaultLocale: 'zh-cn',
    locales: ['zh-cn', 'en'],
    localeConfigs: {
      'zh-cn': {
        htmlLang: 'zh-CN',
        label: '中文',
      },
      en: {
        htmlLang: 'en-US',
        label: 'English',
      },
    },
  },
  presets: [
    [
      'classic',
      {
        docs: {
          path: './official-docs/zh-cn',
          routeBasePath: 'docs',
          sidebarPath: './sidebars.ts',
          editUrl: ({locale, docPath}) =>
            locale === 'en'
              ? `${repoEditBase}/website/i18n/en/docusaurus-plugin-content-docs/current/${docPath}`
              : `${repoEditBase}/website/official-docs/zh-cn/${docPath}`,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],
  themes: [
    '@docusaurus/theme-mermaid',
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        indexBlog: false,
        indexPages: false,
        docsRouteBasePath: 'docs',
        docsDir: [
          './official-docs/zh-cn',
          './i18n/en/docusaurus-plugin-content-docs/current',
        ],
        language: ['en', 'zh'],
        highlightSearchTermsOnTargetPage: true,
        removeDefaultStemmer: true,
      },
    ],
  ],
  markdown: {
    mermaid: true,
  },
  themeConfig: {
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: false,
    },
    navbar: {
      title: 'Geelato Project',
      logo: {
        alt: 'Geelato Project Logo',
        src: 'img/logo.svg',
      },
      items: [
        {to: '/', label: '首页', position: 'left'},
        {
          to: '/docs/guide/quick-start',
          label: '框架',
          position: 'left',
        },
        {
          to: '/docs/authentication/overview',
          label: '认证',
          position: 'left',
        },
        {
          to: '/docs/message/overview',
          label: '消息',
          position: 'left',
        },
        {
          to: '/docs/api/reference',
          label: 'API',
          position: 'left',
        },
        {
          href: 'https://github.com/geelato-projects/geelato-hello-example',
          label: '示例',
          position: 'left',
        },
        {
          type: 'search',
          position: 'right',
        },
        {
          type: 'localeDropdown',
          position: 'right',
        },
        {
          href: 'https://github.com/geelato-projects/geelato-community-backend',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'light',
      links: [
        {
          title: 'Geelato 项目',
          items: [
            {label: '开发者导航', to: '/docs/guide/developer-navigation'},
            {label: '快速开始', to: '/docs/guide/quick-start'},
          ],
        },
        {
          title: '参考',
          items: [
            {label: '最小接入', to: '/docs/guide/minimal-integration'},
            {label: 'Runtime / Designer 部署', to: '/docs/operations/runtime-designer-deployment'},
            {label: 'API 双轨入口', to: '/docs/api/reference'},
          ],
        },
        {
          title: '仓库',
          items: [
            {
              label: 'official-docs',
              href: 'https://github.com/geelato-projects/geelato-community-backend/tree/master/website/official-docs',
            },
            {
              label: 'SrvExplain',
              href: 'https://github.com/geelato-projects/geelato-community-backend/tree/master/SrvExplain',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Geelato Framework. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
