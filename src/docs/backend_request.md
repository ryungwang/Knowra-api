# Knowra 커뮤니티 구현 - 미완료 항목

> 스택: Spring Boot · Spring Security · JWT · JPA · QueryDSL · MariaDB
> 스키마: `knowra_community`
> 최종 수정: 2026-03-26

---

## Step 1. Q클래스 확인

- [ ] `QTblCommunities` Q클래스 생성 확인

> ⚠️ Q클래스 없으면 `mvn clean compile` 실행

---

## Step 4. 커뮤니티 생성 보완

- [ ] `commNm` 중복 확인 로직 (`setCommunity` 내부)

```java
if (tblCommunitiesRepository.findByCommNm(tblCommunities.getCommNm()) != null) {
    throw new RuntimeException("이미 사용 중인 커뮤니티 슬러그입니다.");
}
```

- [ ] 생성자 자동 OWNER 등록 (`setCommunity` 저장 후 추가)

```java
TblCommunityMember ownerMember = TblCommunityMember.builder()
        .commSn(tblCommunities.getCommSn())
        .userSn(userSn)
        .role("OWNER")
        .joinTyp("AUTO")
        .stat("ACTIVE")
        .actvtnYn("Y")
        .creatrSn(userSn)
        .build();
tblCommunityMemberRepository.save(ownerMember);
```

---

## Step 6. 커뮤니티 조회 보완

- [ ] 전체 커뮤니티 목록 조회 API (페이징)
- [ ] 키워드 검색 (`commNm` 또는 `commDsplNm` 기준)
- [ ] 비활성 필터링 (`actvtnYn = 'Y'`, `stat = 'Y'`)

```java
// Repository 추가 필요
Page<TblCommunities> findByCommDsplNmContainingAndActvtnYn(
    String commDsplNm, String actvtnYn, Pageable pageable);

// Controller 추가
@GetMapping("/api/community/getCommunityList")
public ResultVO getCommunityList(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest request) { ... }
```

---

## Step 7. 커뮤니티 개인설정 (설계 필요)

- [ ] `tbl_community_user_setting` 테이블 설계
- [ ] 설정 항목 확정 (닉네임, 알림 ON/OFF 등)
- [ ] `TblCommunityUserSetting` 엔티티 작성
- [ ] `TblCommunityUserSettingRepository` 작성
- [ ] 가입 시 기본 설정 자동 생성 (`setMember` 가입 분기에 추가)
- [ ] 개인설정 조회 API (`GET /api/community/{commSn}/settings/me`)
- [ ] 개인설정 수정 API (`PUT /api/community/{commSn}/settings/me`)

> ⚠️ 테이블 설계 후 별도 논의 필요

---

## Step 8. 커뮤니티 수정 / 삭제

- [ ] 커뮤니티 수정 API — OWNER만, `commDsplNm` / `commDesc` / `prvcyStng` 변경
- [ ] 커뮤니티 삭제 API — OWNER만, 소프트 삭제 (`actvtnYn = 'N'`)
- [ ] OWNER 권한 검증 공통 메서드

```java
private TblCommunityMember validateOwner(long commSn, long userSn) {
    TblCommunityMember member = tblCommunityMemberRepository
        .findByCommSnAndUserSn(commSn, userSn)
        .orElseThrow(() -> new RuntimeException("가입된 커뮤니티가 아닙니다."));

    if (!"OWNER".equals(member.getRole())) {
        throw new RuntimeException("OWNER만 수행할 수 있습니다.");
    }
    return member;
}
```

---

## Step 9. 멤버 관리 (관리자)

- [ ] 멤버 목록 조회 API (`GET /api/community/{commSn}/members`)
- [ ] `PENDING` 멤버 승인 / 거절 — `stat = ACTIVE` or `REJECTED`
- [ ] 멤버 강제 추방 — `stat = BANNED`
- [ ] 추방 해제 — `stat = ACTIVE`
- [ ] 멤버 권한 변경 (MEMBER ↔ ADMIN) — `role` 변경
- [ ] OWNER 권한 위임 — 기존 OWNER `role = ADMIN`, 대상자 `role = OWNER`

---

## Step 10. 내가 만든 커뮤니티 목록

- [ ] 내가 만든 커뮤니티 목록 API

```java
// Repository 추가
List<TblCommunities> findByCreatrSnAndActvtnYn(long creatrSn, String actvtnYn);

// Controller 추가
@PostMapping("/api/community/getMyOwnedCommunityList")
public ResultVO getMyOwnedCommunityList(HttpServletRequest request) { ... }
```

---

## Step 11. 공통 예외 처리

- [ ] `@RestControllerAdvice` 전역 예외 핸들러 작성

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResultVO handleRuntimeException(RuntimeException e) {
        ResultVO resultVO = new ResultVO();
        resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
        resultVO.setResultMessage(e.getMessage());
        return resultVO;
    }
}
```

---

## Step 13. Postman 테스트

- [ ] 커뮤니티 생성 (`POST /api/community/setCommunity`)
- [ ] `commNm` 중복 생성 거부 확인
- [ ] 생성 후 OWNER 자동 등록 확인
- [ ] 커뮤니티 상세 조회 (`GET /api/community/getCommunity?commNm=`)
- [ ] 내 커뮤니티 목록 (`POST /api/community/getMyCommunityList`)
- [ ] 가입 — `public` / `restricted` 분기 확인
- [ ] 중복 가입 / `BANNED` 재가입 방지 확인
- [ ] `WITHDRAWN` 후 재가입 확인
- [ ] 탈퇴 / OWNER 탈퇴 불가 확인

---

## 진행 순서

```
Step 4  → commNm 중복 체크 + OWNER 자동 등록
Step 6  → 전체 목록 + 검색 + 비활성 필터링
Step 8  → 커뮤니티 수정 / 삭제
Step 9  → 멤버 관리
Step 10 → 내가 만든 커뮤니티 목록
Step 11 → 공통 예외 처리
Step 7  → 개인설정 (테이블 설계 후)
Step 13 → Postman 테스트
```
