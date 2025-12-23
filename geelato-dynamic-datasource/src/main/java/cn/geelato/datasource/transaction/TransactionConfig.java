package cn.geelato.datasource.transaction;


import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;


@Configuration
public class TransactionConfig {
    @Bean(name = "userTransaction")
    public UserTransaction userTransaction() throws SystemException {
        UserTransactionImp userTransactionImp = new UserTransactionImp();
        userTransactionImp.setTransactionTimeout(300);
        return userTransactionImp;
    }

    @Bean(name = "atomikosTransactionManager", initMethod = "init", destroyMethod = "close")
    public UserTransactionManager atomikosTransactionManager() throws SystemException {
        UserTransactionManager txManager = new UserTransactionManager();
        txManager.setForceShutdown(false);
        // 与UserTransaction超时保持一致
        txManager.setTransactionTimeout(60);
        // 启用事务恢复（核心：处理悬挂事务）
        txManager.setStartupTransactionService(true);
        // 多实例唯一标识：避免事务日志冲突
        System.setProperty("com.atomikos.icatch.tm_unique_name", "dynamic-xa-tm");
        // 事务日志持久化目录（生产建议挂载持久化存储）
        System.setProperty("com.atomikos.icatch.log_base_dir", "./atomikos-logs");
        return txManager;
    }

    @Bean(name = "dynamicDataSourceTransactionManager")
    @DependsOn({"DynamicDataSourceUserTransaction", "atomikosTransactionManager"})
    public PlatformTransactionManager transactionManager() {
        try {
            UserTransaction userTx = userTransaction();
            UserTransactionManager atomikosTxManager = atomikosTransactionManager();

            JtaTransactionManager jtaTxManager = new JtaTransactionManager(userTx, atomikosTxManager);
            jtaTxManager.setAllowCustomIsolationLevels(true);
            jtaTxManager.setTransactionSynchronization(AbstractPlatformTransactionManager.SYNCHRONIZATION_ALWAYS);
            jtaTxManager.setTransactionManagerName("dynamic-xa-tm");

            return jtaTxManager;
        } catch (SystemException e) {
            throw new RuntimeException("Atomikos 事务管理器初始化失败", e);
        }
    }
}
