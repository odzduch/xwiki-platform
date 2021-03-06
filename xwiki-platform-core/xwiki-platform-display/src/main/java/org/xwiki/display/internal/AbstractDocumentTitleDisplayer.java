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
package org.xwiki.display.internal;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.bridge.DocumentModelBridge;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.util.ParserUtils;
import org.xwiki.velocity.VelocityManager;

/**
 * Displays the title of a document.
 * 
 * @version $Id$
 * @since 3.2M3
 */
public abstract class AbstractDocumentTitleDisplayer implements DocumentDisplayer
{
    /**
     * The key used to store on the XWiki context map the stack trace for the
     * {@link #extractTitleFromContent(org.xwiki.bridge.DocumentModelBridge, DocumentDisplayerParameters)} method.
     */
    private static final String EXTRACT_TITLE_STACK_TRACE_KEY = "internal.extractTitleFromContentStackTrace";

    /**
     * The object used for logging.
     */
    @Inject
    private Logger logger;

    /**
     * The component used to parse the rendered title into an XDOM.
     */
    @Inject
    @Named("html/4.01")
    private Parser htmlParser;

    /**
     * The component used to get the Velocity Engine and the Velocity Context needed to evaluate the Velocity script
     * from the document title.
     */
    @Inject
    private VelocityManager velocityManager;

    /**
     * The component used to get the current document reference.
     */
    @Inject
    private DocumentAccessBridge documentAccessBridge;

    /**
     * The component used to serialize entity references.
     */
    @Inject
    private EntityReferenceSerializer<String> defaultEntityReferenceSerializer;

    /**
     * Execution context handler, needed for accessing the XWiki context map.
     */
    @Inject
    private Execution execution;

    /**
     * Used to emulate an in-line parsing.
     */
    private ParserUtils parserUtils = new ParserUtils();

    @Override
    public XDOM display(DocumentModelBridge document, DocumentDisplayerParameters parameters)
    {
        // 1. Try to use the title provided by the user.
        if (!StringUtils.isEmpty(document.getTitle())) {
            try {
                return parseXDOM(evaluateTitle(document.getTitle(), document.getDocumentReference(), parameters));
            } catch (Exception e) {
                String reference = defaultEntityReferenceSerializer.serialize(document.getDocumentReference());
                logger.warn("Failed to interpret title of document [{}].", reference);
            }
        }

        // 2. Try to extract the title from the document content.
        try {
            XDOM title = safeExtractTitleFromContent(document, parameters);
            if (title != null) {
                return title;
            }
        } catch (Exception e) {
            String reference = defaultEntityReferenceSerializer.serialize(document.getDocumentReference());
            logger.warn("Failed to extract title from content of document [{}].", reference);
        }

        // 3. No title has been found. Use the document name as title.
        return parseXDOM(document.getDocumentReference().getName());
    }

    /**
     * Parses the given title as plain text and returns the generated XDOM.
     * 
     * @param title the title to be parsed
     * @return the XDOM generated from parsing the title as plain text
     */
    protected XDOM parseXDOM(String title)
    {
        try {
            XDOM xdom = htmlParser.parse(new StringReader(title));
            parserUtils.removeTopLevelParagraph(xdom.getChildren());
            return xdom;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Evaluates the Velocity script from the specified title.
     * 
     * @param title the title to evaluate
     * @param documentReference a reference to the document whose title is evaluated
     * @param parameters display parameters
     * @return the result of evaluating the Velocity script from the given title
     */
    protected String evaluateTitle(String title, DocumentReference documentReference,
        DocumentDisplayerParameters parameters)
    {
        StringWriter writer = new StringWriter();
        String nameSpace =
            defaultEntityReferenceSerializer.serialize(parameters.isTransformationContextIsolated() ? documentReference
                : documentAccessBridge.getCurrentDocumentReference());
        Map<String, Object> backupObjects = null;
        try {
            if (parameters.isExecutionContextIsolated()) {
                backupObjects = new HashMap<String, Object>();
                // The following method call also clones the execution context.
                documentAccessBridge.pushDocumentInContext(backupObjects, documentReference);
            }
            velocityManager.getVelocityEngine()
                .evaluate(velocityManager.getVelocityContext(), writer, nameSpace, title);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (parameters.isExecutionContextIsolated()) {
                documentAccessBridge.popDocumentFromContext(backupObjects);
            }
        }
        return writer.toString();
    }

    /**
     * Extracts the title from the document content. This method prevents infinite recursion which can happen for
     * instance if the title displayer is called from a script within the first heading in the document content.
     * 
     * @param document the document to extract the title from
     * @param parameters display parameters
     * @return the title XDOM
     */
    private XDOM safeExtractTitleFromContent(DocumentModelBridge document, DocumentDisplayerParameters parameters)
    {
        // Protect against cycles which can happen for instance if the document title displayer is called on the current
        // document from a script within the first content heading.
        Map<Object, Object> xwikiContext = getXWikiContextMap();
        @SuppressWarnings("unchecked")
        Stack<DocumentReference> stackTrace =
            (Stack<DocumentReference>) xwikiContext.get(EXTRACT_TITLE_STACK_TRACE_KEY);
        if (stackTrace == null) {
            stackTrace = new Stack<DocumentReference>();
            xwikiContext.put(EXTRACT_TITLE_STACK_TRACE_KEY, stackTrace);
        } else if (stackTrace.contains(document.getDocumentReference())) {
            return null;
        }

        stackTrace.push(document.getDocumentReference());
        try {
            return extractTitleFromContent(document, parameters);
        } finally {
            stackTrace.pop();
        }
    }

    /**
     * @return the XWiki context map
     */
    @SuppressWarnings("unchecked")
    private Map<Object, Object> getXWikiContextMap()
    {
        return (Map<Object, Object>) execution.getContext().getProperty("xwikicontext");
    }

    /**
     * Extracts the title from the document content.
     * 
     * @param document the document to extract the title from
     * @param parameters display parameters
     * @return the title XDOM
     */
    protected abstract XDOM extractTitleFromContent(DocumentModelBridge document,
        DocumentDisplayerParameters parameters);

    /**
     * @return the object used for logging
     */
    protected Logger getLogger()
    {
        return logger;
    }
}
