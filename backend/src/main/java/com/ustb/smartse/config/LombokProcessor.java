package com.ustb.smartse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 处理自定义SafeAccessors注解的处理器
 * 会在Spring容器启动时检查安全注解的使用情况
 */
@Slf4j
@Component
public class LombokProcessor implements BeanPostProcessor {
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        
        // 检查是否使用了SafeAccessors注解
        if (clazz.getPackage() != null && 
            clazz.getPackage().getName().startsWith("com.ustb.smartse") && 
            clazz.isAnnotationPresent(com.ustb.smartse.common.annotation.SafeAccessors.class)) {
            log.info("发现使用SafeAccessors注解的bean: {}", beanName);
        }
        
        return bean;
    }
} 