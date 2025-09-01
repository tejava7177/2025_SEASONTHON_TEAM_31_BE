package com.cloudtone31.auth.controller;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    // POST /v1/auth/kakao/logout
    @PostMapping("/v1/auth/kakao/logout")
    public ResponseEntity<Void> kakaoLogout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler()
                .logout(request, response, auth); // 세션 무효화 + SecurityContext 비움 + JSESSIONID 삭제
        return ResponseEntity.noContent().build(); // 204
    }
}
