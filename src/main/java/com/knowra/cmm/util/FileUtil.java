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

@Component
public class FileUtil {

    public static final int BUFF_SIZE = 2048;

	@Value("${File.Store.Path}")
	private String fileStorePath;

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
			int index = orginFileName.lastIndexOf(".");
			String fileExt = orginFileName.substring(index + 1);
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
		int index = orginFileName.lastIndexOf(".");
		String fileExt = orginFileName.substring(index + 1);
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
		int index = orginFileName.lastIndexOf(".");
		String fileExt = orginFileName.substring(index + 1);
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
