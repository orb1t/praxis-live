/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 Neil C Smith.
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
package org.praxislive.ide.project;

import org.praxislive.core.CallArguments;
import org.praxislive.core.Component;
import org.praxislive.core.services.ScriptService;
import org.praxislive.core.services.ServiceUnavailableException;
import org.praxislive.core.types.PString;
import org.praxislive.ide.core.api.Callback;
import org.praxislive.ide.core.api.ExtensionProvider;
import org.praxislive.ide.core.api.HubUnavailableException;
import org.praxislive.ide.util.AbstractHelperComponent;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class ProjectHelper extends AbstractHelperComponent {

    private final static ProjectHelper INSTANCE = new ProjectHelper();


    public void executeScript(String script, Callback callback) throws HubUnavailableException, ServiceUnavailableException {
        send(ScriptService.class, ScriptService.EVAL, CallArguments.create(PString.valueOf(script)), callback);
    }

    public static ProjectHelper getDefault() {
        return INSTANCE;
    }
    
    @ServiceProvider(service=ExtensionProvider.class)
    public static class Provider implements ExtensionProvider {

        @Override
        public Component getExtensionComponent() {
            return getDefault();
        }

    }
}
