# 전역 LESSONS.md

> 모든 프로젝트에서 누적되는 기술적/설계적 교훈. 세션 시작 시 반드시 읽기.
> **읽는 순서**: 먼저 아래 빠른 인덱스만 훑고 → 이번 작업과 관련된 키워드의 항목만 본문(아래) 에서 offset/limit 또는 grep 으로 발췌. 매번 전체 read 하지 말 것 (토큰 낭비).

---

## 📌 빠른 인덱스

| 날짜 | 키워드 | 한줄 요약 |
|------|--------|----------|
| 2026-05-01 | filter-on-read cascade | 사용자 비활성화 cascade 정리는 DB cleanup 보다 read 시점 JOIN 필터 먼저 검토 (race·복귀 자동복원·데이터 보존) |
| 2026-05-01 | TxAfterCommit 헬퍼 | inline TSM 블록 N개 등장하면 즉시 헬퍼 추출, 트랜잭션 비활성 시 즉시 실행 분기로 테스트/스케줄러 안전망 |
| 2026-05-01 | view_requests vs API_REQUESTS | admin repo 의 view_requests.md 가 Flutter 작업 진척도 원장, Flutter repo API_REQUESTS.md 는 스펙 원장 — 둘 역할 분리 |
| 2026-04-30 | `\|\|` falsy default | 좌표·진행률·opacity 등 0 가능 값에 `\|\|` 금지, `??` / nullish 비교 |
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

---

## 2026-04-30 좌표/숫자 default 는 `||` 가 아닌 nullish 비교로 — 0 이 falsy 라 회귀 발생
- **상황**: ShortDduk 템플릿 편집기에서 영상 X/Y 위치를 드래그할 때 좌상단 끝 (X=0 또는 Y=0) 까지 끌어가면 갑자기 50% 로 튕겨돌아가는 버그. videoScalePct 가 0 일 때도 동일.
- **원인**: `TemplatePreview.jsx` 에서 `num(template.videoXPercent) || 50` 패턴 사용. `num()` 이 정상적으로 0 을 반환하지만 `||` 가 0 을 falsy 로 처리해 default 50 으로 덮어씀. 사용자는 분명히 0 을 의도한 좌표인데 코드가 "값 없음" 으로 오해.
- **해결**: `template.videoXPercent != null ? num(template.videoXPercent) : 50` 으로 nullish 체크 명시. JS optional chaining 환경이면 `?? 50` 도 동일.
- **예방**:
  - **숫자 default 에 `||` 금지** — 좌표 (0 가능), 진행률 (0 가능), 카운트 (0 가능), opacity (0 가능), 개수 (0 가능) 등 0 이 의미 있는 값에 default 줄 때는 무조건 `?? default` 또는 `value != null ? value : default`.
  - `||` 는 "0 도 default 로 덮어써도 무방" 한 경우만 — 예: 빈 문자열 → "Untitled" 같은 텍스트.
  - 비슷한 함정: `value || 100` 식의 백분율, `count || 1` 식의 분모, `score || -1` 식의 sentinel — 모두 의도와 다르게 동작 가능.
  - 특히 사용자 인터랙션 (드래그/슬라이더/입력) 으로 0 에 도달할 수 있는 값은 회귀 테스트 케이스에 0/min boundary 명시.

## 2026-04-30 PanelCard 같은 collapsible 컴포넌트는 controlled 모드 분기를 처음부터 지원
- **상황**: ShortDduk 템플릿 편집기에서 "자막/원본영상제목/쇼츠 제목줄/워터마크/로고" 패널들을 토글 (FieldToggle) 만으로 펼침/접힘 제어하고 싶었는데, PanelCard 가 자체 internal state 로만 동작해 헤더 클릭 + 토글 두 곳에서 따로 동작 → 헤더 클릭하면 토글 OFF 상태에서도 펼쳐지는 부조화.
- **원인**: collapsible 컴포넌트를 internal state 만으로 만들면 외부 prop 으로 강제 제어 불가. 헤더 클릭 → toggle 만 호출 → 외부 토글 상태와 sync 안 됨.
- **해결**: React 표준 controlled vs uncontrolled 분기 패턴.
  ```jsx
  function PanelCard({ collapsible, defaultExpanded = true, expanded: expandedProp, onToggle }) {
    const [internal, setInternal] = useState(defaultExpanded);
    const isControlled = expandedProp !== undefined;
    const expanded = isControlled ? expandedProp : internal;
    // controlled mode: 헤더 클릭 비활성, title 만 렌더 (button 아님)
    // uncontrolled: 헤더 button 으로 internal toggle
  }
  ```
- **예방**:
  - 새 collapsible/expandable/toggleable 컴포넌트는 처음부터 `value`/`expanded`/`open` prop 미지정 (uncontrolled) 와 지정 (controlled) 두 모드를 분기.
  - controlled 모드에서는 클릭 핸들러 / chevron / "expand 가능" 시각 단서를 모두 제거 — 외부에서만 제어된다는 사실을 UI 로 표현.
  - "토글 OFF 인데 펼쳐져 있음" 같은 모순 상태가 가능하면 컴포넌트 설계 자체가 잘못된 것. expanded 가 외부 토글의 derived state 가 되도록 controlled 모드 강제.

## 2026-04-30 `overflow-y-auto` 부모 안에서는 `ring` (box-shadow) 이 클립됨 — 1px 윤곽선이 필요하면 `border` 사용
- **상황**: ShortDduk 템플릿 편집기 휴대폰 미리보기 wrapper 가 `ring-1 ring-line/60` 인데 상단 1px 만 안 보임. 좌/우/하단 ring 은 정상.
- **원인**: 부모 stage 가 `overflow-y-auto` 였고 wrapper 가 부모 top edge 에 딱 붙어 있어서 box-shadow 기반 ring 이 부모 클립 박스 위로 1px 삐져나가던 부분만 잘림. border 와 다르게 ring 은 box-shadow 라 layout 영역 밖으로 그려짐.
- **해결**: `ring-1 ring-line/60` → `border border-line/60` 으로 교체. border 는 box 내부에 그려지므로 부모 overflow 와 무관.
- **예방**:
  - `overflow-{x,y,both}-{auto,scroll,hidden}` 부모 안에서 자식의 1px 윤곽선 표현은 **`border` 우선** — `ring` 은 자식이 부모 edge 에서 떨어져 있을 때만 안전.
  - shadow / glow 류 outer effect 도 동일 함정 — `shadow-elev` 류가 부모 클립으로 잘리면 padding 추가 또는 `overflow-visible` 로 회피.
  - "왜 한쪽 변만 안 보이지?" 디버깅 시 즉시 ring vs border 를 의심하고 부모 overflow 체크.

## 2026-04-29 Mock 기반 외부 API 테스트는 enum/string contract 검증 못 함 — 실 호출 dry-run 으로 별도 보강 필요
- **상황**: ShortDduk 의 `CreatomateHttpClient` MockWebServer 테스트 7건이 통과한 상태에서 운영 dry-run 시도 → `Source error: Video-1.color_filter: Expected one of these values: none, brighten, contrast, hue, invert, grayscale, sepia` 로 거절. `CreatomateTemplateBuilder` 가 보내던 `warm`/`vivid` 값은 Creatomate API 가 지원 안 하는 무효 값.
- **원인**: Mock 서버는 우리가 정의한 fake response 만 돌려주므로 우리 요청 body 의 string 값이 진짜 API 의 contract (허용 enum) 에 부합하는지 절대 검증할 수 없음. 즉 mock 통과 = "우리 코드끼리는 일관됨" 일 뿐 "실 API 가 받아준다" 는 보증이 아님.
- **해결**:
  - VideoFilter enum 을 Creatomate 가 받는 단일 값들 (none/grayscale/sepia) 에 1:1 매핑되는 3종으로 단순화. 합성 필터 (sepia + saturate + hue-rotate 같은 vintage) 는 단일 color_filter 로 표현 불가하므로 enum 차원에서 제외.
  - 운영 DB 에 옛 값 (VINTAGE/VIVID) 이 남아있을 가능성 → `AttributeConverter` 로 unknown 값 read 시 NONE 폴백 (안전망). JPA 표준 `@Enumerated(STRING)` 은 unknown 값에서 IllegalArgumentException → 트랜잭션 폭발하므로 위험.
- **예방**:
  - 외부 API 통합 시 mock test 외에 **실 API dry-run 1회를 운영 배포 전 필수 절차로 명시** (TODO 에 항목 등록). `*.mock=false` 로 토글 후 실제 1회 호출.
  - 외부 API 가 enum/허용값 리스트를 강제하는 필드 (color_filter, status, type 등) 는 우리 코드의 enum 을 외부 API 의 enum 과 **1:1 매핑이 가능한 값들로만 구성**. 매핑 불가능한 추가 옵션 (합성 필터, custom value) 은 도입 전 외부 API contract 부터 확인.
  - mock test 자체에 string allow-list assertion 을 넣는 건 외부 API 의 source-of-truth 를 우리 테스트가 다시 적는 것이라 빠르게 stale 됨. 실 API dry-run 이 정공법.
  - DB enum 컬럼은 `@Enumerated(STRING)` 보다 `@Convert(AttributeConverter)` + 정적 `fromSafe(String)` 폴백 메서드 패턴이 운영 안전성 우월. 특히 enum 정리 (VINTAGE 같은 옛 값 제거) 시점에 운영 DB 마이그레이션 없이도 안전.

## 2026-04-27 REQ 로드맵 문서 (API_REQUESTS.md / PLAN.md / TODO.md) 상태 표기는 표 + 본문 헤더 + 본문 첫 줄 3 곳 동기화 필수
- **상황**: chase_and_run_admin 프로젝트의 `API_REQUESTS.md` 갱신 시 표 행의 ⚪→🟢 만 바꾸고 본문 섹션 제목(`## REQ-X 〔⚪ 신규〕`) + 본문 첫 줄(`- **상태**: ⚪ 신규 (...)`) 은 그대로 두는 실수 반복.
- **원인**: 표만 grep/edit 으로 잡고 본문 헤더 / 본문 상태 라인 두 곳을 빠뜨림. 단순 검토 누락.
- **해결**: PL 이 직접 "표에 완료 표시할 때 본문에 상태도 바꿔줘야지" 지적 → 일괄 검토 후 ⚪ 잔재 모두 제거.
- **예방**:
  - REQ 상태 변경 시 항상 `Grep "⚪ 신규"` (또는 변경 전 마커) 로 잔재 확인
  - 표 행 + 본문 헤더 (`〔...〕`) + 본문 "**상태**:" 라인 — 3 곳 모두 같은 상태로 갱신
  - 단일 변경 후 마지막에 같은 마커 grep 으로 0건 확인하는 게 가장 확실

## 2026-04-27 React Query v5 — `mutate(vars, { onSuccess })` 의 per-call 콜백은 컴포넌트 unmount 시 호출 안 됨
- **상황**: ShortDduk YoutubeCallbackPage 에서 OAuth 콜백 처리 후 `connect.mutate({ code }, { onSuccess: () => navigate(SETTINGS) })` 패턴 사용. 콜백 페이지가 "연결 중..." 에서 영구히 stuck.
- **원인**: StrictMode dev 더블 마운트로 첫 인스턴스가 즉시 unmount → React Query v5 의 mutate per-call 콜백은 unmount 시 fire 되지 않음. hook-level `useMutation({ onSuccess })` 는 발화하지만 mutate 호출 시점에 인자로 넘긴 콜백은 폐기됨. 백엔드는 정상 처리되지만 frontend navigate 가 영영 안 일어나 사용자는 stuck 상태로만 보임.
- **해결**: mutation 콜백 의존 제거. `mutate({ code })` 호출 직후 즉시 `navigate(...)` 실행. 캐시 invalidate 같은 것은 hook-level `onSuccess` (mutation 객체에 묶여 unmount 영향 안 받음) 에서 처리.
- **예방**:
  - StrictMode 환경에서 OAuth callback 같은 1회용 페이지의 mutation 콜백에 navigate / 화면 전환 같은 핵심 로직 두지 말 것.
  - 전환은 즉시 실행하거나 hook-level onSuccess 에 두기.
  - 같은 OAuth `code` 의 중복 소비를 막으려면 `useRef` 로는 부족 (re-mount 시 새 ref). `sessionStorage` 같은 컴포넌트 외부 dedup 키 필요.

## 2026-04-27 Refresh token rotation — 동시 요청 race 로 강제 로그아웃, grace window 로 방어
- **상황**: ShortDduk 에서 OAuth 외부 사이트 다녀온 후 풀 reload 시점에 사용자가 로그아웃됨. App.useEffect → `bootstrap.mutate()` (POST /auth/refresh) 가 StrictMode 로 두 번 발화. 첫 번째가 성공해 백엔드 DB 의 `refresh_token` 을 rotate (new1 으로 교체) 한 직후, 두 번째 요청이 옛 쿠키 (브라우저가 첫 번째 Set-Cookie 적용 전) 로 도착해 `findByRefreshToken` 실패 → AUTH_002 → frontend `clear()` + `/login` 리다이렉트.
- **frontend 만으로는 부족**: `useRef` guard 로 dev StrictMode 는 막지만, 모바일 backgrounding 후 복귀, 빠른 더블탭, 다중 탭 동시 호출 같은 다른 race 케이스에 여전히 취약.
- **백엔드 grace window 패턴 (정공법)**: rotate 직후 짧은 시간 (예: 30초) 동안 **옛 refresh_token → 새로 발급된 AuthResult** 를 in-memory 캐시에 보관. 두 번째 요청이 같은 옛 토큰으로 와도 캐시 hit 으로 동일 결과 반환 → 두 클라이언트 모두 성공.
- **구현 노트**:
  - `ConcurrentHashMap<String, Entry>` + Clock 주입 (테스트 가능). TTL 30초, MAX_ENTRIES 도달 시 lazy sweep.
  - 단일 인스턴스 운영 전제. 다중 인스턴스 (Render scale-out) 시 Redis 분산 캐시로 교체 필요.
  - AuthService.refresh 진입 시점에 캐시 먼저 조회 → hit 면 DB 조회 없이 반환, miss 면 정상 처리 후 옛 토큰을 키로 캐시 put.
- **예방**: refresh_token rotation 을 도입하는 모든 시스템은 동시 요청 race 를 가정해야 함. frontend ref guard 만으로는 dev StrictMode 만 막힘. 백엔드 grace window 가 정공법.

## 2026-04-27 Google OAuth scope — sensitive vs restricted, verification 비용 차이
- **상황**: ShortDduk 에서 `youtube.upload` 만 필요한데 `youtube` (전체 read/write) 까지 추가하려 했음.
- **차이**:
  - `youtube.upload` → **sensitive scope** — verification (privacy policy + demo video + 브랜드 확인) 만 받으면 됨. 비용 0, 1~3주.
  - `youtube` (전체) → **restricted scope** — verification 외에 **3rd-party 보안 평가 (CASA Tier 2/3)** 까지 요구. 비용 $4,500~$75,000, 6주~수개월.
