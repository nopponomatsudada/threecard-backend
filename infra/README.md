# Infrastructure

このディレクトリには、Backend Application のインフラストラクチャ設定が含まれています。

## ディレクトリ構成

```
infra/
├── docker/                     # Docker関連
│   ├── Dockerfile              # アプリケーションコンテナ
│   ├── docker-compose.yml      # ローカル開発用
│   ├── docker-compose.prod.yml # 本番参照用
│   └── .dockerignore           # ビルド除外設定
│
├── terraform/                  # AWS IaC
│   ├── modules/                # 再利用可能モジュール
│   │   ├── networking/         # VPC, Subnets, SG
│   │   ├── database/           # RDS PostgreSQL
│   │   └── compute/            # ECS Fargate + ALB
│   ├── environments/           # 環境別設定
│   │   ├── dev/
│   │   ├── staging/
│   │   └── prod/
│   └── README.md
│
├── scripts/                    # ユーティリティスクリプト
│   ├── build.sh                # Dockerビルド
│   └── deploy.sh               # デプロイ実行
│
└── README.md                   # このファイル
```

## クイックスタート

### ローカル開発

```bash
# アプリ + DB を起動
cd infra/docker
docker-compose up -d

# 動作確認
curl http://localhost:8080/health

# 停止
docker-compose down
```

### Docker イメージビルド

```bash
# ルートディレクトリから
./infra/scripts/build.sh

# または直接
docker build -f infra/docker/Dockerfile -t myapp-backend:dev .
```

### AWS デプロイ

```bash
# 1. Terraform で初期設定
cd infra/terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars を編集
terraform init
terraform apply

# 2. ECR にプッシュ & ECS にデプロイ
./infra/scripts/deploy.sh dev
```

## アーキテクチャ

```
                    ┌─────────────────────────────────────────┐
                    │               Internet                   │
                    └────────────────────┬────────────────────┘
                                         │
                    ┌────────────────────▼────────────────────┐
                    │        Application Load Balancer        │
                    │            (Public Subnet)              │
                    └────────────────────┬────────────────────┘
                                         │
          ┌──────────────────────────────┼──────────────────────────────┐
          │                              │                              │
┌─────────▼─────────┐         ┌─────────▼─────────┐         ┌─────────▼─────────┐
│    ECS Task 1     │         │    ECS Task 2     │         │    ECS Task N     │
│  (Private Subnet) │         │  (Private Subnet) │         │  (Private Subnet) │
└─────────┬─────────┘         └─────────┬─────────┘         └─────────┬─────────┘
          │                              │                              │
          └──────────────────────────────┼──────────────────────────────┘
                                         │
                    ┌────────────────────▼────────────────────┐
                    │           RDS PostgreSQL                │
                    │           (Private Subnet)              │
                    │              Multi-AZ                   │
                    └─────────────────────────────────────────┘
```

## 環境比較

| 項目 | Dev | Staging | Prod |
|------|-----|---------|------|
| ECS タスク数 | 1 | 2 | 3+ |
| ECS CPU | 512 | 512 | 1024 |
| ECS Memory | 1GB | 1GB | 2GB |
| RDS インスタンス | t4g.micro | t4g.small | t4g.medium |
| RDS Multi-AZ | No | No | Yes |
| オートスケール | No | Yes | Yes |
| HTTPS | Optional | Recommended | Required |
| 削除保護 | No | No | Yes |

## セキュリティ

### 機密情報の管理

- **絶対にコミットしないファイル**:
  - `terraform.tfvars`
  - `.env`
  - `*.secret`
  - AWS 認証情報

- **AWS Secrets Manager を使用**:
  - データベース認証情報
  - JWT シークレット
  - 外部APIキー

### ネットワークセキュリティ

- ECS タスクはプライベートサブネットで実行
- RDS はプライベートサブネットで実行
- ALB のみがパブリックに公開
- Security Group で最小限のアクセスを許可

## トラブルシューティング

### ローカル開発

```bash
# コンテナのログ確認
docker-compose logs -f app

# DBに直接接続
docker-compose exec db psql -U postgres -d myapp

# コンテナのリビルド
docker-compose build --no-cache app
```

### AWS

```bash
# ECS タスクのログ確認
aws logs tail /ecs/myapp-dev --follow

# ECS サービスの状態確認
aws ecs describe-services \
  --cluster myapp-dev-cluster \
  --services myapp-dev-service

# RDS 接続テスト（踏み台経由）
psql -h <rds-endpoint> -U myapp_admin -d myapp
```

## 関連ドキュメント

- [Terraform詳細](./terraform/README.md)
- [デプロイガイド](../docs/guides/deployment.md)
- [セキュリティガイド](../docs/guides/security.md)
