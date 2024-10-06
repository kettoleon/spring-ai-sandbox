package com.github.kettoleon.llm.sandbox.common.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain customSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(req ->
                        req
//                                .requestMatchers(toStaticResources().atCommonLocations()).permitAll()
//                                .requestMatchers("*.png").permitAll()
//                                .requestMatchers("/login").permitAll()
//                                .requestMatchers("/logout").permitAll()
//                                .requestMatchers("/error").permitAll()
//                                .requestMatchers("/").permitAll()
//                                .requestMatchers("/home").permitAll()
//                                .requestMatchers("/users").hasRole("ADMIN")
//                                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                                .anyRequest().permitAll()
                )
//                .formLogin(login -> login
//                        .loginPage("/login").permitAll()
//                        .loginProcessingUrl("/login").permitAll()
//                )
//                .userDetailsService(userDetailsService)
//                .logout(logout -> logout.permitAll().logoutUrl("/logout").logoutSuccessUrl("/"))
        ;

        return http.build();

    }

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }
}
