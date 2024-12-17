package html2word.test;

import my.html2file.html2word.service.Html2WordService;
import org.junit.Test;

/**
 * html转PDF测试
 *
 * @author 欧阳洁
 * @since 2018-05-28 15:35
 */
public class Html2WordTest {
    @Test
    public void html2word() throws Exception {
        Html2WordService html2WordService = new Html2WordService();// 未引入spring，手动实例化
        String path = html2WordService.excute("https://poi.apache.org/");
        System.out.println("word文件路径：" + path);
    }
}
