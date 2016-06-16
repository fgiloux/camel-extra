# Technical walkthrough of code

This walkthrough will take you throw a working example of how the component works in particular to the transaction support.

A sample route may look like the following:

```xml
<route id="producer">
  <from uri="timer:foo?period=1000"/>
  <transacted ref="PROPAGATION_REQUIRED"/>
  <setBody>
    <constant>Transacted Message test</constant>
  </setBody>
  <setHeader headerName="mq.put.options">
    <constant>MQGMO_SYNCPOINT</constant>
  </setHeader>
  <log message="The message contains ${body} and is being pushed to queue"/>
  <to uri="wmq:queue:REDHAT.TMP.QUEUE"/>
  <to uri="direct:test"/>
</route>

<route>
  <from uri="direct:test"/>
  <log message="logged here!"/>
</route>

```
A second route is used here to show the transaction support is used across routes.

* Timer foo is invoked and creates a Exchange.
* The transacted EIP is invoked which causes several interactions with the TransactionManager.
    1. The transaction manager doGetTransaction is invoked
    2. The transaction manager doBegin is invoked
* The body/header is set
* The WMQProducer process method is invoked which:
    1. Gets the MQQueueManager from the ThreadSynchronisationManager
    2. Uses this MQQueueManager to open/put/close on a destination
* The exchange is routed to direct:test and then logged
* The exchange is finished which causes the transacted EIP to invoke the TransactionManager.
    4. The transaction manager doCommit is invoked
    5. The transaction object and MQQueueManager for the transaction is retrieved
    6. The MQQueueManager's commit function is invoked.
  

## TransactionManager

A custom transaction manager for native IBM MQI has been implemented by extending the Spring PlatformTransactionManager. The DatasourceTransactionManager provides a good example - https://github.com/spring-projects/spring-framework/blob/master/spring-jdbc/src/main/java/org/springframework/jdbc/datasource/DataSourceTransactionManager.java

The transaction manager is responsible for 
* Creating a transaction
* Committing a transaction
* Rolling back a transaction

### Transaction manager facts

* A transaction manager can manage many transactions. 
* Transactions may occur simultaneously.
* There is 1 MQQueueManager per transaction

In order for the transaction manager to identify the correct transaction to commit when there are simultaneous transactions occuring a transaction object is used in conjunction with the methods doGetTransaction.

The doGetTransaction method is responsible for creating a transaction object.

### TransactionSynchronizationManager

This is a utility class which binds resources to threads. Documentation can be found at 
http://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html

The main use of this class is to bind transactional resources to be used. 

Our use of this class is to bind a MQQueueManager to the transaction thread such that it can be used in the Consumer/Producer. This binding takes places in the TransactionManager's doBegin() method. 

The MQQueueManager is then unbound from the thread in the TransactionManager's doCommit() or doRollback() method.
