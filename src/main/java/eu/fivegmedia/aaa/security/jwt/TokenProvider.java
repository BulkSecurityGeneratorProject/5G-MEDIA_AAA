package eu.fivegmedia.aaa.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import eu.fivegmedia.aaa.domain.CatalogToken;
import eu.fivegmedia.aaa.domain.CatalogUser;
import eu.fivegmedia.aaa.repository.CatalogTokenRepository;
import eu.fivegmedia.aaa.repository.CatalogUserRepository;
import eu.fivegmedia.aaa.service.CatalogTokenService;
import eu.fivegmedia.aaa.service.dto.CatalogTokenDTO;
import io.github.jhipster.config.JHipsterProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

@Component
public class TokenProvider {

    private final Logger log = LoggerFactory.getLogger(TokenProvider.class);

    private static final String AUTHORITIES_KEY = "auth";

    private final Base64.Encoder encoder = Base64.getEncoder();

    private String secretKey;

    private long tokenValidityInMilliseconds;

    private long tokenValidityInMillisecondsForRememberMe;

    private final JHipsterProperties jHipsterProperties;

    @Autowired
    private CatalogUserRepository catalogUserRepository;

    @Autowired
    private CatalogTokenRepository catalogTokenRepository;

    
    @Autowired
    private CatalogTokenService catalogTokenService;

    public TokenProvider(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @PostConstruct
    public void init() {
        this.secretKey = encoder.encodeToString(jHipsterProperties.getSecurity().getAuthentication().getJwt()
            .getSecret().getBytes(StandardCharsets.UTF_8));

        this.tokenValidityInMilliseconds =
            1000 * jHipsterProperties.getSecurity().getAuthentication().getJwt().getTokenValidityInSeconds();
        this.tokenValidityInMillisecondsForRememberMe =
            1000 * jHipsterProperties.getSecurity().getAuthentication().getJwt()
                .getTokenValidityInSecondsForRememberMe();
    }
    
    
    public String createToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date validity;
        if (rememberMe) {
            validity = new Date(now + this.tokenValidityInMillisecondsForRememberMe);
        } else {
            validity = new Date(now + this.tokenValidityInMilliseconds);
        }

        String token = Jwts.builder()
            .setSubject(authentication.getName())
            .claim(AUTHORITIES_KEY, authorities)
            .signWith(SignatureAlgorithm.HS512, secretKey)
            .setExpiration(validity)
            .compact();
        
        if(catalogUserRepository != null && catalogTokenService != null) {
	        CatalogUser catalogUser = catalogUserRepository.findByUsername(authentication.getName());
	        if(catalogUser != null) {
	        		CatalogTokenDTO catalogTokenDTO = new CatalogTokenDTO();
	        		catalogTokenDTO.setCatalogUserId(catalogUser.getId());
	        		catalogTokenDTO.setTimestampCreated(System.currentTimeMillis());
	        		catalogTokenDTO.setTimestampExpiration(validity.getTime());
	        		catalogTokenDTO.setType("JWT");
	        		catalogTokenDTO.setValid(true);
	        		catalogTokenDTO.setValue(token);
	        		
	        		catalogTokenService.save(catalogTokenDTO);
	        }
        }
        	return token;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
            .setSigningKey(secretKey)
            .parseClaimsJws(token)
            .getBody();

        Collection<? extends GrantedAuthority> authorities =
            Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        User principal = new User(claims.getSubject(), "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(authToken);
            
            List<CatalogToken> catalogTokenList = catalogTokenRepository.findAllByValueOrderByIdDesc(authToken);
            if(!catalogTokenList.isEmpty() && catalogTokenList.get(0).isValid()) {
            		return true;
            }

        } catch (SignatureException e) {
            log.info("Invalid JWT signature.");
            log.trace("Invalid JWT signature trace: {}", e);
        } catch (MalformedJwtException e) {
            log.info("Invalid JWT token.");
            log.trace("Invalid JWT token trace: {}", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token.");
            log.trace("Expired JWT token trace: {}", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT token.");
            log.trace("Unsupported JWT token trace: {}", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT token compact of handler are invalid.");
            log.trace("JWT token compact of handler are invalid trace: {}", e);
        }
        return false;
    }
}