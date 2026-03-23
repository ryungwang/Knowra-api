package com.knowra.cmm.model;

public enum ResponseCode {

	SUCCESS(200, "성공했습니다."),

	/** 로그인, 로그아웃 관련 오류 */
	NOT_USER(500, "일치하는 사용자가 없습니다."),
	NOT_EQ_PASSWORD(500, "비밀번호가 일치하지 않습니다."),
	LOGIN_ERROR(500, "로그인 처리 중 오류가 발생했습니다."),
	LOGOUT_ERROR(500, "로그아웃 처리 중 오류가 발생했습니다."),
	SIGNATURE_ERROR(500, "서명 오류"),
	SESSION_EXPIRED(500, "세션 만료됨"),
	DUPLICATE_LOGIN(500, "해당 계정은 현재 접속중인 계정입니다."),
	DUPLICATE_LOGOUT(500, "다른 기기에서 로그인하여, 로그아웃되었습니다."),
	WALLET_ADDRESS_DUPLICATE(500, "이미 가입된 지갑 주소입니다."),

	/** 로그인 시 회원 상태 오류 */
	WAITING_FOR_APPROVAL(500, "해당 계정은 승인 대기 계정입니다."),
	REJECT_FOR_APPROVAL(500, "해당 계정은 미승인 계정입니다."),
	SUSPENSION_OF_USE(500, "해당 계정은 이용 정지 계정입니다."),

	/** 회원가입 관련 알림 */
	JOIN_SUCCESS(200, "회원가입 신청이 완료되었습니다.\n관리자 승인 후 이용할 수 있습니다."),

	/** 사용자 관련 오류 */
	USER_SELECT_ERROR(500, "조회 중 오류가 발생했습니다."),

	/** 첨부파일 관련 오류 */
	FILE_INFO_NOT_FOUND(500, "첨부파일 정보가 존재하지 않습니다."),
	FILE_NOT_FOUND(500, "첨부파일을 찾을 수 없습니다."),
	EXPIRE_FILE(500, "첨부파일 다운로드 기간이 만료되었습니다."),
	DOWN_CNT_EXCEEDED(500, "첨부파일 다운로드 횟수 초과로 파일이 만료됐습니다."),
	FILE_DOWN_ERROR(500, "다운로드 처리 중 오류가 발생했습니다."),

	/** 지갑 관련 오류 */
	NOT_WALLET_ERROR(500, "수신 주소가 설정되지 않았습니다.\n관리자에게 문의해주세요."),

	/** 공통 오류 */
	SELECT_ERROR(500, "조회 중 오류가 발생했습니다."),
	UPDATE_ERROR(500, "업데이트 중 오류가 발생했습니다."),
	SELECT_REQUIRE_ERROR(601, "조회 필수 조건이 누락되었습니다."),
	DELETE_ERROR(700, "삭제 중 내부 오류가 발생했습니다."),
	SAVE_ERROR(800, "저장시 내부 오류가 발생했습니다.");



	private int code;
	private String message;

	private ResponseCode(int code, String message) {
		this.code = code;
		this.message = message;
	}

	public int getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}
}
