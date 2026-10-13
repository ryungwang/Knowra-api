package com.knowra.user.sns;

/**
 * 소셜 제공자가 확인해 준 신원. 제공자별 응답 모양이 제각각이라 여기서 한 모양으로 맞춘다.
 *
 * @param provider GOOGLE | KAKAO | NAVER | APPLE — tbl_user.sns_name 에 그대로 들어간다
 * @param id       제공자 안에서의 고유 ID(sub). tbl_user.sns_id
 * @param email    제공자가 준 이메일. 애플은 비공개 릴레이 주소일 수 있고, 카카오는 동의 안 하면 null
 * @param name     표시 이름(없으면 null)
 * @param picture  프로필 이미지 URL(없으면 null)
 */
public record SnsIdentity(String provider, String id, String email, String name, String picture) {}
