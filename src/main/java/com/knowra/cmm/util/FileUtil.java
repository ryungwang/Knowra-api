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

	/**
	 * 첨부파일 저장
	 * @param files
	 * @return
	 * @throws Exception
	 */
	public List<TblComFile> devFilesInf(List<MultipartFile> files, String subPath, String psnTblSn, long fileCnt) throws IOException {
		long fileKey = fileCnt;
		String storePathString = fileStorePath + subPath;
		String relativePathString = "/upload" + subPath;

		File saveFolder = new File(filePathBlackList(storePathString));
		if (!saveFolder.exists() || saveFolder.isFile()) {
			saveFolder.mkdirs();
		}

		String filePath = "";
		List<TblComFile> result  = new ArrayList<>();

		for(MultipartFile f : files){
			TblComFile comFile = new TblComFile();
			String orginFileName = f.getOriginalFilename();
			String fileExt = requireAllowedExtension(orginFileName);
			String newName = getTimeStamp() + "_" + fileKey;
			long _size = f.getSize();
			if (!"".equals(orginFileName)) {
				filePath = storePathString + File.separator + newName + "." + fileExt;
				f.transferTo(new File(filePathBlackList(filePath)).getAbsoluteFile());
			}
			comFile.setStrgFileNm(newName);
			comFile.setAtchFileNm(orginFileName);
			comFile.setAtchFilePathNm(relativePathString);
			comFile.setAtchFileSz(_size);
			comFile.setAtchFileExtnNm(fileExt);
			comFile.setPsnTblSn(psnTblSn);

			fileKey++;

			result.add(comFile);
		}

		return result;
	}

	public TblComFile devFileInf(MultipartFile file, String subPath, String psnTblSn) throws IOException {
		String storePathString = fileStorePath + subPath;
		String relativePathString = "/upload" + subPath;

		File saveFolder = new File(filePathBlackList(storePathString));
		if (!saveFolder.exists() || saveFolder.isFile()) {
			saveFolder.mkdirs();
		}

		String filePath = "";
		TblComFile result  = new TblComFile();

		String orginFileName = file.getOriginalFilename();
		String fileExt = requireAllowedExtension(orginFileName);
		String newName = getTimeStamp() + "_" + 0;
		long _size = file.getSize();
		if (!"".equals(orginFileName)) {
			filePath = storePathString + File.separator + newName + "." + fileExt;
			file.transferTo(new File(filePathBlackList(filePath)).getAbsoluteFile());
		}
		result.setStrgFileNm(newName);
		result.setAtchFileNm(orginFileName);
		result.setAtchFilePathNm(relativePathString);
		result.setAtchFileSz(_size);
		result.setAtchFileExtnNm(fileExt);
		result.setPsnTblSn(psnTblSn);

		return result;
	}

	public String devImageUpload(MultipartFile file, String subPath) throws IOException {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.KOREA);
		Date date = new Date();

		String storePathString = fileStorePath + subPath + sdf.format(date);
		String relativePathString = "/upload" + subPath + sdf.format(date);

		File saveFolder = new File(filePathBlackList(storePathString));
		if (!saveFolder.exists() || saveFolder.isFile()) {
			saveFolder.mkdirs();
		}

		String filePath = "";

		String orginFileName = file.getOriginalFilename();
		String fileExt = requireAllowedExtension(orginFileName);
		String newName = getTimeStamp() + "_" + 0;
		if (!"".equals(orginFileName)) {
			filePath = storePathString + File.separator + newName + "." + fileExt;
			file.transferTo(new File(filePathBlackList(filePath)).getAbsoluteFile());
		}
		return relativePathString + "/" + newName + "." + fileExt;
	}

	public boolean deleteFile(String[] fileNames, String filePath) {
		boolean returnFlag = true;
		if(fileNames != null && fileNames.length > 0){
			for(int i = 0 ; i < fileNames.length; i++){
				File orifile = new File(filePath + "/" + fileNames[i]);
				if(orifile.exists()){
					boolean deleteFlag = orifile.delete();
					if(!deleteFlag){
						returnFlag = false;
						break;
					}
				}
			}
		}

		return returnFlag;
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
		String pattern = "yyyyMMddhhmmssSSS";

		SimpleDateFormat sdfCurrent = new SimpleDateFormat(pattern, Locale.KOREA);
		Timestamp ts = new Timestamp(System.currentTimeMillis());

		rtnStr = sdfCurrent.format(ts.getTime());

		return rtnStr;
	}
}
