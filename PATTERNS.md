# Patterns

이 파일은 `C:/Users/UserK/.claude/LESSONS.md`의 빠른 인덱스에서 Codex용 패턴 후보를 초기 이식한 것이다.
반복되거나 비용이 큰 항목은 활성 패턴으로 유지하고, 기계적으로 감지 가능한 항목은 `scripts/` 검사로 내린다.

## Promotion Rule

- 같은 실수가 2회 이상 반복되면 LESSONS에서 PATTERNS로 승격한다.
- 한 번이어도 비용이 큰 실패는 즉시 패턴으로 승격한다.
- 정규식, AST, 테스트, 빌드 스크립트로 감지 가능하면 `scripts/`에 검사로 만든다.

## Imported Pattern Candidates

| Date | Pattern | Rule |
|------|---------|------|
| 2026-05-01 | filter-on-read cascade | 사용자 비활성화 cascade 정리는 DB cleanup 보다 read 시점 JOIN 필터 먼저 검토 (race·복귀 자동복원·데이터 보존) |
| 2026-05-01 | TxAfterCommit 헬퍼 | inline TSM 블록 N개 등장하면 즉시 헬퍼 추출, 트랜잭션 비활성 시 즉시 실행 분기로 테스트/스케줄러 안전망 |
| 2026-05-01 | view_requests vs API_REQUESTS | admin repo 의 view_requests.md 가 Flutter 작업 진척도 원장, Flutter repo API_REQUESTS.md 는 스펙 원장 — 둘 역할 분리 |
| 2026-04-30 | `\ | \ |
| 2026-04-30 | collapsible controlled | PanelCard 류는 controlled vs uncontrolled 모드 분기 처음부터 |
| 2026-04-30 | ring vs border | `overflow-*-auto` 부모 안에서는 ring(box-shadow) 클립됨, border 사용 |
| 2026-04-29 | Mock API contract | Mock 테스트는 외부 enum/허용값 검증 못 함, 실 dry-run 1회 필수 |
| 2026-04-29 | react-compiler key prop | useEffect 로 prop→state 동기화 금지, key prop 패턴으로 회피 |
| 2026-04-29 | cqmin vmin 매칭 | Creatomate 9:16 자체 미리보기는 container query `cqmin` 1:1 매칭 |
| 2026-04-27 | REQ 상태 동기화 | 표·본문 헤더·본문 첫 줄 3 곳 모두 같은 마커로 갱신 |
| 2026-04-27 | RQ v5 mutate cb | `mutate(vars, { onSuccess })` per-call 콜백은 unmount 시 호출 X |
| 2026-04-27 | refresh token race | rotate 직후 짧은 grace window 로 옛 토큰도 같은 결과 반환 |
| 2026-04-27 | Google OAuth scope | sensitive vs restricted, verification 비용 차이 큼 |
| 2026-04-27 | Jackson 3 asString | `asText()` deprecated, `asString()` 으로 일괄 마이그레이션 |
| 2026-04-27 | BE 미머지 graceful | 백엔드 미머지 REQ 의 Flutter 선제 작업 3단 graceful 패턴 |
| 2026-04-27 | Flutter ARB const Map | const Map 안에 l10n 못 넣음, 색상-only Map + build 시점 switch |
| 2026-04-27 | Flutter PopScope | back 결과 전달 시 시스템 back 도 함께 처리 |
| 2026-04-27 | i18n dead key | 화면 코드에서만 grep, arb/auto-gen 제외해야 잔재 안 남음 |
| 2026-04-27 | Page<T> wrapper | raw `Page<T>` 와 wrapper record 는 직렬화 키 다름 |
| 2026-04-27 | 백엔드 머지 검증 | 명세 문서가 아닌 실제 코드를 읽어야 진짜 머지 상태 확인 |
| 2026-04-27 | React 폴링 pollRef | useCallback+함수선언 상호 참조 시 pollRef 로 ESLint 회피 |
| 2026-04-26 | @Transactional inner try/catch | 영속성 컨텍스트 오염 → 후속 autoflush AssertionFailure |
| 2026-04-26 | react-hooks/purity | useMemo 콜백 내부도 룰이 막음 |
| 2026-04-26 | properties ${ENV:default} | 시크릿 디폴트 두지 말 것 (소스에 박힘) |
| 2026-04-26 | Mockito 헬퍼 덮어쓰기 | stubEmptyScoreMap 류가 공유 stub 덮어써 기존 테스트 깨짐 |
| 2026-04-26 | Jackson test ObjectMapper | `WRITE_DATES_AS_TIMESTAMPS=false` 명시 필수 |
| 2026-04-26 | Spring 7 다중 생성자 | 자동 선택 안 됨, `@JsonCreator` 등 명시 필요 |
| 2026-04-26 | 시크릿 src/main/resources | JAR 빌드 시 시크릿 패키징됨, 외부 경로 + env 로 |
| 2026-04-26 | Git 분리 커밋 | chore/feat 한 doc 에 섞일 때 임시 제거 → 복원 패턴 |
| 2026-04-26 | sub-agent 위임 금지규칙 | Flyway/V*.sql 같은 강한 금지는 prompt 에 inline 반복 강조 |
| 2026-04-26 | @Transactional self-invocation | 같은 클래스 메서드 호출은 트랜잭션 무시 |
| 2026-04-26 | UnexpectedRollbackException | rollback-only 마킹은 outer 트랜잭션까지 전파 |
| 2026-04-26 | Checkstyle MethodName | 기본 패턴이 테스트 `메서드명_상황_기대결과` 언더스코어 차단 |
| 2026-04-26 | STOMP CONNECT 인터셉터 | HTTP 사각지대, 만료 JWT 들고 가면 무한 막다른 길 |
| 2026-04-26 | @SpringBootTest+@MockBean | DB 직접 확인 방식으로 기선택 역할 유지 검증 |
| 2026-04-25 | 결제 HTTP Basic | 토스/Stripe 의 password 는 빈 콜론 (`secret:` 형태) |
| 2026-04-24 | BE/FE 필드명 | 빌드 통과해도 런타임 무음 실패 (camelCase/snake_case) |
| 2026-04-24 | 401 vs 403 | 만료 JWT 를 403 으로 떨어뜨리면 refresh 인터셉터 무력화 |
| 2026-04-24 | WSL2 mirrored | 실기기 LAN 접근 시 Hyper-V 방화벽 별도 설정 필요 |
| 2026-04-24 | Flutter 중첩 Scaffold | 외부 `extendBody:true` 가 내부 FAB 을 BottomNav 뒤로 숨김 |
| 2026-04-24 | adb reverse 자동화 | VPN/LAN 불안정 시 가장 robust |
| 2026-04-24 | Jackson is* boolean | JSON 직렬화에서 `is` 접두어 벗겨짐 |
| 2026-04-24 | vite proxy CORS | BE CORS 없이 proxy 로 덮어두면 절대 URL 박는 순간 터짐 |
| 2026-04-24 | @Profile + Security | FilterChain 먼저 적용되어 404 아닌 401 반환 |
| 2026-04-24 | Mockito @InjectMocks | 의존성 필드 추가 시 기존 테스트 `@Mock` 전체 동기화 |
| 2026-04-24 | Flutter analyzer cascade | 상위 경고 제거 후 하위 드러남, "No issues" 까지 반복 |
| 2026-04-24 | Flutter RadioGroup | RadioListTile deprecation 마이그레이션 시 nullable 함수 제약 |
| 2026-04-24 | Flutter docs/ analyzer | 외부 레퍼런스 .dart 가 analyzer 오염, exclude 등록 필수 |
| 2026-04-24 | adb -s serial | 복수 디바이스 환경 대비 명시 필수 |
| 2026-04-24 | Tailwind 4 @theme | shade 정의 누락 시 클래스가 조용히 무시됨 |
| 2026-04-24 | set-state-in-effect | React 19 룰 — effect body 에서 setState 동기 호출 금지 |
| 2026-04-24 | StrictMode useRef guard | 결제/OAuth 콜백 mutation 1회 guard 필수 |
| 2026-04-24 | vitest .env.test | `VITE_API_BASE_URL` 고정해야 MSW 핸들러 매칭 |
| 2026-04-24 | Input htmlFor+id | label htmlFor 와 input id 양쪽 모두 필요 |
| 2026-04-23 | Flutter kernel 캐시 | analyzer 통과해도 빌드 실패, `flutter clean` 필수 |
| 2026-04-23 | WSL gradlew ENOSYS | Windows git bash 에서 WSL 경로 gradlew 실행 시 |
| 2026-04-23 | Jackson 3 패키지 | Spring Boot 4 는 `tools.jackson.*` 으로 이동 |
| 2026-04-23 | @Modifying clearAutomatically | 생략 시 영속성 컨텍스트 캐시 stale |

## Scripted Checks

- `scripts/check-patterns.ps1`: 현재는 숫자/좌표/진행률 계열의 의심스러운 `|| default` 패턴을 검사한다.
