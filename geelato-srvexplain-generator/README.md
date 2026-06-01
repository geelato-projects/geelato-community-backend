# geelato-srvexplain-generator

用于静态扫描 `geelato-community` 全仓库的 Controller/RequestMapping，并生成仓库根目录下 `SrvExplain/` 的 Markdown 接口调用文档。

## 输出
- `SrvExplain/index.md`：全仓库索引
- `SrvExplain/<module>/README.md`：模块索引
- `SrvExplain/<module>/controllers/*.md`：按 Controller 维度的接口说明
- `SrvExplain/_meta/manifest.json`：统计信息
- `SrvExplain/_meta/conflicts.md`：重复 method+path 等问题清单

## 运行
- 默认会尝试从当前目录或父目录定位仓库根（`pom.xml` 中包含 `geelato-community`）
- 如无法定位，可使用 `--repoRoot` 指定仓库根目录

