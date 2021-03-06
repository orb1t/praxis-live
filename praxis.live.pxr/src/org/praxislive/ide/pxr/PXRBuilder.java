/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 Neil C Smith.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 *
 *
 * Please visit http://neilcsmith.net if you need additional information or
 * have any questions.
 */
package org.praxislive.ide.pxr;

import com.vdurmont.semver4j.Semver;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.praxislive.core.ValueFormatException;
import org.praxislive.core.CallArguments;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.ComponentType;
import org.praxislive.core.ComponentInfo;
import org.praxislive.ide.components.api.Components;
import org.praxislive.ide.core.api.Callback;
import org.praxislive.ide.model.Connection;
import org.praxislive.ide.model.ProxyException;
import org.praxislive.ide.project.api.PraxisProject;
import org.praxislive.ide.properties.PraxisProperty;
import org.praxislive.ide.pxr.PXRParser.AttributeElement;
import org.praxislive.ide.pxr.PXRParser.ComponentElement;
import org.praxislive.ide.pxr.PXRParser.ConnectionElement;
import org.praxislive.ide.pxr.PXRParser.Element;
import org.praxislive.ide.pxr.PXRParser.PropertyElement;
import org.praxislive.ide.pxr.PXRParser.RootElement;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.praxislive.ide.core.api.CoreInfo;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */

@NbBundle.Messages({
    "MSG_versionWarning=File was created with a newer version of Praxis LIVE"
})
class PXRBuilder {

    private final static Logger LOG = Logger.getLogger(PXRBuilder.class.getName());
    private final PraxisProject project;
    private final PXRDataObject source;
    private final RootElement root;
    private final List<String> warnings;
    private final boolean registerRoot;

    private Iterator<Element> iterator;
    private Callback processCallback;
    private PXRRootProxy rootProxy;
    private boolean processed;

    PXRBuilder(PraxisProject project,
            PXRDataObject source,
            RootElement root,
            List<String> warnings) {
        this.project = project;
        this.source = source;
        this.root = root;
        registerRoot = true;
        this.warnings = warnings;
    }

    PXRBuilder(PXRRootProxy rootProxy,
            RootElement root,
            List<String> warnings) {
        this.project = null;
        this.source = null;
        this.rootProxy = rootProxy;
        this.root = root;
        registerRoot = false;
        this.warnings = warnings;
    }

    void process(Callback callback) {
        if (callback == null) {
            throw new NullPointerException();
        }
        this.processCallback = callback;
        if (Components.getRewriteDeprecated()) {
            ElementRewriter rewriter = new ElementRewriter(root, warnings);
            rewriter.process();
        }
        checkVersion();
        buildElementIterator();
        process();
    }

