/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2016 Neil C Smith.
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
package org.praxislive.ide.core;

import org.openide.modules.ModuleInfo;
import org.openide.modules.ModuleInstall;
import org.openide.util.Lookup;

/**
 * Manages a module's lifecycle. Remember that an installer is optional and
 * often not needed at all.
 */
public class Installer extends ModuleInstall {
    
    @Override
    public void restored() {

        String build = null;

        for (ModuleInfo info : Lookup.getDefault().lookupAll(ModuleInfo.class)) {
            if (info.owns(this.getClass())) {
                build = info.getImplementationVersion();
            }
        }
        Core coreInfo = Core.getInstance();
        coreInfo.setBuildVersion(build);
        
        String version = System.getProperty("praxis.version", "DEV");
        coreInfo.setVersion(version);
        System.setProperty("netbeans.buildnumber", version);
        System.setProperty("netbeans.productversion", "Praxis LIVE " + version);
        
        coreInfo.checkForUpdates();
        
        DefaultHubManager.getInstance().start();
        
    }
    
    
}
