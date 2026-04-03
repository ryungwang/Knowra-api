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
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    @Autowired
    private CommonService commonService;

    @GetMapping("/getCategoryList")
    public ResultVO getCategoryList() {
        return commonService.getCategoryList();
    }

    @PostMapping("/uploadImage")
    public ResultVO uploadImage(@RequestParam(required = false) MultipartFile image) {
        return commonService.uploadImage(image);
    }
}
