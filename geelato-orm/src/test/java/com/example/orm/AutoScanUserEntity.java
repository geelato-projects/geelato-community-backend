package com.example.orm;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;

@Entity(name = "AutoScanUser", table = "auto_scan_user")
public class AutoScanUserEntity {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;
}

