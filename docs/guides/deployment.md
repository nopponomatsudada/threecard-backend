# デプロイメントガイド

## 概要

バックエンド API のデプロイ方法を説明します。

## Docker

### Dockerfile

```dockerfile
# Build stage
FROM gradle:8.11-jdk21 AS build
WORKDIR /app

# 依存関係のキャッシュ
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true

# ソースコードをコピーしてビルド
COPY src ./src
RUN gradle shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# セキュリティ: 非 root ユーザーで実行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# JAR ファイルをコピー
COPY --from=build /app/build/libs/*-all.jar app.jar

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml（開発用）

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=jdbc:postgresql://db:5432/appmaster
      - DATABASE_USER=postgres
      - DATABASE_PASSWORD=postgres
      - JWT_SECRET=${JWT_SECRET:-dev-secret-key-change-in-production}
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: appmaster
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

### docker-compose.prod.yml（本番用）

```yaml
version: '3.8'

services:
  app:
    image: ${REGISTRY}/appmaster-backend:${VERSION:-latest}
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USER=${DATABASE_USER}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - KTOR_DEVELOPMENT=false
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '1'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      - app
```

### ビルドとプッシュ

```bash
# ビルド
docker build -t appmaster-backend:latest .

# タグ付け
docker tag appmaster-backend:latest registry.example.com/appmaster-backend:v1.0.0

# プッシュ
docker push registry.example.com/appmaster-backend:v1.0.0
```

## GitHub Actions CI/CD

### .github/workflows/ci.yml

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Run tests
        run: ./gradlew test
        env:
          DATABASE_URL: jdbc:postgresql://localhost:5432/test
          DATABASE_USER: test
          DATABASE_PASSWORD: test

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: build/reports/tests/

  build:
    runs-on: ubuntu-latest
    needs: test

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build with Gradle
        run: ./gradlew shadowJar

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-jar
          path: build/libs/*-all.jar
```

### .github/workflows/deploy.yml

```yaml
name: Deploy

on:
  push:
    tags:
      - 'v*'

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ secrets.REGISTRY_URL }}
          username: ${{ secrets.REGISTRY_USERNAME }}
          password: ${{ secrets.REGISTRY_PASSWORD }}

      - name: Extract version
        id: version
        run: echo "VERSION=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: |
            ${{ secrets.REGISTRY_URL }}/appmaster-backend:${{ steps.version.outputs.VERSION }}
            ${{ secrets.REGISTRY_URL }}/appmaster-backend:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Deploy to production
        run: |
          # SSH経由でデプロイ、またはKubernetesへのデプロイ
          echo "Deploying version ${{ steps.version.outputs.VERSION }}"
```

## Kubernetes

### deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: appmaster-backend
  labels:
    app: appmaster-backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: appmaster-backend
  template:
    metadata:
      labels:
        app: appmaster-backend
    spec:
      containers:
        - name: app
          image: registry.example.com/appmaster-backend:latest
          ports:
            - containerPort: 8080
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: app-secrets
                  key: database-url
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: app-secrets
                  key: jwt-secret
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: appmaster-backend
spec:
  selector:
    app: appmaster-backend
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: appmaster-backend
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts:
        - api.example.com
      secretName: appmaster-tls
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: appmaster-backend
                port:
                  number: 80
```

### secrets.yaml

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: app-secrets
type: Opaque
stringData:
  database-url: "jdbc:postgresql://db.example.com:5432/appmaster"
  jwt-secret: "your-production-secret-key"
```

## 環境変数チェックリスト

### 本番環境必須

| 変数 | 説明 | 例 |
|------|------|-----|
| `DATABASE_URL` | PostgreSQL 接続 URL | `jdbc:postgresql://...` |
| `DATABASE_USER` | DB ユーザー | `appmaster_user` |
| `DATABASE_PASSWORD` | DB パスワード | (シークレット) |
| `JWT_SECRET` | JWT 署名キー（32文字以上） | (シークレット) |

### オプション

| 変数 | デフォルト | 説明 |
|------|-----------|------|
| `PORT` | 8080 | リッスンポート |
| `KTOR_DEVELOPMENT` | false | 開発モード |
| `LOG_LEVEL` | INFO | ログレベル |
| `DATABASE_MAX_POOL_SIZE` | 10 | コネクションプールサイズ |

## ヘルスチェックエンドポイント

```kotlin
// routes/HealthRoutes.kt
fun Route.healthRoutes() {
    get("/health") {
        call.respond(mapOf("status" to "healthy"))
    }

    get("/health/ready") {
        // データベース接続を確認
        try {
            dbQuery { exec("SELECT 1") }
            call.respond(mapOf("status" to "ready"))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                mapOf("status" to "not ready", "error" to e.message)
            )
        }
    }
}
```

## ロギング（本番用）

### logback.xml（本番用）

```xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>requestId</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="WARN">
        <appender-ref ref="JSON"/>
    </root>

    <logger name="com.example" level="INFO"/>
</configuration>
```

## セキュリティチェックリスト

- [ ] JWT_SECRET は 32 文字以上のランダム文字列
- [ ] DATABASE_PASSWORD は強力なパスワード
- [ ] HTTPS のみ許可（本番環境）
- [ ] CORS は本番ドメインのみ許可
- [ ] セキュリティヘッダーを設定
- [ ] レート制限を設定
- [ ] 機密情報をログに出力しない
