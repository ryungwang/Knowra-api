# Codex 완전자동화 키트

이 키트는 Claude Code에서 쓰던 개인 개발 시스템을 Codex 프로젝트에 이식하기 위한 자동화 묶음이다.

## 구성

- `AGENTS.md`: Codex 운영 규칙.
- `LESSONS.md`: 누적 교훈 원장.
- `PATTERNS.md`: 반복 실수를 승격한 규칙.
- `scripts/check-patterns.ps1`: 패턴 위반 검사.
- `scripts/verify.ps1`: 프로젝트 타입 감지 후 가능한 검증 실행.
- `scripts/lesson-promote.ps1`: LESSONS의 항목을 PATTERNS 후보로 승격.
- `scripts/sync-lessons.ps1`: 프로젝트 교훈/패턴을 전역 원장으로 흡수.
- `.githooks/pre-commit`: 커밋 전 빠른 검사 후 전역 교훈 동기화.
- `.githooks/pre-push`: 푸시 전 전체 검사 후 전역 교훈 동기화.
- `.github/workflows/verify.yml`: GitHub Actions 검증.

## 설치

PowerShell에서 대상 프로젝트 루트에 설치:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1 -TargetPath "C:\path\to\project" -Force
```

`-Force`를 주면 기존 파일을 `.bak-YYYYMMDD-HHMMSS`로 백업한 뒤 교체한다.

## 사용

빠른 검사:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode quick
```

전체 검사:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode full
```

LESSONS 항목을 PATTERNS로 승격:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\lesson-promote.ps1 -Keyword "refresh token race"
```

프로젝트 교훈을 전역 원장으로 수동 동기화:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-lessons.ps1 -ProjectPath . -GlobalKitPath C:\Users\UserK\.codex\automation-kit
```

## 운영 루프

1. 문제가 생기면 `LESSONS.md`에 원인, 해결, 예방을 기록한다.
2. 같은 문제가 반복되거나 비용이 크면 `PATTERNS.md`로 승격한다.
3. 정규식, AST, 테스트, 빌드로 잡을 수 있으면 `scripts/`에 검사로 만든다.
4. `scripts/verify.ps1`에 연결한다.
5. `scripts/sync-lessons.ps1`로 전역 `C:\Users\UserK\.codex\automation-kit`에 흡수한다.
6. Git hook이 커밋/푸시 때 자동으로 전역 동기화를 반복한다.
7. CI가 계속 감시하게 둔다.
