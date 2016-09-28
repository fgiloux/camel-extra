package org.apacheextras.camel.component.wmq;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.features.FeaturesService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.security.auth.Subject;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.WrappedUrlProvisionOption.OverwriteMode;

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class providing functionalities to setup and configure
 * a Fuse / Karaf container.
 * This class is based on the code in the class KarafTestSupport of the Apache Karaf project.
 * 
 * @author fgiloux
 *
 */

public class FuseWMQTestSupport extends CamelTestSupport {

	// note, for this to work, you must download and put fuse in your maven repository in the location
    // specified by the maven coordinates here
    public static final String GROUP_ID = "org.jboss.fuse";
    public static final String ARTIFACT_ID = "jboss-fuse-full";
    public static final String VERSION = "6.2.1.redhat-107";

    public static final String RMI_SERVER_PORT = "54445";
    public static final String HTTP_PORT = "10081";
    public static final String RMI_REG_PORT = "2100";
    
    static final Long COMMAND_TIMEOUT = 10000L;
    static final Long SERVICE_TIMEOUT = 30000L;
    
    protected Logger LOG = LoggerFactory.getLogger(getClass());
    
    @Inject
	protected FeaturesService featuresService;
    
    @Inject
    @org.ops4j.pax.exam.util.Filter(value="(camel.context.name=wmq)", timeout=30000)
    protected CamelContext camelContext;
    
    @Inject
    protected BundleContext bundleContext;
    
    ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    public boolean isCreateCamelContextPerClass() {
        // we override this method and return true, to tell Camel test-kit that
        // it should only create CamelContext once (per class), so we will
        // re-use the CamelContext and the injected producers between each test method in this class
        return true;
    }
        
	@Configuration 
	public static Option[] configuration() throws Exception{
		return new Option[] {
			karafDistributionConfiguration()
				.frameworkUrl(maven().groupId(GROUP_ID).artifactId(ARTIFACT_ID).version(VERSION).type("zip"))
				.karafVersion("2.3.0")
				.useDeployFolder(false)
                .name("JBoss Fuse")
                .unpackDirectory(new File("target/paxexam/unpack")),
            configureConsole().ignoreLocalConsole(),
            KarafDistributionOption.doNotModifyLogConfiguration(),
            KarafDistributionOption.replaceConfigurationFile("etc/org.ops4j.pax.logging.cfg", new File(
					"src/test/resources/org.ops4j.pax.logging.cfg")),
            editConfigurationFilePut("etc/config.properties", "karaf.startup.message", "Loading Fuse from: ${karaf.home}"),
            editConfigurationFilePut("etc/org.ops4j.pax.web.cfg", "org.osgi.service.http.port", HTTP_PORT),
            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiRegistryPort", RMI_REG_PORT),
            editConfigurationFilePut("etc/org.apache.karaf.management.cfg", "rmiServerPort", RMI_SERVER_PORT),
            editConfigurationFilePut("etc/users.properties", "admin", "admin,admin"),
            features(maven().groupId("org.apache.camel.karaf").artifactId("apache-camel").versionAsInProject().classifier("features").type("xml"),
                    "camel-test"),
            //KarafDistributionOption.logLevel(LogLevel.TRACE)
                        
            // Install the MQ driver
            //CoreOptions.wrappedBundle("file:src/test/lib/com.ibm.mq.osgi.allclientprereqs_8.0.0.5.jar").overwriteManifest(OverwriteMode.MERGE).exports("*; version=1.1.0.1"),
            //CoreOptions.bundle("file:src/test/lib/com.ibm.mq.osgi.allclient_8.0.0.5.jar"),
            
            // Install camel-wmq bundle
            //mavenBundle().groupId("org.apache-extras.camel-extra-ext").artifactId("camel-wmq").versionAsInProject().type("jar"),

            // Build and start custom bundle                
        	streamBundle(bundle()
        			.add("OSGI-INF/blueprint/blueprint.xml",
							new File("src/test/resources/OSGI-INF/blueprint/blueprint.xml").toURI().toURL())
					//.add("log4j.properties", new File("src/test/resources/log4j.properties").toURI().toURL())			
					.set(Constants.BUNDLE_SYMBOLICNAME,"${package}")
					.set(Constants.IMPORT_PACKAGE, "*")
					.build()).start(),
        	
            
            // enable this if you want to keep the exploded directories of fuse after the tests are run
            keepRuntimeFolder()
		};
		
	}

	@Override
    protected void doPreSetup() throws Exception {
		assertNotNull(camelContext);
    }
	
	@Override
	public void doPostSetup() throws Exception {
        // Assert that the required features have been installed
    	assertTrue(featuresService.isInstalled(featuresService.getFeature("camel-core")));    
    	assertTrue(featuresService.isInstalled(featuresService.getFeature("camel-test")));
        
    	// Use these for debugging when the container configuration seems to cause trouble
//    	System.err.println(executeCommand("features:list", new RolePrincipal("admin")));
//    	System.err.println(executeCommand("camel:route-list", new RolePrincipal("admin")));
//    	System.err.println(executeCommand("osgi:list", new RolePrincipal("admin")));
//    	System.err.println(executeCommand("web:list", new RolePrincipal("admin")));
    }

	/**
	* Executes a shell command and returns output as a String.
	* Commands have a default timeout of 10 seconds.
	*
	* @param command
	* @param principals The principals (e.g. RolePrincipal objects) to run the command under
     * @return
	*/
	protected String executeCommand(final String command, Principal ... principals) {
	   return executeCommand(command, COMMAND_TIMEOUT, false, principals);
	}
	
