//package com.hgx.hgxaiagent.tools;
//
//import cn.hutool.core.io.FileUtil;
//import com.itextpdf.kernel.font.PdfFont;
//import com.itextpdf.kernel.font.PdfFontFactory;
//import com.itextpdf.kernel.pdf.PdfDocument;
//import com.itextpdf.kernel.pdf.PdfWriter;
//import com.itextpdf.layout.Document;
//import com.itextpdf.layout.element.Paragraph;
//import com.hgx.hgxaiagent.constant.FileConstant ;
//import org.springframework.ai.tool.annotation.Tool;
//import org.springframework.ai.tool.annotation.ToolParam;
//
//import java.io.IOException;
//
///**
// * PDF 生成工具
// */
//public class PDFGenerationTool {
//
//    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
//    public String generatePDF(
//            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
//            @ToolParam(description = "Content to be included in the PDF") String content) {
//        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
//        String filePath = fileDir + "/" + fileName;
//        try {
//            // 创建目录
//            FileUtil.mkdir(fileDir);
//            // 创建 PdfWriter 和 PdfDocument 对象
//            try (PdfWriter writer = new PdfWriter(filePath);
//                 PdfDocument pdf = new PdfDocument(writer);
//                 Document document = new Document(pdf)) {
//                // 自定义字体（需要人工下载字体文件到特定目录）
////                String fontPath = Paths.get("src/main/resources/static/fonts/simsun.ttf")
////                        .toAbsolutePath().toString();
////                PdfFont font = PdfFontFactory.createFont(fontPath,
////                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
//                // 使用内置中文字体
//                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
//                document.setFont(font);
//                // 创建段落
//                Paragraph paragraph = new Paragraph(content);
//                // 添加段落并关闭文档
//                document.add(paragraph);
//            }
//            return "PDF generated successfully to: " + filePath;
//        } catch (IOException e) {
//            return "Error generating PDF: " + e.getMessage();
//        }
//    }
//}


package com.hgx.hgxaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.hgx.hgxaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * PDF 生成工具
 */
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                // 获取可用中文字体（应用降级策略）
                PdfFont font = getChineseFont();
                document.setFont(font);

                // 创建段落
                Paragraph paragraph = new Paragraph(content);
                // 添加段落并关闭文档
                document.add(paragraph);
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /**
     * 获取可用中文字体的辅助方法（包含多级降级策略）
     */
    private PdfFont getChineseFont() {
        // 策略一：尝试从项目的 resources 目录加载 simsun.ttf 字体
        // 这种方式兼容 JAR 包部署，读取字节流进行加载
        try {
            ClassPathResource resource = new ClassPathResource("static/fonts/simsun.ttf");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    byte[] fontBytes = is.readAllBytes();
                    return PdfFontFactory.createFont(fontBytes,
                            PdfEncodings.IDENTITY_H,
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                }
            }
        } catch (Exception e) {
            // 静默忽略，尝试下一种加载策略
        }

        // 策略二：若是 Windows 系统环境，尝试直接加载系统自带的微软雅黑字体
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            try {
                String winFontPath = "C:/Windows/Fonts/msyh.ttc,0"; // 0 表示读取字体集合中的第一顺位字体
                return PdfFontFactory.createFont(winFontPath,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            } catch (Exception e) {
                // 静默忽略，尝试最终内置字体降级
            }
        }

        // 策略三：最终降级方案，使用 iText 的内置标准宋体（配合正确的中文字符集映射）
        try {
            return PdfFontFactory.createFont("STSong-Light", "UniGB-UTF16-H");
        } catch (IOException e) {
            // 极端备用情况：若内置中文字体依然加载失败，则返回默认英文字体（可能有中文乱码，但避免程序崩溃退出）
            try {
                return PdfFontFactory.createFont();
            } catch (IOException ex) {
                throw new RuntimeException("Failed to initialize any PDF font", ex);
            }
        }
    }
}