- **사용자 동의 화면 conversion**: restricted 면 "당신의 YouTube 계정 관리 (영상 업로드/수정/삭제, 채널 정보 변경)" 광범위 문구 노출. sensitive 면 "영상 업로드" 한 줄. 거부율 차이 큼.
- **예방**:
  - 외부 OAuth 통합 시 **최소 scope 원칙** 엄수. 필요할 때 `include_granted_scopes=true` 로 incremental authorization 활용.
  - "혹시 나중에 쓸지도 모르니 미리 받아두자" 는 함정. 출시 일정 + 비용 직격탄.
  - Google · Microsoft · Slack 등 모든 OAuth 가 비슷한 scope 등급제. 통합 전 docs 의 sensitive/restricted 분류 확인.

## 2026-04-27 Jackson 3 (Spring Boot 4) — `JsonNode.asText()` 도 deprecated, `asString()` 로 일괄 마이그레이션
- **상황**: ShortDduk Spring Boot 4 (Jackson 3) 프로젝트. `node.asText("default")` 가 deprecated 라 `asText()` no-arg 로 임시 회피. 그런데 빌드해보니 `asText()` 도 deprecated.
- **원인**: Jackson 3.0 부터 `asText()` / `asText(String)` 모두 deprecated. 신규 권장 API 는 **`asString()` / `asString(String defaultValue)` / `asStringOpt()` (Optional<String>)**. 이전 메서드들은 `@Deprecated // since 3.0` 마커.
- **해결**: 전체 src 의 `\.asText\(` → `\.asString\(` sed 일괄 치환. 시그니처 100% 호환 (alias of asString).
- **예방**:
  - Spring Boot 4.x 프로젝트의 IDE 자동 import 가 `node.asText(...)` 를 추천하면 무시하고 `asString(...)` 사용.
  - Jackson 2 시절 `asText()` 가 missing/null 시 `""` 반환하던 contract 는 `asString()` 도 동일.
  - `asTextOpt()` 같은 Optional 변형은 `asStringOpt()` 로 이름 바뀜.
  - build.gradle 에 `options.compilerArgs << '-Xlint:deprecation'` 추가하면 모든 deprecated 호출 위치를 정확한 파일:라인 으로 표시 — 일괄 마이그레이션 전 필수.

---

## 2026-04-26 Spring @Transactional + Hibernate inner try/catch — 영속성 컨텍스트 오염으로 후속 autoflush AssertionFailure
- **상황**: chase_and_run_admin `RankingApplyService.applyGameResult` 에서 봇 player 에 대해 신규 `Ranking` 을 `rankingRepository.save()` 시도. DB constraint 또는 기타 이유로 INSERT 실패 → 호출부의 `try { applyForPlayer(...) } catch (Exception e)` 가 예외를 삼킴 → 영속성 컨텍스트에 id=null 상태의 Ranking 엔티티가 잔류 → 후속 `roomPlayerRepository.findByRoomId...` SELECT 쿼리 실행 시 Hibernate autoflush 발동 → `org.hibernate.AssertionFailure: null id in Ranking entry` 로 전체 트랜잭션 폭발.
- **실수**: inner try/catch 가 예외를 먹으면 "그 블록만 실패하고 나머지는 계속 진행" 된다고 가정. 실제로는 영속성 컨텍스트(1차 캐시)는 예외 전 상태로 오염된 채 남아, 이후 같은 트랜잭션 내 어떤 SELECT 라도 autoflush 를 트리거해 폭발.
- **원인**: Hibernate 는 flush 전략(FlushMode.AUTO) 에 따라 SELECT 직전 영속성 컨텍스트를 자동 flush. id 미할당 엔티티가 남아 있으면 INSERT SQL 을 생성하려다 AssertionFailure.
- **해결 (이번 케이스)**: fail 가능성 있는 엔티티(봇 player) 를 처음부터 처리 대상에서 제외. `playerRepository.findAllById(allPlayerIds)` 로 봇 id 집합 구성 → humanParticipants 만 for 루프 순회. 봇은 비즈니스적으로 ranking 추적이 무의미해 사전 필터링이 자연스러운 해결책이었음.
- **일반 해결 옵션**:
  1. **사전 필터링** (이번 케이스) — fail 가능성 있는 대상을 처음부터 제외. 가장 깔끔.
  2. **`REQUIRES_NEW` 격리 트랜잭션** — `applyForPlayer` 를 별도 `@Transactional(propagation = REQUIRES_NEW)` 메서드로 추출. 내부 실패 시 해당 트랜잭션만 롤백되고 outer 컨텍스트는 정상.
  3. **catch 내 `EntityManager.clear()`** — 예외 catch 시 영속성 컨텍스트 전체를 비워 오염 제거. 단 다른 영속 엔티티의 변경분도 날아가므로 주의.
- **예방**:
  - `@Transactional` 메서드 내 inner try/catch 로 예외를 삼키는 패턴은 "그 블록만 실패" 가 아니라 "영속성 컨텍스트 오염 가능" 으로 인식. 특히 save()/persist() 실패 경우 위험.
  - 봇/게스트 등 특수 엔티티를 범용 집계 서비스에 섞으면 예기치 않은 side effect 발생 가능. 비즈니스 의미가 없는 대상은 서비스 진입 전에 필터링.
  - 유사 증상: `org.hibernate.AssertionFailure: null id`, `TransientPropertyValueException`, `IdentifierGenerationException` + 그 직전에 catch 로 먹힌 예외 로그가 보이면 이 패턴 의심.

---

## 2026-04-26 React 19 — `react-hooks/purity` 룰이 useMemo 콜백 내부도 막음
- **상황**: ShortDduk DashboardPage 에 "오늘의 팁" 일일 시드 추가. `Math.floor(Date.now() / 86_400_000) % TIPS.length` 를 render 본문에서 호출 → ESLint 가 "Cannot call impure function during render" 로 차단.
- **시도 1 (실패)**: `useMemo(() => Date.now() ..., [])` 로 감쌈. **그래도 동일 룰 위반**. React 17/18 의 통념 (useMemo 콜백 내부는 OK) 과 다르게 React 19 의 `react-hooks/purity` 는 useMemo 콜백 내부의 `Date.now/Math.random` 까지 검사함.
- **해결**: 모듈 스코프 const 로 빼서 모듈 로드 시점 1회 계산. `const TODAY_TIP = TIPS[Math.floor(Date.now() / 86_400_000) % TIPS.length];` — 페이지가 여러 번 마운트돼도 같은 값, 자정 지나도 같은 값 (큰 문제 아님).
- **예방**:
  - React 19 + `eslint-plugin-react-hooks` v6 환경에서 render-tree 안에서 `Date.now()` / `Math.random()` / `crypto.randomUUID()` / 파일 시스템 read 등 비순수 함수는 useMemo 로도 우회 불가.
  - 우회 옵션: ① 모듈 스코프 const ② `useState(() => impure())` lazy initializer (React 가 1회 호출 보장 — 룰 통과 여부는 버전별 다름, 실측 필요) ③ `useEffect(() => setX(impure()), [])` (마운트 후 1프레임 비어 보임).
  - "useMemo([]) 로 감싸면 어디든 OK" 라는 가정 버리기 — 룰러는 콜백 내부도 본다.

## 2026-04-23 Flutter — analyzer 통과하지만 빌드는 실패하는 kernel 캐시 이슈
- **상황**: chase_and_run 프로젝트에서 `flutter_local_notifications` v21 API (positional → named args) 에 맞춰 `initialize(settings:)`, `show(id:, title:, body:, notificationDetails:)` 로 수정했고 `flutter analyze` 는 "No issues found" 통과.
- **실수**: analyzer 결과만 믿고 빌드 검증을 생략. 이후 `flutter build apk --debug` 가 동일 파일에서 "Too many positional arguments: 0 allowed, but 1 found" / "4 found" 로 실패.
- **원인**: analyzer 는 매번 원본 소스를 재파싱하지만, `.dart_tool/flutter_build/**` 의 kernel dill 증분 캐시는 플러그인 API 바인딩을 이전 시점(positional) 에 고정해둔 상태. 파일을 named 로 수정해도 빌드 시스템은 옛 resolution 을 이어서 씀.
- **해결**: `flutter clean` → `flutter pub get` → `flutter build apk --debug` 로 성공.
- **예방**: 
  - 플러그인 API 변경(positional ↔ named) 에 대응한 수정 후에는 **반드시 `flutter clean` 후 빌드 검증**. analyzer 초록불만 보고 완료 보고 금지.
  - 빌드 에러의 "n found" 숫자가 현재 소스의 인자 개수와 다르면(= 소스와 어긋난 시그니처) kernel dill 캐시를 먼저 의심.
  - 커밋 전 체크리스트의 "빌드 성공" 항목은 analyzer 로 대체 불가.

---

## 2026-04-23 Gradle/WSL — Windows git bash 에서 WSL 경로 gradlew 실행 시 `잘못된 기능입니다` (ENOSYS)
- **상황**: ShortDduk 프로젝트 작업 중. 워킹 디렉토리가 `\\wsl.localhost\Ubuntu\home\deer\...` 인 상태에서 Claude Code 의 Windows 측 bash 로 `cd api && ./gradlew compileJava --no-daemon` 실행.
- **실수**: `FileHasher` 생성 단계에서 `java.io.IOException: 잘못된 기능입니다` (ERROR_INVALID_FUNCTION / ENOSYS) 로 BUILD FAILED. `--no-watch-fs` 를 붙여도 동일.
- **원인**: Windows 쪽 JVM (git bash 프로세스) 이 `\\wsl.localhost\...` UNC 경로 위에서 Gradle 의 virtual file system 초기화를 시도할 때, WSL 9P/Plan9 가상 파일시스템이 일부 Windows 파일 API (예: 특정 `ReOpenFile` / file id 조회) 를 지원하지 않아 `잘못된 기능입니다` 를 반환. Gradle 7+ 의 file hasher 가 이 API 를 반드시 호출.
- **해결**: WSL 내부에서 실행 — `wsl.exe -d Ubuntu -e bash -lc "cd /home/deer/intellij-workspace/ShortDduk/api && ./gradlew <task> --no-daemon"`. Linux 네이티브 경로로 해석되어 Gradle 정상 동작.
- **예방**:
  - 프로젝트 경로가 `\\wsl.localhost\...` 또는 `\\wsl$\...` 인 경우 Gradle/npm 등 **파일시스템 집약적 빌드 도구는 기본으로 `wsl.exe ... bash -lc "cd /home/... && <cmd>"` 로 실행**한다. Vite 빌드도 동일 이슈 가능성 → 첫 시도부터 WSL 내부 실행.
  - Windows 측 IDE (IntelliJ Windows 빌드) 에서도 동일 재현 가능 — 반드시 WSL 인터프리터를 Gradle/Java 실행기로 지정.
  - 빌드 에러가 "잘못된 기능입니다" 또는 "ERROR_INVALID_FUNCTION" 메시지를 포함하면 99% 이 경로-호스트 미스매치가 원인. 소스 코드 문제 아님 — 실행 위치부터 의심.

---

## 2026-04-23 Jackson 3 — Spring Boot 4.x 는 패키지가 `tools.jackson.*` 으로 이동
- **상황**: Spring Boot 4.0.5 프로젝트에서 `ObjectMapper`/`JsonProcessingException` 을 평소처럼 `com.fasterxml.jackson.databind.ObjectMapper` / `com.fasterxml.jackson.core.JsonProcessingException` 로 import 했다가 `package com.fasterxml.jackson.databind does not exist` 컴파일 오류.
- **원인**: Spring Boot 4 부터 Jackson 3 을 기본 채택 (`spring-boot-starter-jackson` → `tools.jackson.core:jackson-databind:3.x`). Jackson 3 은 메인 패키지를 `tools.jackson.*` 로 리네이밍. **단 어노테이션 모듈 (`jackson-annotations`) 은 하위호환 유지용으로 여전히 `com.fasterxml.jackson.annotation.*`** → `@JsonProperty` 등은 그대로, `ObjectMapper`/`JsonMapper`/예외 클래스만 이동.
- **해결**:
  - `com.fasterxml.jackson.databind.ObjectMapper` → `tools.jackson.databind.ObjectMapper`
  - `com.fasterxml.jackson.core.JsonProcessingException` → `tools.jackson.core.JacksonException` (Jackson 3 에서 예외 계층이 `JacksonException` 으로 단일화됨)
  - `@JsonProperty` 등 annotation 은 **건드리지 않음**
- **예방**:
  - Spring Boot 4.x 프로젝트에서는 IDE 자동 import 가 `com.fasterxml.jackson.databind` 를 제안할 수 있으나 패키지 누락 에러 뜨면 바로 `tools.jackson.databind` 로 교체.
  - 한 프로젝트 내에서 Jackson 2·3 어노테이션이 혼재 (annotations 은 2.21, databind 는 3.1.0) 하지만 런타임 호환. 직접 의존성 명시할 때는 BOM (`tools.jackson:jackson-bom`) 에 위임.
  - `gradle dependencies | grep jackson` 로 현재 버전 빠르게 확인: `tools.jackson.core:jackson-databind:3.x` 가 뜨면 Jackson 3.

---

## 2026-04-23 Spring Data JPA — `@Modifying(clearAutomatically = true)` 생략 시 크레딧 캐시 stale
- **상황**: ShortDduk `CreditService.charge/refund/grantMonthly` 에서 `UserRepository` 의 `@Modifying @Query` UPDATE 호출 후, 같은 트랜잭션에서 `findById` 로 유저를 다시 읽어 `remaining` 값을 계산.
- **원리**: `@Modifying` 기본값은 `clearAutomatically=false` 라 UPDATE 가 DB 에 반영돼도 1차 캐시 (EntityManager) 는 이전 영속 객체를 유지. 같은 트랜잭션에서 `findById` 하면 캐시 히트로 옛 `klingCredits`/`monthlyCredits` 반환 → `remaining` 오계산, `credit_history.remaining` 이 실제 DB 와 어긋남.
- **해결**: `@Modifying(clearAutomatically = true)` 로 선언. UPDATE 이후 영속성 컨텍스트를 clear 해 다음 조회가 DB 를 다시 읽도록 강제.
- **예방**:
  - **크레딧·재고·포인트 등 "숫자 컬럼을 벌크 UPDATE 후 같은 트랜잭션에서 다시 읽는" 패턴은 반드시 `clearAutomatically = true`**. 또는 서비스에서 `EntityManager.clear()` / `refresh()` 수동 호출.
  - 더 깔끔한 대안: UPDATE 쿼리에서 새 값을 직접 반환하는 `RETURNING` (MariaDB 는 미지원) 또는 **서비스 계산 (old 값 ± delta)** 으로 DB 재조회 자체 회피. 재조회가 필수일 때만 `clearAutomatically=true` 사용.
  - 테스트에서 드러나지 않을 가능성 — Mockito 기반 단위 테스트는 영속성 컨텍스트가 없으므로 이 이슈 재현 불가. 통합 테스트 (`@DataJpaTest` + 실제 UPDATE) 를 거쳐야 포착됨.

---

