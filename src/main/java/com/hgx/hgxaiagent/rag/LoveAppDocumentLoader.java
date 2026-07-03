package com.hgx.hgxaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 包含markdown和pdf
 * 文档加载器
 */
@Component
@Slf4j
public class LoveAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载多篇 Markdown 文档
     *
     * @return
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }


    /**
     * 加载多篇 PDF 文档
     */
    public List<Document> loadPdfs() {
        List<Document> allDocuments = new ArrayList<>();

        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.pdf");

            for (Resource resource : resources) {
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        /**
                         *.withHorizontalRuleCreateDocument(true)
                         *  .withIncludeCodeBlock(false)
                         *   .withIncludeBlockquote(false)
                         *    .withAdditionalMetadata("filename", filename)
                         *    注意： PdfDocumentReaderConfig 没有这些方法，所以 不能用
                         */
                        .withPagesPerDocument(1)
                        .build();

                PagePdfDocumentReader pdfDocumentReader = new PagePdfDocumentReader(resource, config);
                allDocuments.addAll(pdfDocumentReader.get());
            }
        } catch (IOException e) {
            log.error("PDF 文档加载失败", e);
        }

        return allDocuments;
    }

//    /**
//     * 加载多篇doc文档
//     */
//
//    public List<Document> loadDocuments() {
//        List<Document> allDocuments = new ArrayList<>();
//
//        try {
//            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.docx");
//
//            for (Resource resource : resources) {
//                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
//                        .withPagesPerDocument(1)
//                        .build();
//
//                PagePdfDocumentReader pdfDocumentReader = new PagePdfDocumentReader(resource, config);
//                allDocuments.addAll(pdfDocumentReader.get());
//            }
//        } catch (IOException e) {
//            log.error("PDF 文档加载失败", e);
//        }
//
//        return allDocuments;
//    }


}






