package com.social.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link AnnotationSecurityInterceptor} so that
 * {@code @RequiresRole} / {@code @RequiresPermission} annotations on controller
 * classes and methods are enforced for every request.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AnnotationSecurityInterceptor annotationSecurityInterceptor;

    public WebMvcConfig(AnnotationSecurityInterceptor annotationSecurityInterceptor) {
        this.annotationSecurityInterceptor = annotationSecurityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(annotationSecurityInterceptor);
    }
}
