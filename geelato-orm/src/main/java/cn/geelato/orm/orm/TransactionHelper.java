package cn.geelato.orm.orm;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

public class TransactionHelper {

    public static TransactionStatus beginTransaction(DataSourceTransactionManager transactionManager){
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionManager.getTransaction(def);
    }

    public static void commitTransaction(DataSourceTransactionManager transactionManager,TransactionStatus status){
        transactionManager.commit(status);
    }

    public static void rollbackTransaction(DataSourceTransactionManager transactionManager,TransactionStatus status){
        transactionManager.rollback(status);
    }
}
