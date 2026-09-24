package cn.geelato.web.platform.srv.announcement;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公告仓库读取器：直接读取远程 git 仓库（geelato.announcement.git.repo-uri）根目录的
 * markdown 文件——fetch 到 JGit 内存仓库后遍历提交树读取 blob，**不落盘、无本地目录**。
 * 每次读取即一次完整拉取，结果由调用方缓存在内存中（拉取时机：应用启动与手动 refresh）。
 * <p>仅支持 https（file 协议供测试）；私有仓库配 username + PAT。失败抛
 * {@link IllegalStateException}（仓库地址落日志，密码不落日志），不做降级。</p>
 *
 * @author geelato
 */
@Slf4j
@Component
public class AnnouncementGitSynchronizer {

    private final AnnouncementGitProperties properties;

    public AnnouncementGitSynchronizer(AnnouncementGitProperties properties) {
        this.properties = properties;
    }

    /**
     * 读取远程仓库根目录全部 .md 文件（仅根层，不递归子目录）。
     *
     * @return 文件名（含扩展名） -> UTF-8 正文；仓库内无 md 文件时返回空 Map
     * @throws IllegalStateException 未配置仓库地址或拉取/读取失败（带仓库地址与原因）
     */
    public synchronized Map<String, String> fetchMarkdownFiles() {
        if (!properties.enabled()) {
            throw new IllegalStateException("公告 git 直连未配置：geelato.announcement.git.repo-uri 为空");
        }
        DfsRepositoryDescription desc = new DfsRepositoryDescription("announcement");
        // Builder 显式设置 FS：InMemoryRepository 默认 FS 为 null，file:// 等本地传输
        // 协议（TransportLocal）依赖它；https 传输不使用 FS，设置无副作用
        InMemoryRepository.Builder builder = new InMemoryRepository.Builder();
        builder.setRepositoryDescription(desc);
        builder.setFS(FS.detect());
        try (InMemoryRepository repo = builder.build()) {
            try (Git git = new Git(repo)) {
                git.fetch()
                        .setRemote(properties.getRepoUri())
                        .setRefSpecs(new RefSpec("+" + Constants.R_HEADS + properties.getBranch()
                                + ":" + Constants.R_HEADS + properties.getBranch()))
                        .setCredentialsProvider(credentialsProvider())
                        .call();
            }
            Ref head = repo.exactRef(Constants.R_HEADS + properties.getBranch());
            if (head == null) {
                throw new IllegalStateException("公告仓库分支不存在: " + properties.getRepoUri()
                        + " 分支 " + properties.getBranch());
            }
            Map<String, String> files = new LinkedHashMap<>();
            try (RevWalk revWalk = new RevWalk(repo);
                 TreeWalk treeWalk = new TreeWalk(repo)) {
                RevCommit commit = revWalk.parseCommit(head.getObjectId());
                RevTree tree = commit.getTree();
                treeWalk.addTree(tree);
                // 非递归模式：仅枚举根层条目，子目录以目录条目出现但不深入
                while (treeWalk.next()) {
                    if (treeWalk.isSubtree()) {
                        continue;
                    }
                    String name = treeWalk.getNameString();
                    if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
                        byte[] bytes = treeWalk.getObjectReader().open(treeWalk.getObjectId(0)).getBytes();
                        files.put(name, new String(bytes, StandardCharsets.UTF_8));
                    }
                }
            }
            log.info("[公告] 已读取公告仓库 {} 分支 {}（commit {}），根目录 md 文件 {} 个",
                    properties.getRepoUri(), properties.getBranch(),
                    head.getObjectId().abbreviate(8).name(), files.size());
            return files;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取公告仓库失败: " + properties.getRepoUri()
                    + " 分支 " + properties.getBranch(), e);
        }
    }

    /** 凭证：username/password 任一非空即启用（PAT），都空返回 null（公开仓库匿名） */
    private CredentialsProvider credentialsProvider() {
        boolean hasUser = properties.getUsername() != null && !properties.getUsername().isBlank();
        boolean hasPassword = properties.getPassword() != null && !properties.getPassword().isBlank();
        if (!hasUser && !hasPassword) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider(
                hasUser ? properties.getUsername() : "",
                hasPassword ? properties.getPassword() : "");
    }
}
