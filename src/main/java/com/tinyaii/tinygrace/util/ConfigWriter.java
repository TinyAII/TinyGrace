package com.tinyaii.tinygrace.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 只改 config.yml 里某一行的值，保留注释与排版。
 *
 * <p>为什么不直接用 {@code plugin.saveConfig()}：Bukkit 保存配置时会把整份文件的注释
 * 全部吃掉，而这份配置的注释是写给服主看的，吃掉一次就废了。
 */
public final class ConfigWriter {

    private ConfigWriter() {
    }

    /**
     * 找到以 keyPrefix 开头的第一个非注释行，整行替换。
     *
     * @return true = 替换成功；false = 没找到
     */
    public static boolean replaceLine(File file, String keyPrefix, String newLine) throws IOException {
        if (file == null || !file.isFile()) {
            return false;
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith(keyPrefix)) {
                lines.set(i, newLine);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            return false;
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        return true;
    }
}