## 2026-04-24 BE/FE 필드명 불일치 — 빌드 통과해도 런타임 무음 실패
- **상황**: chase_and_run_admin Gold 패키지 — BE 새 DTO (`AdminRoomSummary.currentPlayers/maxPlayers`, `PlayerDetailResponse.phoneNumber`, `ResolveReportRequest.status`, 중첩 `player.ranking.totalScore`) 를 FE 가 다른 이름으로 참조 (`currentCount/maxCount`, `phone`, `action`, `player.totalScore`).
- **실수**: BE 225 테스트 통과, FE vite build + eslint 0 경고. 둘 다 "완료" 로 선언. 리뷰어가 잡지 않았으면 방 인원 컬럼 `-/-`, 신고 처리 버튼 400 에러, 누적 통계 모두 0, 휴대폰 `-` 렌더링. UI 테스트/E2E 없으면 프로덕션까지 탐지 불가.
- **원인**:
  1. JS 는 존재하지 않는 키 참조해도 런타임 `undefined` (예외 없음) → `?? '-'` fallback 이 버그를 덮음
  2. 요청 body 는 `@NotNull` validation 이 400 으로 잡긴 하지만 FE 가 catch 하면 "처리에 실패했습니다" 로 뭉뚱그려져 구체 원인 은폐
  3. BE Agent 와 FE Agent 가 병렬로 작업하면 DTO 필드명 계약을 서로 다른 추측으로 구현하기 쉬움 (예: "action" vs "status", "currentCount" vs "currentPlayers")
  4. 중첩 응답 (`PlayerDetailResponse` 가 `ranking` 객체 포함) 을 FE 가 평탄화된 형태로 가정
- **해결**:
  - 리뷰어가 실제 BE DTO 소스 ↔ FE jsx 소스를 나란히 대조해 4건 발견
  - BE 필드명을 진실 소스로 삼고 FE 수정 (`status/phoneNumber/currentPlayers/ranking.totalScore` 로 통일)
  - 닉네임이 BE 에 없어 FE 렌더가 빈값이던 건은 BE 에 `reporterNickname/reportedNickname` 배치 조회로 추가 (ID 만 보여주는 UI 는 실무 무용)
- **예방**:
  - **BE 컨트롤러 구현 직후 FE 호출부 실제 필드 참조를 grep 으로 대조**하는 체크를 리뷰어 프로토콜에 고정. `grep -rn "\.${필드명}" admin/src` / BE DTO 의 `private final \w+ \w+;` 추출 후 FE 소스에서 전수 확인.
  - BE Agent + FE Agent 분업 시 **공통 "API 계약 요약" (필드명/타입/중첩 구조) 을 중간 산출물로 먼저 확정**한 뒤 각자 구현. planner 단계에서 DTO 필드명을 명시하면 합치율 급상승.
  - FE 는 `??` chain 에 의존하지 말고 첫 렌더 시 `console.assert(data.expectedField != null)` 같은 방어 로그를 dev 빌드에서 남기거나, TypeScript + 공유 타입 정의를 도입해 컴파일타임 탐지.
  - 커밋 전 체크리스트에 "신규/수정 DTO 가 있는 경우 FE 의 해당 응답 소비 지점 육안 확인" 항목 추가.
  - 빌드 성공 + 테스트 통과 ≠ 기능 정상. Gold 급 대규모 작업은 가능하면 MSW 기반 통합 테스트 또는 최소 수동 스모크 체크.

---
## 2026-04-24 Flutter/Spring — 만료 JWT 를 401 대신 403 으로 떨어뜨리면 refresh 인터셉터가 무력화됨
- **상황**: chase_and_run 에서 전날 저장된 access token 이 만료된 채 재시작. 앱은 저장된 토큰 유무만 보고 `Authenticated` 로 부팅, 모든 API 호출이 403 으로 실패. STOMP handshake 도 동시에 ERROR frame 으로 끊어짐.
- **실수**: `AuthInterceptor` 를 `statusCode == 401` 일 때만 refresh 흐름을 돌리도록 작성. 근거는 "만료 = 401" 이라는 HTTP 시맨틱이었으나 실제 Spring Security 6 의 `BearerTokenAccessDeniedHandler` / 기본 `ExceptionTranslationFilter` 는 **누락·서명불일치·만료된 JWT 를 기본 403** 으로 내려보낸다. 401 조건이 영원히 안 맞아 refresh 시도 자체가 일어나지 않음 → 사용자는 수동 재로그인 없이는 복구 불가.
- **원인 2중**: 
  1. 백엔드: Spring Security 가 "인증 실패" 를 401 이 아닌 403 으로 응답 (`AuthenticationEntryPoint` 미커스터마이즈 시 기본 동작).
  2. 프론트: refresh 트리거를 401 에만 국한.
- **해결**:
  - 즉시 조치 (프론트): `AuthInterceptor` 를 401·403 모두에서 refresh 시도하도록 확장. `alreadyRetried` 플래그로 legitimate 403 (권한 부족) 무한 루프 방지. refresh endpoint 자신은 `isAuthRoute` 가드로 제외.
  - 근본 조치 (백엔드): `SecurityFilterChain` 의 `exceptionHandling` 에서 `authenticationEntryPoint` 를 커스터마이즈 — 인증 실패(토큰 부재/만료/위조) 는 401, `AccessDeniedHandler` 의 권한 부족은 403 으로 분리. Flutter 측 REQ-A-07 로 추적.
- **예방**:
  - **JWT refresh 인터셉터 작성 시 401·403 을 모두 커버**. HTTP 시맨틱의 이상과 Spring 기본 동작의 현실이 다르다. 특히 `hasRole("PLAYER")` 같은 role 필터를 걸면 기본 구현이 인증 실패도 권한 부족으로 취급해 403 으로 돌아오기 쉬움.
  - 백엔드 Spring Security 설계 시 `authenticationEntryPoint` 커스터마이즈를 체크리스트에 넣기. JWT 인증 실패 = 401 명시.
  - 로그인 가드의 `build()` 가 토큰 *존재* 만 보고 Authenticated 로 통과시키면 만료 토큰을 그대로 사용. 기동 시점에 `/auth/me` 또는 짧은 검증 호출로 1회 liveness 체크하는 게 이상적 (이번 건은 1차 API 호출이 곧 검증이 되므로 생략 가능).
  - 프론트 에러 로거에 Authorization 헤더 유무(토큰 앞자리 마스킹) 를 dev 빌드에서 출력하면 "토큰 누락" vs "토큰 전송되지만 거부" 구분이 즉시 됨.

## 2026-04-24 WSL2 mirrored networking — LAN 외부 기기(실기기)에서 접근 시 Hyper-V 방화벽 별도 설정 필요
- **상황**: chase_and_run 에서 실기기를 같은 공유기 Wi-Fi 로 붙이고 `API_BASE_URL=http://<PC-LAN-IP>:9090` 설정. Windows 에서 `curl localhost:9090` 은 403 (경로 OK), 하지만 실기기·PC 모두 LAN IP 로는 타임아웃.
- **실수**: WSL2 `.wslconfig` 에 `networkingMode=mirrored` + Windows Defender 방화벽 인바운드 규칙 추가로 충분하다고 판단. 실제로는 WSL 이 별도 Hyper-V 방화벽 프로파일을 사용하는 것을 놓침.
- **원인**: Windows 11 24H2 기준 **WSL 전용 Hyper-V 방화벽** 이 일반 Windows Defender 방화벽과 분리 동작. 기본 inbound action 이 Block 으로 설정돼 LAN 인바운드 트래픽이 전부 drop. `New-NetFirewallRule` 은 Defender 측만 건드리고 Hyper-V 측은 그대로라 무력.
- **해결**: 관리자 PowerShell 에서 WSL VM 의 Hyper-V 방화벽 규칙 추가 —
  ```powershell
  New-NetFirewallHyperVRule -Name "WSL-SpringBoot-9090" -DisplayName "WSL Spring Boot 9090" -Direction Inbound -VMCreatorId "{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}" -Protocol TCP -LocalPorts 9090 -Action Allow
  ```
  또는 전체 허용: `Set-NetFirewallHyperVVMSetting -Name "{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}" -DefaultInboundAction Allow` (개발 환경용).
- **예방**:
  - WSL2 mirrored + LAN 노출이 필요한 시나리오에선 **두 방화벽(Defender + Hyper-V)** 을 **모두** 고려. 증상이 "PC 는 localhost OK, LAN IP 는 drop" 이면 Hyper-V 쪽 먼저 의심.
  - `localhost:PORT` 는 mirrored 의 relay 로 접근 가능하지만, `<LAN-IP>:PORT` 는 별도 경로 — 진단 시 둘 다 테스트.
  - Windows 업데이트 후 Hyper-V 방화벽 기본값이 초기화될 수 있음. 영구 설정이라도 주기적 재확인 필요 (실제 2026-04-24 에 "이전에 해놨던 게 깨짐" 경험).

---

## 2026-04-24 Flutter — 중첩 Scaffold 에서 외부 `extendBody: true` 가 내부 FAB 을 BottomNav 뒤로 숨김
- **상황**: chase_and_run 의 `ShellScaffold` 가 `bottomNavigationBar` 를 제공하면서 `extendBody: true` 로 body 가 바텀 내비 뒤까지 늘어나도록 설정. 내부 `RoomListPage` 가 별도 `Scaffold` 로 `floatingActionButton` 을 띄움 — 우측 하단 FAB "방 만들기" 가 화면에 **아예 안 보임**.
- **실수**: FAB 누락으로 오인해 한참 코드 의심. 실제로는 렌더링은 됐지만 외부 `BottomNavigationBar` 가 위에 덮어 가려진 상태.
- **원인**: 외부 Scaffold 의 `extendBody: true` 는 body(= inner Scaffold 영역) 를 BottomNav 뒤까지 확장. 내부 Scaffold 의 FAB 는 내부 body 의 **bottom edge** 기준으로 배치되는데, 이 bottom edge 가 외부 BottomNav 뒤에 있게 됨 → FAB 가 BottomNav 아래로 감춰짐.
- **해결**: `ShellScaffold` 에서 `extendBody: true` 제거. 내부 BottomNav 가 불투명 배경(`AppColors.surface`) 이라 `extendBody` 효과 자체가 시각적으로 없었고, 이 플래그는 FAB 숨김 버그만 유발.
- **예방**:
  - **중첩 Scaffold 패턴에서 외부 `extendBody: true` 는 신중히** — 내부 FAB/하단 고정 요소가 모두 가려질 수 있음. 반투명 BottomNav 를 원할 때만 켜고, 그 경우엔 내부 Scaffold 의 bottom padding 을 직접 추가해야 함.
  - 증상: FAB 이 렌더 트리에는 있는데(inspector 로 확인 가능) 화면에 안 보이면 중첩 Scaffold + extendBody 의심.
  - go_router 의 `ShellRoute` 는 이 패턴을 자주 만들므로 bottom-overlay 조합일 때 체크리스트화.

---

## 2026-04-24 실기기 + WSL 백엔드 — VPN/LAN 상태 불안정할 때는 `adb reverse` + 스크립트 자동화가 가장 robust
- **상황**: chase_and_run 개발 PC 가 VPN 을 수시로 켜고 끄는 환경. LAN IP 경로는 VPN 켜지면 비대칭 라우팅으로 응답 패킷이 VPN 터널로 빠져 실기기 도달 실패. WSL mirrored + Hyper-V 방화벽 튜닝까지 끝냈는데도 매번 환경 재설정 필요.
- **실수**: `10.0.2.2` (에뮬레이터 전용) / LAN IP / localhost+adb reverse / WSL portproxy 등 여러 경로를 번갈아 시도하며 매번 방화벽·네트워크·`.env` 조정.
- **원인**: 여러 경로가 각자의 "환경 전제" 를 가짐. VPN / 공유기 격리 / 방화벽 업데이트 / WSL 재기동 등으로 한두 개씩 깨지면 원인 파악에 30분+.
- **해결**: **`adb reverse tcp:9090 tcp:9090` + `flutter run` 을 `run.ps1` 로 래핑**. USB 터널 기반이라 Wi-Fi/LAN/VPN/방화벽 상태와 완전 무관. USB 재연결/PC 재부팅만 주의하면 됨. `.env` 는 `127.0.0.1:9090` 고정.
- **예방**:
  - 실기기 + WSL 백엔드 조합 + 네트워크 환경이 유동적이면 **처음부터 `adb reverse` 자동화가 기본값**. LAN IP 경로는 CI/다중 단말 테스트에만.
  - 개발 스크립트는 `{프로젝트 루트}/run.ps1` 처럼 README 에서 바로 찾을 수 있는 위치에 두기. Android Studio/VS Code 의 pre-launch hook 도 동일 역할 가능.
  - 프로젝트 CLAUDE.md 에 "실기기 접근 경로" 를 환경별(에뮬/실기기 USB/실기기 Wi-Fi) 로 명시 — 새 PL 또는 Claude 세션이 경로 탐색으로 시간 낭비하지 않도록.

---

## 2026-04-24 Jackson — `boolean is*` 필드는 JSON 에서 `is` 접두어가 벗겨져 직렬화됨
- **상황**: chase_and_run_admin REQ-TEST-01 에서 `PlayerLoginResponse.isNewUser` (boolean) 를 통합 테스트에서 `jsonPath("$.data.isNewUser")` 로 단언 → 401/필드없음 오류 아닌 match 실패.
- **원인**: Jackson 의 기본 BeanIntrospector 가 `isNewUser` 의 getter `isNewUser()` 를 "new**User**" 프로퍼티로 해석 (JavaBeans boolean 규약). JSON 키는 `newUser`. `getIsNewUser()` 형태면 `isNewUser` 로 유지되지만 Lombok `@Getter` + `boolean isNewUser` 조합은 `isNewUser()` 만 생성.
- **해결 (권장)**: **DTO 에 `@JsonProperty("isNewUser")` 명시해서 JSON 키를 고정**. 의미 전달이 명확한 필드명(`isNewUser` = "새 유저인가?") 을 JSON 에도 그대로 노출. chase_and_run_admin 2026-04-24 에 이 방향으로 확정.
  - import: `com.fasterxml.jackson.annotation.JsonProperty` (Jackson 2), 또는 Jackson 3 환경이어도 annotation 패키지는 동일하게 `com.fasterxml.jackson.annotation.*` 유지.
  - 대안 (비권장): 테스트/문서/FE 모두 `newUser` 로 맞춘 뒤 DTO 필드명도 `newUser` 로 리네이밍. 의미 훼손(`newUser = true` 가 무슨 뜻인지 첫눈에 안 보임) 이 크므로 특별한 이유 없으면 `@JsonProperty` 오버라이드를 선호.
- **예방**:
  - `boolean is*` 필드는 Jackson 직렬화 시 접두어 제거된다는 것을 기본값으로 인지. API 스펙에 `isNewUser` 로 명세하면 `@JsonProperty("isNewUser")` 를 반드시 DTO 에 명시.
  - 프런트/Flutter/테스트가 `isNewUser` 로 접근하려고 하면 런타임 `undefined` 로 조용히 실패 → LESSONS 2026-04-24 "BE/FE 필드명 불일치" 재발 위험. PLAN.md 에서는 `isNewUser` 로 적더라도 실제 응답 키와 일치 여부를 통합 테스트(`jsonPath`) 에서 1회 확인.
  - 동일 패턴: `boolean isActive`/`isDeleted`/`isBot` 도 모두 `active`/`deleted`/`bot` 으로 직렬화됨. 의미가 중요하면 `@JsonProperty` 필수.
  - 이미 배포된 DTO 에 `@JsonProperty` 를 뒤늦게 추가하면 JSON 키가 바뀌므로 소비자(FE/Flutter) 호환 확인 필수 — 변경 시 반드시 `view_requests.md` (또는 FE PR) 로 합의.

