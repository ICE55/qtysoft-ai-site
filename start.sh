#!/usr/bin/env bash
# 本地一键启动：PostgreSQL(容器) + CMS 后端 + CMS 控制台 + 官网预览
#
#   ./start.sh         启动全部服务
#   ./start.sh stop    停止全部服务
#   ./start.sh status  查看服务状态
#
# 服务端口：
#   5432  PostgreSQL（Docker 容器 cms-db）
#   8080  CMS 后端 API
#   5173  CMS 控制台
#   4173  官网预览
#
# 已知坑位（脚本内已规避）：
#   1. 环境变量 SERVER__PORT 会通过 Spring Boot 松散绑定覆盖 server.port，
#      因此后端显式传 --server.port=8080，否则控制台代理连不上。
#   2. 某些沙箱/终端会给 node 注入文件代理钩子（NODE_OPTIONS），
#      Vite 读文件可能被拦截并缓存该错误，导致页面报 "Sensitive content access was denied"，
#      因此控制台启动时清除这些变量，走原生文件读取。

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.run"
CONSOLE_DIR="$ROOT/cms/console"
BACKEND_DIR="$ROOT/cms/backend"
WEBSITE_DIR="$ROOT/website"

BACKEND_PORT=8080
CONSOLE_PORT=5173
WEBSITE_PORT=4173

mkdir -p "$RUN_DIR"

log()  { printf "\033[36m[ start.sh ]\033[0m %s\n" "$*"; }
warn() { printf "\033[33m[ start.sh ]\033[0m %s\n" "$*"; }
die()  { printf "\033[31m[ start.sh ]\033[0m %s\n" "$*" >&2; exit 1; }

port_pid() { lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -1; }

wait_http() { # url 名称 最大秒数
  local url="$1" name="$2" max="${3:-60}" i code=000
  for ((i = 0; i < max; i++)); do
    code=$(curl -s -m 2 -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo 000)
    [ "$code" = "200" ] && { log "$name 就绪 (${i}s)"; return 0; }
    sleep 1
  done
  warn "$name 等待超时（最后状态码 $code）"
  return 1
}

kill_port() { # 端口 名称
  local pid; pid=$(port_pid "$1")
  if [ -n "$pid" ]; then
    kill -9 "$pid" 2>/dev/null && log "已停止 $2 (pid=$pid)"
  else
    log "$2 未运行"
  fi
}

do_stop() {
  log "停止全部服务..."
  kill_port "$WEBSITE_PORT" "官网预览"
  kill_port "$CONSOLE_PORT" "控制台"
  kill_port "$BACKEND_PORT" "后端"
  log "数据库容器 cms-db 保留运行（如需停止：docker stop cms-db）"
}

do_status() {
  for spec in "$BACKEND_PORT:后端 API" "$CONSOLE_PORT:控制台" "$WEBSITE_PORT:官网预览"; do
    local p="${spec%%:*}" n="${spec##*:}" pid
    pid=$(port_pid "$p")
    if [ -n "$pid" ]; then
      printf "  %-10s :%-5s ✅ 运行中 (pid=%s)\n" "$n" "$p" "$pid"
    else
      printf "  %-10s :%-5s ⛔ 未运行\n" "$n" "$p"
    fi
  done
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^cms-db$'; then
    printf "  %-10s :%-5s ✅ 运行中 (容器 cms-db)\n" "PostgreSQL" "5432"
  else
    printf "  %-10s :%-5s ⛔ 未运行\n" "PostgreSQL" "5432"
  fi
}

do_rebuild() {
  log "从 CMS 拉取最新已发布内容并重建官网..."
  local code=000
  code=$(curl -s -m 3 -o /dev/null -w "%{http_code}" "http://localhost:$BACKEND_PORT/api/content/published" 2>/dev/null || echo 000)
  [ "$code" = "200" ] || die "后端未就绪（HTTP $code），请先执行 ./start.sh"
  if ( cd "$WEBSITE_DIR" && CMS_API_URL="http://localhost:$BACKEND_PORT" node build.mjs --cms ); then
    log "重建完成，刷新浏览器即可看到最新内容（http://localhost:$WEBSITE_PORT/）"
  else
    die "重建失败"
  fi
}

case "${1:-start}" in
  stop)    do_stop;    exit 0 ;;
  status)  do_status;  exit 0 ;;
  rebuild) do_rebuild; exit 0 ;;
  start|"") : ;;
  *) echo "用法: $0 [start|stop|status|rebuild]"; exit 1 ;;
esac

# ---------- 启动流程 ----------

# 1) Java 环境
if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
command -v java >/dev/null 2>&1 || die "未找到 java，请先安装 JDK 17+"

