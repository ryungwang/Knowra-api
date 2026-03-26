# Knowra 커뮤니티 탐색 - 백엔드 작업 가이드

> 최종 수정: 2026-03-26

---

## 진행 현황 범례
- `[ ]` 미완료
- `[x]` 완료
- `[-]` 진행 중

---

## Step 1. 테이블 설계

### 1-1. 기존 테이블 컬럼 추가 확인
- [ ] `tbl_communities`에 `CATEGORY_SN` 컬럼 있는지 확인
- [ ] `tbl_communities`에 `MEMBER_COUNT` 컬럼 추가 (캐싱용)

```sql
ALTER TABLE tbl_communities
    ADD COLUMN CATEGORY_SN BIGINT NOT NULL COMMENT '카테고리일련번호',
    ADD COLUMN MEMBER_COUNT INT DEFAULT 0 COMMENT '멤버수';
```

### 1-2. 신규 테이블

```sql
-- 사용자 관심사 카테고리 (회원가입 시 선택)
CREATE TABLE tbl_user_interest_category (
    USER_SN         BIGINT NOT NULL COMMENT '사용자일련번호',
    CATEGORY_SN     BIGINT NOT NULL COMMENT '카테고리일련번호',
    FRST_CRT_DT     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    PRIMARY KEY (USER_SN, CATEGORY_SN)
);

-- 행동 로그 (추천 알고리즘용)
CREATE TABLE tbl_user_action_log (
    LOG_SN          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '로그일련번호',
    USER_SN         BIGINT NOT NULL COMMENT '사용자일련번호',
    TARGET_TYPE     VARCHAR(20) NOT NULL COMMENT 'COMMUNITY/POST/COMMENT',
    TARGET_SN       BIGINT NOT NULL COMMENT '대상일련번호',
    ACTION_TYPE     VARCHAR(20) NOT NULL COMMENT 'VIEW/LIKE/COMMENT/JOIN/LEAVE',
    STAY_SECONDS    INT DEFAULT 0 COMMENT '체류시간(초)',
    FRST_CRT_DT     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    INDEX idx_user_action (USER_SN, TARGET_TYPE, FRST_CRT_DT)
);

-- 사용자 관심도 점수 (배치로 계산된 결과 캐싱)
CREATE TABLE tbl_user_interest_score (
    USER_SN         BIGINT NOT NULL COMMENT '사용자일련번호',
    CATEGORY_SN     BIGINT NOT NULL COMMENT '카테고리일련번호',
    SCORE           DOUBLE DEFAULT 0 COMMENT '관심도점수',
    UPDATED_AT      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (USER_SN, CATEGORY_SN)
);
```

---

## Step 2. 엔티티 / Repository

- [ ] `TblUserInterestCategory` 엔티티 작성
- [ ] `TblUserActionLog` 엔티티 작성
- [ ] `TblUserInterestScore` 엔티티 작성
- [ ] `TblUserInterestCategoryRepository` 작성
- [ ] `TblUserActionLogRepository` 작성
- [ ] `TblUserInterestScoreRepository` 작성
- [ ] `tbl_communities`에 `categorySn`, `memberCount` 필드 추가

---

## Step 3. 회원가입 시 관심사 카테고리 저장

- [ ] 회원가입 Request에 `categorySnList` 추가
- [ ] 회원가입 시 `tbl_user_interest_category`에 일괄 저장
- [ ] 개수 제한 없음

```java
@Getter
public class SignupRequest {
    private String loginId;
    private String email;
    private String password;
    private String name;
    private List<Long> categorySnList;  // 관심사 카테고리
}

@Transactional
public void signup(SignupRequest request) {
    TblUser user = ...; // 기존 유저 저장 로직
    userRepository.save(user);

    // 관심사 카테고리 일괄 저장
    List<TblUserInterestCategory> interests = request.getCategorySnList().stream()
            .map(categorySn -> TblUserInterestCategory.builder()
                    .userSn(user.getUserSn())
                    .categorySn(categorySn)
                    .build())
            .collect(Collectors.toList());
    interestCategoryRepository.saveAll(interests);
}
```

---

## Step 4. 인기 커뮤니티 API

- [ ] `GET /api/communities/explore/popular` 구현
- [ ] 점수 계산: 멤버수 * 0.4 + 최근7일 게시글수 * 0.4 + 최근7일 가입수 * 0.2
- [ ] 10개 고정 반환
- [ ] 게시글 테이블 완성 전까지는 멤버수 기준으로 임시 처리

```java
public List<CommunityResponse> findPopular(int limit) {
    return queryFactory
            .select(community)
            .from(community)
            .where(community.actvtnYn.eq("Y"))
            .orderBy(community.memberCount.desc())
            .limit(limit)
            .fetch();
}
```

---

## Step 5. 맞춤 추천 API

