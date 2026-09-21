package com.ustb.smartse.api.test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.common.Message;

public class DashScopeEmbeddingTest {
    
    public static void basicCall() throws ApiException, NoApiKeyException {
        System.out.println("开始基本调用测试...");
        
        // 设置API KEY
        System.setProperty("DASHSCOPE_API_KEY", System.getenv().getOrDefault("DASHSCOPE_API_KEY", ""));
        
        // 创建参数对象
        TextEmbeddingParam param = TextEmbeddingParam.builder()
            .model(TextEmbedding.Models.TEXT_EMBEDDING_V2)  // 使用text-embedding-v2模型
            .texts(Arrays.asList(
                "智能软件工程是人工智能与软件工程的结合",
                "通义千问是阿里云推出的大语言模型",
                "向量嵌入可以将文本转换为向量用于语义搜索"
            ))
            .build();
            
        // 创建TextEmbedding实例并调用
        TextEmbedding textEmbedding = new TextEmbedding();
        TextEmbeddingResult result = textEmbedding.call(param);
        
        // 输出完整结果
        System.out.println("完整结果：");
        System.out.println(result);
        
        // 输出嵌入向量的部分值
        if (result != null && result.getOutput() != null && 
            result.getOutput().getEmbeddings() != null && 
            !result.getOutput().getEmbeddings().isEmpty()) {
                
            System.out.println("\n第一个文本的向量(前5个值)：");
            List<Double> vector = result.getOutput().getEmbeddings().get(0).getEmbedding();
            for (int i = 0; i < Math.min(5, vector.size()); i++) {
                System.out.println("索引 " + i + ": " + vector.get(i));
            }
            
            System.out.println("\n向量维度: " + vector.size());
        }
    }
    
    public static void callWithCallback() throws ApiException, NoApiKeyException, InterruptedException {
        System.out.println("\n开始回调方式调用测试...");
        
        // 设置API KEY
        System.setProperty("DASHSCOPE_API_KEY", System.getenv().getOrDefault("DASHSCOPE_API_KEY", ""));
        
        // 创建参数对象
        TextEmbeddingParam param = TextEmbeddingParam.builder()
            .model(TextEmbedding.Models.TEXT_EMBEDDING_V2)
            .texts(Arrays.asList(
                "风急天高猿啸哀", 
                "渚清沙白鸟飞回", 
                "无边落木萧萧下", 
                "不尽长江滚滚来"
            ))
            .build();
            
        // 创建TextEmbedding实例
        TextEmbedding textEmbedding = new TextEmbedding();
        
        // 使用信号量同步异步回调
        Semaphore sem = new Semaphore(0);
        
        // 异步调用带回调
        textEmbedding.call(param, new ResultCallback<TextEmbeddingResult>() {
            @Override
            public void onEvent(TextEmbeddingResult result) {
                System.out.println("异步回调结果：");
                System.out.println(result);
            }
            
            @Override
            public void onComplete() {
                System.out.println("调用完成");
                sem.release();
            }
            
            @Override
            public void onError(Exception err) {
                System.out.println("调用出错: " + err.getMessage());
                err.printStackTrace();
                sem.release();
            }
        });
        
        // 等待异步调用完成
        sem.acquire();
    }
    
    public static void main(String[] args) {
        System.out.println("DashScope通义千问Embedding模型测试");
        System.out.println("===============================");
        
        try {
            // 测试基本调用
            basicCall();
            
            // 测试异步回调方式
            callWithCallback();
            
            System.out.println("\n测试完成！");
        } catch (ApiException e) {
            System.err.println("API调用错误: " + e.getMessage());
            e.printStackTrace();
        } catch (NoApiKeyException e) {
            System.err.println("未设置API KEY: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("线程中断: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 