package com.knowra.cmm.util;

import com.knowra.common.entity.TblComFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class FileUtil {

    public static final int BUFF_SIZE = 2048;

	/**
	 * 업로드를 허용하는 확장자.
	 *
	 * <p>⚠️ <b>화이트리스트여야 한다.</b> 저장 경로({@code File.Store.Path})가 그대로
	 * {@code /upload} 로 서빙되므로, 막지 않으면 올린 파일을 그 주소로 바로 받아갈 수 있다.
	 * 예전엔 검사가 아예 없어 확장자가 무엇이든 저장됐다 — 경로 조작({@code ..})만 막고
	 * 있었다. 공개 커뮤니티에서 누구나 올릴 수 있는 자리라 블랙리스트로는 부족하다.
	 *
	 * <p>이미지만 받는다. 다른 형식이 필요해지면 <b>여기에 한 줄 추가</b>하고,
	 * 그 형식이 브라우저에서 스크립트로 실행될 여지가 없는지 먼저 확인한다
	 * (svg 를 뺀 이유다 — svg 안에는 스크립트를 넣을 수 있다).
	 */
	private static final Set<String> ALLOWED_EXTENSIONS =
			Set.of("png", "jpg", "jpeg", "gif", "webp");

	@Value("${File.Store.Path}")
	private String fileStorePath;

	/**
	 * 파일명에서 확장자를 꺼내 <b>허용 목록과 대조</b>한다. 소문자로 정규화해 돌려준다.
	 *
	 * <p>확장자가 없는 파일명({@code shell})도 거른다 — 예전 코드는 {@code lastIndexOf(".")}
	 * 가 -1 일 때 {@code substring(0)} 이 되어 <b>파일명 전체를 확장자로</b> 삼았다.
	 *
	 * @throws IOException 확장자가 없거나 허용 목록에 없을 때. 호출부가 이미
	 *         {@code IOException} 을 저장 실패로 처리하고 있어 같은 경로를 탄다.
	 */
	public static String requireAllowedExtension(String originalFileName) throws IOException {
		if (originalFileName == null || originalFileName.isBlank()) {
			throw new IOException("파일 이름이 없습니다.");
		}
		int index = originalFileName.lastIndexOf('.');
		if (index < 0 || index == originalFileName.length() - 1) {
			throw new IOException("확장자가 없는 파일은 올릴 수 없습니다: " + originalFileName);
		}
		String ext = originalFileName.substring(index + 1).toLowerCase(Locale.ROOT);
		if (!ALLOWED_EXTENSIONS.contains(ext)) {
			throw new IOException("허용하지 않는 확장자입니다: " + ext
					+ " (허용: " + String.join(", ", ALLOWED_EXTENSIONS) + ")");
		}
		return ext;
	}

	private final com.knowra.cmm.storage.StorageService storage;

	public FileUtil(com.knowra.cmm.storage.StorageService storage) { this.storage = storage; }

	/** 업로드 분류(전역 규칙 키의 {category}). 새 분류는 여기에만 추가한다. */
	public static final String CAT_PROFILE = "profile", CAT_COMMUNITY_LOGO = "community-logo",
			CAT_COMMUNITY_BANNER = "community-banner", CAT_EDITOR = "editor";

	/**
	 * 업로드는 전부 StorageService 로 간다(prod=S3, 그 밖=로컬 디스크 — 전역 규칙 「파일 스토리지 추상화」).
	 * 키는 전역 규칙 {@code {AWS_S3_PREFIX}/{tenant}/{category}/{uuid}.{ext}} 를 공개 prefix 아래에 둔 모양:
	 * {@code public/{prefix}/{소유자 userSn}/{category}/{uuid}.{ext}}. Knowra 엔 회사 같은 테넌트가 없어 올린 사람을 쓴다.
	 * 확장자 허용 목록(이미지) 검사를 먼저 한다.
	 */
	public TblComFile storeImage(MultipartFile f, long ownerSn, String category, String psnTblSn) throws IOException {
		String origin = f.getOriginalFilename();
		String ext = requireAllowedExtension(origin);
		com.knowra.cmm.storage.StorageService.PublicFile saved = storage.storePublic(f.getBytes(), ownerSn + "/" + category, "x." + ext);
		String key = saved.key();
		int slash = key.lastIndexOf('/');
		String file = key.substring(slash + 1);
		TblComFile c = new TblComFile();
		c.setFileKey(key);
		c.setAtchFilePathNm(key.substring(0, slash));
		c.setStrgFileNm(file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file);
		c.setAtchFileExtnNm(ext);
		c.setAtchFileNm(origin == null ? "" : (origin.length() > 100 ? origin.substring(origin.length() - 100) : origin));
		c.setAtchFileSz(f.getSize());
		c.setPsnTblSn(psnTblSn);
		return c;
	}

	/** 에디터·댓글·DM 사진 — 공개 URL(운영은 S3 절대 주소)을 돌려준다. 그대로 본문 HTML 에 박힌다. */
	public String uploadImage(MultipartFile file, long ownerSn, String category) throws IOException {
		String ext = requireAllowedExtension(file.getOriginalFilename());
		return storage.storePublic(file.getBytes(), ownerSn + "/" + category, "x." + ext).url();
	}

	/**
	 * 첨부 한 건의 실제 파일을 지운다. DB 의 경로({@code /upload/...})는 <b>공개 주소</b>라 그대로 파일 경로로 쓰면 안 된다 —
	 * 예전엔 그렇게 해서 「없는 파일」로 보고 성공 처리돼, 지운 프로필 사진이 주소로 계속 열렸다.
	 * 파일이 이미 없으면 지운 것으로 본다.
	 */
	public boolean deleteStored(TblComFile f) {
		if (f == null) return true;
		if (f.getFileKey() != null) { storage.delete(f.getFileKey()); return true; }   // 저장소 파일
		if (f.getAtchFilePathNm() == null) return true;                                  // 아래는 예전 /upload 파일
		String rel = f.getAtchFilePathNm().startsWith("/upload") ? f.getAtchFilePathNm().substring("/upload".length()) : f.getAtchFilePathNm();
		File target = new File(filePathBlackList(fileStorePath + rel), f.getStrgFileNm() + "." + f.getAtchFileExtnNm()).getAbsoluteFile();
		return !target.exists() || target.delete();
	}


	public static String filePathBlackList(String value) {
		String returnValue = value;
		if (returnValue == null || returnValue.trim().equals("")) {
			return "";
		}

		returnValue = returnValue.replaceAll("\\.\\.", "");

		return returnValue;
	}

	public static String getTimeStamp() {

		String rtnStr = null;

		// 문자열로 변환하기 위한 패턴 설정(년도-월-일 시:분:초:초(자정이후 초))
		String pattern = "yyyyMMddHHmmssSSS";   // HH(24시) — hh 였을 땐 오전·오후 같은 시각 업로드가 같은 이름으로 덮였다

		SimpleDateFormat sdfCurrent = new SimpleDateFormat(pattern, Locale.KOREA);
		Timestamp ts = new Timestamp(System.currentTimeMillis());

		rtnStr = sdfCurrent.format(ts.getTime());

		return rtnStr;
	}
}
