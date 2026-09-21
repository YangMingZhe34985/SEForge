#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
PDF处理脚本，使用PyMuPDF库提取PDF内容
需要安装依赖: pip install PyMuPDF pillow
"""

import sys
import os
import json
import fitz  # PyMuPDF
import base64
from io import BytesIO
from PIL import Image

def extract_text(pdf_path):
    """
    从PDF文件中提取所有文本内容
    
    Args:
        pdf_path: PDF文件路径
        
    Returns:
        str: 提取的文本内容
    """
    try:
        doc = fitz.open(pdf_path)
        text = ""
        
        for page in doc:
            text += page.get_text()
            
        doc.close()
        return text
    except Exception as e:
        print(f"Error extracting text: {str(e)}", file=sys.stderr)
        return ""

def extract_structured_content(pdf_path):
    """
    从PDF文件中提取结构化内容（章节、段落等）
    
    Args:
        pdf_path: PDF文件路径
        
    Returns:
        dict: 包含结构化内容的字典
    """
    try:
        doc = fitz.open(pdf_path)
        result = {
            "title": os.path.basename(pdf_path),
            "total_pages": len(doc),
            "toc": doc.get_toc(),
            "chapters": []
        }
        
        # 提取章节内容
        current_chapter = {"title": "未命名章节", "content": "", "page_start": 0}
        chapters = []
        
        toc = doc.get_toc()
        if toc:
            for i, (level, title, page) in enumerate(toc):
                if i > 0:
                    current_chapter["page_end"] = page - 1
                    chapters.append(current_chapter)
                
                current_chapter = {"title": title, "content": "", "page_start": page - 1}
                
                # 如果是最后一个章节，页面范围到文档结束
                if i == len(toc) - 1:
                    current_chapter["page_end"] = len(doc) - 1
                    chapters.append(current_chapter)
        else:
            # 如果没有目录，将整个文档作为一个章节
            current_chapter["page_end"] = len(doc) - 1
            chapters.append(current_chapter)
        
        # 提取每个章节的内容
        for chapter in chapters:
            content = ""
            for page_num in range(chapter["page_start"], chapter["page_end"] + 1):
                if page_num < len(doc):
                    content += doc[page_num].get_text()
            chapter["content"] = content
        
        result["chapters"] = chapters
        doc.close()
        return result
    except Exception as e:
        print(f"Error extracting structured content: {str(e)}", file=sys.stderr)
        return {"error": str(e)}

def extract_images(pdf_path, output_dir):
    """
    从PDF文件中提取图像
    
    Args:
        pdf_path: PDF文件路径
        output_dir: 图像输出目录
        
    Returns:
        list: 提取的图像文件路径列表
    """
    try:
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
            
        doc = fitz.open(pdf_path)
        image_paths = []
        
        for page_index, page in enumerate(doc):
            image_list = page.get_images(full=True)
            
            for img_index, img in enumerate(image_list):
                xref = img[0]
                base_image = doc.extract_image(xref)
                image_bytes = base_image["image"]
                
                # 保存图像
                image_filename = f"page{page_index+1}_img{img_index+1}.{base_image['ext']}"
                image_path = os.path.join(output_dir, image_filename)
                
                with open(image_path, "wb") as img_file:
                    img_file.write(image_bytes)
                    
                image_paths.append(image_path)
        
        doc.close()
        return image_paths
    except Exception as e:
        print(f"Error extracting images: {str(e)}", file=sys.stderr)
        return []

def extract_text_with_layout(pdf_path):
    """
    从PDF文件中提取带有布局信息的文本
    
    Args:
        pdf_path: PDF文件路径
        
    Returns:
        list: 每页的文本块列表
    """
    try:
        doc = fitz.open(pdf_path)
        pages_blocks = []
        
        for page in doc:
            blocks = page.get_text("blocks")
            page_blocks = []
            
            for block in blocks:
                x0, y0, x1, y1, text, block_type, block_no = block
                page_blocks.append({
                    "text": text,
                    "rect": [x0, y0, x1, y1],
                    "type": block_type
                })
                
            pages_blocks.append(page_blocks)
            
        doc.close()
        return pages_blocks
    except Exception as e:
        print(f"Error extracting text with layout: {str(e)}", file=sys.stderr)
        return []

def main():
    """
    主函数，处理命令行参数
    """
    if len(sys.argv) < 3:
        print("Usage: python pdf_processor.py <command> <pdf_path> [output_dir]", file=sys.stderr)
        sys.exit(1)
        
    command = sys.argv[1]
    pdf_path = sys.argv[2]
    
    if not os.path.exists(pdf_path):
        print(f"PDF file not found: {pdf_path}", file=sys.stderr)
        sys.exit(1)
        
    if command == "extract_text":
        result = extract_text(pdf_path)
        print(result)
        
    elif command == "extract_structured":
        result = extract_structured_content(pdf_path)
        print(json.dumps(result, ensure_ascii=False))
        
    elif command == "extract_images":
        if len(sys.argv) < 4:
            print("Output directory required for extract_images", file=sys.stderr)
            sys.exit(1)
            
        output_dir = sys.argv[3]
        result = extract_images(pdf_path, output_dir)
        print(json.dumps(result, ensure_ascii=False))
        
    elif command == "extract_layout":
        result = extract_text_with_layout(pdf_path)
        print(json.dumps(result, ensure_ascii=False))
        
    else:
        print(f"Unknown command: {command}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main() 