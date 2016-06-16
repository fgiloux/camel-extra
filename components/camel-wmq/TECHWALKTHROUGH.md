# Technical walkthrough of code

A sample route may look like the following:

```
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
