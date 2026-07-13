import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import Translate, {translate} from '@docusaurus/Translate';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import {
  ArrowRight,
  BookOpenText,
  Layers3,
  Rocket,
} from 'lucide-react';

import styles from './index.module.css';

export default function Home(): ReactNode {
  const quickStartLink = useBaseUrl('/docs/guide/quick-start');
  const developerGuideLink = useBaseUrl('/docs/guide/developer-navigation');
  const authOverviewLink = useBaseUrl('/docs/authentication/overview');

  const highlights = [
    {
      title: translate({
        id: 'homepage.highlights.fastBuild.title',
        message: '极速应用构建',
        description: 'Homepage fast build card title',
      }),
      description: translate({
        id: 'homepage.highlights.fastBuild.description',
        message:
          '提供完整的业务脚手架与最小化接入两种模式，无论全新项目还是老项目改造，都能分钟级启动。',
        description: 'Homepage fast build card description',
      }),
      icon: Rocket,
    },
    {
      title: translate({
        id: 'homepage.highlights.aiFriendly.title',
        message: 'AI 与开发者友好',
        description: 'Homepage AI friendly card title',
      }),
      description: translate({
        id: 'homepage.highlights.aiFriendly.description',
        message:
          '首创《开发者与 AI 导航手册》，内置标准化提示词上下文，让大模型（如 Trae/Copilot）秒懂框架底层。',
        description: 'Homepage AI friendly card description',
      }),
      icon: BookOpenText,
    },
    {
      title: translate({
        id: 'homepage.highlights.extensibility.title',
        message: '强大的平台扩展',
        description: 'Homepage extensibility card title',
      }),
      description: translate({
        id: 'homepage.highlights.extensibility.description',
        message:
          '独创 MQL 语法与 Fluent DSL ORM，结合完备的插件机制与 SPI，轻松应对复杂企业级定制需求。',
        description: 'Homepage extensibility card description',
      }),
      icon: Layers3,
    },
  ];

  const capabilityCards = [
    {
      label: 'MQL & ORM',
      detail: translate({
        id: 'homepage.capabilities.mql',
        message:
          '元数据查询语言与 Fluent DSL，无缝兼容 MySQL、PostgreSQL、Oracle 等多款主流关系型数据库。',
        description: 'Homepage capability card for MQL and ORM',
      }),
    },
    {
      label: 'Runtime & Designer',
      detail: translate({
        id: 'homepage.capabilities.runtime',
        message: '运行时与设计时职责分离，支撑低代码与全代码混合开发。',
        description: 'Homepage capability card for runtime and designer',
      }),
    },
    {
      label: 'Auth Server',
      detail: translate({
        id: 'homepage.capabilities.auth',
        message: '独立的统一认证中心，提供标准 OAuth2 与极简 lite-login 接入。',
        description: 'Homepage capability card for auth server',
      }),
    },
    {
      label: 'Plugin & SPI',
      detail: translate({
        id: 'homepage.capabilities.plugin',
        message: '围绕 Meta、Security、ORM 建立可替换的平台级扩展点。',
        description: 'Homepage capability card for plugin and SPI',
      }),
    },
  ];

  return (
    <Layout
      title={translate({
        id: 'homepage.meta.title',
        message: 'Geelato Project - Enterprise Low-Code & Pro-Code Solutions',
        description: 'Homepage SEO title',
      })}
      description={translate({
        id: 'homepage.meta.description',
        message:
          '不仅仅是文档，更是开发者与 AI 协同构建复杂业务系统的超级知识库。涵盖 Geelato Framework、AuthServer 以及 Message 中心。',
        description: 'Homepage SEO description',
      })}>
      <main className={styles.page}>
        <section className={styles.hero}>
          <div className={styles.heroGlow} />
          <div className="container">
            <div className={styles.heroShell}>
              <div className={styles.heroCopy}>
                <div className={styles.eyebrow}>
                  <Rocket size={16} />
                  <Translate
                    id="homepage.hero.eyebrow"
                    description="Homepage eyebrow text">
                    Enterprise Low-Code & Pro-Code Solutions
                  </Translate>
                </div>
                <Heading as="h1" className={styles.heroTitle}>
                  Geelato Project
                </Heading>
                <p className={styles.heroText}>
                  <Translate
                    id="homepage.hero.description.line1"
                    description="Homepage hero first line">
                    不仅仅是文档，更是开发者与 AI 协同构建复杂业务系统的超级知识库。
                  </Translate>
                  <br />
                  <Translate
                    id="homepage.hero.description.line2"
                    description="Homepage hero second line with product pillars"
                    values={{
                      framework: <b>Geelato Framework</b>,
                      authServer: <b>AuthServer</b>,
                      message: <b>Message</b>,
                    }}>
                    {
                      '项目包含三大核心支柱：{framework}（底层架构与建模）、{authServer}（统一认证）以及 {message}（统一消息）。'
                    }
                  </Translate>
                </p>
                <div className={styles.heroActions}>
                  <Link className={styles.primaryAction} to={quickStartLink}>
                    <Translate
                      id="homepage.hero.primaryAction"
                      description="Homepage primary CTA">
                      快速开始
                    </Translate>
                    <ArrowRight size={18} />
                  </Link>
                  <Link className={styles.secondaryAction} to={developerGuideLink}>
                    <Translate
                      id="homepage.hero.secondaryAction"
                      description="Homepage secondary CTA">
                      AI 导航手册
                    </Translate>
                  </Link>
                </div>
                <div className={styles.statRow}>
                  <span>
                    <Translate
                      id="homepage.hero.stat.scaffold"
                      description="Homepage stat pill for scaffold">
                      极速骨架
                    </Translate>
                  </span>
                  <span>
                    <Translate
                      id="homepage.hero.stat.proCode"
                      description="Homepage stat pill for pro-code">
                      全代码开发
                    </Translate>
                  </span>
                  <span>
                    <Translate
                      id="homepage.hero.stat.lowCode"
                      description="Homepage stat pill for low-code">
                      低代码引擎
                    </Translate>
                  </span>
                </div>
              </div>
              <div className={styles.heroPanel}>
                <div className={styles.panelHeader}>
                  <Layers3 size={18} />
                  <Translate
                    id="homepage.hero.panelTitle"
                    description="Homepage capability matrix title">
                    核心能力矩阵
                  </Translate>
                </div>
                <div className={styles.panelGrid}>
                  {capabilityCards.map((card) => (
                    <article key={card.label} className={styles.panelCard}>
                      <span className={styles.panelLabel}>{card.label}</span>
                      <p>{card.detail}</p>
                    </article>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className={styles.section}>
          <div className="container">
            <div className={styles.sectionHeading}>
              <span className={styles.sectionTag}>
                <Translate
                  id="homepage.features.tag"
                  description="Homepage features tag">
                  Features
                </Translate>
              </span>
              <Heading as="h2">
                <Translate
                  id="homepage.features.title"
                  description="Homepage features section title">
                  核心价值主张
                </Translate>
              </Heading>
            </div>
            <div className={styles.cardGrid}>
              {highlights.map((item) => {
                const Icon = item.icon;
                return (
                  <article key={item.title} className={styles.infoCard}>
                    <div className={styles.iconWrap}>
                      <Icon size={20} />
                    </div>
                    <Heading as="h3">{item.title}</Heading>
                    <p>{item.description}</p>
                  </article>
                );
              })}
            </div>
          </div>
        </section>

        <section className={styles.section}>
          <div className="container">
            <div className={styles.pathGrid}>
              <article className={styles.pathCard}>
                <Heading as="h3">
                  <Translate
                    id="homepage.paths.developer.title"
                    description="Homepage developer path title">
                    开发者 (Developer)
                  </Translate>
                </Heading>
                <p>
                  <Translate
                    id="homepage.paths.developer.description"
                    description="Homepage developer path description">
                    从脚手架或最小接入启动，掌握 MQL 与 ORM 核心语法，快速完成业务 CRUD 与扩展定制。
                  </Translate>
                </p>
                <Link className={styles.inlineLink} to={quickStartLink}>
                  <Translate
                    id="homepage.paths.developer.link"
                    description="Homepage developer path link">
                    查看快速入门
                  </Translate>
                </Link>
              </article>
              <article className={styles.pathCard}>
                <Heading as="h3">
                  <Translate
                    id="homepage.paths.architect.title"
                    description="Homepage architect path title">
                    系统架构师 (Architect)
                  </Translate>
                </Heading>
                <p>
                  <Translate
                    id="homepage.paths.architect.description"
                    description="Homepage architect path description">
                    聚焦独立 Auth Server 接入、Runtime/Designer 架构设计、安全上下文与高可用部署治理。
                  </Translate>
                </p>
                <Link className={styles.inlineLink} to={authOverviewLink}>
                  <Translate
                    id="homepage.paths.architect.link"
                    description="Homepage architect path link">
                    了解统一认证中心
                  </Translate>
                </Link>
              </article>
              <article className={styles.pathCard}>
                <Heading as="h3">
                  <Translate
                    id="homepage.paths.ai.title"
                    description="Homepage AI path title">
                    AI 编程助手 (AI Agent)
                  </Translate>
                </Heading>
                <p>
                  <Translate
                    id="homepage.paths.ai.description"
                    description="Homepage AI path description">
                    通过导航手册快速获取框架“黑话”与上下文设定，辅助开发者生成符合最佳实践的高标准业务代码。
                  </Translate>
                </p>
                <Link className={styles.inlineLink} to={developerGuideLink}>
                  <Translate
                    id="homepage.paths.ai.link"
                    description="Homepage AI path link">
                    打开 AI 导航手册
                  </Translate>
                </Link>
              </article>
            </div>
          </div>
        </section>

        <section className={styles.bottomStrip}>
          <div className="container">
            <div className={styles.bottomShell}>
              <div>
                <span className={styles.sectionTag}>Geelato</span>
                <Heading as="h2">
                  <Translate
                    id="homepage.bottom.title"
                    description="Homepage bottom section title">
                    为现代企业级系统与 AI 协同开发而生。
                  </Translate>
                </Heading>
              </div>
              <div className={styles.bottomActions}>
                <Link className={styles.secondaryAction} to={developerGuideLink}>
                  <Translate
                    id="homepage.bottom.secondaryAction"
                    description="Homepage bottom secondary CTA">
                    开发者导航
                  </Translate>
                </Link>
                <Link className={styles.primaryAction} to={quickStartLink}>
                  <Translate
                    id="homepage.bottom.primaryAction"
                    description="Homepage bottom primary CTA">
                    阅读官方指南
                  </Translate>
                  <ArrowRight size={18} />
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
