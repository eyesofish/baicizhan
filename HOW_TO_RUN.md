# Baicizhan 运行说明（Git Bash 命令版）

本文档只给 **Windows + Git Bash** 可直接复制执行的命令。  
默认仓库路径：`/d/Github/baicizhan`

---

## 1. 环境准备（首次）

需要安装：

1. JDK 21
2. Node.js 18+（建议 20 LTS）
3. Docker Desktop（状态必须是 Running）
4. PowerShell（Windows 默认有）

可选自检（在 Git Bash 执行）：

```bash
java -version
node -v
npm -v
docker --version
docker compose version
```

---

## 2. 首次安装依赖（前端）

```bash
cd /d/Github/baicizhan/frontend
npm install
```

---

## 3. 一键启动（前后端 + MySQL + Redis）

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./start-dev.ps1
```

启动脚本会自动完成：

1. 启动 Docker 中的 MySQL + Redis
2. 检查词库并按需导入
3. 启动后端（Spring Boot，`mysql` profile）
4. 启动前端（React + react-scripts，端口 5173）

启动后访问：

1. 前端：`http://localhost:5173`
2. 后端：`http://localhost:8080`
3. Swagger：`http://localhost:8080/swagger-ui.html`

---

## 4. （可选）启动 ANN 服务（embedding recall 推荐）

如果你要测试向量召回（Faiss），再开一个 Git Bash 窗口执行：

```bash
conda activate baicizhan
cd /d/Github/baicizhan
uvicorn ann_service:app --host 0.0.0.0 --port 18080
```

说明：

1. 后端默认 `app.learning.ann.enabled=true`
2. ANN 没启动也能跑，后端会自动降级为规则召回（会有 warn 日志）

---

## 5. 常用启动参数

强制重新导入词库：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./start-dev.ps1 -ForceImport -ImportLimit 10000
```

重置 MySQL/Redis 后再启动（危险：会清空容器卷）：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./start-dev.ps1 -ResetData -ForceImport -ImportLimit 10000
```

---

## 6. 停止项目

停止前后端 + 停止容器（保留 MySQL/Redis 数据）：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./stop-dev.ps1
```

停止并删除容器卷（清数据）：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./stop-dev.ps1 -RemoveData
```

---

## 7. 日志查看（Git Bash）

日志文件位置：

1. `.runtime/backend-dev.out.log`
2. `.runtime/backend-dev.err.log`
3. `.runtime/frontend-dev.out.log`
4. `.runtime/frontend-dev.err.log`

实时追日志：

```bash
cd /d/Github/baicizhan
tail -f .runtime/backend-dev.out.log
```

```bash
cd /d/Github/baicizhan
tail -f .runtime/frontend-dev.out.log
```

---

## 8. 常见问题

1. Docker 连接失败

先打开 Docker Desktop，等到 Running 再执行启动命令。

2. 端口占用（5173/8080/18080）

```bash
powershell.exe -NoProfile -Command "Get-NetTCPConnection -LocalPort 5173,8080,18080 -State Listen"
```

3. 提示已有 dev 进程

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./stop-dev.ps1
```

4. ANN 调用报错

检查 `uvicorn ann_service:app --port 18080` 是否在运行；若不跑 ANN，可忽略（会走规则召回降级）。
