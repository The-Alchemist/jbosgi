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
package org.jboss.test.osgi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.VersionRange;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.util.tracker.ServiceTracker;


/**
 * OSGi integration test support.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-May-2011
 */
public final class FrameworkUtils {

    // Hide ctor
    private FrameworkUtils() {
    }

    public static Bundle[] getBundles(BundleContext context, String symbolicName, VersionRange versionRange) {
        List<Bundle> result = new ArrayList<Bundle>();
        if (Constants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(symbolicName) && versionRange == null) {
            result.add(context.getBundle(0));
        } else {
            for (Bundle aux : context.getBundles()) {
                if (symbolicName == null || symbolicName.equals(aux.getSymbolicName())) {
                    if (versionRange == null || versionRange.includes(aux.getVersion())) {
                        result.add(aux);
                    }
                }
            }
        }
        return !result.isEmpty() ? result.toArray(new Bundle[result.size()]) : null;
    }

    public static int getFrameworkStartLevel(BundleContext context)  {
        return context.getBundle().adapt(FrameworkStartLevel.class).getStartLevel();
    }

    public static void setFrameworkStartLevel(BundleContext context, int level) throws InterruptedException, TimeoutException {
        setFrameworkStartLevel(context, level, 10, TimeUnit.SECONDS);
    }

    public static void setFrameworkStartLevel(BundleContext context, final int level, long timeout, TimeUnit units) throws InterruptedException, TimeoutException {
        final FrameworkStartLevel startLevel = context.getBundle().adapt(FrameworkStartLevel.class);
        if (level != startLevel.getStartLevel()) {
            final CountDownLatch latch = new CountDownLatch(1);
            FrameworkListener listener = new FrameworkListener() {
                @Override
                public void frameworkEvent(FrameworkEvent event) {
                    if (event.getType() == FrameworkEvent.STARTLEVEL_CHANGED && level == startLevel.getStartLevel()) {
                        latch.countDown();
                    }
                }
            };
            startLevel.setStartLevel(level, listener);
            if (latch.await(timeout, units) == false)
                throw new TimeoutException("Timeout changing start level");
        }
    }

    public static void refreshBundles(BundleContext context, Collection<Bundle> bundles) throws InterruptedException, TimeoutException {
        refreshBundles(context, bundles, 10, TimeUnit.SECONDS);
    }

    public static void refreshBundles(BundleContext context, Collection<Bundle> bundles, long timeout, TimeUnit units) throws InterruptedException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        FrameworkListener listener = new FrameworkListener() {
            @Override
            public void frameworkEvent(FrameworkEvent event) {
                if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
                    latch.countDown();
                }
            }
        };
        FrameworkWiring fwrkWiring = context.getBundle().adapt(FrameworkWiring.class);
        fwrkWiring.refreshBundles(bundles, listener);
        latch.await(10, TimeUnit.SECONDS);
    }

    public static <T> T waitForService(BundleContext context, Class<T> clazz) {
        return waitForService(context, clazz, 10, TimeUnit.SECONDS);
    }

    public static <T> T waitForService(final BundleContext context, final Class<T> clazz, long timeout, TimeUnit unit) {
        final AtomicReference<T> atomicref = new AtomicReference<T>();
        final ServiceReferenceHandler<T> handler = new ServiceReferenceHandler<T>() {
            @Override
            public void addingService(ServiceReference<T> sref, T service) {
                atomicref.set(service);
            }
        };
        trackService(context, clazz, handler, timeout, unit);
        Assert.assertNotNull("Service registered: " + clazz.getName(), atomicref.get());
        return atomicref.get();
    }

    public static <T> ServiceReference<T> waitForServiceReference(BundleContext context, Class<T> clazz) {
        return waitForServiceReference(context, clazz, 10, TimeUnit.SECONDS);
    }

    public static <T> ServiceReference<T> waitForServiceReference(final BundleContext context, final Class<T> clazz, long timeout, TimeUnit unit) {
        final AtomicReference<ServiceReference<T>> atomicref = new AtomicReference<ServiceReference<T>>();
        final ServiceReferenceHandler<T> handler = new ServiceReferenceHandler<T>() {
            @Override
            public void addingService(ServiceReference<T> sref, T service) {
                atomicref.set(sref);
            }
        };
        trackService(context, clazz, handler, timeout, unit);
        Assert.assertNotNull("Service registered: " + clazz.getName(), atomicref.get());
        return atomicref.get();
    }

    private static <T> void trackService(final BundleContext context, final Class<T> clazz, final ServiceReferenceHandler<T> handler, long timeout, TimeUnit unit) {
        final CountDownLatch latch = new CountDownLatch(1);
        ServiceTracker<T, T> tracker = new ServiceTracker<T, T>(context, clazz.getName(), null) {
            @Override
            public T addingService(ServiceReference<T> sref) {
                T service = super.addingService(sref);
                handler.addingService(sref, service);
                latch.countDown();
                return service;
            }
        };
        tracker.open();
        try {
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            // ignore
        } finally {
            tracker.close();
        }
    }

    private static interface ServiceReferenceHandler<T> {
        void addingService(ServiceReference<T> sref, T service);
    }
}