---

## 2026-04-24 Spring/Vite — BE 에 CORS 설정 없이 vite proxy 로 덮어두면 `.env` 에 절대 URL 박히는 순간 터짐
- **상황**: chase_and_run_admin admin 에서 `POST /api/v1/admin/auth/login` 호출 시 `OPTIONS 403 Forbidden` 으로 막힘. 이전까지는 정상 동작했는데, REQ-TEST-01 작업 중 FE 에이전트가 `.env.development` 를 신규 생성하면서 `VITE_API_BASE_URL=http://localhost:9090/api/v1` (절대 URL) 을 `.env.example` 에서 그대로 복사. axios baseURL 이 절대 URL 이 되니 브라우저가 cross-origin (localhost:3000 → localhost:9090) preflight 발송 → BE 에 CORS 설정 전무 → Spring Security 가 OPTIONS 를 403 으로 차단.
- **원인**: 프로젝트 초기 세팅 때 `vite.config.js` 의 `server.proxy` 로 `/api` 를 BE 포트로 투명 포워딩 → 로컬 개발에서는 same-origin 으로 보여 CORS 문제가 아예 안 드러남. BE 에 `CorsConfigurationSource` / `http.cors()` 설정이 없어도 "문제 없이 돌아가는 것처럼" 보임. 하지만:
  1. `.env` 에 절대 URL 을 박는 순간 proxy 우회 → CORS 필요 → 터짐
  2. 운영 배포 시 admin/api 가 다른 도메인이면 어차피 터짐
- **해결**: BE 에 CORS 설정 추가 — `CorsConfig` 클래스에 `CorsConfigurationSource` Bean 등록 + `@Value("${app.cors.allowed-origins:}")` 로 origins 를 yml 에서 주입 + `SecurityFilterChain` 에 `http.cors(Customizer.withDefaults())` 추가. `application-local.yml` / `application-dev.yml` 에 `app.cors.allowed-origins` 프로퍼티로 localhost 허용. 운영은 yml 에 별도 세팅.
- **예방**:
  - 프로젝트 초기 Spring 세팅 체크리스트에 **"CORS 설정 Bean + allowed-origins 프로퍼티 분리"** 를 고정 항목으로. vite proxy 는 "로컬 편의" 일 뿐 근본 해결이 아님.
  - `.env.example` 을 만들 때 `VITE_API_BASE_URL` 은 **상대 경로 (`/api/v1`) 를 기본값으로** 두기. 절대 URL 이 필요한 배포 환경만 override.
  - 로컬에서 `OPTIONS 403` 을 보면 원인 후보는 (1) CORS 미설정 (2) `.env` 에 절대 URL 박힘 두 가지. Security 측에서 막는 듯 보여도 실제 원인은 **브라우저 preflight 가 먼저 발동** — same-origin 으로 바꾸거나 CORS 를 열거나.
  - Spring Security 에 `http.cors()` 를 선언하지 않으면 FilterChain 은 `CorsConfigurationSource` Bean 이 있어도 **무시함**. Bean 등록 ≠ 적용. `.cors(Customizer.withDefaults())` 명시 필수.

---

## 2026-04-24 Spring — `@Profile` 로 빈 미등록해도 Security FilterChain 이 먼저 적용되어 404 아닌 401 반환
- **상황**: chase_and_run_admin REQ-TEST-01 dev 프로파일 격리 테스트. `@ActiveProfiles("test")` 에서 `/api/v1/dev/test-token` 호출 시 "빈이 없으니 404 일 것" 으로 `.andExpect(status().isNotFound())` 단언 → 실제는 401 반환으로 테스트 실패.
- **원인**: `@Profile("dev")` 컨트롤러는 빈 자체가 미등록이지만, 일반 SecurityConfig 의 FilterChain 은 운영/로컬/테스트에서 모두 활성. `anyRequest().authenticated()` 가 매처로 먼저 걸려 JWT 없는 요청을 401 로 반환. Spring MVC 의 핸들러 resolution (404 트리거) 은 Security 필터 체인 통과 이후에 수행.
- **해결**: 프로파일 격리 테스트는 `isNotFound()` 대신 `is4xxClientError()` 또는 `isUnauthorized()` 로 완화. 핵심은 "운영에서 dev 엔드포인트가 정상 응답하지 않는다" 이므로 4xx 이면 격리 목적 달성.
- **예방**:
  - 빈 미등록 엔드포인트의 "성공 요청이 처리되지 않음" 을 검증할 때 상태 코드 기대값은 404 가 아닌 **4xx 전반** 으로 잡기. 401/403/404 모두 "dev 빈 없음" 의 유효 신호.
  - 엔드포인트 존재 여부만 체크하려면 `Authentication` 을 인증된 상태로 만든 뒤 호출해야 404 가 나옴. 하지만 dev 격리 테스트는 "인증되지 않은 요청으로도 응답 없음" 자체가 목적이므로 401 허용이 자연스러움.
  - 통합 테스트 assertion 이 "스펙과 구현 호환성" 이 아닌 "Spring 내부 처리 순서" 에 의존하면 취약해지기 쉬움.

---

## 2026-04-24 Mockito — 서비스에 의존성 필드 추가 시 `@InjectMocks` 사용하는 기존 테스트 전체에 `@Mock` 동기화 필수
- **상황**: chase_and_run_admin REQ-TEST-01 에서 `ArrestDetectionService` 에 `PositionStore` 주입 추가 + `FcmPushService` 에 `PlayerRepository` 주입 추가. 새 테스트는 잘 작성했으나 **기존** `ArrestDetectionServiceTest` / `FcmPushServiceTest` 가 `@InjectMocks` 로 서비스를 만들 때 신규 의존성이 null 주입되어 NPE.
- **원인**: Mockito 의 `@InjectMocks` 는 선언된 `@Mock` 필드와 타입 매칭되는 의존성만 주입. 서비스에 새 필드를 추가해도 기존 테스트 파일의 `@Mock` 목록에 해당 타입이 없으면 null 유지. 컴파일은 통과, 테스트 실행 시에만 NPE.
- **해결**: 서비스 리팩토링 직후 해당 서비스를 `@InjectMocks` 로 사용하는 테스트 파일 전체 grep → 누락된 `@Mock` 전부 추가. 봇 가드처럼 `@BeforeEach` 스텁(`given(playerRepository.findById(...)).willReturn(...)`)도 같이 필요한지 확인.
- **예방**:
  - BE 개발팀 체크리스트: **"서비스 생성자 필드 추가 시 `@InjectMocks` grep → 기존 테스트 파일 동기화"** 를 신규 파일 작성 전에 고정 루틴으로. 리팩토링 PR 이 컴파일 통과하는데도 런타임 NPE 를 숨길 수 있음.
  - 가능하면 생성자 주입을 Lombok `@RequiredArgsConstructor` 로 쓰는 경우에도 같은 규칙. IDE 는 Mock 필드 누락을 경고하지 않음.
  - 차선책: `@ExtendWith(MockitoExtension.class)` + `@Mock(lenient = true)` 는 해결이 아니라 은폐. lenient 를 남용하면 다른 NPE 도 같이 숨음.

## 2026-04-24 Flutter — analyzer 경고는 cascade, "No issues found" 까지 반복 순회 필요
- **상황**: chase_and_run 의 analyzer 정리 중 `unnecessary_underscores` 13건을 일괄 제거했더니 별개의 `RadioListTile.groupValue/onChanged deprecation` 2건이 "새로 생긴 것처럼" 보임. 실제로는 숨어 있던 경고가 아니라 두 그룹이 동시 존재하던 것 — 한 번에 다 안 나오고 analyzer 출력의 tail 에만 잡혀 있었음.
- **실수**: "이전 커밋에서 경고를 다 잡았다" 고 판단하고 커밋한 뒤 다음 라운드 시작 시 새 경고를 보고 "회귀" 로 오해할 뻔.
- **원인**: `flutter analyze` 의 출력 잘라보기(`tail -3`) 만으로 상태 추론. 실제로는 출력 길이가 길어 tail 에 일부만 노출. 또한 상위 `error` / `warning` 이 존재할 때 `info` 가 뒤로 밀려 보이지 않기도 함 (우선순위별 정렬).
- **해결**: 경고를 한 그룹씩 (`grep "unnecessary_underscores"` 등) 지목해 전체 건수로 확인 → 일괄 수정 → `flutter analyze` 전체 재실행 → 남은 경고 다시 grep → 반복. 최종 "No issues found" 가 나올 때까지.
- **예방**:
  - 경고 0 을 목표로 할 때 `flutter analyze 2>&1 | grep "rule_name"` 식으로 **rule 별 카운트** 를 먼저 집계한 뒤 수정. tail 출력 만 보지 말 것.
  - 한 라운드의 "정리 완료" 선언 전 반드시 `flutter analyze` **한 번 더** (새 라운드 기준) 실행. cascade 로 드러나는 층이 추가 있으면 같은 세션에 이어서 처리.
  - 각 종류의 경고를 **그룹 단위 커밋** 으로 분리하면 히스토리에서 "왜 이 라운드에 이게 해결됐는지" 트래킹 용이.

---

## 2026-04-24 Flutter — RadioListTile 의 groupValue/onChanged deprecation, RadioGroup 마이그레이션 시 nullable 함수 제약
- **상황**: chase_and_run 의 신고 다이얼로그(VIEW-G-01) 에서 `RadioListTile.groupValue` / `onChanged` 가 Flutter v3.32+ 에서 deprecated. 권장 교체는 `RadioGroup<T>` ancestor 로 상태 관리하고 자식 `RadioListTile` 은 `value` 만 지정하는 패턴.
- **실수**: 기존 코드의 비활성화 패턴 `onChanged: _submitting ? null : (v) => ...` 를 그대로 `RadioGroup.onChanged` 에 옮기려다 **`The argument type 'void Function(T?)?' can't be assigned to the parameter type 'ValueChanged<T?>'`** 컴파일 에러.
- **원인**: `RadioListTile.onChanged` 는 **`ValueChanged<T>?`** (함수 자체가 nullable — null 로 비활성화 가능) 였지만 `RadioGroup.onChanged` 는 **`ValueChanged<T?>`** (함수 non-nullable, 값만 nullable). 전자는 "비활성화 = null 콜백", 후자는 "값 없음 = null 값" 의 시맨틱이 섞여 보이지만 실제론 타입이 다름.
- **해결**: 항상 non-null 함수 제공, 내부에서 조기 리턴으로 비활성화 효과 유지:
  ```dart
  RadioGroup<ReportReason>(
    groupValue: _reason,
    onChanged: (v) {
      if (_submitting) return;   // 기존 null 콜백 의도를 내부 가드로 재현
      setState(() => _reason = v);
    },
    child: ...,
  )
  ```
- **예방**:
  - Flutter deprecated API → 대체 API 로 마이그레이션할 때 **시그니처 nullable 성이 바뀌는지** 반드시 확인. 특히 "비활성화 수단" 이 null 콜백에 의존했다면 대체 API 가 이를 허용하는지 먼저 확인.
  - 대체 API 가 함수 non-nullable 을 요구하면 "내부 가드" 패턴 (`if (disabledFlag) return;`) 으로 전환. 시각적 비활성화 표시(회색 처리 등) 는 별도 state 로 관리 필요.
  - 같은 패턴은 `Radio`, `Checkbox`, `Switch` 등에서도 동일하게 발생 가능 (전체적으로 Material 3 방향은 ancestor-managed group 으로 이동 중).

---

## 2026-04-24 Flutter — docs/ 의 외부 레퍼런스 .dart 파일이 analyzer 오염
- **상황**: chase_and_run 의 `docs/login_screen.dart` 가 다른 프로젝트(flip_admin) 에서 가져온 참고용 샘플 코드. 해당 앱의 `l10n/app_localizations.dart` import + `AppColors` 참조 포함. analyzer 가 이걸 프로젝트 소스로 취급해 `Undefined name 'AppColors'` **error** 발생 — analyzer 출력 전체를 오염.
- **실수**: 경고 정리 라운드 중 이 error 가 갑자기 튀어나와 당황. 실제로는 처음부터 존재했는데 다른 `warning` 들에 가려져 tail 출력에서 밀려있던 것.
- **원인**: Flutter 의 `flutter analyze` 는 기본적으로 **프로젝트 루트 아래의 모든 `.dart` 파일** 을 분석. `lib/` 뿐 아니라 `test/`, `docs/`, 심지어 임의 경로의 샘플 파일까지 포함. `docs/` 는 관례상 문서만 있을 거라 가정했지만 `.dart` 샘플이 섞이면 바로 오염.
- **해결**: `analysis_options.yaml` 에 `analyzer.exclude` 섹션 추가 —
  ```yaml
  analyzer:
    exclude:
      - docs/**
  ```
  이걸로 docs 안의 `.dart` 는 정적 분석 대상에서 완전 제외.
- **예방**:
  - 레퍼런스/샘플 코드를 레포에 둘 때 **확장자를 `.dart.sample` 또는 `.txt`** 로 변경하거나 별도 `samples/` 디렉토리에 두고 `analyzer.exclude` 에 반드시 등록. 그냥 `.dart` 확장자로 두면 "분석 안 되겠지" 기대는 배신됨.
  - 새 프로젝트 셋업 때 `analysis_options.yaml` 의 exclude 에 `docs/**`, `samples/**`, `scripts/**`, `**/*.g.dart` (generated) 등을 선제적으로 넣어두기.
  - analyzer 출력을 확인할 땐 `grep "error\|warning" | wc -l` 로 **에러/경고만 따로 카운트** 해서 가려진 블로커를 놓치지 않기.

