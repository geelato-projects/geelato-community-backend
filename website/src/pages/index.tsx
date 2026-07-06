import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import {
  ArrowRight,
  BookOpenText,
  FileCode2,
  Globe2,
  Layers3,
  Rocket,
  ShieldCheck,
} from 'lucide-react';

import styles from './index.module.css';

const highlights = [
  {
    title: '极速应用构建',
    description:
      '提供完整的业务脚手架与最小化接入两种模式，无论全新项目还是老项目改造，都能分钟级启动。',
    icon: Rocket,
  },
  {
    title: 'AI 与开发者友好',
    description:
      '首创《开发者与 AI 导航手册》，内置标准化提示词上下文，让大模型（如 Trae/Copilot）秒懂框架底层。',
    icon: BookOpenText,
  },
  {
    title: '强大的平台扩展',
    description:
      '独创 MQL 语法与 Fluent DSL ORM，结合完备的插件机制与 SPI，轻松应对复杂企业级定制需求。',
    icon: Layers3,
  },
];

const capabilityCards = [
  {
    label: 'MQL & ORM',
    detail: '元数据查询语言与 Fluent DSL，无缝兼容 MySQL、PostgreSQL、Oracle 等多款主流关系型数据库。',
  },
  {
    label: 'Runtime & Designer',
    detail: '运行时与设计时职责分离，支撑低代码与全代码混合开发。',
  },
  {
    label: 'Auth Server',
    detail: '独立的统一认证中心，提供标准 OAuth2 与极简 lite-login 接入。',
  },
  {
    label: 'Plugin & SPI',
    detail: '围绕 Meta、Security、ORM 建立可替换的平台级扩展点。',
  },
];

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();

  return (
    <Layout
      title="Official Docs"
      description="Geelato Framework 官方文档站，提供中英双语、Starter 接入、Runtime/Designer 分层与 API 双轨入口。">
      <main className={styles.page}>
        <section className={styles.hero}>
          <div className={styles.heroGlow} />
          <div className="container">
            <div className={styles.heroShell}>
              <div className={styles.heroCopy}>
                <div className={styles.eyebrow}>
                  <Rocket size={16} />
                  Enterprise Low-Code & Pro-Code Framework
                </div>
                <Heading as="h1" className={styles.heroTitle}>
                  {siteConfig.title}
                </Heading>
                <p className={styles.heroText}>
                  不仅仅是文档，更是开发者与 AI 协同构建复杂业务系统的超级知识库。提供从底层 ORM、独立认证中心到上层低代码建模的全套解决方案。
                </p>
                <div className={styles.heroActions}>
                  <Link className={styles.primaryAction} to="/zh-cn/guide/quick-start">
                    快速开始
                    <ArrowRight size={18} />
                  </Link>
                  <Link className={styles.secondaryAction} to="/zh-cn/guide/developer-navigation">
                    AI 导航手册
                  </Link>
                </div>
                <div className={styles.statRow}>
                  <span>极速骨架</span>
                  <span>全代码开发</span>
                  <span>低代码引擎</span>
                </div>
              </div>
              <div className={styles.heroPanel}>
                <div className={styles.panelHeader}>
                  <Layers3 size={18} />
                  核心能力矩阵
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
              <span className={styles.sectionTag}>Features</span>
              <Heading as="h2">核心价值主张</Heading>
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
                <Heading as="h3">开发者 (Developer)</Heading>
                <p>从脚手架或最小接入启动，掌握 MQL 与 ORM 核心语法，快速完成业务 CRUD 与扩展定制。</p>
                <Link className={styles.inlineLink} to="/zh-cn/guide/quick-start">
                  查看快速入门
                </Link>
              </article>
              <article className={styles.pathCard}>
                <Heading as="h3">系统架构师 (Architect)</Heading>
                <p>聚焦独立 Auth Server 接入、Runtime/Designer 架构设计、安全上下文与高可用部署治理。</p>
                <Link className={styles.inlineLink} to="/zh-cn/authentication/overview">
                  了解统一认证中心
                </Link>
              </article>
              <article className={styles.pathCard}>
                <Heading as="h3">AI 编程助手 (AI Agent)</Heading>
                <p>通过导航手册快速获取框架“黑话”与上下文设定，辅助开发者生成符合最佳实践的高标准业务代码。</p>
                <Link className={styles.inlineLink} to="/zh-cn/guide/developer-navigation">
                  打开 AI 导航手册
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
                <Heading as="h2">为现代企业级系统与 AI 协同开发而生。</Heading>
              </div>
              <div className={styles.bottomActions}>
                <Link className={styles.secondaryAction} to="/en/guide/developer-navigation">
                  English Guide
                </Link>
                <Link className={styles.primaryAction} to="/zh-cn/guide/quick-start">
                  阅读官方指南
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
