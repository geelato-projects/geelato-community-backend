package cn.geelato.web.platform.m.file.enums;

public enum AttachmentMimeEnum {
    // 文档类
    PDF("pdf", "application/pdf"),
    WORD_DOC("doc", "application/msword"),
    WORD_DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    EXCEL_XLS("xls", "application/vnd.ms-excel"),
    EXCEL_XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPT_PPT("ppt", "application/vnd.ms-powerpoint"),
    PPT_PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    TXT("txt", "text/plain"),
    RTF("rtf", "application/rtf"),
    // 图片类
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    BMP("bmp", "image/bmp"),
    SVG("svg", "image/svg+xml"),
    WEBP("webp", "image/webp"),
    ICO("ico", "image/x-icon"),
    // 音频类
    MP3("mp3", "audio/mpeg"),
    WAV("wav", "audio/wav"),
    OGG("ogg", "audio/ogg"),
    // 视频类
    MP4("mp4", "video/mp4"),
    AVI("avi", "video/x-msvideo"),
    MOV("mov", "video/quicktime"),
    WMV("wmv", "video/x-ms-wmv"),
    FLV("flv", "video/x-flv"),
    MKV("mkv", "video/x-matroska"),
    // 压缩包类
    ZIP("zip", "application/zip"),
    X_ZIP("zip", "application/x-zip-compressed"),
    RAR("rar", "application/x-rar-compressed"),
    Z7("7z", "application/x-7z-compressed"),
    TAR("tar", "application/x-tar"),
    GZ("gz", "application/gzip"),
    TGZ("tgz", "application/gzip"),
    TAR_GZ("tar.gz", "application/gzip"),
    TAR_GZIP("tar.gzip", "application/gzip"),
    TAR_BZ2("tar.bz2", "application/x-bzip2"),
    // 编程类
    JAVA("java", "text/x-java-source"),
    JSON("json", "application/json"),
    XML("xml", "application/xml"),
    JS("js", "application/javascript"),
    TS("ts", "application/typescript"),
    CSS("css", "text/css"),
    HTML("html", "text/html"),
    YAML("yaml", "text/yaml"),
    YML("yml", "text/yaml"),
    SQL("sql", "text/x-sql"),
    // 文本类
    TEXT_XML("xml", "text/xml"),
    TEXT_JS("js", "text/javascript"),
    TEXT_JSON("json", "text/json"),
    TEXT_TS("ts", "text/typescript"),
    // 其他
    EXE("exe", "application/x-msdownload"),
    OTHER("其他", "application/octet-stream");

    private final String label;// 选项内容
    private final String value;// 选项值

    AttachmentMimeEnum(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
