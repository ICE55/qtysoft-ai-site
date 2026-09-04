#!/usr/bin/env node
/**
 * 乾腾元官网 · 零依赖静态站点生成器
 *
 *   node build.mjs             构建到 dist/（默认：本地 content/*.json 兜底）
 *   node build.mjs --cms       从 CMS 拉取已发布内容（环境变量 CMS_API_URL + CMS_DEPLOY_TOKEN）
 *   node build.mjs --watch     监听 src/ 变化自动重建
 *
 * 约定：
 *   1. src/pages/*.html 顶部可写 YAML 风格 frontmatter（title / description / nav）
 *   2. 页面里用 `<!-- @include partials/head.html -->` 引入片段
 *   3. 模板变量：{{title}} {{description}} {{canonical}} {{year}}
 *      CMS 内容变量（点号路径）：{{content.home.hero.title}}
 *      列表循环：<!-- @each content.home.stats.items as item --> ... {{item.value}} ... <!-- @endeach -->
 *   4. 内容来源：--cms 时拉取 CMS /api/content/published；否则读取本地 content/*.json
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
const CMS_MODE = process.argv.includes('--cms');
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

function getByPath(obj, path) {
  return path.split('.').reduce((acc, k) => (acc == null ? undefined : acc[k]), obj);
}

/**
 * 列表循环（支持任意层嵌套）：
 *   <!-- @each content.x.y as item --> ... {{item.field}} ... <!-- @endeach -->
 * 通过深度扫描配对 @each / @endeach（而非贪婪/非贪婪正则），
 * 先展开最内层循环，再逐层向上，避免嵌套时块被错误截断。
 */
function substitute(html, vars) {
  return html.replace(/\{\{\s*([\w.$]+)\s*\}\}/g, (_m, key) => {
    if (key === '$index') return String(vars.$index == null ? '' : vars.$index);
    const v = getByPath(vars, key);
    return v == null ? '' : String(v);
  });
}

function renderEach(html, vars) {
  const openIdx = html.search(/<!--\s*@each\s+[\w.$]+\s+as\s+\w+\s*-->/);
  if (openIdx === -1) return substitute(html, vars);

  const openMatch = /<!--\s*@each\s+([\w.$]+)\s+as\s+(\w+)\s*-->/.exec(html.slice(openIdx));
  const path = openMatch[1];
  const varName = openMatch[2];

  // 定位 open 标签结束位置
  const openTagEnd = openIdx + html.slice(openIdx).indexOf('-->') + 3;

  // 深度扫描，找到与最外层 @each 配对的 @endeach
  let depth = 1;
  let closeIdx = -1;
  const tokenRe = /<!--\s*@(each|endeach)\s/g;
  tokenRe.lastIndex = openTagEnd;
  let tm;
  while ((tm = tokenRe.exec(html))) {
    depth += tm[1] === 'each' ? 1 : -1;
    if (depth === 0) { closeIdx = tm.index; break; }
  }
  if (closeIdx === -1) return substitute(html, vars); // 未配对，放弃

  const closeTagEnd = closeIdx + html.slice(closeIdx).indexOf('-->') + 3;
  const block = html.slice(openTagEnd, closeIdx);

  const arr = getByPath(vars, path);
  let expanded = '';
  if (Array.isArray(arr)) {
    expanded = arr
      .map((item, idx) => renderEach(block, { ...vars, [varName]: item, $index: idx }))
      .join('');
  }
  const newHtml = html.slice(0, openIdx) + expanded + html.slice(closeTagEnd);
  return renderEach(newHtml, vars);
}

function renderVars(html, vars) {
  html = renderEach(html, vars);
  return html.replace(/\{\{\s*([\w.$]+)\s*\}\}/g, (_m, key) => {
    const v = getByPath(vars, key);
    return v === undefined || v === null ? '' : String(v);
  });
}

/**
 * 加载内容：--cms 时从 CMS 拉取已发布内容；否则 / 失败时回退本地 content/*.json
 */
async function loadContent() {
  if (CMS_MODE) {
    const base = (process.env.CMS_API_URL || '').replace(/\/$/, '');
    if (!base) {
      // CI 未配置 CMS_API_URL 时不要去连默认的 localhost，避免无谓等待
      log('未配置 CMS_API_URL，跳过 CMS 拉取，使用本地 content/');
    } else {
      try {
        const url = `${base}/api/content/published`;
        const token = process.env.CMS_DEPLOY_TOKEN || '';
        const res = await fetch(url, {
          headers: { 'X-Deploy-Token': token },
          signal: AbortSignal.timeout(10000)
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        log(`已从 CMS 拉取已发布内容 (${url})`);
        return await res.json();
      } catch (e) {
        log(`CMS 拉取失败，回退本地 content/：${e.message}`);
      }
    }
  }
  const dir = join(ROOT, 'content');
  const obj = {};
  if (existsSync(dir)) {
    for (const f of readdirSync(dir)) {
      if (f.endsWith('.json')) {
        try {
          obj[f.replace(/\.json$/, '')] = JSON.parse(readFileSync(join(dir, f), 'utf8'));
        } catch (err) {
          log(`解析 ${f} 失败：${err.message}`);
        }
      }
    }
  }
  return obj;
}

function renderSitemap(pages, siteUrl) {
  const today = new Date().toISOString().slice(0, 10);
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

function toRelativePaths(html) {
  return html.replace(/(href|src|content)="\/([^/"][^"]*)"/g, '$1="./$2"');
}

async function build() {
  const started = Date.now();
  const content = await loadContent();
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
      email: SITE_EMAIL,
      content
    };

    let html = renderIncludes(body, partials);
    html = renderVars(html, vars);
    html = toRelativePaths(html);

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

  const robotsSrc = join(SRC, 'robots.txt');
  if (existsSync(robotsSrc)) {
    const robots = renderVars(readFileSync(robotsSrc, 'utf8'), { siteUrl: SITE_URL });
    writeFileSync(join(DIST, 'robots.txt'), robots, 'utf8');
  }
  writeFileSync(join(DIST, 'sitemap.xml'), renderSitemap(sitePages, SITE_URL), 'utf8');

  log(`完成：${count} 个页面 → dist/  (${Date.now() - started}ms)${CMS_MODE ? '  [CMS 模式]' : ''}`);
}

build().catch((err) => {
  console.error('[build] 失败:', err.message);
  process.exit(1);
});

if (process.argv.includes('--watch')) {
  log('监听 src/ 变化中…');
  let timer = null;
  watch(SRC, { recursive: true }, () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      build().catch((err) => console.error('[build] 失败:', err.message));
    }, 120);
  });
}
