package com.cloudtone31.user.controller;

import com.cloudtone31.global.api.ApiResponse;
import com.cloudtone31.user.domain.User;
import com.cloudtone31.user.repo.UserRepository;
import com.cloudtone31.user.service.UserService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
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
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;

    /** 현재 로그인한 사용자 정보 조회 : GET /users/me */
    @GetMapping("/users/me")
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

    /** 회원탈퇴 : DELETE /users/delete */
    @DeleteMapping("/users/delete")
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

        userRepository.delete(user);

        // 세션 & 보안 컨텍스트 정리 + JSESSIONID 만료
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        ResponseCookie expired = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());

        return ResponseEntity.ok(ApiResponse.ok(null, "회원탈퇴가 완료되었습니다"));
    }

    /** 닉네임 변경 : PUT /v1/auth/kakao/nickname */
    @PutMapping("/v1/auth/kakao/nickname")   // ← 이 클래스가 @RequestMapping("/users") 라면 최종 경로가 /users/v1/auth/kakao/nickname 이 됩니다.
    public ResponseEntity<ApiResponse<?>> updateNickname(
            @AuthenticationPrincipal OAuth2User principal,
            @Valid @RequestBody NicknameReq req
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("인증이 필요합니다."));
        }

        Long kakaoId = extractKakaoId(principal.getAttributes());
        if (kakaoId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("프로필 정보가 올바르지 않습니다."));
        }

        User updated = userService.updateNickname(kakaoId, req.getNickname());
        NicknameRes body = new NicknameRes(updated.getId(), updated.getNickname());
        return ResponseEntity.ok(ApiResponse.ok(body, "닉네임이 변경되었습니다."));
    }

    // ------ 내부 유틸 & DTO ------

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
    @Getter
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
                    u.getNickname(),       // name은 우선 nickname과 동일
                    u.getNickname(),
                    created,
                    updated
            );
        }
    }

    // 요청 DTO
    @Getter
    static class NicknameReq {
        @NotBlank(message = "닉네임은 비워둘 수 없습니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9가-힣_\\-]+$", message = "닉네임은 한글/영문/숫자/_/-만 사용할 수 있습니다.")
        private String nickname;
    }

    // 응답 DTO
    @Getter
    static class NicknameRes {
        private final Long id;
        private final String nickname;
        public NicknameRes(Long id, String nickname) {
            this.id = id;
            this.nickname = nickname;
        }
    }
}