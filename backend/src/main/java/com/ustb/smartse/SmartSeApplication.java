package com.ustb.smartse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * SmartSE应用程序入口
 */
@SpringBootApplication
public class SmartSeApplication {

    /**
     * 设置默认JVM参数
     */
    static {
        // 如果未设置内存参数，则设置默认值
        if (System.getProperty("java.memory.max") == null) {
            System.setProperty("java.memory.max", "4096m");
        }
        
        // 设置栈内存大小
        if (System.getProperty("java.thread.stack.size") == null) {
            System.setProperty("java.thread.stack.size", "2m");
        }
        
        // 设置文件编码
        System.setProperty("file.encoding", "UTF-8");
        
        // 设置Stanford NLP相关参数
        System.setProperty("edu.stanford.nlp.chinese.Sighan2005DocumentReaderAndWriter.sighanCorporaDict", "./nlp-models/dict");
        System.setProperty("edu.stanford.nlp.wordseg.CorpusDictionary.path", "./nlp-models/dict");
        System.setProperty("chinesedict.path", "./nlp-models/dict");
        System.setProperty("chinesedict.file", "./nlp-models/dict/character_list");
    }

    public static void main(String[] args) {
        // 创建应用构建器
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SmartSeApplication.class);
        
        // 添加默认属性
        builder.properties("spring.config.additional-location=classpath:/");
        
        // 设置JVM参数
        System.setProperty("spring.jvm.args", "-Xmx4096m -Xms1024m -Xss2m");
        
        // 启动应用
        ConfigurableApplicationContext context = builder.run(args);
    }
}