    private void checkVersion() {
        try {
            for (AttributeElement attr : root.attributes) {
                if (PXRParser.VERSION_ATTR.equals(attr.key)) {
                    Semver fileVersion = new Semver(attr.value, Semver.SemverType.LOOSE);
                    Semver runningVersion = new Semver(
                            CoreInfo.getDefault().getVersion(),
                            Semver.SemverType.LOOSE);
                    if (fileVersion.isGreaterThan(runningVersion)) {
                        warn(Bundle.MSG_versionWarning());
                    }
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Exception during checkVersion()", ex);
        }

    }

    private void process() {
        while (iterator.hasNext()) {
            if (!process(iterator.next())) {
                //break;
                return;
            }
        }
        if (!processed && !iterator.hasNext()) {
            processed = true;
            if (registerRoot) {
                PXRRootRegistry.getDefault().register(rootProxy);
            }
            processCallback.onReturn(CallArguments.EMPTY);

        }
    }

    private boolean process(Element element) {
        if (element instanceof PropertyElement) {
            return processProperty((PropertyElement) element);
        } else if (element instanceof AttributeElement) {
            return processAttribute((AttributeElement) element);
        } else if (element instanceof ConnectionElement) {
            return processConnection((ConnectionElement) element);
        } else if (element instanceof RootElement) {
            return processRoot((RootElement) element);
        } else if (element instanceof ComponentElement) {
            return processComponent((ComponentElement) element);
        }
        processCallback.onError(CallArguments.EMPTY);
        return false;
    }

    private void processError(CallArguments args) {
        processed = true;
        processCallback.onError(args);
    }

    private void warn(String msg) {
        if (warnings == null) {
            return;
        }
        warnings.add(msg);
    }

    private boolean processProperty(final PropertyElement prop) {
        LOG.log(Level.FINE, "Processing Property Element : {0}", prop.property);
        final PXRComponentProxy cmp = findComponent(prop.component.address);
        if (cmp == null) {
            propertyError(prop, CallArguments.EMPTY);
            return true;
        }
        PraxisProperty<?> p = cmp.getProperty(prop.property);
        if (p instanceof BoundArgumentProperty) {
            try {
                ((BoundArgumentProperty) p).setValue(prop.args[0], new Callback() {
                    @Override
                    public void onReturn(CallArguments args) {
                        if (p instanceof BoundCodeProperty) {
                            p.setValue(BoundCodeProperty.KEY_LAST_SAVED, prop.args[0]);
                        }
                        if (cmp.isDynamic()) {
                            try {
                                cmp.call("info", CallArguments.EMPTY, new Callback() {
                                    @Override
                                    public void onReturn(CallArguments args) {
                                        try {
                                            cmp.refreshInfo(ComponentInfo.coerce(args.get(0)));
                                        } catch (ValueFormatException ex) {
                                            Exceptions.printStackTrace(ex);
                                        }
                                        process();
                                    }

                                    @Override
                                    public void onError(CallArguments args) {
                                        process();
                                    }
                                });
                                return;
                            } catch (ProxyException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        } else {
                            process();
                        }
                    }

                    @Override
                    public void onError(CallArguments args) {
                        propertyError(prop, args);
                        process();
                    }
                });
                return false;
            } catch (Exception ex) {
                LOG.warning("Couldn't set property " + prop.property);
            }
        }
        propertyError(prop, CallArguments.EMPTY);
        return true;
    }

    private void propertyError(PropertyElement prop, CallArguments args) {
        String err = "Couldn't set property " + prop.component.address + "." + prop.property;
        warn(err);
    }

    private boolean processAttribute(AttributeElement attr) {
        PXRComponentProxy cmp = findComponent(attr.component.address);
        if (cmp != null) {
            cmp.setAttr(attr.key, attr.value);
        }
        return true;
    }

    private boolean processConnection(final ConnectionElement con) {
        LOG.fine("Processing Connection Element : " + con.port1 + " -> " + con.port2);
        try {
            PXRComponentProxy parent = findComponent(con.container.address);
            if (parent instanceof PXRContainerProxy) {
                ((PXRContainerProxy) parent).connect(
                        new Connection(con.component1, con.port1, con.component2, con.port2),
                        new Callback() {
                    @Override
                    public void onReturn(CallArguments args) {
                        process();
                    }

                    @Override
                    public void onError(CallArguments args) {
                        connectionError(con, args);
                        process();
                    }
                });
                return false;
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        connectionError(con, CallArguments.EMPTY);
        return true;
    }

    private void connectionError(ConnectionElement connection, CallArguments args) {
        String p1 = connection.container.address + "/" + connection.component1 + "!" + connection.port1;
        String p2 = connection.container.address + "/" + connection.component2 + "!" + connection.port2;
        String err = "Couldn't create connection " + p1 + " -> " + p2;
        warn(err);
    }

    private boolean processRoot(RootElement root) {
        if (rootProxy != null) {
            LOG.log(Level.FINE, "Root already exists - ignoring Root Element : {0}, Type : {1}",
                    new Object[]{root.address, root.type});
            return true;
        }
        LOG.log(Level.FINE, "Processing Root Element : {0}, Type : {1}", new Object[]{root.address, root.type});
        try {
            final ComponentAddress ad = root.address;
            final ComponentType type = root.type;
            PXRHelper.getDefault().createComponentAndGetInfo(ad, type, new Callback() {
                @Override
                public void onReturn(CallArguments args) {
                    try {
                        rootProxy = new PXRRootProxy(
                                project,
                                source,
                                ad.getRootID(),
                                type,
                                ComponentInfo.coerce(args.get(0)));
                        process();
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                        onError(args);
                    }
                }

                @Override
                public void onError(CallArguments args) {
                    processError(args);
                }
            });

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            processError(CallArguments.EMPTY);
        }
        return false;
    }

    private boolean processComponent(final ComponentElement cmp) {
        LOG.log(Level.FINE, "Processing Component Element : {0}, Type : {1}", new Object[]{cmp.address, cmp.type});
        try {
            ComponentAddress address = cmp.address;
            final PXRComponentProxy parent = findComponent(address.getParentAddress());
            if (parent instanceof PXRContainerProxy) {
                String id = address.getComponentID(address.getDepth() - 1);
                ((PXRContainerProxy) parent).addChild(id, cmp.type, new Callback() {
                    @Override
                    public void onReturn(CallArguments args) {
                        if (parent.isDynamic()) {
                            try {
                                parent.call("info", CallArguments.EMPTY, new Callback() {
                                    @Override
                                    public void onReturn(CallArguments args) {
                                        try {
                                            parent.refreshInfo(ComponentInfo.coerce(args.get(0)));
                                        } catch (ValueFormatException ex) {
                                            Exceptions.printStackTrace(ex);
                                        }
                                        process();
                                    }

                                    @Override
                                    public void onError(CallArguments args) {
                                        process();
                                    }
                                });
                                return;
                            } catch (ProxyException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        } else {
                            process();
                        }
                    }

                    @Override
                    public void onError(CallArguments args) {
                        componentError(cmp, args);
                        process();
                    }
                });
                return false;
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        componentError(cmp, CallArguments.EMPTY);
        return true;
    }

    private void componentError(ComponentElement cmp, CallArguments args) {
        String err = "Couldn't create component " + cmp.address;
        warn(err);
    }

    private PXRComponentProxy findComponent(ComponentAddress address) {
        if (rootProxy == null) {
            return null;
        }
        if (address.getDepth() == 1 && rootProxy.getAddress().equals(address)) {
            return rootProxy;
        } else if (!rootProxy.getAddress().getRootID().equals(address.getRootID())) {
            return null;
        }

        PXRComponentProxy cmp = rootProxy;
        for (int i = 1; i < address.getDepth(); i++) {
            if (cmp instanceof PXRContainerProxy) {
                cmp = ((PXRContainerProxy) cmp).getChild(address.getComponentID(i));
            } else {
                return null;
            }
        }
        return cmp;

    }

    private synchronized void buildElementIterator() {

        if (iterator != null) {
            throw new IllegalStateException();
        }
        List<Element> elements = new LinkedList<Element>();
        addComponentElements(root, elements);
        iterator = elements.iterator();

    }

    private void addComponentElements(ComponentElement component,
            List<Element> elements) {
        elements.add(component);
        elements.addAll(Arrays.asList(component.attributes));
        elements.addAll(Arrays.asList(component.properties));
        for (ComponentElement child : component.children) {
            addComponentElements(child, elements);
        }
        elements.addAll(Arrays.asList(component.connections));
    }

}
