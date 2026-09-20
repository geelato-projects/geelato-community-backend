package cn.geelato.web.platform.srv.announcement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前公告（目录内日期最新一份 markdown 文件）。
 *
 * @author geelato
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementInfo {

    /** 随机数主键（文件名首段），前端已读 localStorage 键 {id}_{userId}_read 的组成部分 */
    private String id;

    /** 公告标题（文件名中段） */
    private String title;

    /** 公告日期（文件名尾段，统一格式化为 yyyy-MM-dd，兼容 yyyyMMdd） */
    private String date;

    /** 公告文件名（含扩展名） */
    private String fileName;

    /** markdown 原文，由前端渲染 */
    private String content;
}
