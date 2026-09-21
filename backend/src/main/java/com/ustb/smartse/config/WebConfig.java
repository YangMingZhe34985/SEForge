package com.ustb.smartse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 从配置文件中读取头像保存路径（推荐：/opt/app/resources/img/avatars/）
     */
    @Value("${avatar.base-dir}")
    private String avatarBaseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射头像访问路径
        registry.addResourceHandler("/avatar/**")
                .addResourceLocations("file:" + avatarBaseDir);

        // 映射类路径下的静态资源
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    /**
     * 跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * 支持 multipart 文件上传
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
