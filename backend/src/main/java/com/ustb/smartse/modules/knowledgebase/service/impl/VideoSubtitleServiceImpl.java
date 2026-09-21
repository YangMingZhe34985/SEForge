package com.ustb.smartse.modules.knowledgebase.service.impl;

import com.ustb.smartse.modules.knowledgebase.entity.KnowledgeEntry;
import com.ustb.smartse.modules.knowledgebase.service.KnowledgeService;
import com.ustb.smartse.modules.knowledgebase.service.VideoSubtitleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.EncoderException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VideoSubtitleServiceImpl implements VideoSubtitleService {

    @Autowired
    private KnowledgeService knowledgeService;

    @Value("${video.temp.dir}")
    private String tempDir;

    @Value("${whisper.model.path}")
    private String whisperModelPath;

    @Value("${whisper.python.path}")
    private String pythonPath;
    
    // Whisper模型大小，可选值: tiny, base, small, medium, large
    private static final String DEFAULT_MODEL_SIZE = "medium";

    @Override
    public List<String> extractSubtitles(File videoFile) {
        try {
            // 1. 验证视频文件
            if (!isValidVideoFile(videoFile)) {
                throw new IllegalArgumentException("Invalid video file");
            }

            // 2. 使用本地Whisper模型提取字幕
            return callLocalWhisper(videoFile, DEFAULT_MODEL_SIZE);
        } catch (Exception e) {
            log.error("Failed to extract subtitles from video: " + videoFile.getName(), e);
            throw new RuntimeException("Failed to extract subtitles", e);
        }
    }

    @Override
    public boolean extractAndSaveSubtitles(File videoFile, String category) {
        try {
            // 1. 提取字幕
            List<String> subtitles = extractSubtitles(videoFile);
            if (subtitles.isEmpty()) {
                return false;
            }

            // 2. 将字幕保存到知识库
            KnowledgeEntry entry = KnowledgeEntry.builder()
                    .title(videoFile.getName())
                    .content(String.join("\n", subtitles))
                    .sourceType(5) // 视频字幕
                    .sourceId("video_" + UUID.randomUUID().toString())
                    .category(category)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .build();

            return knowledgeService.addKnowledgeEntry(entry);
        } catch (Exception e) {
            log.error("Failed to extract and save subtitles: " + videoFile.getName(), e);
            return false;
        }
    }

    @Override
    public boolean downloadAndExtractSubtitles(String videoUrl, String category) {
        try {
            // 1. 下载视频文件
            File videoFile = downloadVideo(videoUrl);
            if (videoFile == null) {
                return false;
            }

            // 2. 提取并保存字幕
            boolean success = extractAndSaveSubtitles(videoFile, category);

            // 3. 清理临时文件
            videoFile.delete();

            return success;
        } catch (Exception e) {
            log.error("Failed to download and extract subtitles from URL: " + videoUrl, e);
            return false;
        }
    }

    private boolean isValidVideoFile(File file) {
        try {
            MultimediaObject multimediaObject = new MultimediaObject(file);
            MultimediaInfo info = multimediaObject.getInfo();
            return info != null && info.getDuration() > 0;
        } catch (EncoderException e) {
            log.error("Invalid video file: " + file.getName(), e);
            return false;
        }
    }

    private List<String> callLocalWhisper(File videoFile) throws IOException, InterruptedException {
        return callLocalWhisper(videoFile, DEFAULT_MODEL_SIZE);
    }

    private List<String> callLocalWhisper(File videoFile, String modelSize) throws IOException, InterruptedException {
        // 创建临时目录用于存放Whisper输出
        Path outputDir = Files.createTempDirectory("whisper_output_");
        String outputPath = outputDir.toString();

        // 构建Whisper命令
        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add("-m");
        command.add("whisper");
        command.add(videoFile.getAbsolutePath());
        command.add("--model");
        command.add(modelSize); // 指定模型大小
        command.add("--model_dir");
        command.add(whisperModelPath);
        command.add("--output_dir");
        command.add(outputPath);
        command.add("--language");
        command.add("zh");
        command.add("--task");
        command.add("transcribe");

        log.info("执行Whisper命令: {}", String.join(" ", command));

        // 执行命令
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // 读取命令输出
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("Whisper output: {}", line);
            }
        }

        // 等待命令执行完成
        boolean completed = process.waitFor(60, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Whisper process timed out");
        }

        // 读取生成的字幕文件
        List<String> subtitles = new ArrayList<>();
        File[] outputFiles = outputDir.toFile().listFiles((dir, name) -> name.endsWith(".txt"));
        if (outputFiles != null && outputFiles.length > 0) {
            try (BufferedReader reader = new BufferedReader(new FileReader(outputFiles[0]))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    subtitles.add(line);
                }
            }
        }

        // 清理临时目录
        Files.walk(outputDir)
                .map(Path::toFile)
                .forEach(File::delete);
        Files.delete(outputDir);

        return subtitles;
    }

    private File downloadVideo(String videoUrl) throws IOException {
        // 创建临时文件
        String fileName = UUID.randomUUID().toString() + ".mp4";
        Path tempPath = Paths.get(tempDir, fileName);
        File tempFile = tempPath.toFile();

        // 下载视频
        URL url = new URL(videoUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }
} 