- [ ] `GET /api/communities/explore/recommended` 구현 (JWT 필요)
- [ ] 행동 로그 있는 유저 → 행동 기반 추천
- [ ] 행동 로그 없는 신규 유저 → 관심사 카테고리 기반 추천
- [ ] 관심사도 없으면 → 인기순으로 대체
- [ ] 이미 가입한 커뮤니티 제외
- [ ] 10개 고정 반환

```java
@Service
@RequiredArgsConstructor
public class CommunityRecommendService {

    public List<CommunityResponse> getRecommended(Long userSn) {
        boolean hasActionLog = actionLogRepository.existsByUserSn(userSn);

        if (hasActionLog) {
            // 행동 기반 추천
            return getActionBasedRecommend(userSn);
        } else {
            // 신규 유저 → 관심사 카테고리 기반
            return getInterestBasedRecommend(userSn);
        }
    }

    private List<CommunityResponse> getInterestBasedRecommend(Long userSn) {
        List<Long> categorySnList = interestRepository.findCategorySnByUserSn(userSn);

        if (categorySnList.isEmpty()) {
            return communityRepository.findPopular(10);  // 인기순 대체
        }

        return communityRepository.findByCategorySnInAndNotJoined(
                categorySnList, userSn, 10);
    }

    private List<CommunityResponse> getActionBasedRecommend(Long userSn) {
        // 행동 로그 기반 점수 계산 (시간 감쇠 적용)
        // 게시글/댓글 구현 후 완성
        return actionLogRepository.findRecommendedByAction(userSn, 10);
    }
}
```

---

## Step 6. 카테고리별 목록 API (더보기)

- [ ] `GET /api/categories` 카테고리 전체 목록
- [ ] `GET /api/communities/category/{id}?page=0&size=10` 구현
- [ ] 멤버수 내림차순 정렬
- [ ] 더보기 버튼 방식 (페이지네이션)

```java
@Getter
@AllArgsConstructor
public class CategoryPageResponse<T> {
    private List<T> content;
    private int currentPage;
    private int totalPages;
    private boolean hasNext;
}

public CategoryPageResponse<CommunityResponse> getCategoryList(
        Long categorySn, int page, int size) {

    Pageable pageable = PageRequest.of(page, size,
            Sort.by("memberCount").descending());

    Page<TblCommunity> result = communityRepository
            .findByCategorySnAndActvtnYn(categorySn, "Y", pageable);

    return new CategoryPageResponse<>(
            result.getContent().stream().map(...).collect(Collectors.toList()),
            result.getNumber(),
            result.getTotalPages(),
            result.hasNext()
    );
}
```

---

## Step 7. 행동 로그 수집

- [ ] 커뮤니티 조회 시 VIEW 로그 저장
- [ ] 가입 시 JOIN 로그 저장 (기존 join API에 추가)
- [ ] 탈퇴 시 LEAVE 로그 저장 (기존 leave API에 추가)
- [ ] 게시글 좋아요/댓글 → 게시글 구현 후 추가
- [ ] `@Async` 비동기 처리로 성능 영향 최소화

```java
@Async
public void saveActionLog(Long userSn, String targetType,
                          Long targetSn, String actionType) {
    TblUserActionLog log = TblUserActionLog.builder()
            .userSn(userSn)
            .targetType(targetType)
            .targetSn(targetSn)
            .actionType(actionType)
            .build();
    actionLogRepository.save(log);
}
```

---

## Step 8. MEMBER_COUNT 동기화

- [ ] 가입 시 `MEMBER_COUNT + 1`
- [ ] 탈퇴/추방 시 `MEMBER_COUNT - 1`
- [ ] 기존 join/leave Service에 추가

```java
// 가입 시
community.setMemberCount(community.getMemberCount() + 1);

// 탈퇴 시
community.setMemberCount(Math.max(0, community.getMemberCount() - 1));
```

---

## API 목록 요약

```
GET /api/categories                                → 카테고리 전체 목록
GET /api/communities/explore/popular               → 인기 커뮤니티 10개
GET /api/communities/explore/recommended           → 맞춤 추천 10개 (JWT 필요)
GET /api/communities/category/{id}?page=0&size=10  → 카테고리별 목록 + 더보기
```

---

## 작업 순서

```
Step 1 → 테이블 설계 및 생성
Step 2 → 엔티티 / Repository 작성
Step 3 → 회원가입 관심사 카테고리 저장
Step 4 → 인기 커뮤니티 API
Step 5 → 맞춤 추천 API
Step 6 → 카테고리별 목록 API
Step 7 → 행동 로그 수집 (게시글 구현 후 완성)
Step 8 → MEMBER_COUNT 동기화
```

---

> ⚠️ 행동 로그 기반 추천(Step 7)은 게시글/댓글 구현 후 완성
> ✅ 신규 유저: 관심사 카테고리 기반 → 없으면 인기순 자동 대체