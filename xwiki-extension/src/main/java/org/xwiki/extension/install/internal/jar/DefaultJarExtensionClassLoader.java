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
package org.xwiki.extension.install.internal.jar;

import java.net.URI;

import org.xwiki.classloader.ExtendedURLClassLoader;
import org.xwiki.classloader.URIClassLoader;
import org.xwiki.component.annotation.Component;

@Component
public class DefaultJarExtensionClassLoader implements JarExtensionClassLoader
{
    private ExtendedURLClassLoader classLoader;

    public ExtendedURLClassLoader getURLClassLoader()
    {
        if (this.classLoader == null) {
            this.classLoader = new URIClassLoader(new URI[] {}, getClass().getClassLoader());
        }

        return this.classLoader;
    }
}