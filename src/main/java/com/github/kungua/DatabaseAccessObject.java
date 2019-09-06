package com.github.kungua;

import java.sql.*;

public class DatabaseAccessObject {
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "root";
    private final Connection connection;

    public DatabaseAccessObject() {
        try {
            this.connection = DriverManager.getConnection("jdbc:h2:file:E:\\hcspx\\tp\\Crawler\\news", USER_NAME, PASSWORD);
        } catch (SQLException e) {
            throw new RuntimeException();
        }
    }

    private String getNextLink(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                return resultSet.getString(1);
            }
        }
        return null;
    }

    public String getNextLinkThenDelete() throws SQLException {
        String link = getNextLink("select *\n" +
                "from LINKS_TO_BE_PROCESSED limit 1");
        if (link != null) {
            updateDatabase(link, "delete\n" +
                    "from LINKS_TO_BE_PROCESSED\n" +
                    "where LINK = ?");
        }
        return link;
    }

    public void updateDatabase(String link, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }

    public void insertNewsIntoDatabase(String url, String title, String content) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into NEWS (title, content, url, created_at, modified_at)\n" +
                "values (?, ?, ?, now(), now())")) {
            statement.setString(1, title);
            statement.setString(2, content);
            statement.setString(3, url);
            statement.executeUpdate();
        }
    }

    public boolean isLinkProcessed(String link) throws SQLException {
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
}
