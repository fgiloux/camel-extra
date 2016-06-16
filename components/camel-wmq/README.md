# Apache Camel IBM MQ component

This Apache Camel components allows you to deal directly with IBM MQ without using the JMS wrapping.
It natively uses the MQ API to consume and produce messages on the destinations.

This fork is a extenson of the camel-extra camel-wmq component. This extension points are:
* supports the configuration via a component instead of on a endpoint
* binding and client connectivity
* connection pooling
* local transaction support by a custom TransactionManager implementation

The component provides both consumer and producer endpoints.

## Building

In order to be able to build the component, you have to add the IBM MQ dependencies in your local Maven repository
using the `mvn install:install-file` command:

```
mvn install:install-file -DgroupId=com.ibm.mq -DartifactId=mqosgi -Dversion=8.0.0.4 -Dpackaging=jar -Dfile=/path/to/com.ibm.mq.osgi.java_8.0.0.4.jar
```

The usual installation path on a Linux system is /opt/mqm/java/lib/com.ibm.mq.osgi.java_8.0.0.4.jar

## Usage

### Component

Sample component setup

```
  <bean id="wmq" class="org.apacheextras.camel.component.wmq.WMQComponent">
  	<property name="config" ref="wmqConfig"/>
  	<property name="transactionManager" ref="transactionManager"/> 
  </bean>
  
  <bean id="wmqConfig" class="org.apacheextras.camel.component.wmq.WMQConfig">
  	<property name="queueManagerName" value="${queueManagerName}"/>
  	<property name="connectionMode" value="${connectionMode}"/>
  	<property name="queueManagerHostname" value="${hostname}"/>
  	<property name="queueManagerChannel" value="${channel}"/>
  	<property name="queueManagerPort" value="${port}"/>
  	<property name="queueManagerUsername" value="${userID}"/>
  	<property name="queueManagerPassword" value="${userPassword}"/>
  	<property name="connectionManager" ref="connectionManager"/>
  </bean>
  
  <bean id="connectionManager" class="com.ibm.mq.MQSimpleConnectionManager">
  	<property name="maxConnections" value="5"></property>
  </bean>
  
  <bean id="transactionManager" class="org.apacheextras.camel.component.wmq.WMQTransactionManager">
    <property name="config" ref="wmqConfig"/>
  </bean>
```

where:
* `wmq` is the component. It requires a `org.apacheextras.camel.component.wmq.WMQConfig` and a `org.apacheextras.camel.component.wmq.WMQTransactionManager`


* `wmqconfig` is a instance of `org.apacheextras.camel.component.wmq.WMQConfig` and requires
  * `queueManager`
  * `connectionMode` - either "binding" or "client"
  * if connectionMode is client then additional information is needed:
    * host
    * port
    * channel
    * username
    * password
  * `connectionManager` - a instance of `com.ibm.mq.MQSimpleConnectionManager` which can control IBM MQ connection pooling. If this is not specified a default pool is created.

* `transactionManager` is a instance of `org.apacheextras.camel.component.wmq.WMQTransactionManager` and requires a reference to `org.apacheextras.camel.component.wmq.WMQConfig`


### URI

The endpoint URI is:

```
wmq:type:name
```

