package com.knowra.common.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.service.CommonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CommonController {

    @Autowired
    private CommonService commonService;

    @PostMapping("/api/test")
    public void test(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request.setCharacterEncoding("UTF-8");
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        System.out.println("Received SSV or XML:");
        System.out.println(sb.toString());

        // 넥사크로 SSV 응답 규격
        StringBuilder ssv = new StringBuilder();

        // 플랫폼 헤더 (반드시 포함)
        ssv.append("PlatformType=Java\n");
        ssv.append("ErrorCode=0\n");
        ssv.append("ErrorMsg=Success\n");

        // 데이터셋 정의
        ssv.append("Dataset:output_ds\n");
        ssv.append("id\tname\n");
        ssv.append("1\t홍길동\n");
        ssv.append("2\t김철수\n");

        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print(ssv.toString());
        System.out.println(ssv);
        out.flush();
    }

    @GetMapping("/api/common/getCategoryList")
    public ResultVO getCategoryList() {
        return commonService.getCategoryList();
    }
}
