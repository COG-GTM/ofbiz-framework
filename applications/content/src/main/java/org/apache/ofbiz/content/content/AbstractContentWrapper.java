/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.content.content;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.cache.UtilCache;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Abstract base implementation of the {@link ContentWrapper} seam.
 *
 * <p>This class centralizes the "fetch localized content for an entity" pattern that used to be
 * copy-pasted across the per-entity {@code *ContentWrapper} classes (PartyContentWrapper,
 * ProductContentWrapper, CategoryContentWrapper, OrderContentWrapper, ...). Concretely it provides
 * shared implementations of:</p>
 * <ul>
 *   <li>the rendered-content cache lookup/store ({@link #getContentAsText(String, String)} and the
 *       reusable {@link #renderAndCacheContentAsText} helper),</li>
 *   <li>the join-entity lookup ({@link #getFirstContentByType(GenericValue, String)}),</li>
 *   <li>rendering through {@link ContentWorker#renderContentAsText}, the candidate-field fallback
 *       and encoding ({@link #renderContentToWriter(Writer, String, boolean)}),</li>
 *   <li>list ({@link #getList(String)}) and id ({@link #getId(String)}) accessors.</li>
 * </ul>
 *
 * <p>Subclasses supply only entity-specific metadata through the abstract hooks below; they keep
 * living in their own components and keep their existing public static methods. This class lives in
 * the {@code content} component and must not add any outbound dependency onto a business module.</p>
 */
public abstract class AbstractContentWrapper implements ContentWrapper {

    private static final String MODULE = AbstractContentWrapper.class.getName();

    // -----------------------------------------------------------------------
    // Entity-specific metadata hooks supplied by subclasses.
    // -----------------------------------------------------------------------

    /** @return the primary key field name of the wrapped entity, e.g. "productId", "partyId". */
    public abstract String getIdFieldName();

    /** @return the join entity name, e.g. "ProductContent", "PartyContent". */
    public abstract String getContentEntityName();

    /** @return the content type field name on the join entity, e.g. "productContentTypeId". */
    public abstract String getContentTypeFieldName();

    /** @return the view entities used for the candidate-field fallback, or an empty list to use
     * the wrapped entity's own fields. */
    public abstract List<String> getCandidateFieldEntityNames();

    /** @return the per-entity rendered-content cache. */
    public abstract UtilCache<String, String> getCache();

    /** @return the wrapped entity {@link GenericValue}. */
    public abstract GenericValue getEntityValue();

    /** @return the locale to render with. */
    public abstract Locale getLocale();

    /** @return the mime type id to render with. */
    public abstract String getMimeTypeId();

    /** @return the dispatcher used to render content. */
    public abstract LocalDispatcher getDispatcher();

    /** @return the context key under which the wrapped entity is exposed while rendering, e.g. "product". */
    public abstract String getEntityContextKey();

    /** @return the context key under which the join value is exposed while rendering, e.g. "productContent". */
    public abstract String getContentContextKey();

    // -----------------------------------------------------------------------
    // Published seam behavior (shared implementations).
    // -----------------------------------------------------------------------

    @Override
    public StringUtil.StringWrapper get(String contentTypeId, String encoderType) {
        return StringUtil.makeStringWrapper(getContentAsText(contentTypeId, encoderType));
    }

    /**
     * Central "fetch localized content for an entity" implementation: cache lookup, rendering,
     * candidate-field fallback and encoding.
     * @param contentTypeId the content type id
     * @param encoderType the encoder type
     * @return the rendered (and encoded) content as text, or {@code null} if there is no entity
     */
    public String getContentAsText(String contentTypeId, String encoderType) {
        GenericValue entity = getEntityValue();
        if (entity == null) {
            return null;
        }
        Delegator delegator = entity.getDelegator();
        return renderAndCacheContentAsText(getCache(), entity, getIdFieldName(), contentTypeId, getLocale(),
                getMimeTypeId(), delegator, encoderType, "Error rendering content", MODULE,
                outWriter -> renderContentToWriter(outWriter, contentTypeId, true));
    }

    /**
     * Gets the list of rendered content values of the given type.
     * @param contentTypeId the content type id
     * @return the list of rendered content, or {@code null} on error
     */
    public List<String> getList(String contentTypeId) {
        GenericValue entity = getEntityValue();
        if (entity == null) {
            return null;
        }
        Delegator delegator = entity.getDelegator();
        try {
            List<GenericValue> contentList = EntityQuery.use(delegator).from(getContentEntityName())
                    .where(getIdFieldName(), entity.getString(getIdFieldName()), getContentTypeFieldName(), contentTypeId)
                    .orderBy("-fromDate")
                    .cache(true)
                    .filterByDate()
                    .queryList();
            List<String> outList = new LinkedList<>();
            if (contentList != null) {
                for (GenericValue joinValue : contentList) {
                    StringWriter outWriter = new StringWriter();
                    Map<String, Object> inContext = new HashMap<>();
                    inContext.put(getEntityContextKey(), entity);
                    inContext.put(getContentContextKey(), joinValue);
                    ContentWorker.renderContentAsText(getDispatcher(), joinValue.getString("contentId"), outWriter, inContext,
                            getLocale(), getMimeTypeId(), null, null, false);
                    outList.add(outWriter.toString());
                }
            }
            return outList;
        } catch (GeneralException | IOException e) {
            Debug.logError(e, MODULE);
            return null;
        }
    }

    /**
     * Gets the content id of the most current content of the given type.
     * @param contentTypeId the content type id
     * @return the content id, or {@code null} if none
     */
    public String getId(String contentTypeId) {
        GenericValue joinValue = getFirstContentByType(getEntityValue(), contentTypeId);
        if (joinValue != null) {
            return joinValue.getString("contentId");
        }
        return null;
    }

    /**
     * Gets the most current join-entity value of the given content type for the wrapped entity.
     * @param entity the wrapped entity
     * @param contentTypeId the content type id
     * @return the first matching join value, or {@code null} if none
     */
    public GenericValue getFirstContentByType(GenericValue entity, String contentTypeId) {
        if (entity == null) {
            return null;
        }
        Delegator delegator = entity.getDelegator();
        try {
            return EntityQuery.use(delegator).from(getContentEntityName())
                    .where(getIdFieldName(), entity.getString(getIdFieldName()), getContentTypeFieldName(), contentTypeId)
                    .orderBy("-fromDate")
                    .filterByDate()
                    .cache()
                    .queryFirst();
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
        }
        return null;
    }

    /**
     * Renders the localized content of the given type into the writer, honoring the join entity
     * first and falling back to the candidate field(s).
     * @param outWriter the writer to render into
     * @param contentTypeId the content type id
     * @param cache whether the entity-managed content lookups may use the cache
     * @throws GeneralException on rendering error
     * @throws IOException on write error
     */
    protected void renderContentToWriter(Writer outWriter, String contentTypeId, boolean cache) throws GeneralException, IOException {
        GenericValue entity = getEntityValue();
        Delegator delegator = entity.getDelegator();
        GenericValue joinValue = getFirstContentByType(entity, contentTypeId);
        if (joinValue != null) {
            Map<String, Object> inContext = new HashMap<>();
            inContext.put(getEntityContextKey(), entity);
            inContext.put(getContentContextKey(), joinValue);
            ContentWorker.renderContentAsText(getDispatcher(), joinValue.getString("contentId"), outWriter, inContext,
                    getLocale(), getMimeTypeId(), null, null, cache);
            return;
        }
        List<String> candidateEntities = getCandidateFieldEntityNames();
        if (UtilValidate.isEmpty(candidateEntities)) {
            String candidateValue = ContentWrapper.getCandidateFieldValue(entity, contentTypeId);
            if (UtilValidate.isNotEmpty(candidateValue)) {
                outWriter.write(candidateValue);
            }
        } else {
            EntityCondition idCondition = EntityCondition.makeCondition(getIdFieldName(), entity.getString(getIdFieldName()));
            for (String candidateEntity : candidateEntities) {
                String candidateValue = ContentWrapper.getCandidateFieldValue(delegator, candidateEntity, idCondition, contentTypeId, cache);
                if (UtilValidate.isNotEmpty(candidateValue)) {
                    outWriter.write(candidateValue);
                    break;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Shared static helper reused by the per-entity static methods.
    // -----------------------------------------------------------------------

    /**
     * Callback that renders the content of a given type into a writer. Used by
     * {@link #renderAndCacheContentAsText} so the per-entity static methods can plug in their own
     * (entity-specific) rendering while sharing the surrounding cache/fallback/encode logic.
     */
    @FunctionalInterface
    public interface ContentRenderer {
        void render(Writer outWriter) throws GeneralException, IOException;
    }

    /**
     * Central implementation of the cached "get content as text" pattern shared by the per-entity
     * static {@code getXxxContentAsText} methods: build the cache key, return the cached value if
     * present, otherwise render via the supplied {@link ContentRenderer}, fall back to the entity's
     * candidate field, encode and cache the result.
     * @param cache the per-entity rendered-content cache
     * @param entity the wrapped entity (a {@code null} entity yields a {@code null} result)
     * @param idFieldName the entity primary key field name, used in the cache key
     * @param contentTypeId the content type id
     * @param locale the locale
     * @param mimeTypeId the mime type id
     * @param delegator the delegator (part of the cache key)
     * @param encoderType the encoder type
     * @param errorLabel the message logged if rendering throws
     * @param module the logging module of the calling class
     * @param renderer the entity-specific rendering callback
     * @return the rendered (and encoded) content, or {@code null} if there is no entity
     */
    public static String renderAndCacheContentAsText(UtilCache<String, String> cache, GenericValue entity, String idFieldName,
            String contentTypeId, Locale locale, String mimeTypeId, Delegator delegator, String encoderType,
            String errorLabel, String module, ContentRenderer renderer) {
        if (entity == null) {
            return null;
        }

        String cacheKey = contentTypeId + CACHE_KEY_SEPARATOR + locale + CACHE_KEY_SEPARATOR + mimeTypeId + CACHE_KEY_SEPARATOR
                + entity.get(idFieldName) + CACHE_KEY_SEPARATOR + encoderType + CACHE_KEY_SEPARATOR + delegator;
        String cachedValue = cache.get(cacheKey);
        if (cachedValue != null || cache.containsKey(cacheKey)) {
            return cachedValue;
        }

        // Get content of given contentTypeId
        boolean doCache = true;
        String outString = null;
        try {
            Writer outWriter = new StringWriter();
            renderer.render(outWriter);
            outString = outWriter.toString();
        } catch (GeneralException | IOException e) {
            Debug.logError(e, errorLabel, module);
            doCache = false;
        }

        // If we did not find any content (or got an error), get the content of a candidateFieldName
        // matching the given contentTypeId
        if (UtilValidate.isEmpty(outString)) {
            outString = ContentWrapper.getCandidateFieldValue(entity, contentTypeId);
        }
        // Encode found content via given encoderType
        outString = ContentWrapper.encodeContentValue(outString, encoderType);

        if (doCache) {
            cache.put(cacheKey, outString);
        }
        return outString;
    }
}
