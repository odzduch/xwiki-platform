/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
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
package org.xwiki.component.internal;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

/**
 * Proxy Component Manager that creates and queries individual Component Managers specific to the current user in the
 * Execution Context. These Component Managers are created on the fly the first time a component is registered for the
 * current user.
 *  
 * @version $Id$
 * @since 2.1RC1
 */
@Component
@Named("user")
@Singleton
public class UserComponentManager extends AbstractGenericComponentManager implements Initializable
{
    /**
     * Used to access the current user in the Execution Context.
     */
    @Inject
    private DocumentAccessBridge documentAccessBridge;

    /**
     * The Component Manager to be used as parent when a component is not found in the current Component Manager.
     */
    @Inject
    @Named("wiki")
    private ComponentManager wikiComponentManager;

    @Override
    public void initialize() throws InitializationException
    {
        // Set the parent to the Wiki Component Manager since if a component isn't found for a particular user
        // we want to check if it's available in the current wiki and if not then in the Wiki Component Manager's
        // parent.
        setInternalParent(this.wikiComponentManager);
    }

    /**
     * {@inheritDoc}
     * @see AbstractGenericComponentManager#getKey()
     */
    @Override
    protected String getKey()
    {
        return this.documentAccessBridge.getCurrentUser();
    }
}
