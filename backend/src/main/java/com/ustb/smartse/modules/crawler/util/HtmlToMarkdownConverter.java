package com.ustb.smartse.modules.crawler.util;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * HTML到Markdown转换工具类
 */
@Component
public class HtmlToMarkdownConverter {

    private final FlexmarkHtmlConverter converter;
    
    public HtmlToMarkdownConverter() {
        MutableDataSet options = new MutableDataSet();
        // 设置转换选项
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false); // 使用 # 样式的标题
        options.set(FlexmarkHtmlConverter.OUTPUT_UNKNOWN_TAGS, false); // 忽略未知标签
        options.set(FlexmarkHtmlConverter.TYPOGRAPHIC_SMARTS, false); // 禁用智能标点
        
        this.converter = FlexmarkHtmlConverter.builder(options).build();
    }
    
    /**
     * 将HTML转换为Markdown
     * @param html HTML内容
     * @return Markdown内容
     */
    public String convert(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        try {
            // 清理HTML
            Document document = Jsoup.parse(html);
            
            // 删除不需要的标签
            document.select("script, style, iframe, noscript").remove();
            
            // 转换为Markdown
            String markdown = converter.convert(document.body().html());
            
            // 后处理
            markdown = postProcess(markdown);
            
            return markdown;
        } catch (Exception e) {
            // 如果转换失败，返回原文本
            return html;
        }
    }
    
    /**
     * Markdown后处理
     * @param markdown Markdown内容
     * @return 处理后的Markdown
     */
    private String postProcess(String markdown) {
        // 删除多余的空行
        markdown = markdown.replaceAll("\\n{3,}", "\n\n");
        
        // 修复列表格式
        markdown = markdown.replaceAll("\\n\\s+\\*", "\n*");
        
        // 替换特殊字符
        markdown = markdown.replace("&nbsp;", " ")
                           .replace("&lt;", "<")
                           .replace("&gt;", ">")
                           .replace("&amp;", "&")
                           .replace("&quot;", "\"");
        
        return markdown;
    }
} 