## 2026-04-24 adb 자동화 — 복수 디바이스 환경 대비 `-s <serial>` 명시 필수
- **상황**: chase_and_run 의 `run.ps1` 이 `adb reverse tcp:9090 tcp:9090` 을 `-s` 없이 호출. 실기기 + 에뮬레이터가 동시 연결된 상태에서 adb 가 "more than one device/emulator" 로 실패하거나, 우연히 에뮬레이터에 포워딩이 적용되어 실기기 앱의 STOMP 는 여전히 `127.0.0.1:<랜덤포트>` 로 Connection refused.
- **실수**: 개발 스크립트가 "보통 디바이스 1대만 연결한다" 는 전제로 `-s` 생략. 실제 개발에선 에뮬 AVD 를 띄워둔 채로 실기기를 붙이는 경우가 매우 흔함.
- **원인**: `adb` CLI 는 `-s <serial>` 없으면 환경변수 `ANDROID_SERIAL` 또는 단일 디바이스만 찾음. 복수면 오류 또는 불확정 동작. `adb reverse` 는 **대상 디바이스 kernel 의 터널링 테이블** 을 건드리므로 정확히 어느 디바이스에 적용됐는지가 critical.
- **해결**: `adb devices` 파싱 → 실기기(emulator-* 아닌) 우선 선택 → `adb -s <target> reverse tcp:9090 tcp:9090` 명시적 호출. 후속 `flutter run` 도 같은 디바이스에 `-d <target>` 으로 고정.
- **예방**:
  - **adb 를 자동화 스크립트에 넣을 땐 반드시 `-s <serial>` 명시**. 개발자 PC 에 에뮬레이터 하나만 있다고 가정하지 말 것.
  - 디바이스 선택 규칙은 "옵션 파라미터 > 유일 디바이스 > 실기기 우선" 순으로 두는 게 실용적. 복수 실기기면 에러 대신 첫 번째 + 안내 출력.
  - 증상 체크: **STOMP/WS 연결이 무작위 포트로 실패 (`127.0.0.1:<높은 포트>` refused)** → 99% `adb reverse` 가 실제로는 안 걸렸거나 엉뚱한 디바이스에 걸린 것. WebSocket 이 실패해 SockJS fallback 으로 XHR polling 을 시도하며 파생 포트가 보이는 것.
  - `.githooks` 같은 공유 스크립트도 동일하게 `-s` 강제.

---

## 2026-04-24 Tailwind 4 — `@theme` 에 shade 정의 누락 시 클래스는 조용히 무시됨
- **상황**: ShortDduk view 에서 `hover:border-brand-300` 을 `ShortsCard` 에 써뒀지만 실제 hover 시 테두리 색이 안 바뀜. 에러 없이 스타일만 빠진 상태로 수주간 방치.
- **원인**: Tailwind 4 는 `tailwind.config.js` 대신 CSS `@theme` 블록에 `--color-brand-*` 변수를 선언. 정의된 shade(50/100/500/600/700) 만 사용 가능하고, **미정의 shade 의 클래스는 Tailwind 가 무시** (오류/경고 없이 unknown class 로 처리). Tailwind 3 의 `theme.extend.colors` 도 유사하지만 3 에선 config 전체를 훑어 검출하기 쉬웠다.
- **해결**: `index.css` `@theme` 에 사용되는 shade 전부 정의 — indigo 기반 팔레트 10 단계(50/100/200/300/400/500/600/700/800/900) 를 한 번에 깔아두면 향후 shade 추가 사용 시에도 안전.
- **예방**:
  - Tailwind 4 프로젝트는 **브랜드 커스텀 컬러를 10 단계 팔레트 전체로 정의** — shade 일부만 쓰더라도 전체 선언 비용은 작고, 미래의 조용한 실패를 차단.
  - 증상: hover/focus/ring 등 특정 variant 에서만 "스타일이 안 먹는다" 는 감 → 바로 @theme 정의 유무부터 확인. 콘솔에 에러가 안 뜨는 점이 함정.
  - 코드베이스 전수 점검: `grep -rEo "(bg|text|border|ring|fill)-brand-[0-9]+" src` 로 사용 중인 shade 목록 추출 → @theme 정의와 차집합 계산.

---

## 2026-04-24 React 19 `react-hooks/set-state-in-effect` — effect body 에서 setState 동기 호출 금지
- **상황**: ShortDduk view 의 `useShortsProgress` 훅에서 `useEffect` 안에 `setProgress(0); setStep(null); ...` 로 6개 state 를 개별 초기화. Vite/eslint-plugin-react-hooks 최신이 "Cannot call setState synchronously within an effect" 에러를 던져 lint 실패.
- **원인**: React 19+ 에서 추가된 purity/cascade 규칙. effect body 안에서 setState 를 동기 호출하면 렌더 직후 바로 재렌더가 연쇄되어 퍼포먼스/추론이 깨진다는 전제. 이벤트 핸들러·비동기 콜백 내부의 setState 는 여전히 OK.
- **해결**:
  1. **state 를 하나의 객체로 묶기** — `const [state, setState] = useState(INITIAL)` → effect 에선 `setState(INITIAL)` 단일 호출. 그리고 단일 호출부에 정당화 주석과 함께 `// eslint-disable-next-line react-hooks/set-state-in-effect` 붙임. 여러 useState 로 쪼개져 있던 것보다 렌더 측 접근도 더 깔끔.
  2. 정당화 주석의 기준: **외부 구독(subscription) 교체 시 snapshot 리셋** 처럼 "effect 의 외부 입력이 바뀌어 초기화가 필수" 인 경우만. 그 외엔 derived state 재설계가 정답.
- **예방**:
  - React 19 마이그레이션/신규 프로젝트에선 이 규칙을 기본값으로 감안하고 상태 설계. "effect 의 deps 가 바뀌면 내부 state 도 리셋되어야 한다" 싶으면 **처음부터 state 를 객체 하나로**.
  - 더 근본적인 대안: 상위 컴포넌트에서 `key={subscriptionId}` 로 컴포넌트를 언마운트-재마운트시켜 자연스러운 리셋. 다만 훅 내부에선 key 를 쓸 수 없으니 객체 state 로.
  - 이벤트 핸들러(`es.addEventListener("progress", () => setState(...))`) 내부 setState 는 규칙 대상 아님 — setState 를 "effect body" 에서만 조심.

---

## 2026-04-24 StrictMode 2회 실행 — 결제/OAuth 콜백 mutation 은 `useRef` 1회 guard
- **상황**: ShortDduk view 의 `PaymentSuccessPage` / `BillingSuccessPage` / `YoutubeCallbackPage` 가 query param 을 읽어 백엔드 `/confirm` / `/subscribe` / `/callback` 을 호출. `useEffect` 로 시작했더니 React 19 dev StrictMode 에서 effect 가 mount → unmount → re-mount 순으로 2번 실행되어 동일 orderId/authKey 가 서버에 두 번 도달 → 두 번째 호출이 "이미 처리됨" 오류 후보.
- **원인**: StrictMode 의 intentional re-mount (development 에서만). 일반 GET 쿼리는 idempotent 라 무해하지만 **"한 번만 실행되어야 하는" side-effect mutation** (결제 승인, OAuth code 교환, 토큰 발급 등) 은 두 번째 호출이 명백한 버그.
- **해결**: `const triggered = useRef(false)` 를 선언하고 effect 첫 줄에서 `if (triggered.current) return; triggered.current = true;` 로 1회 가드. `useRef` 는 StrictMode double invocation 동안 동일 인스턴스로 보존되어 2번째 실행을 차단. 실제 언마운트 후 다른 경로로 재진입하면 ref 가 다시 초기화되어 정상 동작.
- **예방**:
  - **URL 리다이렉트 콜백 페이지** (successUrl/callback/webhook 수신 등) 의 useEffect mutation 에는 `useRef` guard 를 기본값으로. 이 패턴을 한 번 경험하면 이후 모든 콜백 페이지에 동일하게 적용.
  - 서버 측이 idempotent 하게 설계되어 있으면 (예: payment.confirm 이 이미 SUCCESS 면 무시) 1회 중복은 무해하지만, 여전히 UX 상 에러 메시지가 잠깐 뜰 수 있어 방어 권장.
  - `useMutation` 의 `isPending` 체크로도 막을 수 있지만 StrictMode 의 unmount 가 isPending=false 로 돌려놓으므로 불충분. `useRef` 가 단순하고 확실.

---

## 2026-04-24 Vitest + MSW — `.env.test` 로 `VITE_API_BASE_URL` 고정해야 핸들러 매칭
- **상황**: ShortDduk view 에 Vitest + MSW 세팅. `src/test/msw/handlers.js` 에 `http.post("http://localhost:8081/api/v1/auth/login", ...)` 로 등록. 첫 테스트 실행 시 MSW 가 "unhandled request" 로 실패 — axios 요청의 URL 이 상대경로이거나 빈 baseURL 이라 핸들러와 매칭 안 됨.
- **원인**: Vite 는 `mode: 'test'` 일 때 `.env.test` 를 로드. 없으면 `VITE_API_BASE_URL` 가 `undefined` 로 평가. axios 의 `baseURL: undefined` 는 상대경로 요청을 만들고, jsdom 환경에선 현재 URL(`http://localhost:3000`) 을 기준으로 절대화되어 handler URL 과 불일치.
- **해결**: `view/.env.test` 를 생성하고 `VITE_API_BASE_URL=http://localhost:8081/api/v1` (+ 필요하면 `VITE_TOSS_CLIENT_KEY` 등) 을 핸들러 URL 과 **정확히 동일** 하게 지정.
- **예방**:
  - Vitest + Vite 프로젝트 세팅 시 **`.env.test` 를 기본 artefact 로 포함**. `.env.development` 복사 + 테스트용 값으로 덮어쓰기.
  - MSW 핸들러 URL 과 `.env.test` 의 `VITE_API_BASE_URL` 은 **리터럴로 동일** 해야 함. path pattern (예: `*/auth/login`) 을 쓰면 일치 문제는 회피되지만 host 검증이 사라져 유틸성↓.
  - `setupFiles` 의 `server.listen({ onUnhandledRequest: "error" })` 를 걸어 두면 매칭 실패가 즉시 에러로 드러나 디버깅 쉬움 — `"bypass"` 는 무음이라 비추.

---

## 2026-04-24 공통 Input 컴포넌트 — label htmlFor 와 input id 양쪽 모두 필요
- **상황**: ShortDduk view 의 `Input` 컴포넌트가 `<label htmlFor={rest.id || rest.name}>` 으로 시작만 적용하고 `<input ... {...rest}>` 는 id 를 props 로 전달받지 않으면 비워둠. react-hook-form 의 `register("email")` 은 `name` 만 넣고 `id` 는 안 주므로 input 에 id 가 없는 상태. 결과: label for 가 가리키는 input 이 없음 → `getByLabelText("이메일")` 매칭 실패 + 스크린리더가 label 과 input 을 연결하지 못함.
- **원인**: 컴포넌트 작성 시 "id 는 호출자가 넣겠지" 가정했지만 react-hook-form 의 `register()` 는 `name/onChange/onBlur/ref` 만 반환. 자동 id 생성이 없음.
- **해결**: Input 내부에서 `const inputId = rest.id || rest.name;` 을 만들고 `<label htmlFor={inputId}>` + `<input id={inputId} {...rest} />` 양쪽에 같은 값 주입.
- **예방**:
  - 공통 폼 컴포넌트 작성 시 **label ↔ input 연결은 컴포넌트 내부 책임** 으로 가져가기. 호출자가 id 를 챙기게 하면 거의 항상 빠짐.
  - 접근성 회귀를 잡는 가장 싼 방법은 **Testing Library 의 `getByLabelText` 로 테스트** 를 작성하는 것. 이 쿼리가 실패하면 label-input 연결이 깨진 신호.
  - 자동 id 를 고유하게 만들려면 `useId()` (React 18+) 를 컴포넌트 내부에서 호출. 다만 react-hook-form 의 `name` 을 id 로 재사용하는 것도 충분 (한 form 안에 같은 name 중복은 이미 폼 충돌).

---

## 2026-04-25 토스/Stripe 류 결제 게이트웨이 — HTTP Basic 의 password 가 빈 콜론
- **상황**: ShortDduk `TossHttpClient` 작성 중. confirm/billing 호출에 `Authorization: Bearer secretKey` 로 짰다가 401. 처음엔 secret-key 자체 의심.
- **원인**: 토스 (Stripe 동일 패턴) 는 HTTP Basic 인증을 사용하되 **username = secretKey, password = 빈 문자열**. base64 인코딩 시 secretKey 뒤에 콜론을 반드시 붙여야 함. 콜론이 없으면 RFC 7617 위반으로 401. 헷갈리는 이유는 "API 키" 라는 단어 때문에 Bearer 또는 단순 base64(key) 로 넘기기 쉽기 때문.
  - 올바름: `Basic ${base64("test_sk_xxx:")}`  → 디코드하면 `test_sk_xxx:`
  - 틀림 ①: `Basic ${base64("test_sk_xxx")}`  → 디코드하면 `test_sk_xxx` (콜론 누락 → 401)
  - 틀림 ②: `Bearer test_sk_xxx`  → 토스는 Bearer 미지원
- **해결**: `(secretKey + ":").getBytes(UTF-8)` 인코딩. 한 번 wire 로 캡처해 base64 디코드해보면 trailing `:` 유무가 즉시 보임.
- **예방**:
  - 결제 게이트웨이 (토스/Stripe/Paystack/Razorpay/Adyen 등) 는 거의 다 **HTTP Basic + password=빈** 패턴. 새 게이트웨이 통합 시 401 이면 콜론 누락부터 의심.
  - 통합 테스트 한 건은 이걸 목적으로: `assertThat(req.getHeader("Authorization")).isEqualTo("Basic " + base64(secretKey + ":"))`. 직접 비교가 가장 확실하고 회귀 잡기 쉬움.
  - YouTube/FCM 처럼 OAuth2 access_token 을 쓰는 곳은 `Bearer {token}` — 게이트웨이별로 인증 방식이 섞여 있으니 한 프로젝트에서 두 패턴이 공존할 수 있음에 주의.

---

## 2026-04-26 Spring properties 의 `${ENV:default}` 에 시크릿 디폴트 두지 말 것
- **상황**: ShortDduk `application.properties` 작업 중 `openai.api-key=${OPENAI_API_KEY:sk-proj-SjYO...}` 형태로 **실제 OpenAI 시크릿 키가 디폴트값으로** 박혀 있는 것을 발견. 다행히 `git log -p -S 'sk-proj-SjY' -- application.properties` 로 git history 에는 노출 안 된 working tree 변경분이었지만, 그대로 커밋됐다면 GitHub 푸시 즉시 키 회전 + Anthropic/OpenAI 결제 사고 가능성. 원인은 "로컬 개발 편의" 로 디폴트 박은 뒤 잊어버림.
- **원인**:
  - `${VAR:default}` 문법은 "env 누락 시 fallback" 으로 의도되지만, 실제 운영 키를 fallback 으로 넣어 두면 **로컬 부팅 시 자동으로 운영 키가 사용됨** → 로컬 테스트가 운영 API 를 때림.
  - properties 파일은 IDE 의 plain text 검색에 노출되고, 실수로 stash/branch 이동 시 다른 머신으로 흘러갈 수 있고, secret scanner 가 못 잡는 케이스 (gitignored 파일에서 일반 추적 파일로 옮겨질 때) 가 발생.
  - "어차피 로컬 디폴트야" 라는 self-talk 가 가장 위험. local 디폴트는 **mock=true** 또는 **빈 문자열** 이어야 한다.
- **해결**:
  - 시크릿 디폴트 즉시 제거 → `${OPENAI_API_KEY:}` 빈 디폴트 또는 `${OPENAI_API_KEY}` (없으면 fail-fast).
  - 동시에 `*.mock=true` 디폴트와 묶어서, "키 없으면 mock 으로 동작" 으로 안전망 구성. 운영 profile 만 `mock=false` + 시크릿 env 필수.
  - 노출 가능성 있는 키는 **즉시 회전**. git history 에 없어도 working tree 에 있었으면 동료/AI agent context/터미널 스크롤백 어디든 남았을 수 있다고 가정.
