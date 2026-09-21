package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeService;
import com.ustb.smartse.modules.knowledgebase.service.PdfProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PDF处理服务实现类
 */
@Slf4j
@Service
public class PdfProcessingServiceImpl implements PdfProcessingService {

    @Autowired
    private KnowledgeService knowledgeService;

    @Value("${pdf.temp.dir:./temp/pdf}")
    private String tempDir;

    @Value("${python.path:python}")
    private String pythonPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String extractText(File pdfFile) {
        try {
            // 调用Python脚本提取文本
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonPath,
                    "-u", // 添加-u参数，强制Python使用unbuffered模式
                    getPythonScriptPath(),
                    "extract_text",
                    pdfFile.getAbsolutePath()
            );
            processBuilder.redirectErrorStream(true);
            
            // 设置环境变量，确保Python使用UTF-8编码
            Map<String, String> env = processBuilder.environment();
            env.put("PYTHONIOENCODING", "utf-8");
            
            Process process = processBuilder.start();

            // 读取输出，使用UTF-8编码
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待进程完成
            boolean completed = process.waitFor(5, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("PDF文本提取超时");
            }

            if (process.exitValue() != 0) {
                log.error("PDF文本提取失败: {}", output);
                throw new RuntimeException("PDF文本提取失败");
            }

            return output.toString();
        } catch (Exception e) {
            log.error("PDF文本提取异常", e);
            throw new RuntimeException("PDF文本提取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String extractText(InputStream inputStream) {
        try {
            // 将输入流保存到临时文件
            File tempFile = createTempFile(inputStream);
            String result = extractText(tempFile);
            tempFile.delete();
            return result;
        } catch (Exception e) {
            log.error("从输入流提取PDF文本失败", e);
            throw new RuntimeException("从输入流提取PDF文本失败: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractStructuredContent(File pdfFile) {
        try {
            // 调用Python脚本提取结构化内容
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonPath,
                    "-u", // 添加-u参数，强制Python使用unbuffered模式
                    getPythonScriptPath(),
                    "extract_structured",
                    pdfFile.getAbsolutePath()
            );
            processBuilder.redirectErrorStream(true);
            
            // 设置环境变量，确保Python使用UTF-8编码
            Map<String, String> env = processBuilder.environment();
            env.put("PYTHONIOENCODING", "utf-8");
            
            Process process = processBuilder.start();

            // 读取输出，使用UTF-8编码
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // 等待进程完成
            boolean completed = process.waitFor(5, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("PDF结构化内容提取超时");
            }

            if (process.exitValue() != 0) {
                log.error("PDF结构化内容提取失败: {}", output);
                throw new RuntimeException("PDF结构化内容提取失败");
            }

            // 解析JSON结果
            return objectMapper.readValue(output.toString(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("PDF结构化内容提取异常", e);
            throw new RuntimeException("PDF结构化内容提取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<File> extractImages(File pdfFile, String outputDir) {
        try {
            // 确保输出目录存在
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
            }

            // 调用Python脚本提取图像
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonPath,
                    "-u", // 添加-u参数，强制Python使用unbuffered模式
                    getPythonScriptPath(),
                    "extract_images",
                    pdfFile.getAbsolutePath(),
                    outputDir
            );
            processBuilder.redirectErrorStream(true);
            
            // 设置环境变量，确保Python使用UTF-8编码
            Map<String, String> env = processBuilder.environment();
            env.put("PYTHONIOENCODING", "utf-8");
            
            Process process = processBuilder.start();

            // 读取输出，使用UTF-8编码
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // 等待进程完成
            boolean completed = process.waitFor(10, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("PDF图像提取超时");
            }

            if (process.exitValue() != 0) {
                log.error("PDF图像提取失败: {}", output);
                throw new RuntimeException("PDF图像提取失败");
            }

            // 解析JSON结果（图像文件路径列表）
            List<String> imagePaths = objectMapper.readValue(output.toString(), new TypeReference<List<String>>() {});
            return imagePaths.stream()
                    .map(File::new)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("PDF图像提取异常", e);
            throw new RuntimeException("PDF图像提取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean processPdfToKnowledgeBase(File pdfFile, String category) {
        try {
            List<KnowledgeEntry> entries = processPdfToEntries(pdfFile, category);
            if (entries.isEmpty()) {
                return false;
            }

            // 批量保存到知识库
            for (KnowledgeEntry entry : entries) {
                knowledgeService.addKnowledgeEntry(entry);
            }
            return true;
        } catch (Exception e) {
            log.error("处理PDF到知识库失败", e);
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<KnowledgeEntry> processPdfToEntries(File pdfFile, String category) {
        try {
            // 提取结构化内容
            Map<String, Object> structuredContent = extractStructuredContent(pdfFile);
            
            List<KnowledgeEntry> entries = new ArrayList<>();
            String title = (String) structuredContent.get("title");
            
            // 处理章节内容
            List<Map<String, Object>> chapters = (List<Map<String, Object>>) structuredContent.get("chapters");
            if (chapters != null && !chapters.isEmpty()) {
                for (Map<String, Object> chapter : chapters) {
                    String chapterTitle = (String) chapter.get("title");
                    String content = (String) chapter.get("content");
                    
                    if (StringUtils.hasText(content)) {
                        // 创建知识条目
                        KnowledgeEntry entry = KnowledgeEntry.builder()
                                .title(title + " - " + chapterTitle)
                                .content(content)
                                .sourceType(4) // PDF类型
                                .sourceId("pdf_" + UUID.randomUUID().toString())
                                .category(category)
                                .createTime(new Date())
                                .updateTime(new Date())
                                .build();
                        entries.add(entry);
                    }
                }
            } else {
                // 如果没有章节，将整个PDF内容作为一个条目
                String content = extractText(pdfFile);
                if (StringUtils.hasText(content)) {
                    KnowledgeEntry entry = KnowledgeEntry.builder()
                            .title(title)
                            .content(content)
                            .sourceType(4) // PDF类型
                            .sourceId("pdf_" + UUID.randomUUID().toString())
                            .category(category)
                            .createTime(new Date())
                            .updateTime(new Date())
                            .build();
                    entries.add(entry);
                }
            }
            
            return entries;
        } catch (Exception e) {
            log.error("处理PDF到条目失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取Python脚本路径
     */
    private String getPythonScriptPath() throws IOException {
        ClassPathResource resource = new ClassPathResource("python/pdf_processor.py");
        File tempScript = File.createTempFile("pdf_processor", ".py");
        try (InputStream is = resource.getInputStream()) {
            Files.copy(is, tempScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        tempScript.setExecutable(true);
        return tempScript.getAbsolutePath();
    }

    /**
     * 创建临时文件
     */
    private File createTempFile(InputStream inputStream) throws IOException {
        // 确保临时目录存在
        Path tempDirPath = Paths.get(tempDir);
        if (!Files.exists(tempDirPath)) {
            Files.createDirectories(tempDirPath);
        }

        // 创建临时文件
        File tempFile = File.createTempFile("pdf_", ".pdf", tempDirPath.toFile());
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }
} 