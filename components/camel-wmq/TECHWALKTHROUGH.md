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
* The exchange is routed to direct:test and then logged which ends the routing.
* The exchange is finished which causes the transacted EIP to invoke the TransactionManager.
    4. The transaction manager doCommit is invoked
    5. The transaction object and MQQueueManager for the transaction is retrieved
    6. The MQQueueManager's commit function is invoked.
  

## TransactionManager

A custom transaction manager called WMQTransactionManager has been implemented by extending the Spring PlatformTransactionManager. The purpose of this class is to provide transactional support to the native IBM MQ interface.

The DatasourceTransactionManager provides a good example - https://github.com/spring-projects/spring-framework/blob/master/spring-jdbc/src/main/java/org/springframework/jdbc/datasource/DataSourceTransactionManager.java

The transaction manager is responsible for 
* Creating a transaction
* Committing a transaction
* Rolling back a transaction

### Transaction manager facts

* A transaction manager can manage many transactions. 
* Transactions may occur simultaneously.
* There is 1 MQQueueManager per transaction

If multiple transactions/threads are running then the interaction with the transaction manager will be interweaved. 
For example 
* Transaction 1 - begin
* Transaction 2 - begin
* Transaction 2 - commit
* Transaction 1 - commit

It is important that every time a thread wishes to interact with the transaction manager the correct transaction is bound to the transaction manager. To support this the abstract method doGetTransaction is implemented. 

This method is responsible for swapping between the transaction contexts based, this is achieved by looking up the transaction resources from the current thread using the TransactionSynchronisationManager.


### TransactionSynchronizationManager

This is a utility class which binds transaction resources to threads, in our case we bind a MQQueueManager instance and a unique id. 

The JavaDoc for this class can be found at 
http://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html

The MQQueueManager (and id) are binded to the thread in the TransactionManager's doBegin() method. The MQQueueManager is then retrieved via a TransactionSynchronizationManager.getResource() in the Producer and Consumer. Using this approach the Producer and Consumer delegate the responsibility for transactional awareness.

Similarly the MQQueueMananger (and id) are retrieved from the thread in the TransactionManager's doCommit() and doRollback() methods. Once these methods have completed the MQQueueManager is then released using the TransactionSynchronizationManager.unbind() function.

