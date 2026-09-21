package com.ustb.smartse.modules.knowledgebase.test;

import com.ustb.smartse.modules.knowledgebase.service.VideoSubtitleService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.List;
import java.util.Scanner;

/**
 * 视频字幕提取测试类
 */
@Configuration
@ComponentScan("com.ustb.smartse")
public class VideoSubtitleTest {

    public static void main(String[] args) {
        try {
            // 初始化Spring上下文
            ApplicationContext context = new AnnotationConfigApplicationContext(VideoSubtitleTest.class);
            VideoSubtitleService videoSubtitleService = context.getBean(VideoSubtitleService.class);
            
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("=== 视频字幕提取测试 ===");
            System.out.println("请选择测试方式:");
            System.out.println("1. 从本地视频文件提取字幕");
            System.out.println("2. 从URL下载视频并提取字幕");
            
            int choice = Integer.parseInt(scanner.nextLine());
            
            switch (choice) {
                case 1:
                    // 从本地视频文件提取字幕
                    System.out.println("请输入视频文件路径: ");
                    String filePath = scanner.nextLine();
                    
                    File videoFile = new File(filePath);
                    if (!videoFile.exists() || !videoFile.isFile()) {
                        System.err.println("文件不存在或不是有效文件!");
                        return;
                    }
                    
                    System.out.println("请选择操作:");
                    System.out.println("1. 仅提取字幕");
                    System.out.println("2. 提取字幕并保存到知识库");
                    
                    int operation = Integer.parseInt(scanner.nextLine());
                    
                    if (operation == 1) {
                        // 仅提取字幕
                        System.out.println("正在提取字幕...");
                        List<String> subtitles = videoSubtitleService.extractSubtitles(videoFile);
                        System.out.println("提取结果:");
                        System.out.println("-------------------");
                        for (int i = 0; i < subtitles.size(); i++) {
                            System.out.println((i + 1) + ": " + subtitles.get(i));
                        }
                        System.out.println("-------------------");
                        System.out.println("共提取 " + subtitles.size() + " 行字幕");
                    } else if (operation == 2) {
                        // 提取并保存到知识库
                        System.out.println("请输入分类:");
                        String category = scanner.nextLine();
                        
                        System.out.println("正在提取字幕并保存到知识库...");
                        boolean success = videoSubtitleService.extractAndSaveSubtitles(videoFile, category);
                        System.out.println("处理结果: " + (success ? "成功" : "失败"));
                    } else {
                        System.out.println("无效选择!");
                    }
                    break;
                    
                case 2:
                    // 从URL下载视频并提取字幕
                    System.out.println("请输入视频URL: ");
                    String videoUrl = scanner.nextLine();
                    
                    System.out.println("请输入分类:");
                    String category = scanner.nextLine();
                    
                    System.out.println("正在下载视频并提取字幕...");
                    boolean success = videoSubtitleService.downloadAndExtractSubtitles(videoUrl, category);
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