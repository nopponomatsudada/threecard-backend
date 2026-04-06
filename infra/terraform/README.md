# Terraform Infrastructure

このディレクトリには、Backend Application のAWSインフラストラクチャをIaC（Infrastructure as Code）として管理するTerraform設定が含まれています。

## ディレクトリ構成

```
terraform/
├── modules/                    # 再利用可能なモジュール
│   ├── networking/             # VPC, Subnets, Security Groups
│   ├── database/               # RDS PostgreSQL
│   └── compute/                # ECS Fargate + ALB
│
├── environments/               # 環境別設定
│   ├── dev/                    # 開発環境
│   ├── staging/                # ステージング環境
│   └── prod/                   # 本番環境
│
└── README.md
```

## 前提条件

1. **Terraform** (>= 1.5.0)
2. **AWS CLI** と設定済みの認証情報
3. **S3 バケット** (state管理用、チーム開発時)
4. **DynamoDB テーブル** (state lock用、チーム開発時)

## クイックスタート

### 1. 環境変数の設定

```bash
cd environments/dev
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars を編集して値を設定
```

### 2. 初期化

```bash
terraform init
```

### 3. プランの確認

```bash
terraform plan
```

### 4. 適用

```bash
terraform apply
```

## モジュール詳細

### networking モジュール

VPC とネットワークリソースを作成します。

| リソース | 説明 |
|---------|------|
| VPC | メインVPC (デフォルト: 10.0.0.0/16) |
| Public Subnets | ALB用 (2 AZ) |
| Private Subnets | ECS/RDS用 (2 AZ) |
| Internet Gateway | パブリックサブネット用 |
| NAT Gateway | プライベートサブネットのアウトバウンド用 |
| Security Groups | ALB, App, DB用 |

### database モジュール

RDS PostgreSQL インスタンスを作成します。

| 項目 | Dev | Staging | Prod |
|------|-----|---------|------|
| インスタンス | db.t4g.micro | db.t4g.small | db.t4g.medium |
| ストレージ | 20GB | 50GB | 100GB |
| Multi-AZ | No | No | Yes |
| バックアップ | 7日 | 14日 | 30日 |
| 削除保護 | No | No | Yes |

### compute モジュール

ECS Fargate クラスターとALBを作成します。

| 項目 | Dev | Staging | Prod |
|------|-----|---------|------|
| CPU | 512 | 512 | 1024 |
| Memory | 1024MB | 1024MB | 2048MB |
| 希望タスク数 | 1 | 2 | 3 |
| オートスケール | No | Yes (2-4) | Yes (3-10) |
| HTTPS | Optional | Recommended | Required |
| Container Insights | No | No | Yes |

## 環境別設定

### 開発環境 (dev)

- VPC CIDR: 10.0.0.0/16
- 最小構成でコスト最適化
- HTTPのみ（HTTPS任意）
- オートスケール無効

### ステージング環境 (staging)

- VPC CIDR: 10.1.0.0/16
- 本番に近い構成
- HTTPS推奨
- オートスケール有効

### 本番環境 (prod)

- VPC CIDR: 10.2.0.0/16
- 高可用性構成
- HTTPS必須
- Multi-AZ RDS
- 削除保護有効
- Performance Insights有効

## Secrets Management

機密情報は AWS Secrets Manager で管理します。

```bash
# シークレット作成例
aws secretsmanager create-secret \
  --name myapp/dev/db-credentials \
  --secret-string '{"username":"admin","password":"your-password"}'

aws secretsmanager create-secret \
  --name myapp/dev/jwt-secret \
  --secret-string '{"secret":"your-jwt-secret-at-least-32-chars"}'
```

## State Management

チーム開発では S3 バックエンドを推奨します。

### State用リソース作成

```bash
# S3 バケット
aws s3api create-bucket \
  --bucket myapp-terraform-state \
  --region ap-northeast-1 \
  --create-bucket-configuration LocationConstraint=ap-northeast-1

aws s3api put-bucket-versioning \
  --bucket myapp-terraform-state \
  --versioning-configuration Status=Enabled

# DynamoDB テーブル (state lock用)
aws dynamodb create-table \
  --table-name terraform-state-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

### Backend設定の有効化

各環境の `main.tf` で backend ブロックのコメントを外します：

```hcl
terraform {
  backend "s3" {
    bucket         = "myapp-terraform-state"
    key            = "dev/terraform.tfstate"  # 環境ごとに変更
    region         = "ap-northeast-1"
    dynamodb_table = "terraform-state-lock"
    encrypt        = true
  }
}
```

## 一般的な操作

### インフラの更新

```bash
terraform plan -out=tfplan
terraform apply tfplan
```

### 特定リソースの再作成

```bash
terraform taint module.compute.aws_ecs_service.app
terraform apply
```

### 出力値の確認

```bash
terraform output
terraform output alb_dns_name
```

### 状態の確認

```bash
terraform state list
terraform state show module.database.aws_db_instance.main
```

## コスト概算 (月額、ap-northeast-1)

| 環境 | NAT Gateway | RDS | ECS | ALB | 合計 |
|------|-------------|-----|-----|-----|------|
| Dev | ~$45 | ~$15 | ~$20 | ~$20 | ~$100 |
| Staging | ~$45 | ~$30 | ~$40 | ~$20 | ~$135 |
| Prod | ~$45 | ~$100 | ~$120 | ~$20 | ~$285 |

※ データ転送量、ストレージ使用量により変動します。

## トラブルシューティング

### ECS タスクが起動しない

1. CloudWatch Logs でエラーを確認
2. Task Definition の環境変数を確認
3. Security Group でDB接続が許可されているか確認
4. Secrets Manager へのアクセス権限を確認

### RDS 接続エラー

1. Security Group の設定を確認
2. サブネットグループの設定を確認
3. 認証情報が正しいか確認

### ALB ヘルスチェック失敗

1. アプリケーションの `/health` エンドポイントを確認
2. Security Group でALBからの通信が許可されているか確認
3. Target Group のヘルスチェック設定を確認

## 参考リンク

- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [AWS ECS Best Practices](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/intro.html)
- [AWS RDS Best Practices](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_BestPractices.html)
