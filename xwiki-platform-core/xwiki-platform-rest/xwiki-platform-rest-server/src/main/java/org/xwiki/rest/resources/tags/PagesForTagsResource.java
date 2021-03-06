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
package org.xwiki.rest.resources.tags;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.xwiki.component.annotation.Component;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.rest.DomainObjectFactory;
import org.xwiki.rest.RangeIterable;
import org.xwiki.rest.Utils;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.model.jaxb.Pages;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;

@Component("org.xwiki.rest.resources.tags.PagesForTagsResource")
@Path("/wikis/{wikiName}/tags/{tagNames}")
public class PagesForTagsResource extends XWikiResource
{
    @GET
    public Pages getTags(@PathParam("wikiName") String wikiName, @PathParam("tagNames") String tagNames,
        @QueryParam("start") @DefaultValue("0") Integer start, @QueryParam("number") @DefaultValue("-1") Integer number)
        throws XWikiException, QueryException
    {
        String database = Utils.getXWikiContext(componentManager).getDatabase();

        Pages pages = objectFactory.createPages();

        /* This try is just needed for executing the finally clause. */
        try {
            Utils.getXWikiContext(componentManager).setDatabase(wikiName);

            String[] tagNamesArray = tagNames.split(",");

            List<String> documentNames = new ArrayList<String>();
            for (String tagName : tagNamesArray) {
                List<String> documentNamesForTag = getDocumentsWithTag(tagName);

                /* Avoid duplicates */
                for (String documentName : documentNamesForTag) {
                    if (!documentNames.contains(documentName)) {
                        documentNames.add(documentName);
                    }
                }
            }

            RangeIterable<String> ri = new RangeIterable<String>(documentNames, start, number);

            for (String documentName : ri) {
                Document doc = Utils.getXWikiApi(componentManager).getDocument(documentName);
                if (doc != null) {
                    pages.getPageSummaries().add(
                        DomainObjectFactory.createPageSummary(objectFactory, uriInfo.getBaseUri(), doc, Utils
                            .getXWikiApi(componentManager)));
                }
            }

        } finally {
            Utils.getXWikiContext(componentManager).setDatabase(database);
        }

        return pages;
    }

    private List<String> getDocumentsWithTag(String tag) throws QueryException
    {
        String query =
            "select doc.fullName from XWikiDocument as doc, BaseObject as obj, DBStringListProperty as prop "
                + "where obj.name=doc.fullName and obj.className='XWiki.TagClass' and obj.id=prop.id.id "
                + "and prop.id.name='tags' and :tag in elements(prop.list) order by doc.name asc";

        List<String> documentsWithTag = queryManager.createQuery(query, Query.HQL).bindValue("tag", tag).execute();

        return documentsWithTag;
    }
}
