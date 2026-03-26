# Knowra API — Claude 가이드

## 코드 작성 원칙

- **가독성·확장성 최우선** — 성능보다 예측 가능성과 디버깅 용이성을 우선한다
- 메서드는 의도가 명확하게, 복잡한 로직은 잘게 분리한다
- 조건 추가·변경이 한 곳에서 끝나도록 설계한다
- 반복되는 패턴은 공통 추상화한다

## 기술 선택 기준

- **QueryDSL 유지** — JPA `@EntityGraph` 대신 QueryDSL을 사용한다. 조건 추가 시 `.where()` 한 줄로 끝나고, Hibernate가 생성하는 SQL 대신 직접 제어할 수 있어 확장성과 디버깅이 명확하다
- 성능 차이가 명확히 발생하는 경우에만 예외로 최적화를 고려한다

## 패키지 구조 원칙

- `cmm` — 시스템 설정 패키지 (JWT, Redis, ModelMapper 등 인프라 설정)
- `common` — 공통 엔티티·DTO (`BaseAuditEntity`, `BasePostEntity`, `BaseCmtEntity`, `CmtDTO` 등)
- 도메인 공통 클래스는 `cmm`이 아닌 `common` 패키지에 넣는다

## 엔티티 작성 규칙

- 상속 구조: `@MappedSuperclass` + `@SuperBuilder` + `@NoArgsConstructor` 조합을 사용한다
- 기본값이 있는 필드는 반드시 `@Builder.Default`를 붙인다 — 누락 시 빌더로 생성하면 null이 들어가 DB constraint 오류 발생
- 엔티티 생성 시 `ModelMapper.map(params, Entity.class)` 사용 금지 — `@Builder.Default` 값이 무시됨. 반드시 빌더 패턴으로 직접 생성한다

## DB 카탈로그 구조

| 카탈로그          | 용도                                      |
|-------------------|-------------------------------------------|
| `KNOWRA_COMMUNITY`| 커뮤니티, 게시글, 댓글, 좋아요, 태그 매핑 |
| `KNOWRA_COM`      | 공통 (태그 원본, 파일 등)                 |
| `KNOWRA_USER`     | 사용자                                    |

- 크로스 카탈로그 JOIN은 QueryDSL로 처리한다 (Hibernate 자동 생성 SQL 대신)

## 응답 형식

- 모든 API 응답은 `ResultVO`를 사용한다
- 성공: `ResponseCode.SUCCESS`, 조회 오류: `ResponseCode.SELECT_ERROR`, 저장 오류: `ResponseCode.SAVE_ERROR`

## 페이지네이션 규칙

- 피드형 목록 (최신순·공지): **커서 기반** (`commPostSn DESC`) — 새 게시글 추가돼도 중복/누락 없음
- 랭킹형 목록 (인기순): **offset 기반** — 순위가 동적으로 바뀌므로 커서가 의미 없음

## Redis 규칙

- Community 게시글 조회수: DB 인덱스 **15번**, 키 형식 `post:viewcnt:{commPostSn}`
- 조회수는 Redis INCR로 누적 후 `ViewCountSyncScheduler`가 10분마다 DB에 동기화
- 파이프라인/트랜잭션은 `SessionCallback` + `opsForValue()` 사용 (`StringRedisConnection` 직접 캐스팅 금지)
