package com.encore.logeat.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

@PropertySource("classpath:jwt.yml")
@Service
public class JwtTokenProvider {

	private final String secretKey;
	private final long expirationHours;
	private final String issuer;

	public JwtTokenProvider(@Value("${secret-key}") String secretKey,
		@Value("${expiration-hours}") long expirationHours, @Value("${issuer}") String issuer) {
		this.secretKey = secretKey;
		this.expirationHours = expirationHours;
		this.issuer = issuer;
	}

	public String createToken(String userSpecification) {

		return Jwts.builder()
			.signWith(SignatureAlgorithm.HS256, secretKey.getBytes())
			.setSubject(userSpecification)
			.setIssuer(issuer)
			.setIssuedAt(Timestamp.valueOf(LocalDateTime.now()))
			.setExpiration(Date.from(Instant.now().plus(expirationHours, ChronoUnit.HOURS)))
			.compact();
	}

	public String validateTokenAndGetSubject(String token) {
		Jws<Claims> claimsJws = Jwts.parser().setSigningKey(secretKey.getBytes())
			.parseClaimsJws(token);
		if(!claimsJws.getBody().getExpiration().before(new Date())) {
			return claimsJws.getBody().getSubject();
		}
		return null;
	}
}