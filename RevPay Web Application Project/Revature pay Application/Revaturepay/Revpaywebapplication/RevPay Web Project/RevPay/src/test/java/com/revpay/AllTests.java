package com.revpay;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        UserRepositoryTest.class,
        WalletRepositoryTest.class,
        TransactionServiceImplTest.class,
        WalletServiceImplTest.class
})
public class AllTests {
   
}
