package com.ustb.smartse.modules.knowledgebase.test;

import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.service.PdfProcessingService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * PDF处理测试类
 */
@Configuration
@ComponentScan("com.ustb.smartse")
public class PdfProcessingTest {

    public static void main(String[] args) {
        try {
            // 初始化Spring上下文
            ApplicationContext context = new AnnotationConfigApplicationContext(PdfProcessingTest.class);
            PdfProcessingService pdfProcessingService = context.getBean(PdfProcessingService.class);
            
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("=== PDF处理测试 ===");
            System.out.println("请输入PDF文件路径: ");
            String filePath = scanner.nextLine();
            
            File pdfFile = new File(filePath);
            if (!pdfFile.exists() || !pdfFile.isFile()) {
                System.err.println("文件不存在或不是有效文件!");
                return;
            }
            
            System.out.println("请选择测试功能:");
            System.out.println("1. 提取文本");
            System.out.println("2. 提取结构化内容");
            System.out.println("3. 处理并转换为知识条目(不保存)");
            System.out.println("4. 处理并保存到知识库");
            
            int choice = Integer.parseInt(scanner.nextLine());
            
            switch (choice) {
                case 1:
                    // 提取文本
                    System.out.println("正在提取文本...");
                    String text = pdfProcessingService.extractText(pdfFile);
                    System.out.println("提取结果:");
                    System.out.println("-------------------");
                    System.out.println(text);
                    System.out.println("-------------------");
                    break;
                    
                case 2:
                    // 提取结构化内容
                    System.out.println("正在提取结构化内容...");
                    Map<String, Object> structuredContent = pdfProcessingService.extractStructuredContent(pdfFile);
                    System.out.println("提取结果:");
                    System.out.println("-------------------");
                    System.out.println("标题: " + structuredContent.get("title"));
                    System.out.println("总页数: " + structuredContent.get("total_pages"));
                    System.out.println("章节数: " + ((List<?>) structuredContent.get("chapters")).size());
                    System.out.println("-------------------");
                    break;
                    
                case 3:
                    // 处理为知识条目
                    System.out.println("请输入分类:");
                    String category = scanner.nextLine();
                    System.out.println("正在处理PDF为知识条目...");
                    List<KnowledgeEntry> entries = pdfProcessingService.processPdfToEntries(pdfFile, category);
                    System.out.println("处理结果:");
                    System.out.println("-------------------");
                    System.out.println("生成知识条目数: " + entries.size());
                    for (int i = 0; i < entries.size(); i++) {
                        KnowledgeEntry entry = entries.get(i);
                        System.out.println("条目 " + (i + 1) + ": " + entry.getTitle());
                        System.out.println("内容长度: " + (entry.getContent() != null ? entry.getContent().length() : 0) + " 字符");
                        System.out.println();
                    }
                    System.out.println("-------------------");
                    break;
                    
                case 4:
                    // 处理并保存到知识库
                    System.out.println("请输入分类:");
                    category = scanner.nextLine();
                    System.out.println("正在处理PDF并保存到知识库...");
                    boolean success = pdfProcessingService.processPdfToKnowledgeBase(pdfFile, category);
                    System.out.println("处理结果: " + (success ? "成功" : "失败"));
                    break;
                    
                default:
                    System.out.println("无效选择!");
                    break;
            }
            
            System.out.println("测试完成!");
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 