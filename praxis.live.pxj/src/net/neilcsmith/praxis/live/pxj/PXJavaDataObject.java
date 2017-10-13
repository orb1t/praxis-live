/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2017 Neil C Smith.
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
package net.neilcsmith.praxis.live.pxj;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import net.neilcsmith.praxis.core.ControlAddress;
import net.neilcsmith.praxis.live.model.ComponentProxy;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataNode;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.nodes.Children;
import org.openide.nodes.CookieSet;
import org.openide.nodes.Node;
import org.openide.nodes.NodeAdapter;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Neil C Smith
 */
class PXJavaDataObject extends MultiDataObject {

    private final ControlAddress controlAddress;

    public PXJavaDataObject(FileObject fo, MultiFileLoader loader) throws DataObjectExistsException {
        super(fo, loader);
        CookieSet cookies = getCookieSet();
        cookies.add(new PXJavaEditorSupport(this, cookies));
        controlAddress = (ControlAddress) fo.getAttribute(PXJDataObject.CONTROL_ADDRESS_KEY);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @Override
    protected Node createNodeDelegate() {
        Node cmpNode = ComponentProxy.find(controlAddress.getComponentAddress())
                    .map(ComponentProxy::getNodeDelegate)
                    .orElse(null);
        return new PXJavaNode(this, cmpNode);
    }

    private static class PXJavaNode extends DataNode {

        Node componentNode;
        ComponentListener cmpL;
        ComponentNodeListener cmpNL;

        private PXJavaNode(PXJavaDataObject dob, Node componentNode) {
            super(dob, Children.LEAF, componentNode == null ? dob.getLookup() : 
                    new ProxyLookup(dob.getLookup(), componentNode.getLookup()));
            this.componentNode = componentNode;
            if (componentNode != null) {
                cmpL = new ComponentListener();
                cmpNL = new ComponentNodeListener();
                componentNode.addPropertyChangeListener(cmpL);
                componentNode.addNodeListener(cmpNL);
            }
        }

        
        @Override
        public PropertySet[] getPropertySets() {
            if (componentNode != null) {
                return componentNode.getPropertySets();
            } else {
                return super.getPropertySets();
            }
        }

        @Override
        public void destroy() throws IOException {
            super.destroy();
            if (componentNode != null) {
                componentNode.removePropertyChangeListener(cmpL);
                componentNode.removeNodeListener(cmpNL);
                componentNode = null;
                cmpL = null;
                cmpNL = null;
            }
        }

        class ComponentListener implements PropertyChangeListener {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
            }

        }

        class ComponentNodeListener extends NodeAdapter {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (PROP_PROPERTY_SETS.equals(evt.getPropertyName())) {
                    firePropertySetsChange(null, null);
                }
            }

        }

    }

}
