package com.logistics.service.config;


import org.apache.commons.io.IOUtils;
import com.logistics.service.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

@Slf4j
@Configuration
@EnableResourceServer
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {


    /**
     * resource_id must match on Authorization Server
     */
    private String resourceId = "resource_id";

    private final AppConfig appConfig;

    private final CustomAccessTokenConverter customAccessTokenConverter;

    @Autowired
    public ResourceServerConfig(CustomAccessTokenConverter customAccessTokenConverter, AppConfig appConfig) {
        this.customAccessTokenConverter = customAccessTokenConverter;
        this.appConfig = appConfig;
        log.debug("RESOURCE_ID {}", this.resourceId);
        log.debug("CLIENT_ID {}", this.appConfig.clientId);
        log.debug("CLIENT_SECRET {}", this.appConfig.clientSecret);
        log.debug("INFO_URL {}", this.appConfig.tokenInfoUri);
    }

    /**
     * Configure ``Allowed Resource`` only in here.
     *
     * @param http HttpSecurity.
     * @throws Exception Error.
     */
    @Override
    public void configure(HttpSecurity http) throws Exception {

        http
                .exceptionHandling()
                .authenticationEntryPoint
                        ((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .and()
                .csrf().disable()
                .headers().frameOptions().disable()
                .and()
                .authorizeRequests()
                .antMatchers("/api/v1/hello/**").permitAll()
                .antMatchers(HttpMethod.GET, "/v2/**").access("#oauth2.hasScope('read')")
                .antMatchers(HttpMethod.POST, "/v2/**").access("#oauth2.hasScope('write')")
                .antMatchers(HttpMethod.PATCH, "/v2/**").access("#oauth2.hasScope('write')")
                .antMatchers(HttpMethod.PUT, "/v2/**").access("#oauth2.hasScope('write')")
                .antMatchers(HttpMethod.DELETE, "/v2/**").access("#oauth2.hasScope('write')")
                .and()
                .authorizeRequests()
                .anyRequest().authenticated();
    }

    /**
     * Decode Jwt Using PublicKey.
     *
     * @return JwtAccessTokenConverter
     */
    @Bean
    public JwtAccessTokenConverter accessTokenConverter() {
        final JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setAccessTokenConverter(customAccessTokenConverter);
        final Resource resource = new ClassPathResource("certificate/pubkey.txt");
        String publicKey = null;
        try {
            publicKey = IOUtils.toString(resource.getInputStream(), Charset.defaultCharset());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        converter.setVerifierKey(publicKey);
        return converter;
    }

    /**
     * Matching resource_id on Authorization Server.
     *
     * @param resources ResourceServerSecurityConfigurer.
     * @throws Exception Error if token not accepted..
     */
    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources.tokenServices(tokenServices());
        resources.resourceId(this.resourceId);
    }

    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(accessTokenConverter());
    }

    /**
     * We use remote token (Token from Authorization Server).
     * if token from Authorization Server deleted, resource server must revoke access token again.
     *
     * @return RemoteTokenServices.
     */
    @Primary
    @Bean
    public RemoteTokenServices tokenServices() {
        RemoteTokenServices tokenService = new RemoteTokenServices();
        tokenService.setCheckTokenEndpointUrl(
                this.appConfig.tokenInfoUri);
        tokenService.setClientId(this.appConfig.clientId);
        tokenService.setClientSecret(this.appConfig.clientSecret);
        return tokenService;
    }
}
