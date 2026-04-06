#!/usr/bin/env python3
"""
Gradle 依存関係監査スクリプト

build.gradle.kts から依存関係を抽出し、既知の問題をチェックします。
"""

import re
import sys
from pathlib import Path
from typing import Dict, List, Tuple

# 既知の脆弱性データベース（簡易版）
KNOWN_VULNERABILITIES = {
    'org.apache.logging.log4j:log4j-core': {
        'vulnerable_versions': ['2.0', '2.14.1'],
        'fixed_version': '2.17.0',
        'severity': 'CRITICAL',
        'cve': 'CVE-2021-44228',
    },
    'com.fasterxml.jackson.core:jackson-databind': {
        'vulnerable_versions': ['2.0.0', '2.12.5'],
        'fixed_version': '2.12.6',
        'severity': 'HIGH',
        'cve': 'CVE-2020-36518',
    },
}

# 非推奨ライブラリ
DEPRECATED_LIBRARIES = [
    ('com.android.support:', 'AndroidX に移行してください'),
    ('jcenter()', 'mavenCentral() に移行してください'),
]


def parse_gradle_dependencies(content: str) -> List[Tuple[str, str]]:
    """build.gradle.kts から依存関係を抽出"""
    dependencies = []

    # implementation("group:artifact:version") パターン
    pattern = r'implementation\(["\']([^:]+):([^:]+):([^"\']+)["\']\)'
    for match in re.finditer(pattern, content):
        group, artifact, version = match.groups()
        dependencies.append((f"{group}:{artifact}", version))

    return dependencies


def check_vulnerabilities(dependencies: List[Tuple[str, str]]) -> List[Dict]:
    """脆弱性チェック"""
    issues = []

    for dep, version in dependencies:
        if dep in KNOWN_VULNERABILITIES:
            vuln = KNOWN_VULNERABILITIES[dep]
            # 簡易的なバージョン比較（実際はもっと厳密に）
            issues.append({
                'type': 'vulnerability',
                'severity': vuln['severity'],
                'library': dep,
                'version': version,
                'cve': vuln['cve'],
                'fixed_version': vuln['fixed_version'],
            })

    return issues


def check_deprecated(content: str) -> List[Dict]:
    """非推奨ライブラリチェック"""
    issues = []

    for pattern, message in DEPRECATED_LIBRARIES:
        if pattern in content:
            issues.append({
                'type': 'deprecated',
                'severity': 'WARNING',
                'pattern': pattern,
                'message': message,
            })

    return issues


def main():
    # build.gradle.kts を検索
    current = Path.cwd()
    gradle_files = list(current.rglob('build.gradle.kts'))

    if not gradle_files:
        print("Error: build.gradle.kts が見つかりません")
        sys.exit(1)

    all_issues = []

    for gradle_file in gradle_files:
        print(f"Checking: {gradle_file}")
        content = gradle_file.read_text()

        dependencies = parse_gradle_dependencies(content)
        all_issues.extend(check_vulnerabilities(dependencies))
        all_issues.extend(check_deprecated(content))

    # 結果表示
    print("\n" + "=" * 60)
    print("Dependency Audit Report")
    print("=" * 60)

    if not all_issues:
        print("\n✅ 問題は検出されませんでした")
        sys.exit(0)

    critical = [i for i in all_issues if i['severity'] == 'CRITICAL']
    high = [i for i in all_issues if i['severity'] == 'HIGH']
    warning = [i for i in all_issues if i['severity'] == 'WARNING']

    if critical:
        print("\n🔴 CRITICAL:")
        for issue in critical:
            print(f"  • {issue['library']}:{issue['version']}")
            print(f"    CVE: {issue['cve']}")
            print(f"    修正: {issue['fixed_version']} 以上にアップデート")

    if high:
        print("\n🟠 HIGH:")
        for issue in high:
            print(f"  • {issue['library']}:{issue['version']}")
            print(f"    CVE: {issue['cve']}")
            print(f"    修正: {issue['fixed_version']} 以上にアップデート")

    if warning:
        print("\n🟡 WARNING:")
        for issue in warning:
            print(f"  • {issue['pattern']}")
            print(f"    {issue['message']}")

    print(f"\n合計: {len(all_issues)} 件の問題")

    if critical or high:
        sys.exit(1)
    sys.exit(0)


if __name__ == '__main__':
    main()
