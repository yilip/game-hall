package com.example.game.hall;

import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/files")
public class FileListController {

    private static final String BASE_DIR = "D:/claw";

    @GetMapping("/**")
    public Map<String, Object> listFiles(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI().replace("/api/files", "");
        if (path.isEmpty()) path = "/";

        Map<String, Object> result = new HashMap<>();
        result.put("path", path);

        try {
            Path requestedPath = Path.of(BASE_DIR + path).normalize();
            Path basePath = Path.of(BASE_DIR).normalize();

            if (!requestedPath.startsWith(basePath)) {
                result.put("error", "Access denied: path traversal detected");
                return result;
            }

            File dir = requestedPath.toFile();

            if (!dir.exists()) {
                result.put("error", "Path not found");
                return result;
            }

            if (dir.isFile()) {
                result.put("type", "file");
                result.put("name", dir.getName());
                result.put("size", dir.length());
                return result;
            }

            List<Map<String, Object>> items = new ArrayList<>();
            File[] files = dir.listFiles();
            if (files != null) {
                Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File f : files) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", f.getName());
                    item.put("isDir", f.isDirectory());
                    item.put("size", f.isDirectory() ? 0 : f.length());
                    items.add(item);
                }
            }
            result.put("type", "directory");
            result.put("items", items);
        } catch (Exception e) {
            result.put("error", "Invalid path: " + e.getMessage());
        }

        return result;
    }
}
