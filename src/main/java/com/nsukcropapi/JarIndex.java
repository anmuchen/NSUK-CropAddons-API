package com.nsukcropapi;

import net.neoforged.fml.ModList;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 列出自 jar（或开发环境资源目录）里 {@code data/<modId>/<dir>/} 下的 json 文件名。
 *
 * <p>这样新增作物只要往那个目录丢一个 JSON，不用改主类、不用重新列文件名——
 * 整合包作者直接往模组 jar 里加文件也能生效。</p>
 */
public final class JarIndex {

    private JarIndex() {
    }

    public static List<String> jsonNames(Class<?> anchor, String modId, String dir) {
        List<String> out = new ArrayList<>();
        if (modId == null || dir == null) {
            return out;
        }
        String prefix = "data/" + modId + "/" + dir + "/";
        try {
            Path root = modRoot(modId, anchor);
            if (root == null) {
                NsukCropApi.LOGGER.warn("[nsukcropapi] 定位不到 {} 的模组文件", modId);
                return out;
            }
            if (Files.isDirectory(root)) {
                collectDirectory(root.resolve(modId).resolve(dir), prefix, out);
            } else {
                collectZip(root, prefix, out);
            }
        } catch (IOException | RuntimeException exception) {
            NsukCropApi.LOGGER.warn("[nsukcropapi] 扫描 {} 失败", prefix, exception);
        }
        Collections.sort(out);
        return out;
    }

    /** 模组 jar（开发环境则是资源目录）的路径。 */
    private static Path modRoot(String modId, Class<?> anchor) {
        try {
            return ModList.get().getModContainerById(modId)
                    .map(container -> container.getModInfo().getOwningFile().getFile().getFilePath())
                    .orElse(null);
        } catch (Throwable throwable) {
            // 开发环境 / 接口变动：退回用资源 URL 反推
            return rootFromResource(anchor, modId);
        }
    }

    /** 兜底：从 {@code union:/...!/data/<mod>/<dir>/} 这类 URL 里抠出 jar 路径。 */
    private static Path rootFromResource(Class<?> anchor, String modId) {
        if (anchor == null) {
            return null;
        }
        try {
            URL url = anchor.getResource("/data/" + modId + "/");
            if (url == null) {
                return null;
            }
            String spec = url.getPath();
            int bang = spec.indexOf("!/");
            if (bang < 0) {
                return null;
            }
            String jarPart = URLDecoder.decode(spec.substring(0, bang), StandardCharsets.UTF_8);
            if (jarPart.startsWith("file:")) {
                jarPart = jarPart.substring(5);
            }
            while (jarPart.startsWith("/") && jarPart.length() > 2 && jarPart.charAt(2) == ':') {
                jarPart = jarPart.substring(1);
            }
            return Paths.get(jarPart);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 开发环境 / 解包目录：资源就是普通文件夹。 */
    private static void collectDirectory(Path directory, String prefix, List<String> out) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) {
                out.add(path.getFileName().toString());
            }
        }
    }

    /** 生产环境：按前缀列 jar 内的 ZipEntry。 */
    private static void collectZip(Path jar, String prefix, List<String> out) throws IOException {
        if (!Files.isRegularFile(jar)) {
            return;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".json")) {
                    out.add(name.substring(prefix.length()));
                }
            }
        }
    }
}
