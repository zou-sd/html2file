package my.html2file.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载相关工具类
 *
 * @author 欧阳洁
 * @create 2017-08-14 11:53
 **/
public class DownloadUtils {
    protected static final Logger logger = LoggerFactory.getLogger(DownloadUtils.class);

    /**
     * 从网络Url中下载文件
     *
     * @param urlStr
     * @param fileName
     * @param savePath
     * @throws IOException
     */
    public static void downLoadFromUrl(String urlStr, String fileName, String savePath) throws IOException {
        downLoadFromUrl(urlStr, fileName, savePath, 3000);
    }

    /**
     * 从网络Url中下载文件
     *
     * @param urlStr
     * @param fileName
     * @param savePath
     * @throws IOException
     */
    public static void downLoadFromUrl(String urlStr, String fileName, String savePath, int timeout) throws IOException {
        String data = getContentFromUrl(urlStr, timeout);
        if (data == null) {
            logger.error("文件下载失败！");
            return;
        }
        // 文件保存位置
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            boolean success;
            do {
                success = saveDir.mkdirs();
            } while (!success);
        }
        File file = new File(saveDir + File.separator + fileName);
        try (FileWriter fileWriter = new FileWriter(file);
             BufferedWriter writer = new BufferedWriter(fileWriter)) {
            writer.write(data);
        }
        logger.info("[" + urlStr + "] 下载成功！");
    }

    /**
     * 从网络Url读取内容
     *
     * @param urlStr
     * @throws IOException
     */
    public static String getContentFromUrl(String urlStr) throws IOException {
        return getContentFromUrl(urlStr, 3000);
    }

    /**
     * 从网络Url读取内容
     *
     * @param urlStr
     * @return
     */
    public static String getContentFromUrl(String urlStr, int timeout) throws IOException {
        logger.info("开始读取：{}", urlStr);
        HttpURLConnection conn = getHttpURLConnection(urlStr, timeout);
        if (conn == null) {
            return null;
        }
        // 获取响应状态码
        int statusCode = conn.getResponseCode();
        // 如果返回值正常，数据在网络中是以流的形式得到服务端返回的数据
        String data = "";
        if (statusCode == 200) { // 正常响应
            // 从流中读取响应信息
            byte[] bytes = null;
            // 得到输入流
            try (InputStream inputStream = conn.getInputStream()) {
                // 获取字节数组
                bytes = readInputStream(inputStream);
            } catch (IOException e) {
                logger.error("读取异常！", e);
            }
            if (bytes == null || bytes.length == 0) {
                data = "";
            } else {
                data = new String(bytes, StandardCharsets.UTF_8);
            }
            logger.info("[{}] 读取成功！", urlStr);
        } else if (statusCode >= 300 && statusCode < 400) { // 状态码为重定向时，获取重定向目标的URL
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                logger.info("原始请求地址：{}，请求重定向地址：{}", conn.getURL().toString(), redirectUrl);
                data = getContentFromUrl(redirectUrl, 3000);
            }
        } else {
            logger.error("请求失败：url:{} status:{}", conn.getURL().toString(), statusCode);
        }
        // 断开连接，释放资源
        conn.disconnect();
        return data;
    }

    private static HttpURLConnection getHttpURLConnection(String siteUrl, int timeout) {
        URL url;
        try {
            url = new URL(siteUrl);
        } catch (MalformedURLException e) {
            logger.error("地址无效！", e);
            return null;
        }
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            logger.error("打开连接异常！", e);
            return null;
        }

        // 设置超时间为3秒
        conn.setConnectTimeout(timeout);
        // 设置请求头中的 User-Agent，防止屏蔽程序抓取而返回403错误
        conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0; Windows NT; DigExt)");
        // 设置超时时间
        conn.setConnectTimeout(timeout);
        // 设置是否使用缓存
        // connection.setUseCaches(true);
        // 设置此 HttpURLConnection 实例是否应该自动执行 HTTP 重定向，默认为true
        conn.setInstanceFollowRedirects(false);
        // 设置是否从HttpUrlConnection读入
        // connection.setDoInput(true);
        // 设置是否向HttpURLConnection输出
        // connection.setDoOutput(false);

        // 连接
        try {
            conn.connect();
        } catch (IOException e) {
            logger.error("连接异常！", e);
            return null;
        }
        return conn;
    }

    /**
     * 从输入流中获取字节数组
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static byte[] readInputStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        byte[] buffer = new byte[1024];
        int len;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            while ((len = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }
}
