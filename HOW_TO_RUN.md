# Baicizhan 项目一键拉起教程（Git Bash 版）

这份文档按 **Windows + Git Bash** 写，默认你当前目录是：

`/d/Github/baicizhan`

---

## 1. 先准备环境（只需要一次）

1. 安装 JDK 21  
2. 安装 Node.js 18+（建议 20 LTS）  
3. 安装 Docker Desktop，并确保状态是 Running  
4. Windows 上可调用 `powershell.exe`（默认都有）

可选自检命令（在 Git Bash 里执行）：

```bash
java -version
node -v
npm -v
docker --version
docker compose version
```

---

## 2. 第一次安装前端依赖（只需要一次）

```bash
cd /d/Github/baicizhan/frontend
npm install
```

---

## 3. 一条命令拉起整个项目

回到项目根目录，执行：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./start-dev.ps1
```

这条命令会自动做完：

1. 启动 `docker-compose.yml` 里的 MySQL + Redis
2. 自动检查词库数据是否存在
3. 如果数据库为空，自动执行词库导入
4. 启动后端（Spring Boot，`mysql` profile）
5. 启动前端（Vue3 + Vite）

说明：首次默认导入 2000 词，优先保证快速可用。

启动成功后可访问：

1. 前端: `http://localhost:5173`
2. 后端: `http://localhost:8080`
3. Swagger: `http://localhost:8080/swagger-ui.html`

---

## 4. 常用启动参数

强制重新导入词库：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./start-dev.ps1 -ForceImport -ImportLimit 10000
```

重置 MySQL/Redis 数据后再启动（危险，会清空容器卷）：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./start-dev.ps1 -ResetData -ForceImport -ImportLimit 10000
```

---

## 5. 一条命令停止项目

保留 MySQL/Redis 数据：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./stop-dev.ps1
```

连容器数据一起删掉：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./stop-dev.ps1 -RemoveData
```

---

## 6. 日志在哪看

统一启动脚本会把日志写到根目录 `.runtime`：

1. 后端标准输出: `.runtime/backend-dev.out.log`
2. 后端错误输出: `.runtime/backend-dev.err.log`
3. 前端标准输出: `.runtime/frontend-dev.out.log`
4. 前端错误输出: `.runtime/frontend-dev.err.log`

---

## 7. 常见问题

1. `docker compose` 连接不到引擎  
先手动打开 Docker Desktop，等状态变成 Running 再执行启动命令。

2. 5173 或 8080 端口被占用  
先执行停止命令，再检查端口占用（Git Bash 里调用 PowerShell）：

```bash
powershell.exe -NoProfile -Command "Get-NetTCPConnection -LocalPort 5173,8080 -State Listen"
```

3. 启动时提示已经有 dev 进程  
先执行：

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./stop-dev.ps1
```
