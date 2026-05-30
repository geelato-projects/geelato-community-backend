package cn.geelato.orm;

import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.command.SaveCommand;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.orm.executor.DefaultMetaCommandExecutor;
import cn.geelato.orm.support.OrmTestSupport;
import cn.geelato.orm.support.QueryCommandAdapter;
import cn.geelato.orm.support.SaveCommandAdapter;
import cn.geelato.orm.support.TestUserEntity;
import cn.geelato.orm.value.ValueRefs;
import cn.geelato.core.orm.Dao;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

public class MetaCommandExecutorTest extends OrmTestSupport {

//    @Test
    public void shouldReturnPagedResultAndClearOverrideDataSource() {
        Dao dao = Mockito.mock(Dao.class);
        DefaultMetaCommandExecutor executor = new DefaultMetaCommandExecutor(dao);
        QueryCommand command = QueryCommandAdapter.forList(
                MetaFactory.query(TestUserEntity.class)
                        .select(new String[]{"id", "name"})
                        .where(Filter.eq("delStatus", 0))
                        .page(1, 10)
        );
        List<Map<String, Object>> rows = List.of(Map.of("id", "1001", "name", "Alice"));

//        Mockito.when(dao.queryForMapList(Mockito.any())).thenReturn(rows);
        Mockito.when(dao.queryTotal(Mockito.any())).thenReturn(1L);

        PageResult<Map<String, Object>> result = executor.queryForPage(command, "portal");

        Assert.assertEquals(1L, result.getTotal());
        Assert.assertEquals(1, result.getRecords().size());
        Assert.assertEquals("Alice", result.getRecords().get(0).get("name"));
        Assert.assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }

//    @Test
    public void shouldSaveParentAndChildCommandsInsideOrmExecutor() {
        Dao dao = Mockito.mock(Dao.class);
        DefaultMetaCommandExecutor executor = new DefaultMetaCommandExecutor(dao);
        SaveCommand command = SaveCommandAdapter.fromInsert(
                MetaFactory.insert(TestUserEntity.class)
                        .value("name", "Alice")
                        .value("creator", ValueRefs.ctx("userId"))
                        .child("TestOrder", child -> child
                                .value("userId", ValueRefs.parent("id"))
                                .value("code", "SO-001"))
        );

//        Mockito.when(dao.save(Mockito.any())).thenReturn("PK-1", "PK-2");

        String savedId = executor.save(command, "portal");

        Assert.assertEquals("PK-1", savedId);
        Assert.assertEquals(1, command.getCommands().size());
        Assert.assertEquals("U1001", command.getValueMap().get("creator"));
        Assert.assertEquals(command.getPK(), command.getCommands().get(0).getValueMap().get("userId"));
//        Mockito.verify(dao, Mockito.times(2)).save(Mockito.any());
        Assert.assertNull(DynamicDataSourceHolder.getDataSourceKey());
    }
}
