package com.cloudtone31.user.controller;

import com.cloudtone31.global.api.ApiResponse;
import com.cloudtone31.user.domain.User;
import com.cloudtone31.user.repo.UserRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * 현재 로그인한 사용자 정보 조회
     * GET /users/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> me(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("인증이 필요합니다."));
        }

        Long kakaoId = extractKakaoId(principal.getAttributes());
        if (kakaoId == null) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.fail("OAuth2 attributes에서 카카오 ID를 찾을 수 없습니다."));
        }

        User user = userRepository.findByKakaoId(kakaoId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.fail("사용자를 찾을 수 없습니다."));
        }

        UserMeDto dto = UserMeDto.of(user);
        return ResponseEntity.ok(ApiResponse.ok(dto, "사용자 정보를 성공적으로 조회했습니다."));
    }

    /**
     * 회원탈퇴
     * DELETE /users/delete
     */
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<?>> deleteMe(@AuthenticationPrincipal OAuth2User principal,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response,
                                                   Authentication authentication) {
        if (principal == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("인증이 필요합니다."));
        }

        Long kakaoId = extractKakaoId(principal.getAttributes());
        if (kakaoId == null) {
            return ResponseEntity.status(400).body(ApiResponse.fail("프로필 정보가 올바르지 않습니다."));
        }

        User user = userRepository.findByKakaoId(kakaoId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(ApiResponse.fail("사용자를 찾을 수 없습니다."));
        }

        // 실제 삭제 (soft delete가 필요하면 status 필드 플래그로 변경)
        userRepository.delete(user);

        // 보안 컨텍스트 & 세션 정리 + JSESSIONID 만료
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        ResponseCookie expired = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());

        return ResponseEntity.ok(ApiResponse.ok("회원탈퇴가 완료되었습니다"));
    }

    // ----- 내부 유틸 -----

    /** kakaoId, id, sub 등 어떤 키로 와도 최대로 잡아줌 */
    private Long extractKakaoId(Map<String, Object> attr) {
        for (String k : List.of("kakaoId", "id", "sub")) {
            Object v = attr.get(k);
            if (v instanceof Number n) return n.longValue();
            if (v instanceof String s && !s.isBlank()) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignore) {}
            }
        }
        return null;
    }

    // ----- 응답 DTO (snake_case) -----

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class UserMeDto {
        private final Long id;

        @JsonProperty("kakao_id")
        private final String kakaoId;

        private final String name;      // 없으면 nickname과 동일값
        private final String nickname;

        @JsonProperty("created_at")
        private final String createdAt; // ISO-8601(UTC)

        @JsonProperty("updated_at")
        private final String updatedAt;

        private UserMeDto(Long id, String kakaoId, String name, String nickname, String createdAt, String updatedAt) {
            this.id = id;
            this.kakaoId = kakaoId;
            this.name = name;
            this.nickname = nickname;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        static UserMeDto of(User u) {
            ZoneId sys = ZoneId.systemDefault();
            String created = u.getCreatedAt() == null ? null : u.getCreatedAt().atZone(sys).toInstant().toString();
            String updated = u.getUpdatedAt() == null ? null : u.getUpdatedAt().atZone(sys).toInstant().toString();

            return new UserMeDto(
                    u.getId(),
                    u.getKakaoId() == null ? null : String.valueOf(u.getKakaoId()),
                    u.getNickname(),       // name은 우선 nickname과 동일하게
                    u.getNickname(),
                    created,
                    updated
            );
        }

        // getters (record가 아니므로 필요시 lombok @Getter 사용 가능)
        public Long getId() { return id; }
        public String getKakaoId() { return kakaoId; }
        public String getName() { return name; }
        public String getNickname() { return nickname; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
    }
}