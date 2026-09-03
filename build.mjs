#!/usr/bin/env node
/**
 * 乾腾元官网 · 零依赖静态站点生成器
 *
 *   node build.mjs           构建到 dist/
 *   node build.mjs --watch   监听 src/ 变化自动重建
 *
 * 约定：
 *   1. src/pages/*.html 顶部可写 YAML 风格 frontmatter（title / description / nav）
 *   2. 页面里用 `<!-- @include partials/head.html -->` 引入片段，片段可嵌套
 *   3. 模板变量用 {{title}} {{description}} {{canonical}} {{year}}
 */
import {
  readFileSync, writeFileSync, mkdirSync, cpSync, readdirSync,
  rmSync, existsSync, watch
} from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const SRC = join(ROOT, 'src');
const DIST = join(ROOT, 'dist');
const PAGES_DIR = join(SRC, 'pages');
const PARTIALS_DIR = join(SRC, 'partials');
const SITE_URL = (process.env.SITE_URL || 'https://www.qtysoft-ai.com').replace(/\/$/, '');
// 去掉 www. 后的主域名，用于派生业务邮箱（可用环境变量覆盖）
const SITE_HOST = new URL(SITE_URL).hostname.replace(/^www\./, '');
const SITE_EMAIL = process.env.SITE_EMAIL || `hi@${SITE_HOST}`;

const log = (...a) => console.log('[build]', ...a);

function parseFrontmatter(raw) {
  const match = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(raw);
  if (!match) return { data: {}, body: raw };
  const data = {};
  for (const line of match[1].split(/\r?\n/)) {
    const idx = line.indexOf(':');
    if (idx === -1) continue;
    data[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  }
  return { data, body: raw.slice(match[0].length) };
}

function collectPartials(dir, prefix = '') {
  const map = {};
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      Object.assign(map, collectPartials(full, `${prefix}${entry.name}/`));
    } else if (entry.name.endsWith('.html')) {
      map[`partials/${prefix}${entry.name}`] = readFileSync(full, 'utf8');
    }
  }
  return map;
}

function renderIncludes(html, partials, stack = 0) {
  if (stack > 5) throw new Error('include 嵌套层数超过 5，可能存在循环引用');
  const next = html.replace(/[ \t]*<!--\s*@include\s+([^\s]+?)\s*-->/g, (_m, name) => {
    const tpl = partials[name];
    if (tpl === undefined) throw new Error(`找不到片段: ${name}`);
    return tpl;
  });
  return next === html ? html : renderIncludes(next, partials, stack + 1);
}

function renderVars(html, vars) {
  return html.replace(/\{\{\s*(\w+)\s*\}\}/g, (_m, key) => vars[key] ?? '');
}

/**
 * 按页面列表生成 sitemap.xml。首页权重最高、更新最频繁，其余页面月度。
 * 404 页不进 sitemap（已在调用处排除）。
 */
function renderSitemap(pages, siteUrl) {
  const today = new Date().toISOString().slice(0, 10);
  // 首页排在最前，其余按文件名排序
  const ordered = [...pages].sort((a, b) => {
    if (a === 'index.html') return -1;
    if (b === 'index.html') return 1;
    return a.localeCompare(b);
  });
  const entries = ordered.map((file) => {
    const isHome = file === 'index.html';
    const loc = `${siteUrl}/${isHome ? '' : file}`;
    const priority = isHome ? '1.0' : '0.8';
    const changefreq = isHome ? 'weekly' : 'monthly';
    return [
      '  <url>',
      `    <loc>${loc}</loc>`,
      `    <lastmod>${today}</lastmod>`,
      `    <changefreq>${changefreq}</changefreq>`,
      `    <priority>${priority}</priority>`,
      '  </url>'
    ].join('\n');
  });
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ...entries,
    '</urlset>',
    ''
  ].join('\n');
}

/**
 * 把根绝对路径（/assets/... /product.html）改写成同级相对路径（./assets/...）。
 * 站点所有页面都平铺在根目录，所以 `./` 在任何页面下都指向同一层，
 * 这样 dist/ 无论是挂在域名根还是子路径下都能直接打开。
 * 外链（https://、协议相对 //）与 SEO 用的绝对 URL 不受影响。
 */
function toRelativePaths(html) {
  return html.replace(/(href|src|content)="\/([^/"][^"]*)"/g, '$1="./$2"');
}

function build() {
  const started = Date.now();
  if (!existsSync(PARTIALS_DIR)) throw new Error(`缺少目录: ${PARTIALS_DIR}`);
  const partials = collectPartials(PARTIALS_DIR);

  if (existsSync(DIST)) rmSync(DIST, { recursive: true, force: true });
  mkdirSync(DIST, { recursive: true });

  const files = readdirSync(PAGES_DIR).filter((f) => f.endsWith('.html'));
  let count = 0;
  const sitePages = [];

  for (const file of files) {
    const { data, body } = parseFrontmatter(readFileSync(join(PAGES_DIR, file), 'utf8'));
    const isHome = file === 'index.html';
    const vars = {
      title: data.title || '乾腾元 QTY AI · 企业级 AI Agent 全栈服务商',
      description: data.description || '乾腾元是企业级 AI Agent 全栈服务商，提供智能体开发平台、企业知识引擎、工具连接器与全链路运营，让 AI 真正进入企业业务流。',
      nav: data.nav || '',
      canonical: `${SITE_URL}/${isHome ? '' : file}`,
      year: String(new Date().getFullYear()),
      siteUrl: SITE_URL,
      email: SITE_EMAIL
    };

    let html = renderIncludes(body, partials);
    html = renderVars(html, vars);
    html = toRelativePaths(html);

    // 当前页导航高亮
    if (vars.nav) {
      const token = `data-nav="${vars.nav}"`;
      html = html.replace(token, `${token} data-active="true" aria-current="page"`);
    }

    writeFileSync(join(DIST, file), html, 'utf8');
    if (file !== '404.html') sitePages.push(file);
    count += 1;
  }

  if (existsSync(join(SRC, 'assets'))) {
    cpSync(join(SRC, 'assets'), join(DIST, 'assets'), { recursive: true });
  }
  for (const f of ['favicon.svg', 'og-cover.svg']) {
    const p = join(SRC, f);
    if (existsSync(p)) cpSync(p, join(DIST, f));
  }

  // robots.txt 走模板变量渲染，sitemap 按页面列表自动生成 —— 换域名只需改 SITE_URL
  const robotsSrc = join(SRC, 'robots.txt');
  if (existsSync(robotsSrc)) {
    const robots = renderVars(readFileSync(robotsSrc, 'utf8'), {
      siteUrl: SITE_URL
    });
    writeFileSync(join(DIST, 'robots.txt'), robots, 'utf8');
  }
  writeFileSync(join(DIST, 'sitemap.xml'), renderSitemap(sitePages, SITE_URL), 'utf8');

  log(`完成：${count} 个页面 → dist/  (${Date.now() - started}ms)`);
}

build();

if (process.argv.includes('--watch')) {
  log('监听 src/ 变化中…');
  let timer = null;
  watch(SRC, { recursive: true }, () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      try {
        build();
      } catch (err) {
        console.error('[build] 失败:', err.message);
      }
    }, 120);
  });
}
