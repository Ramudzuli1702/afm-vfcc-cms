package com.afmvfcc.utils;

import com.afmvfcc.db.DatabaseConnection;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Restores a .sql backup into the configured database. Shared by Settings and
 * the first-run setup wizard (which can restore straight after creating the DB).
 *
 * Uses the mysql command-line client when it can be found (fastest, handles
 * any mysqldump output), otherwise replays the file statement by statement over
 * JDBC. Either way, migrations run afterwards so an older backup is brought up
 * to the current schema.
 */
public class BackupRestore {

    public static void restore(File sqlFile) throws Exception {
        String mysql = findMysqlCli();
        if (mysql != null) restoreViaCli(mysql, sqlFile);
        else               restoreViaJdbc(sqlFile);
        DatabaseConnection.runMigrations();
    }

    private static void restoreViaCli(String mysqlPath, File sqlFile) throws Exception {
        String[] db = DatabaseConnection.connectionSettings(); // host, port, database, user, password
        ProcessBuilder pb = new ProcessBuilder(
                mysqlPath, "--host=" + db[0], "--port=" + db[1], "--user=" + db[3],
                "--default-character-set=utf8mb4", db[2]);
        // Password via environment, not the command line (visible in process lists)
        pb.environment().put("MYSQL_PWD", db[4]);
        pb.redirectInput(sqlFile);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
        }
        int exitCode = process.waitFor();
        if (exitCode != 0)
            throw new Exception("mysql exited with code " + exitCode + ":\n" + output);
    }

    private static void restoreViaJdbc(File sqlFile) throws Exception {
        List<String> statements = splitStatements(Files.readString(sqlFile.toPath(), StandardCharsets.UTF_8));
        Connection conn = DatabaseConnection.getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String sql : statements) {
                if (sql.toUpperCase().startsWith("DELIMITER")) continue;
                try {
                    stmt.execute(sql);
                } catch (SQLException ex) {
                    try { stmt.execute("SET FOREIGN_KEY_CHECKS = 1"); } catch (Exception ignored) {}
                    conn.rollback();
                    throw new Exception("SQL error on statement:\n" +
                            sql.substring(0, Math.min(120, sql.length())) +
                            "\n\nError: " + ex.getMessage(), ex);
                }
            }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            conn.commit();
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Splits a SQL script into statements on semicolons that are outside quotes,
     * backticks and comments — so text like meeting minutes containing ";" or
     * "--" survives. MySQL versioned comments (/*!40101 ... *&#47;) are kept as
     * statements; plain comments are dropped.
     */
    static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int n = script.length();
        char quote = 0; // ' " or ` while inside a quoted section
        for (int i = 0; i < n; i++) {
            char c = script.charAt(i);
            char next = i + 1 < n ? script.charAt(i + 1) : 0;
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && quote != '`' && i + 1 < n) { cur.append(next); i++; }
                else if (c == quote) {
                    if (next == quote) { cur.append(next); i++; } // doubled quote = escaped
                    else quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') { quote = c; cur.append(c); continue; }
            if (c == '-' && next == '-' && (i + 2 >= n || Character.isWhitespace(script.charAt(i + 2)))) {
                while (i < n && script.charAt(i) != '\n') i++;
                cur.append('\n');
                continue;
            }
            if (c == '#') {
                while (i < n && script.charAt(i) != '\n') i++;
                cur.append('\n');
                continue;
            }
            if (c == '/' && next == '*') {
                int end = script.indexOf("*/", i + 2);
                if (end < 0) end = n - 2;
                boolean versioned = i + 2 < n && script.charAt(i + 2) == '!';
                if (versioned) cur.append(script, i, end + 2);
                i = end + 1;
                continue;
            }
            if (c == ';') {
                String sql = cur.toString().trim();
                if (!sql.isEmpty()) out.add(sql);
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    /** Finds the mysql client (distinct from mysqldump), or null. */
    public static String findMysqlCli() {
        return findTool("mysql");
    }

    /** Finds mysqldump, or null. */
    public static String findMysqldump() {
        return findTool("mysqldump");
    }

    private static String findTool(String name) {
        try {
            if (new ProcessBuilder(name, "--version").start().waitFor() == 0) return name;
        } catch (Exception ignored) {}
        String exe = name + ".exe";
        List<File> candidates = new ArrayList<>();
        for (String root : new String[] { "C:\\Program Files\\MySQL", "C:\\Program Files (x86)\\MySQL" }) {
            File[] servers = new File(root).listFiles(File::isDirectory);
            if (servers != null) for (File s : servers) candidates.add(new File(s, "bin\\" + exe));
        }
        candidates.add(new File("C:\\xampp\\mysql\\bin\\" + exe));
        File[] wamp = new File("C:\\wamp64\\bin\\mysql").listFiles(File::isDirectory);
        if (wamp != null) for (File w : wamp) candidates.add(new File(w, "bin\\" + exe));
        for (String p : new String[] { "/usr/local/bin/", "/opt/homebrew/bin/", "/usr/local/mysql/bin/" })
            candidates.add(new File(p + name));
        for (File f : candidates) if (f.isFile()) return f.getAbsolutePath();
        return null;
    }
}
