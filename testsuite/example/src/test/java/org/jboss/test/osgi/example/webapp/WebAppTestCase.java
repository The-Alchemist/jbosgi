/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.osgi.example.webapp;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.jar.JarFile;

import javax.inject.Inject;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.test.osgi.example.webapp.bundle.EndpointServlet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.startlevel.StartLevel;

/**
 * A test that deployes a WAR bundle
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Oct-2009
 */
@RunWith(Arquillian.class)
public class WebAppTestCase {

    @Inject
    public BundleContext context;

    @Inject
    public Bundle bundle;

    @Deployment
    public static WebArchive createdeployment() {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, "example-webapp");
        archive.addClasses(EndpointServlet.class);
        archive.addAsWebResource("webapp/message.txt", "message.txt");
        archive.addAsWebInfResource("webapp/web.xml", "web.xml");
        // [SHRINKWRAP-278] WebArchive.setManifest() results in WEB-INF/classes/META-INF/MANIFEST.MF
        archive.add(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addManifestHeader(Constants.BUNDLE_CLASSPATH, ".,WEB-INF/classes");
                builder.addManifestHeader("Web-ContextPath", "example-webapp");
                builder.addImportPackages(StartLevel.class, HttpServlet.class, Servlet.class);
                return builder.openStream();
            }
        }, JarFile.MANIFEST_NAME);
        return archive;
    }

    @Test
    public void testServletAccess() throws Exception {
        bundle.start();
        String line = getHttpResponse("/example-webapp/servlet?test=plain", 5000);
        assertEquals("Hello from Servlet", line);
    }

    @Test
    public void testServletInitProps() throws Exception {
        bundle.start();
        String line = getHttpResponse("/example-webapp/servlet?test=initProp", 5000);
        assertEquals("initProp=SomeValue", line);
    }

    @Test
    public void testResourceAccess() throws Exception {
        bundle.start();
        String line = getHttpResponse("/example-webapp/message.txt", 5000);
        assertEquals("Hello from Resource", line);
    }

    private String getHttpResponse(String reqPath, int timeout) throws IOException {
        String host = "localhost";
        int port = 8090;

        int fraction = 200;
        String line = null;
        IOException lastException = null;
        while (line == null && 0 < (timeout -= fraction)) {
            try {
                URL url = new URL("http://" + host + ":" + port + reqPath);
                BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                line = br.readLine();
                br.close();
            } catch (IOException ex) {
                lastException = ex;
                try {
                    Thread.sleep(fraction);
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        if (line == null && lastException != null)
            throw lastException;

        return line;
    }
}
