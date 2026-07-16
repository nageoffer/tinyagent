package com.nageoffer.ai.tinyagent.react.rag;

import com.nageoffer.ai.tinyagent.react.EmbeddingClient;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class KnowledgeImporter {

    private final DataSource dataSource;
    private final EmbeddingClient embeddingClient;

    public KnowledgeImporter(DataSource dataSource, EmbeddingClient embeddingClient) {
        this.dataSource = dataSource;
        this.embeddingClient = embeddingClient;
    }

    public void importFromResources(String resourceDir) {
        clearAll();

        URL dirUrl = getClass().getClassLoader().getResource(resourceDir);
        if (dirUrl == null) {
            throw new IllegalArgumentException("资源目录不存在：" + resourceDir);
        }

        Path dirPath;
        try {
            dirPath = Path.of(dirUrl.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("资源目录路径解析失败：" + resourceDir, e);
        }

        List<Path> txtFiles;
        try (Stream<Path> paths = Files.list(dirPath)) {
            txtFiles = paths
                    .filter(p -> p.toString().endsWith(".txt"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("读取资源目录失败：" + resourceDir, e);
        }

        System.out.println("[知识导入] 找到 " + txtFiles.size() + " 个文档");
        int totalChunks = 0;

        for (Path file : txtFiles) {
            String fileName = file.getFileName().toString();
            String fileContent;
            try {
                fileContent = Files.readString(file);
            } catch (IOException e) {
                System.out.println("[知识导入] 读取失败，跳过：" + fileName);
                continue;
            }

            List<String> chunks = splitByParagraph(fileContent);
            System.out.println("[知识导入] " + fileName + " → " + chunks.size() + " 个片段");

            for (String chunk : chunks) {
                double[] embedding = embeddingClient.embed(chunk);
                insertChunk(fileName, chunk, embedding);
                totalChunks++;
            }
        }
        System.out.println("[知识导入] 完成，共导入 " + totalChunks + " 个片段");
    }

    private void clearAll() {
        String sql = "DELETE FROM knowledge_chunk";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                System.out.println("[知识导入] 清空旧数据 " + deleted + " 条");
            }
        } catch (SQLException e) {
            throw new RuntimeException("清空知识库失败", e);
        }
    }

    private List<String> splitByParagraph(String content) {
        String[] paragraphs = content.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 10) {
                chunks.add(trimmed);
            }
        }
        return chunks;
    }

    private void insertChunk(String source, String content, double[] embedding) {
        String sql = "INSERT INTO knowledge_chunk (source, content, embedding) "
                + "VALUES (?, ?, ?::vector)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            ps.setString(2, content);
            ps.setString(3, toVectorString(embedding));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("写入知识片段失败", e);
        }
    }

    private String toVectorString(double[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
