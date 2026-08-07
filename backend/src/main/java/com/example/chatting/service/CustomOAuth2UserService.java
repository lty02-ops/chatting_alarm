package com.example.chatting.service;

import com.example.chatting.entity.AppUser;
import com.example.chatting.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final AppUserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId().toUpperCase();
        Profile profile = profile(provider, oauthUser.getAttributes());

        AppUser user = userRepository.findByProviderAndProviderId(provider, profile.id())
                .orElseGet(() -> createUser(provider, profile));
        user.setEmail(profile.email());
        user.setProfileImageUrl(profile.image());
        userRepository.save(user);
        currentUserService.ensureFriendCode(user);

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("internalUserId", user.getId().toString());
        attributes.put("internalNickname", user.getNickname());
        attributes.put("provider", user.getProvider());
        attributes.put("profileImageUrl", user.getProfileImageUrl());
        return new DefaultOAuth2User(oauthUser.getAuthorities(), attributes, "internalUserId");
    }

    private AppUser createUser(String provider, Profile profile) {
        AppUser user = new AppUser();
        user.setProvider(provider);
        user.setProviderId(profile.id());
        user.setEmail(profile.email());
        user.setProfileImageUrl(profile.image());
        user.setNickname(uniqueNickname(profile.nickname(), provider, profile.id()));
        return user;
    }

    private String uniqueNickname(String requested, String provider, String providerId) {
        String base = requested == null || requested.isBlank() ? provider.toLowerCase() + "사용자" : requested.trim();
        base = base.length() > 30 ? base.substring(0, 30) : base;
        if (!userRepository.existsByNickname(base)) return base;
        String suffix = providerId.replaceAll("[^a-zA-Z0-9]", "");
        suffix = suffix.length() > 6 ? suffix.substring(suffix.length() - 6) : suffix;
        String candidate = base + "_" + suffix;
        int number = 2;
        while (userRepository.existsByNickname(candidate)) candidate = base + "_" + number++;
        return candidate;
    }

    @SuppressWarnings("unchecked")
    private Profile profile(String provider, Map<String, Object> attributes) {
        if ("KAKAO".equals(provider)) {
            Map<String, Object> account = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
            Map<String, Object> properties = (Map<String, Object>) attributes.getOrDefault("properties", Map.of());
            return new Profile(String.valueOf(attributes.get("id")), value(account.get("email")),
                    value(properties.get("nickname")), value(properties.get("profile_image")));
        }
        if ("NAVER".equals(provider)) {
            Map<String, Object> response = (Map<String, Object>) attributes.getOrDefault("response", Map.of());
            return new Profile(value(response.get("id")), value(response.get("email")),
                    value(response.get("nickname")), value(response.get("profile_image")));
        }
        return new Profile(value(attributes.get("sub")), value(attributes.get("email")),
                value(attributes.get("name")), value(attributes.get("picture")));
    }

    private String value(Object value) { return value == null ? null : String.valueOf(value); }
    private record Profile(String id, String email, String nickname, String image) {}
}