- **예방**:
  - `application.properties` (공통) 에는 시크릿 자체를 두지 않는다. 공통 = 비-시크릿 (ports, JPA basics, model 이름, base-url 등) 만.
  - 프로파일 분리: `application-local.properties` (mock=true, weak default OK) + `application-prod.properties` (no defaults, fail-fast).
  - **CI 단계에서 secret scanner**: `gitleaks` 또는 GitHub `push protection` 활성화. 로컬에선 pre-commit hook 으로 `^[a-z._-]+\.api-key=[^$\s]` (시크릿이 `${...}` 가 아닌 리터럴인 경우) 패턴 차단.
  - properties 파일에서 시크릿을 발견하면 PR 차단 → 회전 → 재PR. "이번만 봐주자" 절대 금지.
  - **신규 프로젝트 시작 시 `.env.example` 와 properties 파일에 *모든 시크릿 자리는 빈 디폴트* 로 미리 깔아두기**. 나중에 채우려고 자리 비우면 임시값을 박는 유혹 발생.

---

## 2026-04-26 Mockito 단위 테스트 — stubEmptyScoreMap/stubBuildStats 헬퍼가 evaluateEnd 공유 stub 을 덮어써 기존 테스트 깨짐
- **상황**: chase_and_run_admin REQ-D-03.10 에서 `GameEndService.end()` 에 새 의존성(buildStats/buildScoreMap)을 추가. 테스트 헬퍼 `stubBuildStats` 에서 `questRepository.findByGameId` stub 을 emptyList 로 덮어쓰자, `evaluateEnd → allQuestsCleared` 에서 동일 메서드를 호출하는 기존 테스트 `thiefWinWhenAllQuestsCleared` 가 winnerTeam=null 로 실패. 또한 `stubEmptyScoreMap` 에서 `findByRoomIdAndLeftAtIsNullOrderByJoinedAtAsc` stub 을 emptyList 로 덮어쓰자 `copWinWhenAllThievesInactive` 가 IN_PROGRESS 로 실패.
- **원인**: 같은 메서드 (`questRepository.findByGameId`, `roomPlayerRepository.findByRoomIdAndLeftAtIsNullOrderByJoinedAtAsc`) 가 ① 기존 평가 흐름 (evaluateEnd 내부) ② 신규 단계 (end() 내부 buildStats/sendMyResults) 에서 모두 호출됨. 헬퍼 stub 이 나중에 선언되면 Mockito 가 마지막 given 을 우선하여 기존 stub 을 덮음.
- **해결**:
  - `stubBuildStats` 에서 `questRepository.findByGameId` stub 제거 — 이 메서드는 `allQuestsCleared` 에서도 쓰이므로 각 테스트에서 직접 제어.
  - `stubEmptyScoreMap` 에서 `findByRoomIdAndLeftAtIsNullOrderByJoinedAtAsc` stub 제거 — 동일 이유. Mockito 기본값(emptyList) 에 의존하거나 테스트에서 명시적 stub.
- **예방**:
  - 테스트 헬퍼 메서드에서 stub 을 설정할 때 **해당 메서드가 다른 실행 경로에서도 사용되는지** 먼저 확인. 공유되는 메서드를 헬퍼에서 덮으면 순서에 따라 기존 테스트가 깨짐.
  - `evaluateEnd` (검사 경로) + `end()` (실행 경로) 처럼 같은 서비스에서 두 단계가 같은 repository 메서드를 호출하면, 헬퍼를 분리(`stubForEvaluation` vs `stubForEndExecution`) 하거나 lenient() 스텁으로 선언 순서 무관하게 처리.
  - 테스트 실패 원인이 "서비스 로직 버그" 인지 "Mock stub 덮어쓰기" 인지 구분하려면 `given(...).willReturn(...)` 호출 순서와 해당 메서드가 어느 경로에서 몇 번 호출되는지 트레이싱.

## 2026-04-26 Jackson — 테스트 전용 ObjectMapper 에 WRITE_DATES_AS_TIMESTAMPS=false 설정 필수
- **상황**: chase_and_run_admin REQ-D-03.10 `LobbyEventSerializationTest` 에서 `startedAt` (LocalDateTime) 직렬화 결과가 `"[2026, 4, 26, 12, 0]"` (배열) 로 나와 `startsWith("2026-04-")` 단언 실패.
- **원인**: 테스트 내 수동 생성한 `ObjectMapper` 에 `JavaTimeModule` 만 등록하고 `WRITE_DATES_AS_TIMESTAMPS=false` 를 별도 설정하지 않으면 기본값 true 가 적용되어 LocalDateTime 이 타임스탬프 배열로 직렬화됨. Spring Boot 자동 설정 (`spring.jackson.serialization.write-dates-as-timestamps=false`) 은 `ObjectMapper` Bean 에만 적용되고 테스트 수동 생성 인스턴스에는 미적용.
- **해결**: 테스트 ObjectMapper 에 `.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` 추가.
- **예방**:
  - 테스트 내 `new ObjectMapper().registerModule(new JavaTimeModule())` 패턴은 반드시 `.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` 를 함께 체이닝. ISO-8601 문자열을 기대하는 단언이 있으면 더욱 중요.
  - 더 간단한 대안: `Jackson2ObjectMapperBuilder.json().modules(new JavaTimeModule()).build()` — Spring 기본 설정을 자동 반영.
  - 직렬화 테스트 작성 시 ObjectMapper 인스턴스 생성 방식을 항상 명시 (Spring Bean vs 수동 생성).

## [2026-04-26] Figma SVG export 가 raster 일 때
- **상황**: ShortDduk 파비콘 교체 — 사용자가 Figma 에서 export 한 favicon.svg 가 347KB.
- **실수**: SVG export 는 vector 일 거라고 가정하고 그대로 사용하려 했음. 실제로는 SVG 컨테이너 안에 base64 PNG 가 박힌 raster-embedded SVG 였음 (`<rect ... fill="url(#pattern0_xxx)"/>` + `<image xlink:href="data:image/png;base64,...">`).
- **원인**: 원본 디자인이 raster (PNG/JPG) image 였거나, Figma 에서 fill 이 "Image fill" 로 적용된 레이어. SVG export 는 raster fill 을 그대로 base64 로 embed.
- **해결**: 사용자에게 다음 안내:
  1. Figma 에서 Fill 이 Solid/Gradient 인지 확인 (Image 면 vector 가 아님)
  2. 텍스트는 Outline Stroke (Cmd+Shift+O) 로 path 변환
  3. 진짜 vector 원본 없으면 vectorizer.ai 등으로 PNG → SVG 변환
  4. 차선책: SVG 포기, PNG fallback 만 사용 (16/32/180/192/512)
- **예방**:
  - SVG export 받을 때 파일 크기로 1차 판별 — 단순 로고가 100KB 이상이면 raster embed 의심
  - `grep -c "data:image" file.svg` 로 base64 raster 개수 확인
  - 첫 줄에 `<rect ... fill="url(#pattern...)"/>` 같은 패턴 fill 이 있으면 raster embed 신호
  - 모던 브라우저는 32×32 PNG 도 retina 에서 깔끔하게 렌더링하므로 SVG 가 필수는 아님

## 2026-04-26 Spring Boot 4 / Spring 7 — 다중 생성자 클래스의 자동 선택 안 됨
- 상황: ShortDduk `FcmHttpClient` 가 production 생성자(@Value 주입 4-arg) + test-only 생성자(직접 주입 5-arg) 2개를 가진 채 `@Component` 등록. 부팅 시 `Failed to instantiate ... No default constructor found`로 실패
- 실수: Spring Boot 3 / Spring 6 까지 통하던 "여러 생성자 중 public 한 개만 의미 있으면 자동 선택" 동작을 Spring Boot 4 / Spring 7 부터는 **더 이상 적용 안 함**. 결과적으로 Spring 이 no-arg 기본 생성자로 fallback 하다 NoSuchMethodException
- 원인: Spring 7 의 `determineCandidateConstructors` 가 다중 생성자에서 "단 하나의 후보" 를 자동 추론하지 않음. `@Autowired` 가 명시돼야 함
- 해결: production 생성자에 `@Autowired` 명시. 단일 생성자 클래스는 변경 불필요
- 예방:
  - 인프라/서비스 클래스에 **테스트 편의용 패키지-프라이빗 생성자를 추가할 때는 production 생성자에 반드시 `@Autowired` 부착** — Spring 8 까지 안전
  - 또는 테스트 편의용 생성자를 만들지 않고 `@MockBean` / 리플렉션 / `setHttp()` 같은 setter 로 대체
  - 새 프로젝트라면 처음부터 `@Autowired` 를 일관되게 붙이는 컨벤션 권장 (단일 생성자라도 명시는 무해)
- 식별: `No default constructor found` + `<init>()` 가 NoSuchMethodException 메시지에 보이면 이 패턴 의심

## 2026-04-26 시크릿 파일을 src/main/resources/ 에 두면 JAR 빌드 시 시크릿 패키징됨
- 상황: ShortDduk Firebase 통합 — 사용자가 `service-account.json` (FCM 자격증명) 을 `api/src/main/resources/service-account.json` 에 둠
- 실수: src/main/resources/ 는 classpath 라 `gradle build` 산출물 JAR 에 그대로 패키징됨 → Render 등 배포 환경에 올라가는 JAR 이 시크릿 컨테이너가 됨
- 원인: classpath = build 산출물에 복사된다는 사실 간과. .gitignore 만 있으면 안전하다고 오해
- 해결:
  1. `api/secrets/.gitkeep` 디렉토리 생성 (빈 폴더지만 협업자 일관성 위해 .gitkeep 만 추적)
  2. `.gitignore` 에 `api/secrets/*` + `!api/secrets/.gitkeep` + `**/service-account*.json` + `**/firebase-adminsdk*.json` + `**/google-credentials*.json` 추가
  3. 시크릿 파일 이동 후 `*.credentials-path` 같은 properties 가 절대경로로 그 파일을 가리키도록
  4. Render/Docker 배포 시는 secret file 마운트 (예: Render Secret Files) 또는 env 에 JSON 통째 넣고 부팅 시 풀어내는 방식
- 예방:
  - 외부 서비스 자격증명 JSON / .pem / 키파일은 **반드시 src/main/resources/ 밖**에 둘 것
  - 새 프로젝트 초기 설정 시 `secrets/` 폴더와 `.gitignore` 패턴을 선제 등록
  - `**/service-account*.json` 류 글로브 패턴은 협업자 실수도 흡수

---

## 2026-04-26 Git 분리 커밋 — chore/feat 가 같은 doc 파일에 섞일 때 임시 제거 → 복원 패턴

- **상황**: chase_and_run_admin 에서 Flyway 잔재 제거 (chore) 와 REQ-C-09 방장 강퇴 기능 (feat) 을 분리 커밋해야 했음. CHANGELOG.md / DECISIONS.md / PLAN.md / TODO.md 4개 doc 파일에 두 작업의 변경이 섞여 있는 상태 (chore 는 V*.sql 언급 정리, feat 는 REQ-C-09 섹션 신규 추가).
- **시도 1 (실패)**: `git add <file>` 단위 분리는 한 파일 안의 hunk 분리가 안 되므로 부적합. `git add -p` 는 인터랙티브라 비대화형 환경에서 사용 불가.
- **해결 (성공)**: 다음 5단계 워크플로우.
  1. doc 파일에서 commit-2 전용 섹션 (REQ-C-09 신규 블록들) 의 시작/끝 위치를 `Grep -n` 으로 식별
  2. `Edit` 으로 commit-2 섹션을 임시 제거 (또는 `head -N file > tmp && mv tmp file` 로 truncate). doc 파일은 commit-1 (chore) 만의 변경이 남는 상태가 됨
  3. `git add <chore 대상 파일들> + <삭제 파일들>` → `git commit -m "chore: ..."`
  4. 임시 제거했던 commit-2 섹션을 다시 `Edit` 또는 cat 으로 복원
  5. 신규 코드 파일들 + 복원된 doc 파일들 staging → `git commit -m "feat: ..."`
- **예방**:
  - 비대화형 분리 커밋은 "변경 백업 → 한 쪽 제거 → 커밋 → 복원 → 커밋" 패턴이 가장 robust. `git add -p` 의존 X.
  - 큰 신규 섹션 (수백 줄) 은 conversation 컨텍스트에 그대로 보존돼 있으면 Read 한 번으로 백업 충분. 별도 임시 파일 불필요.
  - 5단계 사이에 `git status --short` 로 staging 상태 검증 — 빠진 파일이 있는지 / 의도치 않은 파일이 들어갔는지.
  - 마지막 단계 직전 `./gradlew test` (또는 lint/build) 로 doc 복원 후 빌드 무결성 재확인.

---

## 2026-04-26 BE 에이전트 위임 — 명시적 금지 규칙도 prompt 에 반복 강조 필요 (Flyway/V*.sql 케이스)

- **상황**: chase_and_run_admin 은 `feedback_no_flyway.md` 메모리로 Flyway 미사용 + `db/migration/V*.sql` 작성 금지가 명시. 그럼에도 BE 에이전트(be-developer) 에게 REQ-C-09 위임 시 V5 SQL 파일을 새로 생성. PL이 두 번이나 같은 지적 반복 ("flyway 안쓴다고 몇번을 이야기하니").
- **원인**: ① auto memory 는 main agent 컨텍스트에만 자동 주입되고 sub-agent 호출 시에는 prompt 로 명시적으로 전달해야 적용됨 ② sub-agent 의 학습 데이터 default (Spring + JPA → Flyway 마이그레이션) 가 강력해 단발성 지시 한 줄로는 무시됨
- **해결**: ① V5 SQL 파일 + tracked V2~V4 SQL 일괄 삭제 ② DECISIONS.md 에 "Flyway 미사용 — JPA ddl-auto:update 단일 정책 (전사)" 결정 명문화 ③ 메모리 prompt 강화 ("Flyway 미사용. V*.sql 만들지 말 것. JPA 어노테이션만 추가")
- **예방**:
  - **에이전트 위임 prompt 에는 프로젝트별 강한 금지 규칙을 직접 inline 으로 반복**. 메모리/CLAUDE.md 에 있다고 sub-agent 가 자동으로 알 거라 가정 X.
  - sub-agent 산출물 검수 시 **체크리스트에 "프로젝트별 금지 디렉토리/파일 신규 생성 여부" 항목 추가**: chase_and_run_admin 은 `db/migration/` 디렉토리에 신규 파일이 생기면 즉시 fail.
  - 검수팀 (reviewer) 호출 시 "Flyway 미사용 규칙 위반 여부 확인" 같은 프로젝트-specific 체크 명시.
  - 같은 지적이 2회 이상 반복되면 — 에이전트 prompt 템플릿 자체를 갱신 (1회성 메모리 저장 X). 이번 사례는 `feedback_no_flyway.md` 보강 + 향후 BE 위임 시 inline 반복 의무화.

