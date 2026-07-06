import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const repoEditBase =
  'https://github.com/geelato-projects/geelato-community-backend/edit/master';

const config: Config = {
  title: 'Geelato Framework',
  tagline: '官方框架文档站，覆盖 Starter、Runtime、Designer 与 API 双轨入口。',
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
    defaultLocale: 'en',
    locales: ['en'],
  },
  presets: [
    [
      'classic',
      {
        docs: false,
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],
  themes: ['@docusaurus/theme-mermaid'],
  markdown: {
    mermaid: true,
  },
  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'en',
        path: './official-docs/en',
        routeBasePath: 'en',
        sidebarPath: './sidebars.en.ts',
        editUrl: `${repoEditBase}/website/official-docs/en/`,
      },
    ],
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'zh-cn',
        path: './official-docs/zh-cn',
        routeBasePath: 'zh-cn',
        sidebarPath: './sidebars.zh-cn.ts',
        editUrl: `${repoEditBase}/website/official-docs/zh-cn/`,
      },
    ],
  ],
  themeConfig: {
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      defaultMode: 'light',
      respectPrefersColorScheme: false,
    },
    navbar: {
      title: 'Geelato Framework',
      logo: {
        alt: 'Geelato Framework Logo',
        src: 'img/logo.png',
      },
      items: [
        {to: '/', label: 'Home', position: 'left'},
        {
          href: '/zh-cn/authentication/overview',
          label: '统一认证',
          position: 'left',
        },
        {
          href: '/zh-cn/api/reference',
          label: 'API',
          position: 'left',
        },
        {
          href: 'https://github.com/geelato-projects/geelato-hello-example',
          label: 'Example',
          position: 'left',
        },
        {
          label: 'Language',
          position: 'right',
          items: [
            {label: 'English', to: '/en/guide/developer-navigation'},
            {label: '中文', to: '/zh-cn/guide/developer-navigation'},
          ],
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
          title: 'Framework',
          items: [
            {label: 'English Docs', to: '/en/guide/developer-navigation'},
            {label: '中文文档', to: '/zh-cn/guide/developer-navigation'},
          ],
        },
        {
          title: 'Reference',
          items: [
            {label: 'Minimal Integration', to: '/en/guide/minimal-integration'},
            {label: 'Runtime / Designer 部署', to: '/zh-cn/operations/runtime-designer-deployment'},
            {label: 'API 双轨入口', to: '/zh-cn/api/reference'},
          ],
        },
        {
          title: 'Repository',
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
