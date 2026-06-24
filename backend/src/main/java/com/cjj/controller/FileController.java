package com.cjj.controller;

import com.cjj.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * city-review 图片上传控制器
 *
 * 上传路径：{uploadDir}/{date}/{uuid}.{ext}
 * 访问 URL：http://localhost:8081/uploads/{date}/{uuid}.{ext}
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
public class FileController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 上传单张图片
     * POST /api/upload/image
     * 返回可访问的 URL 路径
     */
    @PostMapping("/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file) {
        return doUpload(file);
    }

    /**
     * 上传多张图片（写点评专用）
     * POST /api/upload/images
     * 接收多个文件，返回 URL 列表
     */
    @PostMapping("/images")
    public Result uploadImages(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            Result r = doUpload(file);
            if (r.getCode() == 200 && r.getData() != null) {
                urls.add(r.getData().toString());
            }
        }

        if (urls.isEmpty()) {
            return Result.fail("所有图片上传失败");
        }

        log.info("city-review 批量图片上传成功 → {} 张", urls.size());
        return Result.ok(urls);
    }

    private Result doUpload(MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("请选择要上传的文件");
        }

        // 校验类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("仅支持上传图片文件");
        }

        // 校验大小（最大 10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            return Result.fail("图片大小不能超过 10MB");
        }

        try {
            // 按日期分目录：uploads/blogs/20260623/
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            // 生成唯一文件名
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String newName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ext;

            // 完整路径：uploads/blogs/{dateDir}/{filename}
            // 修复 Tomcat 相对路径问题：非绝对路径时用 user.dir 构建
            Path dirPath = Paths.get(uploadDir, "blogs", dateDir);
            if (!dirPath.isAbsolute()) {
                dirPath = Paths.get(System.getProperty("user.dir")).resolve(uploadDir).resolve("blogs").resolve(dateDir);
            }
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(newName);

            // 保存文件
            file.transferTo(filePath.toFile());

            // 返回可访问 URL
            String url = "/uploads/blogs/" + dateDir + "/" + newName;
            log.info("city-review 图片上传成功 → {}", url);
            return Result.ok(url);

        } catch (IOException e) {
            log.error("city-review 图片上传失败", e);
            return Result.fail("上传失败，请稍后再试");
        }
    }

    /**
     * 删除图片（可选）
     */
    @DeleteMapping("/image")
    public Result deleteImage(@RequestParam("path") String path) {
        try {
            // 安全检查：只允许删除 uploads 目录下的文件
            if (path == null || path.contains("..") || !path.startsWith("/uploads/")) {
                return Result.fail("非法路径");
            }

            Path filePath = Paths.get(uploadDir, path.replace("/uploads/", ""));
            File file = filePath.toFile();
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    return Result.ok("删除成功");
                }
            }
            return Result.fail("文件不存在或无法删除");
        } catch (Exception e) {
            return Result.fail("删除失败");
        }
    }
}
