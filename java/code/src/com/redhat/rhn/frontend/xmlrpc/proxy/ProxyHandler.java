/**
 * Copyright (c) 2008 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 * 
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation. 
 */
package com.redhat.rhn.frontend.xmlrpc.proxy;

import com.redhat.rhn.common.client.ClientCertificate;
import com.redhat.rhn.common.client.ClientCertificateDigester;
import com.redhat.rhn.common.client.InvalidCertificateException;
import com.redhat.rhn.domain.server.Server;
import com.redhat.rhn.frontend.xmlrpc.BaseHandler;
import com.redhat.rhn.frontend.xmlrpc.InvalidProxyVersionException;
import com.redhat.rhn.frontend.xmlrpc.MethodInvalidParamException;
import com.redhat.rhn.frontend.xmlrpc.ProxyAlreadyRegisteredException;
import com.redhat.rhn.frontend.xmlrpc.ProxyNotActivatedException;
import com.redhat.rhn.frontend.xmlrpc.ProxySystemIsSatelliteException;
import com.redhat.rhn.manager.entitlement.EntitlementManager;
import com.redhat.rhn.manager.system.SystemManager;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

/**
 * ProxyHandler
 * @version $Rev$
 * @xmlrpc.namespace proxy
 * @xmlrpc.doc Provides methods to activate/deactivate a proxy 
 * server.
 */
public class ProxyHandler extends BaseHandler {
    private static Logger log = Logger.getLogger(ProxyHandler.class);

    /**
     * Test, if the system identified by the given client certificate, is proxy.
     * @param clientcert client certificate of the system.
     * @return 1 if system is proxy, 0 otherwise.
     * @throws MethodInvalidParamException thrown if certificate is invalid.
     *
     * @xmlrpc.doc Test, if the system identified by the given client
     * certificate i.e. systemid file, is proxy.
     * @xmlrpc.param #param_desc("string", "systemid", "systemid file")
     * @xmlrpc.returntype #return_int_success()
     */
    public int isProxy(String clientcert)
        throws MethodInvalidParamException {

        StringReader rdr = new StringReader(clientcert);
        Server srvr = null;
        
        ClientCertificate cert;
        try {
            cert = ClientCertificateDigester.buildCertificate(rdr);
            srvr = SystemManager.lookupByCert(cert);
        }
        catch (IOException ioe) {
            log.error("IOException - Trying to access a system with an " +
                    "invalid certificate", ioe);
            throw new MethodInvalidParamException();
        }
        catch (SAXException se) {
            log.error("SAXException - Trying to access a " +
                    "system with an invalid certificate", se);
            throw new MethodInvalidParamException();
        }
        catch (InvalidCertificateException e) {
            log.error("InvalidCertificateException - Trying to access a " +
                    "system with an invalid certificate", e);
            throw new MethodInvalidParamException();
        }
        return (srvr.isProxy() ? 1 : 0);
    }
    
    /**
     * Deactivates the system identified by the given client certificate.
     * @param clientcert client certificate of the system.
     * @return 1 if the deactivation succeeded, 0 otherwise.
     * @throws ProxyNotActivatedException thrown if server is not a proxy.
     * @throws MethodInvalidParamException thrown if certificate is invalid.
     * 
     * @xmlrpc.doc Deactivates the proxy identified by the given client
     * certificate i.e. systemid file.
     * @xmlrpc.param #param_desc("string", "systemid", "systemid file")
     * @xmlrpc.returntype #return_int_success()
     */
    public int deactivateProxy(String clientcert)
        throws ProxyNotActivatedException, MethodInvalidParamException {
        
        StringReader rdr = new StringReader(clientcert);
        try {
            ClientCertificate cert = ClientCertificateDigester.buildCertificate(rdr);
            Server srvr;
            try {
                srvr = SystemManager.lookupByCert(cert);
            }
            catch (InvalidCertificateException e) {
                log.error("Trying to access a system with an invalid certificate", e);
                throw new MethodInvalidParamException();
            }

            if (!srvr.isProxy()) {
                throw new ProxyNotActivatedException();
            }
            
            SystemManager.deactivateProxy(srvr);
            return 1;
        }
        catch (IOException e) {
            log.error("Problem reading certificate", e);
            throw new ProxyNotActivatedException(e);
        }
        catch (SAXException e) {
            log.error("Problem parsing certificate", e);
            throw new ProxyNotActivatedException(e);
        }
    }
    
    /**
     * Activates the proxy identified by the given client certificate.
     * @param clientcert client certificate of the system.
     * @param version Proxy version
     * @return 1 if the deactivation succeeded, 0 otherwise.
     * @throws ProxyAlreadyRegisteredException thrown if system has already been
     * registered.
     * @throws MethodInvalidParamException thrown if certificate is invalid.
     * @throws ProxySystemIsSatelliteException thrown if client certificate is
     * for a satellite
     * @throws InvalidProxyVersionException thrown if version is not supported.
     * 
     * @xmlrpc.doc Activates the proxy identified by the given client
     * certificate i.e. systemid file.
     * @xmlrpc.param #param_desc("string", "systemid", "systemid file")
     * @xmlrpc.param #param_desc("string", "version", "Version of proxy to be 
     * registered.")
     * @xmlrpc.returntype #return_int_success()
     */
    public int activateProxy(String clientcert, String version)
        throws ProxyAlreadyRegisteredException, MethodInvalidParamException,
               ProxySystemIsSatelliteException, InvalidProxyVersionException {
        
        StringReader rdr = new StringReader(clientcert);
        try {
            ClientCertificate cert = ClientCertificateDigester.buildCertificate(rdr);
            Server srvr = SystemManager.lookupByCert(cert);
            
            if (srvr.isProxy()) {
                throw new ProxyAlreadyRegisteredException();
            }
            
            // if the server does nto have enterprise_entitled entitlement, add it
            //
            
            if (!srvr.hasEntitlement(EntitlementManager.MANAGEMENT)) {
                SystemManager.entitleServer(srvr, EntitlementManager.MANAGEMENT);
            }
            SystemManager.activateProxy(srvr, version);
            return 1;
        }
        catch (InvalidCertificateException e) {
            log.error("Trying to access a system with an invalid certificate", e);
            throw new MethodInvalidParamException();
        }
        catch (IOException e) {
            log.error("Problem reading certificate", e);
        }
        catch (SAXException e) {
            log.error("Problem parsing certificate", e);
        }

        return 0;
    }
}