where:
* `type` is optional and the destination type. It can be either `queue` or `topic` (if not provided, the component assumes
  it's a queue.
* `name` is the destination name.

Here's a couple of URI examples:

```
wmq:queue:foo
wmq:topic:bar
wmq:foo
```

## Message Body & Headers

The WMQ consumer endpoint populates the body of the Camel in message with the payload of the MQ message.

On the other hand, the WMQ producer endpoint sends a MQ message with payload populated with the Camel in message body.

Additionally, both endpoints support the following headers (the consumer populates these headers, and the producer
uses it if present):

* `mq.mqmd.format`: the message MQMD format
* `mq.mqmd.charset`: the message MQMD character set
* `mq.mqmd.expiry`: the message MQMD expiry
* `mq.mqmd.put.appl.name`: the message MQMD put application name
* `mq.mqmd.group.id`: the message MQMD group ID
* `mq.mqmd.msg.seq.number`: the message MQMD sequence number
* `mq.mqmd.msg.accounting.token`: the message MQMD accounting token
* `mq.mqmd.correl.id`: the message MQMD correlation ID
* `mq.mqmd.replyto.q`: the message MQMD ReplyTo queue name
* `mq.mqmd.replyto.q.mgr`: the message MQMD ReplyTo queue manager name
* `mq.rfh2.format`: the message RFH2 format (optional)
* `mq.rfh2.struct.id`: the message RFH2 struct ID
* `mq.rfh2.encoding`: the message RFH2 encoding
* `mq.rfh2.coded.charset.id`: the message RFH2 coded character set ID
* `mq.rfh2.flags`: the message RFH2 flags
* `mq.rfh2.version`: the message RFH2 version
* `mq.rfh2.folder.[FOLDER_NAME]`: the message RFH2 folder, where the [FOLDER_NAME] can be MCD, JMS, USR, PSC, PSCR, OTHER (depending of the content of the message).

## Installation in Apache Karaf

The camel-wmq component can be installed directly in Karaf. 

To install into Karaf a IBM MQ Client must be installed on the server. The native IBM MQ client libaries must then be made available to the Karaf instance, followed lastly by some IBM OSGi jar files.

Steps for installation on Karaf:
* Install a IBM MQ Client or Server. This is dependent on platform, please refer to IBM MQ documentation for details. Linux documentation can be found here https://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.ins.doc/q008640_.htm


* Install the IBM MQ native libraries in Karaf lib folder. The 2 files can be found in /opt/mqm/java/lib64 and are called libmqjbnd.so and libmqjexitstub02.so. We can do that with symbolic links by executing the following in the Karaf lib folder:
  ```
  ln -s /opt/mqm/java/lib64/libmqjbnd.so
	ln -s /opt/mqm/java/lib64/libmqjexitstub02.so
	```

* Install the IBM MQ OSGi jars into Karaf. These are included in a IBM MQ installation usually under `/opt/mqm/java/lib/OSGi'. The two files to install are:
  * com.ibm.mq.osgi.allclientprereqs_8.0.0.4.jar 
  * com.ibm.mq.osgi.allclient_8.0.0.4.jar (a wrap is required to import the packages org.xml.sax and org.xml.sax.helpers, these packages are provided by the JRE rt.jar, a alternative change would be to change the Karaf system bundle package exports to include these 2 packages as the system bundle is not providing these by default to all bundles)
  ```
  osgi:install 'wrap:file:/opt/mqm/java/lib/OSGi/com.ibm.mq.osgi.allclient_8.0.0.4.jar$overwrite=merge&Import-Package=\
        com.ibm.crypto.provider;resolution:=optional,\
        com.ibm.misc;resolution:=optional,\
        com.ibm.security.pkcs7;resolution:=optional,\
        com.ibm.security.pkcs9;resolution:=optional,\
        com.ibm.security.pkcsutil;resolution:=optional,\
        com.ibm.security.util;resolution:=optional,\
        com.ibm.security.x509;resolution:=optional,\
        com.sun.jndi.fscontext,\
        com.sun.jndi.ldap;resolution:=optional,\
        com.sun.jndi.toolkit.chars,\
        com.sun.jndi.toolkit.corba;resolution:=optional,\
        com.sun.jndi.toolkit.ctx;resolution:=optional,\
        com.sun.jndi.toolkit.dir;resolution:=optional,\
        com.sun.jndi.toolkit.url;resolution:=optional,\
        com.sun.jndi.url.file,\
        com.sun.jndi.url.jndi,\
        com.sun.jndi.url.ldap;resolution:=optional,\
        javax.crypto;resolution:=optional,\
        javax.crypto.spec;resolution:=optional,\
        javax.jms,\
        javax.management,\
        javax.naming,\
        javax.naming.directory,\
        javax.naming.event,\
        javax.naming.ldap,\
        javax.naming.spi,\
        javax.security.auth.x500;resolution:=optional,\
        org.osgi.framework,\
        javax.security.cert;resolution:=optional,\
        javax.net.ssl;resolution:=optional,\
        javax.net.ssl_0.0.0;resolution:=optional,\
        org.xml.sax,\
        org.xml.sax.helpers,\
        javax.xml.parsers'

	osgi:install file:/opt/mqm/java/lib/OSGi/com.ibm.mq.osgi.allclientprereqs_8.0.0.4.jar
  ```
  
