package com.lightweightai.web.config;

import com.lightweightai.user.UserService;
import com.lightweightai.web.service.AuthSessionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AuthConfig.class);

    private final AuthSessionService authSessionService;
    private final UserService userService;

    public AuthConfig(AuthSessionService authSessionService, UserService userService) {
        this.authSessionService = authSessionService;
        this.userService = userService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(authSessionService))
                .addPathPatterns("/**")
                .excludePathPatterns("/ws/**");
    }

    @PostConstruct
    public void seedAdmin() {
        try {
            PasswordEncoder encoder = new BCryptPasswordEncoder();
            userService.seedAdminIfNeeded(encoder.encode("admin123"));
        } catch (Exception e) {
            logger.warn("Failed to seed admin user: {}", e.getMessage());
        }
    }
}
