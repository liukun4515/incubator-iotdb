package cn.edu.thu.tsfiledb.service;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;

import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.edu.thu.tsfiledb.conf.TsFileDBConstant;
import cn.edu.thu.tsfiledb.exception.StartupException;

public class Daemon {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Daemon.class);
    static final Daemon instance = new Daemon();
    private JMXConnectorServer jmxServer;
    private MBeanServer mbs;
    
    public Daemon(){
	mbs = ManagementFactory.getPlatformMBeanServer();
    }
    
    public void active(){
	StartupChecks checks = new StartupChecks().withDefaultTest();
	try {
	    checks.verify();
	} catch (StartupException e) {
	    LOGGER.error("TsFileDB: failed to start because of some check fail. {}", e.getMessage());
	    return;
	}
	try {
	    setUp();
	} catch (MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException
		| NotCompliantMBeanException | TTransportException e) {
	    LOGGER.error("TsFileDB: failed to start because: {}",e.getMessage());
	}
    }
    
    private void setUp() throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, TTransportException{
	maybeInitJmx();
	registJDBCServer();
    }
    
    private void maybeInitJmx() {
	if (System.getProperty(TsFileDBConstant.REMOTE_JMX_PORT_NAME) != null) {
	    LOGGER.warn("JMX settings in tsfile-env.sh have been bypassed as the JMX connector server is "
		    + "already initialized. Please refer to tsfile-env.sh for JMX configuration info");
	    return;
	}
	System.setProperty(TsFileDBConstant.SERVER_RMI_ID, "true");
	boolean localOnly = false;
	String jmxPort = System.getProperty(TsFileDBConstant.TSFILEDB_REMOTE_JMX_PORT_NAME);
	
	if (jmxPort == null) {
	    localOnly = true;
	    jmxPort = System.getProperty(TsFileDBConstant.TSFILEDB_LOCAL_JMX_PORT_NAME);
	}

	if (jmxPort == null)
	    return;

	try {
	    jmxServer = JMXServerUtils.createJMXServer(Integer.parseInt(jmxPort), localOnly);
	    if (jmxServer == null)
		return;
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
    
    private void registJDBCServer() throws TTransportException, MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException{
	JDBCServerMBean mbean = new JDBCServer();
	mbean.startServer();
	ObjectName mBeanName = new ObjectName("JDBCServer", "type", "JDBCServer");
	mbs.registerMBean(mbean, mBeanName);
    }

    public static void main(String[] args) {
	Daemon daemon = new Daemon();
	daemon.active();
    }

}