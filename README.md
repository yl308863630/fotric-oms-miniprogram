# OMS 订单管理系统

一个基于 Spring Boot + Vue 3 的订单管理系统。

## 技术栈

### 后端
- Spring Boot 3.2.4
- Java 17
- MySQL
- Spring Data JPA
- Spring Security + JWT

### 前端
- Vue 3.4.21
- TypeScript 5.2.2
- Vite 5.2.0
- Element Plus 2.6.1
- Pinia 2.1.7
- Vue Router 4.3.0

## 功能模块

- 商品管理
- 销售订单管理
- 采购订单管理
- 合同管理
- 合作伙伴管理
- 结算管理
- 发票管理
- 物流跟踪
- 用户管理

## 快速开始

### 本地开发

**方式一：一键启动脚本（推荐）**

在项目根目录下：

| 脚本 | 说明 |
|------|------|
| `start-backend.bat` | 只启动后端（8080），双击或在 CMD 中运行 |
| `start-frontend.bat` | 只启动前端（3000），双击或在 CMD 中运行 |
| `start-all.bat` | 同时启动后端和前端（各开一个窗口） |

PowerShell 版本：`start-backend.ps1`、`start-frontend.ps1`、`start-all.ps1`，在项目根目录执行 `.\start-all.ps1` 即可同时启动前后端。

**方式二：命令行手动启动**

后端启动：
```bash
cd oms-backend
mvn spring-boot:run
```

前端启动（新开一个终端）：
```bash
cd oms-frontend
npm install
npm run dev
```

启动后浏览器访问：http://localhost:3000

### 服务器部署

#### 拉取最新代码
```bash
cd /path/to/oms-order-system
git pull
```

#### 重新构建并启动
```bash
# 停止旧服务
pkill -f "java -jar"
pkill -f "npm run dev"

# 构建后端
cd oms-backend
mvn clean package -DskipTests

# 构建前端
cd ../oms-frontend
npm install
npm run build
cp -r dist/* ../oms-backend/src/main/resources/static/

# 启动服务
cd ../oms-backend
nohup java -jar target/oms-backend.jar > app.log 2>&1 &
```

## Git 工作流程

### 本地开发
```bash
# 查看修改
git status

# 添加修改
git add .

# 提交修改
git commit -m "描述您的修改"

# 推送到GitHub
git push
```

### 服务器更新
```bash
# 拉取最新代码
git pull

# 重启服务
# ...
```

### 回滚版本
```bash
# 查看历史
git log --oneline

# 回滚到上一个版本
git reset --hard HEAD~1

# 回滚到指定版本
git reset --hard <commit-id>
```

## 配置说明

### 数据库配置
编辑 `oms-backend/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/oms_db
    username: your_username
    password: your_password
```

### 顺丰API配置
编辑 `oms-backend/src/main/java/com/oms/config/SfExpressConfig.java`

## 项目结构

```
oms-order-system/
├── oms-backend/          # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
├── oms-frontend/         # 前端项目
│   ├── src/
│   │   ├── views/
│   │   ├── components/
│   │   └── utils/
│   └── package.json
└── docs/                 # 文档
```

## 许可证

MIT License