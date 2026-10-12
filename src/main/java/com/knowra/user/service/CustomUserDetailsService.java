package com.knowra.user.service;

import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final TblUserRepository tblUserRepository;

    /**
     * 로그인 폼은 <b>아이디(loginId)</b>를 보낸다. 예전엔 닉네임으로만 찾아서 닉네임 ≠ 아이디인
     * 계정(가입 때 이메일로 가입하고 닉네임을 따로 정한 모든 사람)은 비밀번호가 맞아도
     * "사용자를 찾을 수 없습니다"로 튕겼다. 아이디로 먼저 찾고, 닉네임으로 로그인하던
     * 기존 습관도 끊지 않도록 닉네임을 뒤에 둔다.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        TblUser tblUser = tblUserRepository.findByLoginId(username)
                .or(() -> tblUserRepository.findByNickName(username))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return new User(
                tblUser.getLoginId(),
                tblUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
