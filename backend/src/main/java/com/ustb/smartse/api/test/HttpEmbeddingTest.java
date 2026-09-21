package com.ustb.smartse.api.test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 使用HTTP请求直接测试通义千问Embedding API
 */
public class HttpEmbeddingTest {

    private static final String API_KEY = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/embeddings/text-embedding-v2/text-embedding";

    public static void main(String[] args) {
        System.out.println("开始测试通义千问Embedding API");
        System.out.println("API URL: " + API_URL);
        
        // 准备测试文本和请求体
        String testText = "智能软件工程是人工智能与软件工程的结合";
        String requestBody = String.format(
                "{\"input\":{\"texts\":[\"%s\"]},\"parameters\":{\"text_type\":\"query\"}}",
                testText);
        
        System.out.println("测试文本: " + testText);
        System.out.println("请求体: " + requestBody);
        
        // 构建HTTP请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        System.out.println("发送请求...");
        
        // 发送请求并获取响应
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // 输出状态码和响应
            System.out.println("\n响应状态码: " + response.statusCode());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                System.out.println("API调用成功!");
                
                // 为了不输出过长的向量，只显示部分响应
                if (responseBody.length() > 500) {
                    System.out.println("响应体前500字符: " + responseBody.substring(0, 500) + "...");
                } else {
                    System.out.println("完整响应体: " + responseBody);
                }
            } else {
                System.out.println("API调用失败: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("发送请求出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 