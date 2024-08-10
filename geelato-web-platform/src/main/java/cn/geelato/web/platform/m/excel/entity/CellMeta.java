package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;

/**
 *  占位符元数据
 *  用于word或excel的占位符替换
 */
@Setter
@Getter
public class CellMeta {
    private int index;

    private PlaceholderMeta placeholderMeta;

}
