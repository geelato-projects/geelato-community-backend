package cn.geelato.core.meta.spi;

/**
 * 为上层保留的 MetaStore 提供器扩展点。
 */
public interface MetaStoreProvider {

    MetaStore getMetaStore();
}
