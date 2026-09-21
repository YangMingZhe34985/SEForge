package com.ustb.smartse.api.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.smartse.api.llm.dto.OpenAiMessage;
import com.ustb.smartse.api.llm.dto.OpenAiRequest;
import com.ustb.smartse.api.llm.dto.OpenAiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DeepSeekApi {

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用DeepSeek API进行聊天
     * @param prompt 提示内容
     * @return 处理后的响应内容
     */
    public String chat(String prompt) {
        try {
            log.info("准备调用DeepSeek API，提示长度: {} 字符", prompt.length());
            
            // 组装请求
            OpenAiRequest request = new OpenAiRequest();
            request.setModel("deepseek-chat");
            request.setMessages(Collections.singletonList(new OpenAiMessage("user", prompt)));
            // 添加响应格式控制
            request.setTemperature(0.1); // 降低温度，使输出更确定性
            request.setMaxTokens(4000); // 增加最大令牌数，确保完整响应
            
            // 使用Map设置response_format
            Map<String, String> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            request.setResponseFormat(responseFormat); // 请求JSON格式响应

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("Accept", "application/json");

            HttpEntity<OpenAiRequest> httpEntity = new HttpEntity<>(request, headers);
            
            // 记录请求详情（不包含API密钥）
            try {
                String requestJson = objectMapper.writeValueAsString(request);
                log.debug("DeepSeek API请求内容: {}", requestJson);
            } catch (Exception e) {
                log.warn("无法序列化请求对象: {}", e.getMessage());
            }

            log.info("开始发送DeepSeek API请求到: {}", apiUrl);
            
            // 发送POST请求，增加错误处理
            ResponseEntity<OpenAiResponse> response;
            try {
                response = restTemplate.postForEntity(apiUrl, httpEntity, OpenAiResponse.class);
            } catch (HttpClientErrorException e) {
                log.error("DeepSeek API客户端错误: {}, 响应体: {}", e.getStatusCode(), e.getResponseBodyAsString());
                return extractJsonFromErrorResponse(e.getResponseBodyAsString());
            } catch (HttpServerErrorException e) {
                log.error("DeepSeek API服务器错误: {}, 响应体: {}", e.getStatusCode(), e.getResponseBodyAsString());
                return "DeepSeek服务器错误，请稍后再试。";
            } catch (ResourceAccessException e) {
                log.error("DeepSeek API连接错误: {}", e.getMessage());
                return "无法连接到DeepSeek服务，请检查网络连接。";
            }

            log.info("DeepSeek API响应状态码: {}", response.getStatusCode());
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 记录完整响应内容
                try {
                    String responseJson = objectMapper.writeValueAsString(response.getBody());
                    log.debug("DeepSeek API完整响应: {}", responseJson);
                } catch (Exception e) {
                    log.warn("无法序列化响应对象: {}", e.getMessage());
                }
                
                // 边界防护：choices 可能为空或缺少 message，避免 IndexOutOfBounds / NPE
                OpenAiResponse body = response.getBody();
                if (body.getChoices() == null || body.getChoices().isEmpty()
                        || body.getChoices().get(0).getMessage() == null) {
                    log.error("DeepSeek API响应缺少有效的choices内容");
                    return "调用大模型失败，请稍后再试。";
                }

                String content = body.getChoices().get(0).getMessage().getContent();
                log.info("DeepSeek API响应内容长度: {} 字符", content != null ? content.length() : 0);
                
                // 增强的响应内容清理和提取
                content = enhancedResponseCleaning(content);
                log.debug("清理后的DeepSeek响应内容: {}", content);
                
                return content;
            } else {
                log.error("调用DeepSeek失败: 状态码{}, 响应体: {}", 
                          response.getStatusCode(), 
                          response.getBody() != null ? objectMapper.writeValueAsString(response.getBody()) : "null");
                return "调用大模型失败，请稍后再试。";
            }
        } catch (Exception e) {
            log.error("调用DeepSeek异常: {}", e.getMessage(), e);
            return "系统异常，请稍后再试。";
        }
    }
    
    /**
     * 增强的响应内容清理和JSON提取
     * @param content 原始响应内容
     * @return 清理和提取后的内容
     */
    private String enhancedResponseCleaning(String content) {
        if (content == null) {
            return "";
        }
        
        // 简单替换，避免使用复杂正则表达式
        content = content.replace("```json", "")
                         .replace("```javascript", "")
                         .replace("```", "")
                         .replace("`", "");
        
        // 直接使用字符索引方法提取JSON
        String jsonContent = extractJsonUsingCharIndex(content);
        if (jsonContent != null) {
            return jsonContent;
        }
        
        // 如果提取失败，返回原始清理后的内容
        return content.trim();
    }
    
    /**
     * 使用字符索引方法提取JSON
     */
    private String extractJsonUsingCharIndex(String content) {
        try {
            content = content.trim();
            
            // 尝试提取JSON对象
            int objectStart = content.indexOf('{');
            int arrayStart = content.indexOf('[');
            
            if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
                int objectEnd = findMatchingCloseBrace(content, objectStart, '{', '}');
                if (objectEnd > objectStart) {
                    String jsonObject = content.substring(objectStart, objectEnd + 1);
                    // 验证JSON有效性
                    try {
                        objectMapper.readTree(jsonObject);
                        return jsonObject;
                    } catch (Exception e) {
                        log.debug("提取的JSON对象格式无效: {}", e.getMessage());
                    }
                }
            } else if (arrayStart >= 0) {
                int arrayEnd = findMatchingCloseBrace(content, arrayStart, '[', ']');
                if (arrayEnd > arrayStart) {
                    String jsonArray = content.substring(arrayStart, arrayEnd + 1);
                    // 验证JSON有效性
                    try {
                        objectMapper.readTree(jsonArray);
                        return jsonArray;
                    } catch (Exception e) {
                        log.debug("提取的JSON数组格式无效: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("使用字符索引提取JSON失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 从错误响应中提取可能的JSON
     */
    private String extractJsonFromErrorResponse(String errorResponse) {
        try {
            // 尝试解析错误响应中可能包含的JSON
            return enhancedResponseCleaning(errorResponse);
        } catch (Exception e) {
            log.warn("从错误响应中提取JSON失败: {}", e.getMessage());
            return "请求处理失败，请稍后再试。";
        }
    }
    
    /**
     * 查找匹配的右括号
     */
    private int findMatchingCloseBrace(String content, int startPos, char openBrace, char closeBrace) {
        int count = 1;
        for (int i = startPos + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == openBrace) {
                count++;
            } else if (c == closeBrace) {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return -1; // 找不到匹配的右括号
    }
}
