
package com.hooya.util;

import lombok.SneakyThrows;
import com.itextpdf.html2pdf.HtmlConverter;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.PicturesManager;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.apache.poi.hwpf.usermodel.PictureType;
import org.apache.poi.xwpf.converter.core.FileImageExtractor;
import org.apache.poi.xwpf.converter.xhtml.XHTMLConverter;
import org.apache.poi.xwpf.converter.xhtml.XHTMLOptions;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @AUTHOR majiang
 * @DATE 2024/12/17 15:28
 **/
public class WordToPDF {

    @SneakyThrows
    public static void main(String[] args) {
        String inputFilePath = "E:\\新表设计.docx";
        String outputFilePath = "E:\\新表设计.pdf";

        try {
            convertWordToPdf(inputFilePath, outputFilePath);
            System.out.println("Conversion successful!");
        } catch (IOException | InvalidFormatException e) {
            e.printStackTrace();
        }
    }

    public static void convertWordToPdf(String inputFilePath, String outputFilePath) throws IOException, InvalidFormatException {
        // 确定文件类型并加载相应的文档
        if (inputFilePath.toLowerCase().endsWith(".docx")) {
            try (FileInputStream fis = new FileInputStream(inputFilePath)) {
                XWPFDocument document = new XWPFDocument(OPCPackage.open(fis));

                // 创建临时 HTML 文件
                Path tempHtmlPath = Files.createTempFile("temp", ".html");
                File tempHtmlFile = tempHtmlPath.toFile();

                // 将 XWPF 文档转换为 XHTML
                XHTMLOptions options = XHTMLOptions.create();
                options.setExtractor(new FileImageExtractor(tempHtmlFile.getParentFile()));
                try (OutputStream out = new FileOutputStream(tempHtmlFile)) {
                    XHTMLConverter.getInstance().convert(document, out, options);
                }

                // 将 XHTML 转换为 PDF
                try (FileInputStream htmlInputStream = new FileInputStream(tempHtmlFile);
                     FileOutputStream pdfOutputStream = new FileOutputStream(outputFilePath)) {
                    HtmlConverter.convertToPdf(htmlInputStream, pdfOutputStream);
                }

                // 删除临时 HTML 文件
                Files.deleteIfExists(tempHtmlPath);
            } catch (NullPointerException e) {
                System.err.println("处理 .docx 文件时发生空指针异常: " + e.getMessage());
                e.printStackTrace(); // 打印详细的异常信息
            } catch (IOException e) {
                System.err.println("读取文件时发生 I/O 异常: " + e.getMessage());
            } catch (InvalidFormatException e) {
                System.err.println("无效的文件格式: " + e.getMessage());
            }
        } else if (inputFilePath.toLowerCase().endsWith(".doc")) {
            try (FileInputStream fis = new FileInputStream(inputFilePath)) {
                HWPFDocument document = new HWPFDocument(fis);

                // 创建临时 HTML 文件
                Path tempHtmlPath = Files.createTempFile("temp", ".html");
                File tempHtmlFile = tempHtmlPath.toFile();

                org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                WordToHtmlConverter wordToHtmlConverter = new WordToHtmlConverter(doc);
                wordToHtmlConverter.setPicturesManager(new PicturesManager() {
                    @Override
                    public String savePicture(byte[] content, PictureType pictureType, String suggestedName, float widthInches, float heightInches) {
                        // 确保图片文件夹存在
                        File imgFolder = new File(tempHtmlFile.getParentFile(), "images");
                        if (!imgFolder.exists()) {
                            imgFolder.mkdirs();
                        }
                        File imgFile = new File(imgFolder, suggestedName);
                        try (FileOutputStream fos = new FileOutputStream(imgFile)) {
                            fos.write(content);
                        } catch (IOException e) {
                            System.err.println("保存图片时发生 I/O 异常: " + e.getMessage());
                            e.printStackTrace();
                        }
                        return "images/" + suggestedName;
                    }
                });
                wordToHtmlConverter.processDocument(document);

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.transform(new DOMSource(doc), new StreamResult(new OutputStreamWriter(new FileOutputStream(tempHtmlFile), "UTF-8")));

                // 将 XHTML 转换为 PDF
                try (FileInputStream htmlInputStream = new FileInputStream(tempHtmlFile);
                     FileOutputStream pdfOutputStream = new FileOutputStream(outputFilePath)) {
                    HtmlConverter.convertToPdf(htmlInputStream, pdfOutputStream);
                }

                // 删除临时 HTML 文件
                Files.deleteIfExists(tempHtmlPath);
            } catch (NullPointerException e) {
                System.err.println("处理 .doc 文件时发生空指针异常: " + e.getMessage());
                e.printStackTrace(); // 打印详细的异常信息
            } catch (IOException e) {
                System.err.println("读取文件时发生 I/O 异常: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("处理 .doc 文件时发生异常: " + e.getMessage());
            }
        } else {
            System.err.println("不支持的文件类型: " + inputFilePath);
        }
    }
}