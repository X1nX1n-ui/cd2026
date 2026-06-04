package com.cd.config;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();

        FastJsonConfig config = new FastJsonConfig();
        config.setCharset(StandardCharsets.UTF_8);
        config.setSerializerFeatures(
                SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteNullListAsEmpty,
                SerializerFeature.WriteNullStringAsEmpty
        );
        converter.setFastJsonConfig(config);

        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(MediaType.APPLICATION_JSON);
        supportedMediaTypes.add(MediaType.APPLICATION_JSON_UTF8);
        converter.setSupportedMediaTypes(supportedMediaTypes);

        converters.add(0, converter);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation.endsWith("/") ? uploadLocation : uploadLocation + "/");
    }
}
