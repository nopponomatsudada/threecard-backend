# Architecture Checklist

## 依存方向

- [ ] Routes → Domain の依存のみ
- [ ] Data → Domain の依存のみ
- [ ] Domain 層は他レイヤーに依存していない
- [ ] Routes から Data への直接アクセスがない

## Domain 層

- [ ] Entity は不変（immutable）
- [ ] ValueObject でプリミティブをラップ
- [ ] Repository はインターフェースのみ定義
- [ ] UseCase は単一責任
- [ ] DomainError で例外を定義
- [ ] 外部ライブラリに依存していない

## Data 層

- [ ] Repository インターフェースを実装
- [ ] Exposed Table が適切に定義
- [ ] Domain Entity へのマッピングが正しい
- [ ] トランザクション管理が適切

## Routes 層

- [ ] Request/Response DTO を使用
- [ ] 入力バリデーションを実施
- [ ] UseCase 経由でビジネスロジック呼び出し
- [ ] エラーを適切な HTTP ステータスに変換

## DI 設定

- [ ] 全ての依存が Koin で管理
- [ ] スコープが適切に設定
- [ ] 循環依存がない
