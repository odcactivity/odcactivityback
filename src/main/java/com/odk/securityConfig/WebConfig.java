package com.odk.securityConfig;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String UPLOAD_DIR = "uploads/personnels/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        Path uploadPath = Paths.get(UPLOAD_DIR);
        String uploadAbsolutePath = uploadPath.toFile().getAbsolutePath();
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:images/");
    }
}
