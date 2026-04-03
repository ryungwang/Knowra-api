package com.knowra.common.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.repository.TblComCategoryRepository;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.user.entity.QTblUserFlwr;
import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUserFlwr;
import com.knowra.user.repository.TblUserRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
public class CommonService {

    private static final Logger logger = LoggerFactory.getLogger(CommonService.class);

    private final EntityManager em;

    @Value("${Globals.pageSize}")
    private int pageSize;

    @Value("${Globals.pageUnit}")
    private int pageUnit;

    private final FileUtil fileUtil;
    private final TblComFileRepository tblComFileRepository;
    private final TblComCategoryRepository tblComCategoryRepository;
    private final TblUserRepository tblUserRepository;
//    private final TblBbsRepository tblBbsRepository;


//    public ResultVO getBnrList(Map<String, Object> params) {
//        ResultVO resultVO = new ResultVO();
//
//        try {
//            QTblBnr qTblBnr = QTblBnr.tblBnr;
//            QTblComFile qTblComFile = QTblComFile.tblComFile;
//
//            JPAQueryFactory q = new JPAQueryFactory(em);
//            BooleanBuilder builder = new BooleanBuilder();
//            builder.and(qTblBnr.actvtnYn.eq("Y"))
//                .and(
//                    Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", qTblBnr.startDt).loe(
//                    Expressions.stringTemplate("DATE_FORMAT(NOW(), '%Y-%m-%d')"))
//                )
//                .and(Expressions.stringTemplate("DATE_FORMAT({0}, '%Y-%m-%d')", qTblBnr.endDt).goe(
//                        Expressions.stringTemplate("DATE_FORMAT(NOW(), '%Y-%m-%d')")));
//
//            List<BnrDto> bnrList = q
//                    .select(
//                        Projections.constructor(
//                            BnrDto.class,
//                            qTblBnr,
//                            qTblComFile
//                        )
//                    ).from(qTblBnr)
//                    .join(qTblComFile)
//                    .on(
//                        qTblComFile.psnTblSn.eq(
//                            Expressions.stringTemplate("CONCAT('bnr_', {0})", qTblBnr.bnrSn)
//                        )
//                    )
//                    .where(builder)
//                    .fetch();
//
//            resultVO.putResult("bnrList", bnrList);
//            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
//        }catch (Exception e){
//            e.printStackTrace();
//            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
//            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
//        }
//
//        return resultVO;
//    }
//
//    public ResultVO getMenuList() {
//        ResultVO resultVO = new ResultVO();
//
//        try {
//            QTblMenu qTblMenu = QTblMenu.tblMenu;
//            JPAQueryFactory q = new JPAQueryFactory(em);
//            BooleanBuilder builder = new BooleanBuilder();
//            builder.and(qTblMenu.actvtnYn.eq("Y"));
//
//            List<TblMenu> menuList = q
//                    .selectFrom(qTblMenu)
//                    .where(builder)
//                    .fetch();
//
//            List<Map<String, Object>> m = new ArrayList<>();
//
//            List<String> menuTypes = Arrays.asList("C", "S", "M", "B", "P");
//
//            for (String type : menuTypes) {
//                List<TblMenu> filtered = menuList.stream()
//                        .filter(menu -> type.equals(menu.getMenuType()))
//                        .collect(Collectors.toList());
//
//                Map<String, Object> map = new HashMap<>();
//                map.put("type", type);
//                map.put("items", filtered);
//
//                m.add(map);
//            }
//
//
//            resultVO.putResult("m", m);
//            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
//        }catch (Exception e){
//            e.printStackTrace();
//            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
//            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
//        }
//
//        return resultVO;
//    }
//
//    public ResultVO getBbsList(Map<String, Object> params) {
//        ResultVO resultVO = new ResultVO();
//
//        try {
//            QTblBbs qTblBbs = QTblBbs.tblBbs;
//            JPAQueryFactory q = new JPAQueryFactory(em);
//            BooleanBuilder builder = new BooleanBuilder();
//            builder.and(qTblBbs.actvtnYn.eq("Y"));
//
//            List<TblBbs> tblBbs = q
//                    .selectFrom(qTblBbs)
//                    .where(builder)
//                    .orderBy(qTblBbs.frstCrtDt.desc())
//                    .fetch();
//
//            resultVO.putResult("bbsList", tblBbs);
//            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
//        }catch (Exception e){
//            e.printStackTrace();
//            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
//            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
//        }
//
//        return resultVO;
//    }
//
//    public ResultVO getPstList(Map<String, Object> params) {
//        ResultVO resultVO = new ResultVO();
//        PaginationInfo paginationInfo = new PaginationInfo();
//
//        try {
//            QTblPst qTblPst = QTblPst.tblPst;
//            QTblUser qTblUser = QTblUser.tblUser;
//            QTblComFile qTblComFile = QTblComFile.tblComFile;
//
//            JPAQueryFactory q = new JPAQueryFactory(em);
//            BooleanBuilder builder = new BooleanBuilder();
//            builder.and(qTblPst.bbsSn.eq(Long.valueOf(params.get("bbsSn").toString())).and(qTblPst.actvtnYn.eq("Y")));
//
//            if (!StringUtils.isEmpty(params.get("pageIndex"))) {
//                paginationInfo.setCurrentPageNo(Integer.parseInt(params.get("pageIndex").toString()));
//            }
//
//            if (!StringUtils.isEmpty(params.get("pageSize"))) {
//                paginationInfo.setPageSize(Integer.parseInt(params.get("pageSize").toString()));
//            }else{
//                paginationInfo.setPageSize(pageSize);
//            }
//
//            if (!StringUtils.isEmpty(params.get("pageUnit"))) {
//                paginationInfo.setRecordCountPerPage(Integer.parseInt(params.get("pageUnit").toString()));
//            }else{
//                paginationInfo.setRecordCountPerPage(pageUnit);
//            }
//
//            if(!StringUtils.isEmpty(params.get("searchVal"))){
//                if (!StringUtils.isEmpty(params.get("searchType"))) {
//                    if(params.get("searchType").equals("pstTtl")){
//                        builder.and(qTblPst.pstTtl.contains(params.get("searchVal").toString()));
//                    }else if(params.get("searchType").equals("pstCn")){
//                        builder.and(qTblPst.pstCn.contains(params.get("searchVal").toString()));
//                    }
//                }else{
//                    builder.and(
//                        qTblPst.pstTtl.contains(params.get("searchVal").toString()))
//                        .or(qTblPst.pstCn.contains(params.get("searchVal").toString())
//                    );
//                }
//            }
//
//            JPQLQuery<Long> fileCnt = JPAExpressions
//                    .select(qTblComFile.count())
//                    .from(qTblComFile)
//                    .where(
//                            qTblComFile.psnTblSn.eq(
//                                    Expressions.stringTemplate(
//                                            "CONCAT('pst_', {0})", qTblPst.pstSn
//                                    )
//                            )
//                    );
//
//            List<PstDto> tblPst = q
//                    .select(
//                        Projections.constructor(
//                            PstDto.class,
//                            qTblPst,
//                            qTblUser,
//                            fileCnt
//                        )
//                    ).from(qTblPst)
//                    .join(qTblUser)
//                    .on(qTblPst.creatrSn.eq(qTblUser.userSn))
//                    .where(builder)
//                    .offset(paginationInfo.getFirstRecordIndex())
//                    .limit(paginationInfo.getRecordCountPerPage())
//                    .orderBy(qTblPst.frstCrtDt.desc())
//                    .fetch();
//
//
//            Long totCnt = q.select(qTblPst.count())
//                    .from(qTblPst)
//                    .join(qTblUser)
//                    .on(qTblPst.creatrSn.eq(qTblUser.userSn))
//                    .where(builder)
//                    .fetchOne();
//            if(totCnt == null) totCnt = 0L;
//            paginationInfo.setTotalRecordCount(totCnt.intValue());
//
//            resultVO.putResult("pst", tblPst);
//            resultVO.putPaginationInfo(paginationInfo);
//            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
//        }catch (Exception e){
//            e.printStackTrace();
//            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
//            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
//        }
//
//        return resultVO;
//    }

    public ResultVO setComFileDel(TblComFile tblComFile) {
        ResultVO resultVO = new ResultVO();

        try {
            boolean isDelete = fileUtil.deleteFile(new String[]{tblComFile.getStrgFileNm() + "." + tblComFile.getAtchFileExtnNm()}, tblComFile.getAtchFilePathNm());
            if(isDelete){
                tblComFileRepository.delete(tblComFile);
            }else{
                throw new NullPointerException();
            }

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setCkEditorFiles(List<MultipartFile> files) {
        ResultVO resultVO = new ResultVO();
        List<TblComFile> fileList = new ArrayList<>();
        try{
            if(files != null){
                fileList = fileUtil.devFilesInf(
                    files,
                    "/ckeditor/",
                    "ckeditor",
                    0
                );
            }

            resultVO.putResult("files", fileList);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        }catch (IOException e) {
            resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
        }
        return resultVO;
    }

    public ResultVO getCategoryList() {
        ResultVO resultVO = new ResultVO();

        try {
            resultVO.putResult("categoryList", tblComCategoryRepository.findAllByActvtnYn("Y"));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }
}