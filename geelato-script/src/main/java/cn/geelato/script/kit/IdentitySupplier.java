package cn.geelato.script.kit;

import cn.geelato.script.core.IdentitySnapshot;

/**
 * 身份快照来源（缺省读调用线程 SecurityContext；宿主可替换来源或注入异步身份）。
 */
@FunctionalInterface
public interface IdentitySupplier {

    IdentitySnapshot get();
}
