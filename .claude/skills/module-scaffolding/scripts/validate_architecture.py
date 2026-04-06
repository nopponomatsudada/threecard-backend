#!/usr/bin/env python3
"""
アーキテクチャ検証スクリプト

Domain 層が外部依存を持っていないか、依存方向が正しいかを検証します。
"""

import os
import re
import sys
from pathlib import Path
from typing import List, Tuple

# 禁止されるインポート（Domain 層）
DOMAIN_FORBIDDEN_IMPORTS = [
    r'import\s+io\.ktor\.',
    r'import\s+org\.jetbrains\.exposed\.',
    r'import\s+org\.koin\.',
    r'import\s+com\.example\..*\.data\.',
    r'import\s+com\.example\..*\.routes\.',
]

# 禁止されるインポート（Routes 層）
ROUTES_FORBIDDEN_IMPORTS = [
    r'import\s+com\.example\..*\.data\.dao\.',
    r'import\s+com\.example\..*\.data\.entity\.',
    r'import\s+org\.jetbrains\.exposed\.',
]


def find_kotlin_files(directory: Path) -> List[Path]:
    """Kotlin ファイルを再帰的に検索"""
    return list(directory.rglob('*.kt'))


def check_imports(file_path: Path, forbidden_patterns: List[str]) -> List[Tuple[int, str]]:
    """禁止されたインポートを検出"""
    violations = []
    with open(file_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            for pattern in forbidden_patterns:
                if re.search(pattern, line):
                    violations.append((line_num, line.strip()))
    return violations


def validate_domain_layer(src_path: Path) -> List[str]:
    """Domain 層の検証"""
    errors = []
    domain_path = src_path / 'domain'

    if not domain_path.exists():
        return errors

    for kt_file in find_kotlin_files(domain_path):
        violations = check_imports(kt_file, DOMAIN_FORBIDDEN_IMPORTS)
        for line_num, line in violations:
            errors.append(f"[Domain] {kt_file.relative_to(src_path)}:{line_num}: 外部依存が検出されました: {line}")

    return errors


def validate_routes_layer(src_path: Path) -> List[str]:
    """Routes 層の検証"""
    errors = []
    routes_path = src_path / 'routes'

    if not routes_path.exists():
        return errors

    for kt_file in find_kotlin_files(routes_path):
        violations = check_imports(kt_file, ROUTES_FORBIDDEN_IMPORTS)
        for line_num, line in violations:
            errors.append(f"[Routes] {kt_file.relative_to(src_path)}:{line_num}: Data 層への直接依存が検出されました: {line}")

    return errors


def main():
    # プロジェクトルートを検出
    current = Path.cwd()
    src_path = current / 'src' / 'main' / 'kotlin'

    if not src_path.exists():
        # src/main/kotlin が見つからない場合、再帰的に探す
        for path in current.rglob('src/main/kotlin'):
            src_path = path
            break

    if not src_path.exists():
        print("Error: src/main/kotlin ディレクトリが見つかりません")
        sys.exit(1)

    print(f"Validating architecture in: {src_path}")
    print("=" * 60)

    errors = []
    errors.extend(validate_domain_layer(src_path))
    errors.extend(validate_routes_layer(src_path))

    if errors:
        print("\n❌ アーキテクチャ違反が検出されました:\n")
        for error in errors:
            print(f"  • {error}")
        print(f"\n合計: {len(errors)} 件の違反")
        sys.exit(1)
    else:
        print("\n✅ アーキテクチャ検証に合格しました")
        sys.exit(0)


if __name__ == '__main__':
    main()
