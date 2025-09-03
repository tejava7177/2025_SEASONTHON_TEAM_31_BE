//package com.cloudtone31.user.controller;
//
//
//import com.cloudtone31.user.domain.User;
//import com.cloudtone31.user.repo.UserRepository;
//import lombok.AllArgsConstructor;
//import lombok.Getter;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.security.oauth2.core.user.OAuth2User;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import com.fasterxml.jackson.annotation.JsonInclude;
//import com.fasterxml.jackson.annotation.JsonProperty;
//
//import java.time.ZoneId;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/users")
//@RequiredArgsConstructor
//public class UserMeController {
//
//    private final UserRepository userRepository;
//
//    @GetMapping("/me")
//    public ResponseEntity<ApiResponse<UserMeDto>> me(@AuthenticationPrincipal OAuth2User principal) {
//        if (principal == null) {
//            return ResponseEntity.status(401).body(ApiResponse.fail("인증이 필요합니다."));
//        }
//
//        Long kakaoId = extractKakaoId(principal.getAttributes());
//        if (kakaoId == null) {
//            return ResponseEntity.internalServerError()
//                    .body(ApiResponse.fail("OAuth2 attributes에서 카카오 ID를 찾을 수 없습니다."));
//        }
//
//        User user = userRepository.findByKakaoId(kakaoId).orElse(null);
//        if (user == null) {
//            return ResponseEntity.status(404).body(ApiResponse.fail("사용자를 찾을 수 없습니다."));
//        }
//
//        UserMeDto dto = UserMeDto.of(user);
//        return ResponseEntity.ok(ApiResponse.ok(dto, "사용자 정보를 성공적으로 조회했습니다."));
//    }
//
//    // kakaoId, id, sub 등 어떤 키가 와도 최대한 잡아줌
//    private Long extractKakaoId(Map<String, Object> attr) {
//        for (String k : List.of("kakaoId", "id", "sub")) {
//            Object v = attr.get(k);
//            if (v instanceof Number n) return n.longValue();
//            if (v instanceof String s && !s.isBlank()) {
//                try { return Long.parseLong(s); } catch (NumberFormatException ignore) {}
//            }
//        }
//        return null;
//    }
//
//    // --- 공용 응답 래퍼 ---
//    @Getter
//    @JsonInclude(JsonInclude.Include.NON_NULL)
//    @AllArgsConstructor
//    static class ApiResponse<T> {
//        private boolean success;
//        private T data;
//        private String message;
//
//        static <T> ApiResponse<T> ok(T data, String message) {
//            return new ApiResponse<>(true, data, message);
//        }
//        static <T> ApiResponse<T> fail(String message) {
//            return new ApiResponse<>(false, null, message);
//        }
//    }
//
//    // --- 응답 DTO (snake_case) ---
//    @Getter
//    @JsonInclude(JsonInclude.Include.NON_NULL)
//    static class UserMeDto {
//        private final Long id;
//
//        @JsonProperty("kakao_id")
//        private final String kakaoId;
//
//        private final String name;      // 없으면 nickname과 동일하게 반환
//        private final String nickname;
//
//        @JsonProperty("created_at")
//        private final String createdAt; // ISO-8601 (UTC)
//
//        @JsonProperty("updated_at")
//        private final String updatedAt;
//
//        private UserMeDto(Long id, String kakaoId, String name, String nickname, String createdAt, String updatedAt) {
//            this.id = id;
//            this.kakaoId = kakaoId;
//            this.name = name;
//            this.nickname = nickname;
//            this.createdAt = createdAt;
//            this.updatedAt = updatedAt;
//        }
//
//        static UserMeDto of(User u) {
//            // DB가 LocalDateTime이면 UTC 문자열로 변환
//            ZoneId sys = ZoneId.systemDefault();
//            String created = u.getCreatedAt() == null ? null : u.getCreatedAt().atZone(sys).toInstant().toString();
//            String updated = u.getUpdatedAt() == null ? null : u.getUpdatedAt().atZone(sys).toInstant().toString();
//
//            return new UserMeDto(
//                    u.getId(),
//                    u.getKakaoId() == null ? null : String.valueOf(u.getKakaoId()),
//                    u.getNickname(),                 // name: 우선 nickname과 동일하게
//                    u.getNickname(),
//                    created,
//                    updated
//            );
//        }
//    }
//}