package com.cumt.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cumt.inventory.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.io.IOException;

/**
 * 安全配置：
 * <ul>
 *   <li>页面：表单登录（/login），会话保持；CSRF 防护开启（Thymeleaf 表单自动携带 _csrf）。</li>
 *   <li>REST 接口 /api/**：同样需要登录（会话 Cookie 或 HTTP Basic），关闭 CSRF 便于脚本/压测调用；
 *       未登录返回 401 JSON、无权限返回 403 JSON，而不是跳转登录页。</li>
 *   <li>角色：URL 级（/users/** 仅 ADMIN）+ 方法级（@PreAuthorize 控制基础资料增删改）。</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout"))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)));
        return http.build();
    }

    private static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    /** 未登录：接口返回 401 JSON，页面跳转登录页 */
    private AuthenticationEntryPoint entryPoint(ObjectMapper om) {
        LoginUrlAuthenticationEntryPoint login = new LoginUrlAuthenticationEntryPoint("/login");
        return (request, response, authException) -> {
            if (isApi(request)) {
                writeJson(response, om, HttpServletResponse.SC_UNAUTHORIZED, ApiResponse.fail("UNAUTHORIZED", "请先登录"));
            } else {
                login.commence(request, response, authException);
            }
        };
    }

    /** 无权限：接口返回 403 JSON，页面转发到 403 提示页 */
    private AccessDeniedHandler accessDeniedHandler(ObjectMapper om) {
        return (request, response, ex) -> {
            if (isApi(request)) {
                writeJson(response, om, HttpServletResponse.SC_FORBIDDEN, ApiResponse.fail("FORBIDDEN", "没有操作权限"));
            } else {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                request.getRequestDispatcher("/403").forward(request, response);
            }
        };
    }

    private static void writeJson(HttpServletResponse response, ObjectMapper om, int status, Object body)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        om.writeValue(response.getOutputStream(), body);
    }
}