## 2026-04-26 Spring @Transactional self-invocation — 같은 클래스 메서드 호출은 트랜잭션 무시
- 상황: ShortDduk `ShortsPipelineService.run()` 이 같은 클래스의 `@Transactional` step 메서드들 (`step1GenerateScript`, `step2Tts` 등) 을 `this.step1GenerateScript(...)` 식으로 호출. mock 모드에서 파이프라인이 끝까지 실행됐는데도 DB 의 `shorts.status` 가 PROCESSING 그대로 + `progress=0`. 단 `notificationService.notify()` 같은 외부 빈 호출은 정상 동작 → "DB 는 PROCESSING 인데 알림은 완료" 모순 증상
- 실수: `@Transactional` 이 Spring AOP 프록시 기반이라 같은 클래스 안에서 호출하면 프록시를 거치지 않아 트랜잭션이 안 걸림. 결과로 dirty checking 변경(`entity.updateXxx()`)이 commit 안 됨. setter 호출은 됐으나 flush 안 되어 **DB 변경이 사라짐**
- 원인: Spring AOP 의 동작 — 외부에서 빈을 호출할 때만 프록시가 가로채 트랜잭션 적용. self-invocation 은 프록시 우회
- 식별: 외부 빈 호출 (`creditService.use()`) 의 변경은 DB 에 반영되는데, 같은 클래스 내 메서드의 변경 (`shorts.complete()`) 은 안 반영되면 self-invocation 의심. show-sql 로 SELECT 만 보이고 UPDATE 가 안 보이는 게 결정적 증거
- 해결 (3가지 패턴):
  - **A. self-injection (코드 변경 최소, 추천)**:
    ```java
    private ShortsPipelineService self;
    @Autowired
    public void setSelf(@Lazy ShortsPipelineService self) { this.self = self; }
    // run() 에서 self.step1(...) 으로 호출
    ```
    @Lazy 로 순환 의존 회피. setter 주입이라 final 필드 일관성은 깨지지만 다른 옵션보다 깔끔
  - B. step 메서드들을 별도 ServiceClass 로 분리 (가장 정석. 변경 큼)
  - C. `AopContext.currentProxy()` + `@EnableAspectJAutoProxy(exposeProxy=true)` (덜 직관적)
- 예방:
  - `@Transactional` 메서드를 같은 클래스 다른 메서드에서 호출하는 패턴이 보이면 즉시 self-injection 또는 분리
  - 또는 메서드 시작점에 `Assert.state(TransactionSynchronizationManager.isActualTransactionActive(), "tx required")` 같은 가드로 빠르게 감지
  - 단위 테스트에서 `pipeline.setSelf(pipeline)` 로 자기 자신 주입 → mock 환경에선 트랜잭션 없어도 동작 동일

## 2026-04-26 UnexpectedRollbackException — rollback-only 마킹은 outer 까지 전파
- 상황: ShortDduk `ShortsPipelineService.step1GenerateScript()` 가 `@Transactional` 이고, 안에서 `creditService.use()` (역시 `@Transactional` PROPAGATION_REQUIRED) 호출. 크레딧 부족 시 `BusinessException(CREDIT_001)` throw → step1 의 catch 가 잡아 fallback 처리. 그러나 step1 이 끝날 때 `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`
- 실수: PROPAGATION_REQUIRED 는 outer 트랜잭션이 있으면 join. inner method 에서 RuntimeException 이 throw 되면 Spring 이 **트랜잭션 매니저에 setRollbackOnly() 마킹**. 같은 트랜잭션이라 outer (step1) 의 commit 시점에 거부됨. catch 로 잡아도 마킹은 안 풀림
- 원인: 트랜잭션은 동기화 매니저 1개에 묶여 있음. 한 번 rollback-only 가 되면 commit 불가. catch 는 **자바 예외 흐름** 만 제어할 뿐 트랜잭션 상태는 못 풀어줌
- 식별: catch 로 잡았는데도 `UnexpectedRollbackException` + `marked as rollback-only` 메시지가 보이면 이 패턴
- 해결:
  - **A. inner 메서드를 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 로 격리 (의도가 "이 차감은 즉시 commit, 실패는 fallback" 이면 정확한 매칭)**:
    inner 트랜잭션이 outer 와 분리 → fail 시 inner 만 rollback, outer 영향 없음
    호출처가 여러 곳이면 영향 검토 필수 (예: PaymentService 같은 atomic 결제 흐름)
  - B. ShortsPipelineService 안에 wrapper 메서드 (`@Transactional(propagation = REQUIRES_NEW)`) 만들어 inner 호출 → self.wrapper() 로 호출. inner 자체 어노테이션 안 건드리고 격리. 호출처별 영향 분리 가능
  - C. `noRollbackFor = BusinessException.class` 추가 — outer 의 자동 rollback 만 막음. inner 의 setRollbackOnly 는 못 막아 효과 제한적
- 예방:
  - "fallback 처리 의도" 의 try-catch 가 있으면, catch 대상이 `@Transactional` 메서드에서 던진 예외인지 확인. 그렇다면 REQUIRES_NEW 격리 필요
  - 트랜잭션 격리 의도를 명시적으로 — 어노테이션 하나로 행동 분기되는 함정이라 코드 리뷰에서 자주 놓침
  - 자가검증: `try { businessOp(); } catch (...) { fallback(); }` 형태가 Spring `@Transactional` 안에 있으면 거의 항상 위험 신호

---

## 2026-04-26 Checkstyle — `MethodName` 기본 패턴이 테스트 `메서드명_상황_기대결과` 언더스코어를 차단
- **상황**: chase_and_run_admin REQ-C-08 검수 FAIL-1 수정 중 `checkstyleTest` 실행하니 기존 테스트 파일들에서 `Name '...' must match pattern '^[a-z][a-zA-Z0-9]*$'` 오류 대량 발생 (수십 건). 이번 작업과 무관한 pre-existing 위반.
- **원인**: `checkstyle.xml` 의 `MethodName` 모듈이 기본 패턴 (`^[a-z][a-zA-Z0-9]*$`) 그대로 적용됨. 프로젝트 테스트 네이밍 컨벤션인 `methodName_situation_expectedResult` 언더스코어 패턴이 위반 대상.
- **해결**: `checkstyle.xml` 의 `MethodName` 에 `format` 프로퍼티로 패턴 변경 (`^[a-z][a-zA-Z0-9_]*$`) — 언더스코어 허용.
- **예방**:
  - 프로젝트 신규 셋업 시 **`checkstyle.xml` 의 `MethodName` 패턴을 첫 테스트 파일 작성 전에 확인/조정**. 기본값 `^[a-z][a-zA-Z0-9]*$` 는 테스트 언더스코어 네이밍과 충돌.
  - `checkstyleTest` 를 CI 에 넣기 전 기존 테스트 파일 전체 통과 여부를 먼저 검증해야 pre-existing 위반을 배치로 떠안지 않음.
  - 패턴 `^[a-z][a-zA-Z0-9_]*$` 로 변경 시 프로덕션 코드 메서드명도 언더스코어를 허용하게 되므로, 테스트 전용 패턴이 필요하면 Checkstyle `SuppressionFilter` 또는 `SuppressWarnings` annotation 을 대안으로 검토.

## 2026-04-26 STOMP CONNECT 는 HTTP 인터셉터 사각지대 — 만료 JWT 들고 가면 무한 막다른 길
- **상황**: chase_and_run Flutter — STOMP 배너 "연결 끊김 · 탭하여 재시도" 를 사용자가 탭했더니 백엔드 로그에 `STOMP CONNECT 거부: 유효하지 않은 Player JWT`. 매번 재로그인해야 풀림.
- **원인**: AuthInterceptor 의 401 → refresh → retry 흐름은 **HTTP 만** 가린다. STOMP CONNECT 는 별도 WebSocket 핸드셰이크라 인터셉터를 거치지 않음. 토큰 저장소의 access token 이 만료된 상태에서 STOMP 만 시도하면 영원히 ERROR 프레임만 받음.
- **해결**: 클라이언트 측 JWT exp 클레임 사전 검사 (`isJwtExpired(token, skew: 30s)`) → 만료면 `_refreshDio` (pristine, 인터셉터 우회) 로 `/auth/refresh` 직접 호출 → 새 토큰 저장 후 STOMP CONNECT.
- **분류 로직 재사용**: AuthInterceptor 의 `isTransientRefreshFailure` 그대로 import — transient(5xx/네트워크)면 토큰 유지, permanent(4xx)면 `signOut()` 트리거 → router 가 /login 으로 redirect.
- **예방**: 토큰 의존하는 별도 채널 (WebSocket / Server-Sent Events / gRPC stream 등) 을 추가할 때마다 "이 채널은 HTTP 인터셉터의 refresh 우산 밖이다" 를 의식하고 사전 freshness 체크 + signOut 분기를 함께 설계할 것. JWT 디코딩은 base64url 패딩 보정 (`mod 4` 안 맞으면 `=` 추가) 필수 — Dart `base64Url.decode` 는 패딩 자동 보정 안 함.
- **테스트**: `isJwtExpired` 는 순수 함수라 12 케이스 단위 테스트로 커버 (null/빈/형식불량/exp누락/skew 0 비교/string exp/패딩 보정). STOMP 통합 경로는 매뉴얼 스모크 (만료 토큰으로 앱 재시작 → 배너 → 자동 refresh + connect).

## 2026-04-26 @SpringBootTest + @MockBean 혼합 통합 테스트 — DB 직접 확인 방식으로 기선택 역할 유지 검증
- **상황**: chase_and_run_admin REQ-C-08 I-06 — "기선택 역할 게임 시작 후 유지" 를 `@MockBean RoomService` 가 있는 `RoomRoleIntegrationTest` 에 추가해야 함. 실제 `assignRoles()` 로직을 실행하려면 RoomService Mock 제거 필요 — 기존 I-01~I-05 와 구조 충돌.
- **결정**: I-06 검증 방법 = DB 직접 확인. H2 DB 에 기선택 role 포함 RoomPlayer 를 직접 저장하고, `MockBean.startGame()` 을 void stub 한 뒤 start API 호출 후 `RoomPlayerRepository` 로 DB 의 role 값을 조회. `assignRoles()` 의 실제 로직 검증은 `RoomServiceTest#U-10(assignRoles_preSelectedPlayersRolePreserved)` 단위 테스트로 분리.
- **원칙**:
  - `@MockBean` 이 있는 통합 테스트에서 "실제 서비스 로직 검증" 이 필요하면 두 선택지: ① MockBean 제거 → 전체 구조 변경 (부담 큼) ② DB 직접 셋업 + 단위 테스트 보완 (점진적, Javadoc 사유 명시).
  - 검증 방법 선택 사유를 Javadoc 에 명시하면 다음 개발자가 "왜 이렇게 구현했는지" 를 즉시 파악 가능.
  - PLAN 명세의 "DB 직접 확인 가능" 조항을 활용하는 것이 구조적으로 가장 안전한 결정.

## 2026-04-27 백엔드 미머지 REQ 의 Flutter 선제 작업 — 3단 graceful 패턴
- **상황**: chase_and_run REQ-G-02/A-08/D-03.4-V2/D-03.11 — 백엔드 PR 머지 전에 Flutter 측 UI/Repository/Notifier/STOMP 구독 등 "백엔드 머지하면 그대로 동작" 상태로 선제 작업 필요. 그냥 작성하면 백엔드 미머지 상태로 사용자가 진입 시 404/null/에러 화면 노출.
- **패턴 (3단 graceful)**:
  1. **Repository 단** — 404 / 미구현 응답을 도메인 예외로 정규화: `XxxNotImplementedException`. 실제 4xx/5xx 와 분리해서 throw.
  2. **State 단** — Notifier 의 build/fetch 가 위 예외를 catch 해서 `state.notImplemented = true` (또는 동등한 sentinel) 로 전환. 다른 정상 상태와 동일한 단일 데이터 구조 안에 보존.
  3. **UI 단** — `state.notImplemented` 분기로 "준비 중" 빈 상태 위젯 표시. 사용자에게 "기능 준비 중입니다" 한 줄 안내. 에러 화면 X.
- **추가 변종 — backward-compat 페이로드**: 같은 채널의 페이로드가 V1/V2 둘 다 들어올 수 있는 케이스 (예: STOMP `/user/queue/captured` 가 capturedAtLat/Lng 추가될 예정). 헬퍼 함수 한 곳 (`effectiveJail(runtime, session)` 같은 식) 에 V2 우선 → V1 폴백 분기를 가두고, 호출처는 그 결과만 사용. V1/V2 분기가 코드 곳곳에 퍼지지 않음.
- **테스트**: `notImplemented` 플래그도 정상 케이스와 동일하게 단위 테스트 (404 mock → state.notImplemented=true 검증). UI 분기는 실기기 회귀에서 자연스럽게 검증.
- **원칙**: "백엔드 머지 대기" 라는 이유로 Flutter 작업을 미루지 말 것. 미머지/머지 양쪽에서 동작하도록 graceful 처리하면 Flutter 측을 병렬로 끝낼 수 있고, 머지 시점에 wire-up 만 검증하면 됨. 단, **반드시 단위 테스트로 양쪽 경로 검증**해야 머지 후 회귀 부담이 줄어듦.

## 2026-04-27 Flutter ARB i18n — `const Map` 안에 l10n 못 넣음, 색상-only Map + build 시점 switch 로 분리
- **상황**: flip_user 마이페이지 역경매 status chip — `static const Map<String, ({String label, Color color})> _statusMap = { 'PENDING': (label: '입찰 대기 중', ...) }` 패턴이 화면에 깔려 있는데, l10n 으로 전환하려니 `AppLocalizations.of(context)` 는 런타임 호출이라 `const` 표현식에 못 들어감.
- **해결 패턴**:
  ```dart
  static const Map<String, Color> _statusColors = {
    'PENDING': Color(0xFFFF9800), 'REVIEWED': ..., 'CANCELLED': ...,
  };
  String _labelOf(AppLocalizations l10n) => switch (status) {
        'PENDING' => l10n.tourRequestStatusPending,
        ...
        null => l10n.tourRequestStatusUnknown,
        _ => status!,
      };
  ```
  build() 안에서 `_statusColors[status] ?? AppColors.outline` + `_labelOf(l10n)` 두 결과를 합쳐 사용.
- **원칙**: `const` 가 필요한 데이터(색상·아이콘 등 컴파일 타임 상수) 와 런타임 데이터(번역 라벨) 를 같은 자료구조에 묶지 말 것. 분리 후 build/render 시점에 합치는 패턴이 i18n 친화적.

## 2026-04-27 Flutter PopScope — back 결과로 데이터 전달 시 시스템 back 도 함께 처리
- **상황**: 상세 화면에서 수정 후 목록으로 돌아갈 때 갱신된 엔티티를 `Navigator.pop(context, updatedEntity)` 로 전달. AppBar 의 IconButton back 만 커스텀하면 Android 시스템 back 버튼 / iOS gesture back 은 기본 pop(null) 이 호출돼 결과가 누락됨.
- **해결**: Scaffold 를 `PopScope<T>(canPop: false, onPopInvokedWithResult: (didPop, result) { if (didPop) return; Navigator.of(context).pop(_dirty ? _request : null); })` 로 감싸고, IconButton 의 onPressed 도 동일 헬퍼(`_popWithResult`) 를 호출하게 통일. `_dirty` 플래그로 변경이 있었을 때만 결과를 전달 (불필요한 갱신 회피).
- **원칙**: pop 결과 데이터 전달이 필요한 화면은 (1) 커스텀 IconButton (2) 시스템 back (3) gesture back (iOS) 세 경로 모두에서 동일 결과가 나오도록 PopScope 한 곳에서 가두는 게 안전. WillPopScope 는 deprecated, PopScope (Flutter 3.16+) 사용.
- **추가 — 낙관적 업데이트**: list ↔ detail 네비게이션에서 단순 `_reload()` 만 의존하면 백엔드 캐시·응답 지연 시 미반영 가능. detail pop 시 갱신된 엔티티를 결과로 받아 list 에서 id 매칭 후 즉시 교체 + 기존 reload 백업의 조합이 가장 안정적. 이 경우 FutureBuilder → 직접 상태 패턴 (`List<T>? _items / _loading / _error`) 으로 전환해야 items 직접 mutate 가능.

