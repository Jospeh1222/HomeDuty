package org.HomeDuty.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // pgAdmin'deki veri tabanı bilgilerine göre burayı düzenle
    private static final String URL = "jdbc:postgresql://localhost:5432/homeduty";
    private static final String USER = "postgres";
    private static final String PASS = "372024"; // Kendi şifreni yaz

    public static Connection getConnection() throws SQLException {
        try {
            // Sürücüyü yükle (Maven bağımlılığı ekli olmalı)
            Class.forName("org.postgresql.Driver");
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL Sürücüsü bulunamadı!", e);
        }
    }
}