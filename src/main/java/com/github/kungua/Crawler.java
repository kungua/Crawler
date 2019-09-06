package com.github.kungua;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class Crawler extends Thread {
    private CrawlerDao dao;

    public Crawler(CrawlerDao dao) {
        this.dao = dao;
    }

    @Override
    public void run() {
        try {
            String link;
//            先从数据库里拿出来一个链接(拿出来并从数据库中删除掉) 准备处理之
            while ((link = dao.getNextLinkThenDelete()) != null) {
                if (dao.isLinkProcessed(link)) {
                    continue;
                }
                if (isInterestingLink(link)) {
                    Document doc = HttpGetAndParseHtml(link);
                    parseUrlsFromPageAndStoreIntoDatabase(doc);
                    storeIntoDatabaseIfItIsNewsPage(doc, link);
                    dao.insertProcessedLink(link);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void parseUrlsFromPageAndStoreIntoDatabase(Document doc) throws SQLException {
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");
            href = DownloadPictureHelper.wrapLinkWithProtocolIfLinkNotHas(href);
            if (!href.toLowerCase().startsWith("javascript")) {
                dao.insertLinkToBeProcessed(href);
            }
        }
    }


    private void storeIntoDatabaseIfItIsNewsPage(Document doc, String link) throws SQLException {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag :
                    articleTags) {
                String title = articleTag.child(0).text();
                String content = doc.select("article").html();
                parseAndDownloadImage(doc, title);
                dao.insertNewsIntoDatabase(link, title, content);
            }
        }
    }

    private void parseAndDownloadImage(Document doc, String title) {
        // 将相关图片下载到目录中
        ArrayList<Element> imgTagList = doc.select("article").select("p > img");
        // java for each 竟然不提供 index
        for (Element imgTag :
                imgTagList) {
            int index = imgTagList.indexOf(imgTag);
            String url = DownloadPictureHelper.wrapLinkWithProtocolIfLinkNotHas(imgTag.attr("src"));
            DownloadPictureHelper.downloadPicture(url, title, index);
        }
    }

    private static Document HttpGetAndParseHtml(String link) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        System.out.println("handle link " + link);

        HttpGet httpGet = new HttpGet(link);
        httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0");

        try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
            HttpEntity entity1 = response.getEntity();
            String html = EntityUtils.toString(entity1);
            return Jsoup.parse(html);
        }
    }

    private static boolean isInterestingLink(String link) {
        return (isNewsPage(link) || isIndexPage(link)) &&
                isNotLoginPage(link);
    }

    private static boolean isIndexPage(String link) {
        return "https://sina.cn".equals(link);
    }

    private static boolean isNewsPage(String link) {
        return link.contains("news.sina.cn");
    }

    private static boolean isNotLoginPage(String link) {
        return !link.contains("passport.sina.cn");
    }
}
