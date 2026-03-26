package com.knowra.user.service;

import com.knowra.cmm.service.RedisApiService;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final TblUserRepository tblUserRepository;

    @PersistenceContext
    private EntityManager em;

    private final RedisApiService redisApiService;

    public Long getUserSn(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + loginId))
                .getUserSn();
    }

    public TblUser getUser(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + loginId));
    }

}
