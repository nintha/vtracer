package none.nintha.vtracer.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpSender {
    private static final Logger logger = LoggerFactory.getLogger(HttpSender.class);

    /**
     * 向指定URL发送GET方法的请求
     *
     * @param url 发送请求的URL
     * @return URL 所代表远程资源的响应结果
     */
    public static String get(String url) {
        String html = null;
        try {
            OkHttpClient client = new OkHttpClient();//创建OkHttpClient对象
            Request request = new Request.Builder().url(url).build();
            Response response = client.newCall(request).execute();//得到Response 对象
            if (response.isSuccessful()) {
                html = response.body().string();
            }
        } catch (Exception e) {
            logger.error("", e);
        }
        return html;
    }


    public static void main(String[] args) {
        String html = get("http://www.bilibili.com/video/av22758735");
        System.out.println(html);
    }
}
