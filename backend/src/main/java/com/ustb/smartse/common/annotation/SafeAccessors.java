package com.ustb.smartse.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义安全访问器注解，标记为需要Lombok处理的类
 * 这个注解只是一个标记，实际处理逻辑在LombokProcessor中
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface SafeAccessors {
} 