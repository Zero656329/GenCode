package com.gencode.framework.config;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Jackson 全局定制：
 * 1. Long（包装类型）序列化为字符串，防止雪花 ID 前端精度丢失；
 *    注意不注册 long 基本类型，分页 total 等数值字段保持数字输出；
 * 2. LocalDateTime/Date 统一 yyyy-MM-dd HH:mm:ss，反序列化容忍多种格式。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .serializerByType(Date.class, new DateSerializer(false, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")))
                .deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer())
                .deserializerByType(Date.class, new FlexibleDateDeserializer());
    }

    /**
     * LocalDateTime 宽松反序列化：支持 yyyy-MM-dd HH:mm:ss、yyyy-MM-dd、ISO 等
     */
    public static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (StrUtil.isBlank(text)) {
                return null;
            }
            String value = text.trim();
            try {
                return value.length() <= 10
                        ? LocalDate.parse(value).atStartOfDay()
                        : LocalDateTimeUtil.parse(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("日期格式不正确: " + text);
            }
        }
    }

    /**
     * Date 宽松反序列化（hutool DateUtil 支持多种常见格式）
     */
    public static class FlexibleDateDeserializer extends JsonDeserializer<Date> {

        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (StrUtil.isBlank(text)) {
                return null;
            }
            return DateUtil.parse(text.trim());
        }
    }
}
