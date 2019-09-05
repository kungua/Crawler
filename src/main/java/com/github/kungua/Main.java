package com.github.kungua;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class Main {
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";

    private static String getNextLink(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                return resultSet.getString(1);
            }
        }
        return null;
    }

    private static String getNextLinkThenDelete(Connection connection) throws SQLException {
        String link = getNextLink(connection, "select *\n" +
                "from LINKS_TO_BE_PROCESSED limit 1");
        if (link != null) {
            updateDatabase(connection, link, "delete\n" +
                    "from LINKS_TO_BE_PROCESSED\n" +
                    "where LINK = ?");
        }
        return link;
    }

    @SuppressFBWarnings("DMI_CONSTANT_DB_PASSWORD")
    public static void main(String[] args) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:file:E:\\hcspx\\tp\\Crawler\\news", USER_NAME, PASSWORD);
        String link;
//            先从数据库里拿出来一个链接(拿出来并从数据库中删除掉) 准备处理之
        while ((link = getNextLinkThenDelete(connection)) != null) {
            if (isLinkProcessed(connection, link)) {
                continue;
            }
            if (isInterestingLink(link)) {
                Document doc = HttpGetAndParseHtml(link);
                parseUrlsFromPageAndStoreIntoDatabase(connection, doc);
                storeIntoDatabaseIfItIsNewsPage(connection, doc, link);
                updateDatabase(connection, link, "insert into LINKS_TO_ALREADY_PROCESSED (LINK) values (?)");
            }
        }
    }

    private static void parseUrlsFromPageAndStoreIntoDatabase(Connection connection, Document doc) throws SQLException {
        for (Element aTag : doc.select("a")) {
            String href = aTag.attr("href");
            if (href.startsWith("//")) {
                href = "https:" + href;
            }
            if (!href.toLowerCase().startsWith("javascript")) {
                updateDatabase(connection, href, "insert into LINKS_TO_BE_PROCESSED (LINK) values (?)");
            }
        }
    }

    private static boolean isLinkProcessed(Connection connection, String link) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select *\n" +
                "from LINKS_TO_ALREADY_PROCESSED where LINK = ?")) {
            statement.setString(1, link);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void updateDatabase(Connection connection, String link, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }


    private static void storeIntoDatabaseIfItIsNewsPage(Connection connection, Document doc, String link) throws SQLException {
        ArrayList<Element> articleTags = doc.select("article");
        if (!articleTags.isEmpty()) {
            for (Element articleTag :
                    articleTags) {
                String title = articleTag.child(0).text();
                String content = articleTag.select("p").stream().map(Element::text).collect(Collectors.joining("\n"));
                System.out.println("title = " + title);
                try (PreparedStatement statement = connection.prepareStatement("insert into NEWS (title, content, url, created_at, modified_at)\n" +
                        "values (?, ?, ?, now(), now())")) {
                    statement.setString(1, title);
                    statement.setString(2, content);
                    statement.setString(3, link);
                    statement.executeUpdate();
                }
            }
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
