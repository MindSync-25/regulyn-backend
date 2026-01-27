package com.regulyn.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenService {
  
  private final SecretKey signingKey;
  private final long expirationMs;
  
  public JwtTokenService(
      @Value("${jwt.secret:change-me-in-production-this-must-be-at-least-256-bits-long}") String secret,
      @Value("${jwt.expiration-ms:86400000}") long expirationMs) { // 24 hours default
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
  }
  
  public String createToken(UUID userId, UUID tenantId, List<String> roles) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + expirationMs);
    
    return Jwts.builder()
        .subject(userId.toString())
        .claim("tid", tenantId.toString())
        .claim("roles", roles)
        .issuedAt(now)
        .expiration(expiration)
        .signWith(signingKey, SignatureAlgorithm.HS256)
        .compact();
  }
  
  public Claims validateAndExtractClaims(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
  
  public boolean isTokenValid(String token) {
    try {
      Claims claims = validateAndExtractClaims(token);
      return !claims.getExpiration().before(new Date());
    } catch (Exception e) {
      return false;
    }
  }
  
  public UUID getTenantId(Claims claims) {
    String tid = claims.get("tid", String.class);
    return tid != null ? UUID.fromString(tid) : null;
  }
  
  public UUID getUserId(Claims claims) {
    String sub = claims.getSubject();
    return sub != null ? UUID.fromString(sub) : null;
  }
  
  @SuppressWarnings("unchecked")
  public List<String> getRoles(Claims claims) {
    Object roles = claims.get("roles");
    if (roles instanceof List) {
      return (List<String>) roles;
    }
    return List.of();
  }
}