## 2026-04-27 i18n 전환 후 dead key cleanup — 화면 코드에서만 grep, arb/auto-gen 제외
- **상황**: flip_user 트립/인기 코스 화면이 하드코딩 카드 → FlipService 실데이터로 전환되면서 `tripCityHighlightTitle`, `popularCourseTitle1~4`, `homePopularCourseTitle1~2` 등 i18n 키 ~17개가 dead 상태가 됨.
- **검증 방법**: `Grep <key_pattern>` 시 `glob: !{app_localizations*.dart,*.arb}` 로 자동생성 + 정의 파일 제외 → 실제 사용처 0건이면 안전하게 삭제. 그냥 grep 하면 ARB / 생성된 dart 파일에서 자기 자신을 매치해 false positive.
- **원칙**: 기능 전환(하드코딩 → 실데이터) 직후 같은 PR 또는 후속 작은 PR 로 dead key 를 정리. 미루면 ARB 파일이 부풀고 다음 사람이 "이거 쓰는 거 맞나?" 헷갈림.

## 2026-04-27 Spring `Page<T>` raw 반환 vs wrapper record — 직렬화 키가 다르다
- **상황**: chase_and_run_admin REQ-G-02 (`/reports/my`) 검증 중 발견. `ReportController` 가 `Page<MyReportSummary>` 를 그대로 반환했는데 Flutter 무한스크롤이 동작 안 함.
- **원인**: Spring 의 `PageImpl` 기본 직렬화 키는 `{ content, number, size, totalElements, totalPages, ... }` — page 번호가 **`number`**. 같은 프로젝트의 다른 응답들 (`GameHistoryResponse`, `RankingListResponse`) 은 별도 record 로 래핑해 `page` 키를 사용 중. Flutter 측 모델 (`MyReportsPage.fromJson`) 이 컨벤션 따라 `page` 로 읽어 페이지 번호가 영원히 0.
- **해결**: ① 백엔드 — `MyReportsResponse` record 신설 (다른 응답들과 동일 패턴, `page` 키). ② Flutter 임시 폴백 — `(json['page'] ?? json['number'])` 로 양쪽 수용 → 머지 전후 모두 안전.
- **원칙**: Spring `Page<T>` 를 컨트롤러에서 raw 반환하지 말 것. 프로젝트 응답 컨벤션이 `page` / `currentPage` / 별도 메타 객체 등 무엇이든 그것에 맞춘 record/DTO 를 통일적으로 거쳐야 클라이언트가 예측 가능한 키로 받음. raw `Page` 노출은 프로젝트 컨벤션 위반 + 클라이언트 사일런트 버그 (페이지 번호 0 으로 굳어 무한스크롤 미작동).
- **검증 방법**: 백엔드 응답 스펙은 명세 문서가 아니라 실제 컨트롤러/DTO 코드를 읽어야 함. 명세 ("`{page, totalElements, ...}` 응답") 와 구현 (`Page<>` raw → `number`) 이 어긋날 수 있음 — Flutter 가 명세대로 읽으면 깨짐. PR 검수 단계에서 응답 JSON 의 실제 키를 print 로라도 확인해야 사일런트 버그 차단.

## 2026-04-27 백엔드 머지 검증 — 명세 문서가 아닌 실제 코드를 읽어야
- **상황**: chase_and_run_admin 4건 (REQ-G-02 / D-03.4-V2 / D-03.11 / C-08-FIX) 머지 후 Flutter wire-up 전 검증 단계. `API_REQUESTS.md` 본문은 ⚪ 신규 그대로였지만 상단 로드맵 표는 🟢 완료로 업데이트돼 있었음 (작성자가 표만 업데이트). admin 레포 코드 직접 비교로 4건 모두 머지 확인.
- **결과**: 머지 자체는 다 됐지만 2건에서 페이로드 키 mismatch:
  - REQ-G-02: 위 Spring Page raw 케이스
  - REQ-D-03.4-V2: `CaptureEvent.occurredAt` (Java 필드명) vs 명세 `capturedAt` — Jackson 기본 직렬화로 필드명이 그대로 키로 노출
- **원칙**: "🟢 완료" 표시만 보고 wire-up 으로 넘어가지 말고, 응답 DTO/이벤트 객체 클래스를 직접 열어 필드명 단위로 비교. 특히 (1) 페이지 응답의 페이지 번호 키 (2) 이벤트 클래스의 시각 필드 (3) 직렬화 어노테이션 부재 시 필드명 그대로 노출되는 케이스. 명세는 의도, 코드는 진실. 둘이 어긋나면 머지된 쪽(코드)이 사실이고 명세는 따라가야 함.
- **운영**: mismatch 발견 시 (1) Flutter 측에 즉시 폴백 추가 (`a ?? b` 한 줄) (2) `API_REQUESTS.md` 에 FIX REQ 등록 (3) 백엔드 머지 후 폴백 정리. 폴백 우선이 맞음 — 백엔드 머지를 기다리는 동안에도 클라이언트가 동작해야 함.

---

## 2026-04-27 React 폴링 훅 — useCallback+함수선언 상호 참조 시 pollRef 패턴으로 ESLint 경고 없이 재귀 setTimeout 구현
- **상황**: chase_and_run_admin SystemLogContext 에서 `usePollChannel` 커스텀 훅 구현. `scheduleNext(ms)` 가 `poll()` 을 setTimeout 으로 재호출, `poll()` 이 `scheduleNext()` 를 호출하는 상호 참조 패턴. `useCallback` + `react-hooks/exhaustive-deps` 가 서로 순환 의존 경고 유발.
- **해결**: `pollRef = useRef(null)` + `pollRef.current = async function poll() { ... }` 패턴. 매 렌더마다 최신 버전으로 갱신되며, `setTimeout(() => pollRef.current(), delay)` 로 호출해 클로저 stale 없이 재귀 타이머 구현. ESLint exhaustive-deps 경고 0건.
- **예방**:
  - 재귀 setTimeout (폴링, 백오프) 패턴은 `useCallback` 의존성 배열 문제를 피하기 위해 처음부터 `ref.current = function` 패턴으로 설계.
  - 가변 상태 (sinceId, backoffIdx, stopped 플래그) 는 모두 useRef 로 관리 — setState 는 UI 표시용 lines 배열에만.
  - 인증 에러(401/403) 는 폴링 정지 + 사용자 가시 메시지 push, 5xx/네트워크는 백오프 큐(5→10→30→60초).

---

## 2026-04-29 React 19 + react-compiler — useEffect 로 prop→state 동기화 금지, key prop 패턴으로 회피
- **상황**: ShortDduk 템플릿 편집기 페이지 (`TemplateEditorPage.jsx`). React Query 로 받아온 `tpl` 을 `draft` 로컬 state 에 복사해 사용자 편집 → 저장 흐름. 처음엔 흔한 `useEffect(() => { if (tpl) setDraft(tpl); }, [tpl])` 패턴으로 작성.
- **에러**: ESLint `react-hooks/set-state-in-effect` 가 error 수준으로 차단 — "Calling setState synchronously within an effect can trigger cascading renders". React 19 + react-compiler 환경에선 이 룰이 강제됨 (이전 버전에선 warning).
- **해결**: prop→state 동기화는 useEffect 대신 **부모에서 key prop + 자식의 useState 초기값** 패턴.
  ```jsx
  // 부모: tpl 로드 후에만 자식 마운트, key 로 templateSn 변경 시 강제 remount
  if (!tpl) return <Loading />;
  return <Editor key={tpl.templateSn} initial={tpl} />;

  // 자식: prop 으로 받은 initial 을 useState 초기값으로
  function Editor({ initial }) {
    const [draft, setDraft] = useState(initial);
    // useEffect 동기화 불필요 — key 변경 시 Editor 가 새로 마운트되며 useState 가 새 initial 로 초기화
  }
  ```
- **부수 케이스 — react-hook-form `watch()`**: 같은 react-compiler 가 `watch()` 를 "incompatible library" 경고 (warning 수준이지만 향후 error 화 가능). 해법: 의존하는 필드만 hook-form 에서 분리해 별도 `useState` 로 관리하고 onChange 에서 직접 set. 또는 `<Controller render={({ field }) => ...}>` 사용.
- **예방**:
  - "loaded data → editable copy" 패턴은 처음부터 key prop + 자식 분리로 설계. useEffect 동기화는 외부 시스템(DOM/timer/subscription) 전용.
  - prop 으로 받은 초기값은 useState 의 첫 인자로만 — 이후 prop 변경 무시 (key 가 변경 신호).
  - React 19 + react-compiler 도입한 프로젝트에선 ESLint 규칙 `react-hooks/set-state-in-effect`, `react-hooks/incompatible-library` 가 활성. 새 컴포넌트 작성 전 이 두 룰 위반 패턴을 먼저 확인.

---

## 2026-04-29 Creatomate 자체 9:16 미리보기 — container query `cqmin` 으로 vmin 1:1 매칭
- **상황**: ShortDduk 템플릿 편집기에 자체 미리보기 컴포넌트 (`TemplatePreview.jsx`) 구현. Creatomate 가 사용하는 `vmin` 단위 (예: `font_size: "8 vmin"`) 를 박스 안에서 정확히 환산해야 사용자가 본 미리보기와 실제 mp4 가 일치.
- **함정**: viewport `vmin` (CSS) 은 브라우저 viewport 짧은 변 기준이라 미리보기 박스가 화면 일부일 때 완전히 어긋남. 100%, 50% 처럼 % 단위로 환산하면 박스 크기 변할 때 비율 깨짐.
- **해결**: **container query 단위 `cqmin`** 사용 (Chrome 105+, Safari 16+). 박스에 `container-type: size` 부여, 자식은 `font-size: 8cqmin` → 박스 짧은 변의 1% 단위로 정확 환산. Creatomate 의 vmin 정의 (출력 영상 짧은 변 기준) 와 1:1.
  ```jsx
  <div style={{ aspectRatio: "9/16", containerType: "size" }}>
    <span style={{ fontSize: `${template.subtitleFontSizeVmin}cqmin` }}>...</span>
  </div>
  ```
- **추가 매칭 포인트**:
  - 텍스트 외곽선: `-webkit-text-stroke` + `paint-order: stroke fill` (Creatomate 의 stroke→fill 순서와 일치)
  - 배경 padding: Creatomate 의 `background_x_padding: "20%"` 는 element height 기준 — CSS `padding: 0.6em 1.2em` 로 근사 (정확히 일치하려면 element height 측정 후 px 환산)
  - 영상 filter: CSS `filter: sepia(0.85) / grayscale(1) / saturate(1.4) contrast(1.1)` 등으로 Creatomate `color_filter` 4종 (sepia/grayscale/warm/vivid) 근사
- **예방**:
  - "외부 렌더 엔진을 자체 캔버스로 흉내" 작업은 단위 매칭부터 설계. 외부 엔진의 단위 spec (vmin/em/% 의 기준축) 을 먼저 명세 → 동등한 CSS 단위 (cqmin/em/% with container-type) 매핑.
  - container query 미지원 브라우저 호환이 필요하면 ResizeObserver + state 로 박스 px 측정 후 자식에 inline px 주입. 단 React 렌더 한 번 더 발생.
  - 폰트 매칭: 외부 엔진과 동일한 Google Fonts 이름을 `<head>` 에 import (lazy 로딩 X — 첫 페인트 깜빡임으로 폰트 폴백 차이 발생).

---

## 2026-05-06 Java record component 이름과 no-arg 정적 팩토리 이름 충돌
- **상황**: Spring API 공통 응답을 `record ApiResponse<T>(boolean success, T data, ApiError error)` 로 만들면서 `static ApiResponse<Void> success()` 팩토리도 함께 정의.
- **실수**: record 컴포넌트 `success` 는 인스턴스 접근자 `success()` 를 자동 생성하므로, 같은 시그니처의 no-arg 정적 메서드를 둘 수 없어 `invalid accessor method in record` 컴파일 오류 발생.
- **원인**: record 접근자는 메서드 시그니처 관점에서 정적 팩토리와도 이름/인자 목록이 충돌한다. 반환 타입만 다른 오버로드는 불가능.
- **해결**: 필드명 `success` 는 유지하고 no-arg 팩토리를 `empty()` 로 변경. 인자가 있는 `success(T data)` 는 시그니처가 달라 사용 가능.
- **예방**: record 컴포넌트명과 같은 no-arg 메서드명을 만들지 않는다. 공통 응답 record에서는 `success(T data)`, `empty()`, `error(...)` 처럼 팩토리 이름을 분리한다.

---

## 2026-05-06 검증 래퍼 출력보다 실제 빌드 도구 결과를 우선 확인
- **상황**: Spring `scripts/verify.ps1 -Mode full` 실행 중 Gradle `:test` 가 실패했는데 래퍼 스크립트 말미에 `Verification passed.` 문구가 출력됨.
- **실수**: 래퍼의 최종 문구만 보면 통과로 오판할 수 있었다.
- **원인**: 검증 래퍼가 하위 명령 실패 상태를 문구/종료 처리에 완전히 반영하지 못하는 케이스가 있었다.
- **해결**: 원문 출력의 `BUILD FAILED`, `FAILED`, 실패 테스트 목록을 직접 확인하고 실패 테스트를 수정한 뒤 재실행했다.
- **예방**: 검증 스크립트는 최종 문구만 보지 말고 하위 빌드 도구의 핵심 라인(`BUILD SUCCESSFUL`/`BUILD FAILED`, failed test count)을 함께 확인한다. 래퍼에서 모순된 출력이 보이면 실패로 간주하고 스크립트 개선 TODO를 별도 기록한다.
## 2026-05-06 Gradle 테스트 병렬 실행 충돌
- 상황: 같은 Gradle 프로젝트에서 `./gradlew test --tests ...` 두 개를 병렬 실행했다.
- 실수: 두 테스트 작업이 `build/test-results/test/binary`를 동시에 정리하면서 한쪽이 `Unable to delete directory`로 실패했다.
- 원인: Gradle test 결과 디렉터리는 프로젝트 단위 공유 산출물이라 같은 워크트리에서 병렬 테스트 실행에 안전하지 않다.
- 해결: 실패한 테스트를 단독 재실행했고 정상 통과를 확인했다.
- 예방: 같은 Gradle 워크트리에서는 테스트/검증 명령을 병렬 실행하지 말고 순차 실행한다.
