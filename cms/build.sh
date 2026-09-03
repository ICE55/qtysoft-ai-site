#!/usr/bin/env bash
# CMS 整栈：构建镜像 + 启动（在 cms/ 目录下执行：bash build.sh）
set -e

REGISTRY="${REGISTRY:-local}"
TAG="${TAG:-latest}"

echo "▶ 构建后端镜像 ${REGISTRY}/cms-backend:${TAG}"
docker build -t "${REGISTRY}/cms-backend:${TAG}" ./backend

echo "▶ 构建控制台镜像 ${REGISTRY}/cms-console:${TAG}"
docker build -t "${REGISTRY}/cms-console:${TAG}" ./console

echo "▶ 启动整栈（cms-db + cms-backend + cms-console）"
docker compose up -d

echo "✅ 控制台： http://localhost:\${CONSOLE_PORT:-80}"
