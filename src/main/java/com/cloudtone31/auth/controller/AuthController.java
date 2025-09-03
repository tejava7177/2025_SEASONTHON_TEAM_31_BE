package com.cloudtone31.auth.controller;


import com.cloudtone31.user.domain.User;
import com.cloudtone31.user.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RequestMapping("/v1/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        // 1) kakaoId, id, sub 순서로 시도
        Long kakaoId = getLongAttr(principal, "kakaoId", "id", "sub");
        if (kakaoId == null) {
            // 어떤 키/값이 들어왔는지 한 번에 보자 (디버깅용)
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Cannot resolve Kakao id from OAuth2 attributes",
                    "expectedKeys", List.of("kakaoId", "id", "sub"),
                    "actualAttributes", principal.getAttributes()
            ));
        }

        var user = userRepository.findByKakaoId(kakaoId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body("user not found");

        record MeDto(Long id, String nickname, String email, String profileImage, LocalDateTime lastLoginAt) {}
        return ResponseEntity.ok(new MeDto(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImage(),
                user.getLastLoginAt()
        ));
    }

    @SuppressWarnings("unchecked")
    private Long getLongAttr(OAuth2User p, String... keys) {
        for (String k : keys) {
            Object v = p.getAttributes().get(k);
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s && !s.isBlank()) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignore) {}
            }
        }
        return null;
    }

    @PostMapping("/kakao/logout")
    public ResponseEntity<Void> kakaoLogout(HttpServletRequest request,
                                            HttpServletResponse response,
                                            Authentication authentication) {

        // ★ 새 세션 만들지 않도록 false
        var session = request.getSession(false);

        // 인증/세션 없으면 401 반환 (Postman에서 쿠키 빠뜨린 경우 여기로 옴)
        if (authentication == null || session == null) {
            return ResponseEntity.status(401).build();
        }

        // SecurityContext + 세션 함께 정리
        new SecurityContextLogoutHandler().logout(request, response, authentication);

        // 브라우저 쿠키 제거 지시(중요: path="/" 유지)
        ResponseCookie expired = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());

        // 204 No Content
        return ResponseEntity.noContent().build();
    }
}
