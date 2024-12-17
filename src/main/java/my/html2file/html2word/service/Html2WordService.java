package my.html2file.html2word.service;

import my.html2file.utils.BaseUtils;
import my.html2file.utils.DownloadUtils;
import my.html2file.utils.FilesUtils;
import my.html2file.utils.PathUtils;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * html 转 word 服务
 *
 * @author 欧阳洁
 * @since 2018-06-14 11:31
 */
@Service
public class Html2WordService {
    protected static final Logger logger = LoggerFactory.getLogger(Html2WordService.class);

    /**
     * 解析生成PDF
     *
     * @param pageUrl
     * @return
     */
    public String excute(String pageUrl) throws Exception {
        String outputPath = new StringBuilder("/output/").append(BaseUtils.getDateStr("yyyyMMdd"))
                .append("/word/").append(BaseUtils.uuid2()).append(".doc").toString();
        boolean success = convert(pageUrl, outputPath);
        if (success) {
            return outputPath;
        } else {
            if (FilesUtils.isExistNotCreate(outputPath)) {
                return outputPath;
            } else {
                throw new RuntimeException("转化异常！[" + outputPath + "]");
            }
        }
    }

    /**
     * html 转 word
     *
     * @param pageUrl
     * @param outputPath
     * @return
     */
    private boolean convert(String pageUrl, String outputPath) {
        String absoluteOutputPath = PathUtils.getClassRootPath(outputPath);
        FilesUtils.checkFolderAndCreate(absoluteOutputPath);
        // 拼一个标准的HTML格式文档
        String content;
        try {
            content = getHtmlDealed(pageUrl);
            // System.out.println(content);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        try (InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
             OutputStream os = new FileOutputStream(absoluteOutputPath);
             POIFSFileSystem fs = new POIFSFileSystem()) {
            // 对应于org.apache.poi.hdf.extractor.WordDocument
            fs.createDocument(is, "WordDocument");
            fs.writeFilesystem(os);
            return true;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * 处理html字符串
     *
     * @param pageUrl
     * @return
     */
    private String getHtmlDealed(String pageUrl) throws IOException {
        String html = DownloadUtils.getContentFromUrl(pageUrl);
        if (BaseUtils.isBlank(html)) {
            return html;
        }
        // 对样式文件处理
        html = loadCssFileStrToHtml(pageUrl, html);
        // 对图片文件处理
        html = loadImgHttpSrcToHtml(pageUrl, html);
        int indexHead = html.indexOf("<head>");
        if (indexHead > 0) {
            StringBuilder newHtmlBud = new StringBuilder();
            newHtmlBud.append(html, 0, indexHead + 6);
            newHtmlBud.append("<meta charset=\"UTF-8\">");
            return newHtmlBud.append(html.substring(indexHead + 6)).toString();
        } else {
            return html;
        }
    }

    /**
     * 获取 带 http 的 domain
     *
     * @param pageUrl
     * @return
     */
    private String getDomainWithHttp(String pageUrl) {
        int mIndex = pageUrl.indexOf(":");
        if (mIndex > 0) {
            int firstXIndex = pageUrl.indexOf("/", mIndex + 3);
            if (firstXIndex > 0) {
                return pageUrl.substring(0, firstXIndex);
            }
        }
        return pageUrl;
    }

    /**
     * html文档对link标签进行处理，改成<style></style>
     *
     * @param pageUrl
     * @param htmlText
     * @return
     */
    private String loadCssFileStrToHtml(String pageUrl, String htmlText) throws IOException {
        String linkCssHref;
        // <link type="text/css" href="skin/basic.css" rel="stylesheet">
        String regEx_link = "<link.*?\\bhref *= *[\"']([^\"']*?)[\"'][^>]*?>";
        Pattern p_link = Pattern.compile(regEx_link, Pattern.CASE_INSENSITIVE);
        Matcher m_link = p_link.matcher(htmlText);
        StringBuffer html_sb = new StringBuffer();
        String domain = getDomainWithHttp(pageUrl);
        while (m_link.find()) {
            linkCssHref = m_link.group(1);
            if (BaseUtils.isBlank(linkCssHref) || !linkCssHref.endsWith(".css")) {
                m_link.appendReplacement(html_sb, m_link.group());
            } else {
                if (linkCssHref.startsWith("//")) {
                    if (domain.startsWith("https")) {
                        linkCssHref = "https:" + linkCssHref;
                    } else {
                        linkCssHref = "http:" + linkCssHref;
                    }
                } else if (linkCssHref.startsWith("/")) {
                    linkCssHref = domain + linkCssHref;
                } else if (!linkCssHref.startsWith("http")) {
                    if (pageUrl.endsWith("/")) {
                        linkCssHref = pageUrl + linkCssHref;
                    } else {
                        linkCssHref = pageUrl + "/" + linkCssHref;
                    }
                }
                logger.info("处理内联样式表：{}", linkCssHref);
                String cssStr = DownloadUtils.getContentFromUrl(linkCssHref);
                String styleCssTag = "<style>" + cssStr + "<style>";
                m_link.appendReplacement(html_sb, styleCssTag);
            }
        }
        m_link.appendTail(html_sb);
        return html_sb.toString();
    }

    /**
     * html文档对img标签进行处理，其src属性使用相对路径时，将其替换为绝对路径
     *
     * @param pageUrl
     * @param htmlText
     * @return
     */
    private String loadImgHttpSrcToHtml(String pageUrl, String htmlText) {
        String regEx_img = "<img.*?\\bsrc *= *[\"']([^\"']*?)[\"'][^>]*?>";
        Pattern p_img = Pattern.compile(regEx_img, Pattern.CASE_INSENSITIVE);
        Matcher m_img = p_img.matcher(htmlText);
        StringBuffer html_sb = new StringBuffer();
        String domain = getDomainWithHttp(pageUrl);
        while (m_img.find()) {
            // 得到<img .../>数据
            String imgSrc = m_img.group(1);
            if (BaseUtils.isBlank(imgSrc) || imgSrc.startsWith("http")) {
                m_img.appendReplacement(html_sb, m_img.group());
            } else {
                if (imgSrc.startsWith("//")) {
                    if (domain.startsWith("https")) {
                        imgSrc = "https:" + imgSrc;
                    } else {
                        imgSrc = "http:" + imgSrc;
                    }
                } else if (imgSrc.startsWith("/")) {
                    imgSrc = domain + imgSrc;
                } else {
                    if (pageUrl.endsWith("/")) {
                        imgSrc = pageUrl + imgSrc;
                    } else {
                        imgSrc = pageUrl + "/" + imgSrc;
                    }
                }
                logger.info("处理图片：{}", imgSrc);
                String img_Tag = m_img.group().replace(m_img.group(1), imgSrc);
                //*** 匹配<img>中的src数据,并替换为本地服务http的地址
                m_img.appendReplacement(html_sb, img_Tag);
            }
        }
        m_img.appendTail(html_sb);
        return html_sb.toString();
    }
}
