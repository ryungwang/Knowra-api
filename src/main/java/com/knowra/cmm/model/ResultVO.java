package com.knowra.cmm.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ResultVO {

	private int resultCode = 0;

	private String resultMessage = "OK";
	private Map<String, Object> result = new HashMap<String, Object>();

	private PaginationInfo paginationInfo = null;

	public void putPaginationInfo(PaginationInfo paginationInfo) { this.paginationInfo = paginationInfo;};

	public void putResult(String key, Object value) {
		result.put(key, value);
	}

	public Object getResult(String key) {
		return this.result.get(key);
	}
}

