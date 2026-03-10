package com.oms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态文件路径，让Spring Boot能够访问uploads文件夹中的文件
        String uploadPath = new File("uploads").getAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + File.separator);
    }
}
