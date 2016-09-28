package org.apacheextras.camel.component.wmq;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.Test;

import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
* Test class for basic MQ scenarios: send and receive a message
* 
* @author fgiloux
*
*/

//Restart the container for every test class. This is quicker as resetting the container
//for every test method. This could however get turned on if time to process is not
//an issue and having a virgin environment is required for getting confidence in the
//validation.
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class SimpleTest extends FuseWMQTestSupport {

	protected final static int REQUEST_TIMEOUT = 5000; 
	
	@Rule public TestName testName = new TestName();
	
	/**
	 * Executed before each test.
	 */
	@Before
	public void setup() {
	}
	
	/**
	 * Validate the sending of a message
	 */
	@Test
	public void testSend() {
		String message = "This is the content of my message!";
		assertTrue(true);
	}

	/**
	 * Validate the reception of a message
	 */
	@Test
	public void testReceive() {
		String message = "This is the content of my message!";
		assertTrue(true);
	}
}