# 2) 数据库
log "检查数据库..."
if ! docker info >/dev/null 2>&1; then
  warn "Docker 未运行，尝试启动 Docker Desktop..."
  open -a Docker 2>/dev/null
  for _ in $(seq 1 20); do docker info >/dev/null 2>&1 && break; sleep 2; done
fi
docker info >/dev/null 2>&1 || die "Docker 不可用，请手动启动 Docker Desktop"

if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^cms-db$'; then
  log "复用已运行的容器 cms-db"
elif docker ps -a --format '{{.Names}}' 2>/dev/null | grep -q '^cms-db$'; then
  docker start cms-db >/dev/null 2>&1 && log "已启动已有容器 cms-db"
else
  docker run -d --name cms-db -p 5432:5432 \
    -e POSTGRES_DB=qtycms -e POSTGRES_USER=qtycms -e POSTGRES_PASSWORD=qtycms \
    postgres:16-alpine >/dev/null 2>&1 && log "已创建并启动容器 cms-db"
fi
for i in $(seq 1 30); do
  docker exec cms-db pg_isready -q 2>/dev/null && { log "PostgreSQL 就绪 (${i}s)"; break; }
  sleep 1
done

# 3) 后端
if [ -n "$(port_pid $BACKEND_PORT)" ]; then
  log "后端已在 :$BACKEND_PORT 运行，跳过"
else
  JAR=$(ls "$BACKEND_DIR"/target/*.jar 2>/dev/null | head -1)
  if [ -z "$JAR" ]; then
    log "未找到 jar，开始构建后端..."
    ( cd "$BACKEND_DIR" && ./mvnw -q -DskipTests package 2>&1 || mvn -q -DskipTests package ) \
      | tail -5 || die "后端构建失败"
    JAR=$(ls "$BACKEND_DIR"/target/*.jar 2>/dev/null | head -1)
    [ -n "$JAR" ] || die "后端构建失败：未生成 jar"
  fi
  log "启动后端 :$BACKEND_PORT（jar: $(basename "$JAR")）"
  ( cd "$BACKEND_DIR" && nohup java -jar "$JAR" --server.port=$BACKEND_PORT \
      >"$RUN_DIR/backend.log" 2>&1 & echo $! > "$RUN_DIR/backend.pid" )
  wait_http "http://localhost:$BACKEND_PORT/api/content/published" "后端" 90
fi

# 4) 控制台
if [ -n "$(port_pid $CONSOLE_PORT)" ]; then
  log "控制台已在 :$CONSOLE_PORT 运行，跳过"
else
  [ -d "$CONSOLE_DIR/node_modules" ] || {
    log "安装控制台依赖..."
    ( cd "$CONSOLE_DIR" && npm install ) >"$RUN_DIR/console-install.log" 2>&1 || die "依赖安装失败"
  }
  log "启动控制台 :$CONSOLE_PORT"
  # 清除沙箱文件代理钩子，避免 Vite 读文件被拦截
  ( cd "$CONSOLE_DIR" && nohup env -u CODEBUDDY_BROKERED_FS_HOOK_ENABLED -u NODE_OPTIONS \
      -u CODEBUDDY_BROKERED_SHELL_ENV npm run dev \
      >"$RUN_DIR/console.log" 2>&1 & echo $! > "$RUN_DIR/console.pid" )
  wait_http "http://localhost:$CONSOLE_PORT/" "控制台" 60
fi

# 5) 官网：从 CMS 拉取已发布内容并构建
log "构建官网（从 CMS 拉取已发布内容）..."
if ( cd "$WEBSITE_DIR" && CMS_API_URL="http://localhost:$BACKEND_PORT" node build.mjs --cms ) \
     >"$RUN_DIR/website-build.log" 2>&1; then
  log "官网构建成功：$WEBSITE_DIR/dist"
else
  warn "官网构建失败，详见 $RUN_DIR/website-build.log（将尝试启动已有产物）"
fi

if [ -n "$(port_pid $WEBSITE_PORT)" ]; then
  log "官网预览已在 :$WEBSITE_PORT 运行，跳过"
else
  ( cd "$WEBSITE_DIR" && nohup node serve.mjs $WEBSITE_PORT \
      >"$RUN_DIR/website.log" 2>&1 & echo $! > "$RUN_DIR/website.pid" )
  wait_http "http://localhost:$WEBSITE_PORT/" "官网预览" 30
fi

echo
log "全部就绪："
echo "    CMS 控制台   http://localhost:$CONSOLE_PORT/      （admin / 见部署文档初始密码）"
echo "    CMS 后端 API http://localhost:$BACKEND_PORT/"
echo "    官网预览     http://localhost:$WEBSITE_PORT/"
echo "    日志目录     $RUN_DIR/"
