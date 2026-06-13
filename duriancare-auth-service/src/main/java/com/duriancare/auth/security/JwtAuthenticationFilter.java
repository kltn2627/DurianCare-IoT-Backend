package com.duriancare.auth.security;

import com.duriancare.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RevokedTokenService revokedTokenService;

    public JwtAuthenticationFilter(JwtService jwtService, RevokedTokenService revokedTokenService) {
        this.jwtService = jwtService;
        this.revokedTokenService = revokedTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parseAccessToken(authorization.substring(7));
                AuthenticatedUser principal = jwtService.toAuthenticatedUser(claims);
                if (revokedTokenService.isRevoked(principal.tokenId())) {
                    throw new InvalidTokenException("Token has been revoked");
                }
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException exception) {
                SecurityContextHolder.clearContext();
                request.setAttribute("authenticationError", exception.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
