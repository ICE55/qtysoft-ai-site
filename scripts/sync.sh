#!/usr/bin/env bash
# 重建静态站点并同步到 GitHub（ICE55/qtysoft-ai-site, main 分支）
set -e
cd "$(dirname "$0")/.."

echo "▶ 重建静态站点..."
node build.mjs

git add -A
if git diff --cached --quiet; then
  echo "无变更，跳过提交"
else
  MSG="chore: sync site $(date +%Y-%m-%dT%H:%M:%S)"
  git commit -q -m "$MSG"
  echo "▶ 已提交: $MSG"
fi

git push origin main
echo "✅ 已同步到 GitHub: https://github.com/ICE55/qtysoft-ai-site"