	/**
	* Executes a shell command and returns output as a String.
	* Commands have a default timeout of 10 seconds.
	*
	* @param command The command to execute.
	* @param timeout The amount of time in millis to wait for the command to execute.
	* @param silent  Specifies if the command should be displayed in the screen.
	* @param principals The principals (e.g. RolePrincipal objects) to run the command under
	*/
	protected String executeCommand(final String command, final Long timeout, final Boolean silent, final Principal ... principals) {

        waitForCommandService(command);
        String response;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArrayOutputStream);
        final Callable<String> commandCallable = new Callable<String>() {
            @Override
            public String call() throws Exception {
                try {
                    if (!silent) {
                        LOG.error(command);
                    }
                    final CommandProcessor commandProcessor = getOsgiService(CommandProcessor.class);
                    final CommandSession commandSession = commandProcessor.createSession(System.in, printStream, System.err);
                    commandSession.execute(command);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                printStream.flush();
                return byteArrayOutputStream.toString();
            }
        };

        FutureTask<String> commandFuture;
        if (principals.length == 0) {
            commandFuture = new FutureTask<String>(commandCallable);
        } else {
            // If principals are defined, run the command callable via Subject.doAs()
            commandFuture = new FutureTask<String>(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    Subject subject = new Subject();
                    subject.getPrincipals().addAll(Arrays.asList(principals));
                    return Subject.doAs(subject, new PrivilegedExceptionAction<String>() {
                        @Override
                        public String run() throws Exception {
                            return commandCallable.call();
                        }
                    });
                }
            });
        }

        try {
            executor.submit(commandFuture);
            response = commandFuture.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            response = "SHELL COMMAND TIMED OUT: ";
        }
        return response;
    }
	
	protected <T> T getOsgiService(Class<T> type, long timeout) {
	   return getOsgiService(type, null, timeout);
	}
	
	protected <T> T getOsgiService(Class<T> type) {
	   return getOsgiService(type, null, SERVICE_TIMEOUT);
	}

    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        ServiceTracker tracker = null;
        try {
            String flt;
            if (filter != null) {
                if (filter.startsWith("(")) {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
                } else {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
                }
            } else {
                flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
            }
            Filter osgiFilter = FrameworkUtil.createFilter(flt);
            tracker = new ServiceTracker(bundleContext, osgiFilter, null);
            tracker.open(true);
            // Note that the tracker is not closed to keep the reference
            // This is buggy, as the service reference may change i think
            Object svc = type.cast(tracker.waitForService(timeout));
            if (svc == null) {
                Dictionary dic = bundleContext.getBundle().getHeaders();
                LOG.error("Test bundle headers: " + explode(dic));

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
                    LOG.error("ServiceReference: " + ref);
                }

                for (ServiceReference ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
                    LOG.error("Filtered ServiceReference: " + ref);
                }

                throw new RuntimeException("Gave up waiting for service " + flt);
            }
            return type.cast(svc);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Invalid filter", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    

    private void waitForCommandService(String command) {
        // the commands are represented by services. Due to the asynchronous nature of services they may not be
        // immediately available. This code waits for the services to be available, in their secured form. It
        // means that the code waits for the command service to appear with the roles defined.
    
        if (command == null || command.length() == 0) {
            return;
        }
       
        int spaceIdx = command.indexOf(' ');
        if (spaceIdx > 0) {
            command = command.substring(0, spaceIdx);
        }
        int colonIndx = command.indexOf(':');
        
        try {
            if (colonIndx > 0) {
                String scope = command.substring(0, colonIndx);
                String function = command.substring(colonIndx + 1);
                waitForService("(&(osgi.command.scope=" + scope + ")(osgi.command.function=" + function + ")(org.apache.karaf.service.guard.roles=*))", SERVICE_TIMEOUT);
            } else {
                waitForService("(&(osgi.command.function=" + command + ")(org.apache.karaf.service.guard.roles=*))", SERVICE_TIMEOUT);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
        
    private void waitForService(String filter, long timeout) throws InvalidSyntaxException,
        InterruptedException {
        
        ServiceTracker st = new ServiceTracker(bundleContext,
                                               bundleContext.createFilter(filter),
                                               null);
        
        try {
            st.open();
            st.waitForService(timeout);
        } finally {
            st.close();
        }
    }
	
    /*
     * Explode the dictionary into a ,-delimited list of key=value pairs
     */
     private static String explode(Dictionary dictionary) {
         Enumeration keys = dictionary.keys();
         StringBuffer result = new StringBuffer();
         while (keys.hasMoreElements()) {
             Object key = keys.nextElement();
             result.append(String.format("%s=%s", key, dictionary.get(key)));
             if (keys.hasMoreElements()) {
                 result.append(", ");
             }
         }
         return result.toString();
     }

     /**
      * Provides an iterable collection of references, even if the original array is null
      */
     private static Collection<ServiceReference> asCollection(ServiceReference[] references) {
         return references != null ? Arrays.asList(references) : Collections.<ServiceReference>emptyList();
     }
     
	/*
	protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        WMQComponent c = new WMQComponent(camelContext);
        c.setConnectionMode("connection");
        c.setQueueManagerHostname("192.168.33.10");
        c.setQueueManagerPort("1414");
        c.setQueueManagerUserID("mqm");
        c.setQueueManagerPassword("4pple4pple123");
        c.setQueueManagerChannel("test2.channel");
        c.setQueueManagerName("venus.queue.manager");
        camelContext.addComponent("wmq", c);
        return camelContext;
    }
	
	@Produce(uri = "direct:start")
    ProducerTemplate template;
	
	@Test
    public void testSendNotMatchingMessage() throws Exception {
        template.sendBody("Sending message");
    }
	
	@Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
            	from("direct:start").to("wmq:queue:REDHAT.TMP.QUEUE");
            }
        };
    }*/
}
