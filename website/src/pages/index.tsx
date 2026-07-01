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
    title: 'Docs-as-Code',
    description:
      '官方文档源文件与仓库代码同行，围绕 BOM、Starter、Runtime、Designer 与 Sample 演进。',
    icon: BookOpenText,
  },
  {
    title: 'Bilingual Structure',
    description:
      '首版同时落中英文入口，便于对内推广与对外发布，后续可按版本持续扩展内容。',
    icon: Globe2,
  },
  {
    title: 'API Dual Track',
    description:
      'OpenAPI 负责标准契约，SrvExplain 保留静态扫描补充说明，减少迁移断层。',
    icon: FileCode2,
  },
];

const capabilityCards = [
  {
    label: 'Starter',
    detail: '统一接入入口，承接最小 Web 工程骨架。',
  },
  {
    label: 'Runtime',
    detail: '面向运行时部署与业务执行能力。',
  },
  {
    label: 'Designer',
    detail: '面向设计时配置、建模和治理能力。',
  },
  {
    label: 'SPI',
    detail: '围绕 Meta、Security、ORM、Datasource 建立可替换扩展点。',
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
                  Official framework documentation
                </div>
                <Heading as="h1" className={styles.heroTitle}>
                  {siteConfig.title}
                </Heading>
                <p className={styles.heroText}>
                  从分散的工程文档、样例 README 与静态接口说明，收敛为一个面向框架消费者和平台维护者的官方入口。
                </p>
                <div className={styles.heroActions}>
                  <Link className={styles.primaryAction} to="/zh-cn/intro">
                    进入中文文档
                    <ArrowRight size={18} />
                  </Link>
                  <Link className={styles.secondaryAction} to="/en/intro">
                    Open English Docs
                  </Link>
                </div>
                <div className={styles.statRow}>
                  <span>双语源文件</span>
                  <span>官方站点骨架</span>
                  <span>OpenAPI + SrvExplain</span>
                </div>
              </div>
              <div className={styles.heroPanel}>
                <div className={styles.panelHeader}>
                  <Layers3 size={18} />
                  Documentation stack
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
              <span className={styles.sectionTag}>First release scope</span>
              <Heading as="h2">首批官方内容骨架</Heading>
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
                <Heading as="h3">框架使用者</Heading>
                <p>从 BOM 与 Starter 入手，优先进入最小接入、Sample Quickstart、核心模块参考与扩展点说明。</p>
                <Link className={styles.inlineLink} to="/zh-cn/guide/minimal-integration">
                  查看最小接入
                </Link>
              </article>
              <article className={styles.pathCard}>
                <Heading as="h3">平台维护者</Heading>
                <p>聚焦 Runtime / Designer 分层、SecurityContext 生命周期、ORM/数据源扩展与部署治理。</p>
                <Link className={styles.inlineLink} to="/zh-cn/operations/runtime-designer-deployment">
                  查看部署说明
                </Link>
              </article>
              <article className={styles.pathCard}>
                <Heading as="h3">API 调用方</Heading>
                <p>统一从官方 API 页进入 OpenAPI 标准契约，同时保留 SrvExplain 静态说明作为补充入口。</p>
                <Link className={styles.inlineLink} to="/zh-cn/api/reference">
                  查看 API 双轨入口
                </Link>
              </article>
            </div>
          </div>
        </section>

        <section className={styles.bottomStrip}>
          <div className="container">
            <div className={styles.bottomShell}>
              <div>
                <span className={styles.sectionTag}>Governance</span>
                <Heading as="h2">首版目标不是“一次写完”，而是建立长期可维护的官方文档机制。</Heading>
              </div>
              <div className={styles.bottomActions}>
                <Link className={styles.secondaryAction} to="/en/api/reference">
                  API Reference
                </Link>
                <Link className={styles.primaryAction} to="/zh-cn/reference/override-default-implementations">
                  查看扩展与覆盖
                  <ShieldCheck size={18} />
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
