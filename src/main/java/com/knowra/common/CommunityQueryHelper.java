package com.knowra.common;

import com.knowra.community.entity.QTblComm;
import com.knowra.community.entity.QTblCommMbr;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;

/**
 * 커뮤니티 관련 공통 QueryDSL 조건 헬퍼
 */
public class CommunityQueryHelper {

    private CommunityQueryHelper() {}

    /**
     * 커뮤니티 접근 가능 조건
     * <p>공개 커뮤니티(public / anonymous) 이거나 viewerSn 유저가 ACTIVE 멤버인 경우에만 접근 허용</p>
     *
     * @param qComm     QTblComm 인스턴스
     * @param viewerSn  조회자 SN (비회원이면 null → 공개 커뮤니티만)
     */
    public static BooleanExpression accessCondition(QTblComm qComm, Long viewerSn) {
        BooleanExpression isPublic = qComm.prvcyStng.in("public", "anonymous");
        if (viewerSn == null) return isPublic;
        QTblCommMbr qMbr = QTblCommMbr.tblCommMbr;
        BooleanExpression isMember = JPAExpressions.selectOne().from(qMbr)
                .where(qMbr.commSn.eq(qComm.commSn)
                        .and(qMbr.userSn.eq(viewerSn))
                        .and(qMbr.stat.eq("ACTIVE")))
                .exists();
        return isPublic.or(isMember);
    }
}
