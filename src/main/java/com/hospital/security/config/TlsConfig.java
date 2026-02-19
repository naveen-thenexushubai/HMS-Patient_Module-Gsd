package com.hospital.security.config;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * TLS configuration for production deployment.
 *
 * This configuration is optional and only active in production profile.
 * It provides HTTP to HTTPS redirection for applications that handle TLS termination
 * at the application level rather than at a load balancer or reverse proxy.
 *
 * Load Balancer Consideration:
 * If your deployment uses a load balancer (AWS ALB, nginx, Traefik) for TLS termination,
 * you can disable this configuration and run the application on HTTP internally.
 * Ensure X-Forwarded-Proto headers are preserved for audit logging.
 */
@Configuration
@Profile("prod")
public class TlsConfig {

    /**
     * Redirects HTTP (8080) to HTTPS (8443) in production.
     *
     * This configuration:
     * 1. Adds a security constraint that requires HTTPS for all endpoints
     * 2. Creates an HTTP connector on port 8080 that redirects to HTTPS port 8443
     *
     * Optional: Can be handled by load balancer/reverse proxy instead.
     *
     * @return Configured TomcatServletWebServerFactory with HTTP redirect
     */
    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                // Require HTTPS for all requests
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };

        // Add HTTP connector that redirects to HTTPS
        tomcat.addAdditionalTomcatConnectors(httpConnector());
        return tomcat;
    }

    /**
     * Creates an HTTP connector on port 8080 that redirects to HTTPS port 8443.
     *
     * @return Configured Connector for HTTP to HTTPS redirect
     */
    private Connector httpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(8080);
        connector.setSecure(false);
        connector.setRedirectPort(8443);
        return connector;
    }
}
