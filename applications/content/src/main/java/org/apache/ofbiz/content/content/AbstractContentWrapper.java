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
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.ModelUtil;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * AbstractContentWrapper
 *
 * <p>Shared base for the per-entity {@code *ContentWrapper} classes (Party, Product, Order,
 * WorkEffort, ...). It centralizes the "fetch localized content for an entity" pattern that
 * was previously copy-pasted across every wrapper: per-entity render caching, join-entity
 * lookup, rendering via {@link ContentWorker#renderContentAsText}, candidate-field fallback
 * and encoding.</p>
 *
 * <p>Subclasses supply only entity-specific metadata via the abstract hooks below; the shared
 * algorithm ({@link #get}, {@link #getContentAsText}, {@link #getList}, {@link #getId},
 * {@link #getFirstContentByType}) lives here.</p>
 *
 * <p>Dependency direction: the {@code content} component <em>provides</em> this seam; the
 * business modules (party / product / order / workeffort) <em>depend on</em> it. Nothing here
 * may add an outbound dependency from {@code content} onto a business module.</p>
 */
public abstract class AbstractContentWrapper implements ContentWrapper {

    private static final String MODULE = AbstractContentWrapper.class.getName();

    // ------------------------------------------------------------------
    // Entity-specific metadata supplied by concrete subclasses.
    // ------------------------------------------------------------------

    /** The primary-key field name of the owning entity, e.g. {@code "partyId"}, {@code "productId"}. */
    protected abstract String getIdFieldName();

    /** The join entity that links the owning entity to Content, e.g. {@code "PartyContent"}, {@code "ProductContent"}. */
    protected abstract String getContentEntityName();

    /** The content-type field on the join entity, e.g. {@code "partyContentTypeId"}, {@code "productContentTypeId"}. */
    protected abstract String getContentTypeFieldName();

    /**
     * View entities used for the candidate-field fallback (when there is no join-entity content),
     * e.g. {@code ["PartyAndPerson", "PartyAndGroup"]}. The first non-empty candidate wins.
     */
    protected abstract List<String> getCandidateFieldEntityNames();

    /** The per-entity render cache. */
    protected abstract UtilCache<String, String> getCache();

    /** The owning entity value. */
    protected abstract GenericValue getEntityValue();

    /** The locale to render content for. */
    protected abstract Locale getLocale();

    /** The mime type to render content as. */
    protected abstract String getMimeTypeId();

    /** The dispatcher used to render content. */
    protected abstract LocalDispatcher getDispatcher();

    // ------------------------------------------------------------------
    // Shared, published implementation.
    // ------------------------------------------------------------------

    @Override
    public StringUtil.StringWrapper get(String contentTypeId, String encoderType) {
        return StringUtil.makeStringWrapper(getContentAsText(contentTypeId, true, encoderType));
    }

    /**
     * Central "get content as text" routine shared by all wrappers. Renders the localized content
     * for the given content type, falling back to a matching candidate field on the entity, and
     * caches the (possibly null) rendered result.
     * @param contentTypeId the content type id
     * @param useCache whether to use the render cache
     * @param encoderType the encoder type
     * @return the rendered content, or {@code null}
     */
    public String getContentAsText(String contentTypeId, boolean useCache, String encoderType) {
        GenericValue entity = getEntityValue();
        if (entity == null) {
            return null;
        }
        Delegator delegator = entity.getDelegator();
        LocalDispatcher dispatcher = getDispatcher();
        Locale locale = getLocale();
        String mimeTypeId = getMimeTypeId();
        if (UtilValidate.isEmpty(mimeTypeId)) {
            mimeTypeId = ContentWrapper.getDefaultMimeTypeId(delegator);
        }
        String id = entity.getString(getIdFieldName());
        UtilCache<String, String> cache = getCache();

        String cacheKey = null;
        if (useCache && cache != null) {
            cacheKey = contentTypeId + CACHE_KEY_SEPARATOR + locale + CACHE_KEY_SEPARATOR + mimeTypeId + CACHE_KEY_SEPARATOR + id;
            String cachedValue = cache.get(cacheKey);
            if (cachedValue != null || cache.containsKey(cacheKey)) {
                return cachedValue;
            }
        }

        String outString = null;
        try {
            Writer outWriter = new StringWriter();
            renderContentAsText(id, entity, contentTypeId, locale, mimeTypeId, delegator, dispatcher, outWriter, false);
            outString = outWriter.toString();
        } catch (GeneralException | IOException e) {
            Debug.logError(e, "Error rendering content for " + getContentEntityName(), MODULE);
            useCache = false;
        }

        // If no content was found (or an error occurred), fall back to a candidate field matching the content type.
        if (UtilValidate.isEmpty(outString)) {
            outString = ContentWrapper.getCandidateFieldValue(entity, contentTypeId);
        }
        // Encode found content via the given encoderType.
        outString = ContentWrapper.encodeContentValue(outString, encoderType);

        if (useCache && cache != null && cacheKey != null) {
            cache.put(cacheKey, outString);
        }
        return outString;
    }

    /**
     * Renders the localized content for the given content type into {@code outWriter}. Honors join-entity
     * content over entity fields; when there is no join-entity content it writes the first non-empty
     * candidate-field value found across {@link #getCandidateFieldEntityNames()}.
     */
    protected void renderContentAsText(String id, GenericValue entity, String contentTypeId, Locale locale, String mimeTypeId,
            Delegator delegator, LocalDispatcher dispatcher, Writer outWriter, boolean cache) throws GeneralException, IOException {
        GenericValue content = getFirstContentByType(id, entity, contentTypeId, delegator);
        if (content != null) {
            // when rendering the content, always include the owning entity and join records that this comes from
            Map<String, Object> inContext = new HashMap<>();
            inContext.put(getEntityContextName(), entity);
            inContext.put(getContentContextName(), content);
            ContentWorker.renderContentAsText(dispatcher, content.getString("contentId"), outWriter, inContext, locale, mimeTypeId,
                    null, null, cache);
        } else if (contentTypeId != null) {
            String candidateValue = null;
            for (String candidateEntityName : getCandidateFieldEntityNames()) {
                candidateValue = ContentWrapper.getCandidateFieldValue(delegator, candidateEntityName,
                        EntityCondition.makeCondition(getIdFieldName(), id), contentTypeId, cache);
                if (UtilValidate.isNotEmpty(candidateValue)) {
                    break;
                }
            }
            if (UtilValidate.isNotEmpty(candidateValue)) {
                outWriter.write(candidateValue);
            }
        }
    }

    /**
     * Gets the list of rendered content strings for the given content type.
     * @param contentTypeId the content type id
     * @return the rendered content list, or {@code null} on error
     */
    public List<String> getList(String contentTypeId) {
        GenericValue entity = getEntityValue();
        if (entity == null) {
            return null;
        }
        Delegator delegator = entity.getDelegator();
        LocalDispatcher dispatcher = getDispatcher();
        Locale locale = getLocale();
        String mimeTypeId = getMimeTypeId();
        try {
            List<GenericValue> contentValueList = EntityQuery.use(delegator).from(getContentEntityName())
                    .where(getIdFieldName(), entity.getString(getIdFieldName()), getContentTypeFieldName(), contentTypeId)
                    .orderBy("-fromDate")
                    .cache(true)
                    .filterByDate()
                    .queryList();

            List<String> contentList = new LinkedList<>();
            if (contentValueList != null) {
                for (GenericValue contentValue : contentValueList) {
                    StringWriter outWriter = new StringWriter();
                    Map<String, Object> inContext = new HashMap<>();
                    inContext.put(getEntityContextName(), entity);
                    inContext.put(getContentContextName(), contentValue);
                    ContentWorker.renderContentAsText(dispatcher, contentValue.getString("contentId"), outWriter, inContext, locale,
                            mimeTypeId, null, null, false);
                    contentList.add(outWriter.toString());
                }
            }
            return contentList;
        } catch (GeneralException | IOException e) {
            Debug.logError(e, MODULE);
            return null;
        }
    }

    /**
     * Gets the contentId of the first join-entity content of the given type.
     * @param contentTypeId the content type id
     * @return the contentId, or {@code null}
     */
    public String getId(String contentTypeId) {
        GenericValue entity = getEntityValue();
        if (entity == null) {
            return null;
        }
        GenericValue content = getFirstContentByType(null, entity, contentTypeId, entity.getDelegator());
        return content != null ? content.getString("contentId") : null;
    }

    /**
     * Gets the first (most recent, active) join-entity content record of the given type.
     * @param id the owning entity id (resolved from {@code entity} when {@code null})
     * @param entity the owning entity value
     * @param contentTypeId the content type id
     * @param delegator the delegator (resolved from {@code entity} when {@code null})
     * @return the join-entity {@link GenericValue}, or {@code null}
     */
    public GenericValue getFirstContentByType(String id, GenericValue entity, String contentTypeId, Delegator delegator) {
        if (id == null && entity != null) {
            id = entity.getString(getIdFieldName());
        }
        if (delegator == null && entity != null) {
            delegator = entity.getDelegator();
        }
        if (delegator == null) {
            throw new IllegalArgumentException("Delegator missing");
        }
        try {
            return EntityQuery.use(delegator).from(getContentEntityName())
                    .where(getIdFieldName(), id, getContentTypeFieldName(), contentTypeId)
                    .orderBy("-fromDate")
                    .filterByDate()
                    .cache()
                    .queryFirst();
        } catch (GeneralException e) {
            Debug.logError(e, MODULE);
        }
        return null;
    }

    /** The render-context key for the owning entity, derived from the id field name (e.g. {@code "party"}). */
    protected String getEntityContextName() {
        String idFieldName = getIdFieldName();
        if (idFieldName != null && idFieldName.endsWith("Id")) {
            return idFieldName.substring(0, idFieldName.length() - 2);
        }
        return idFieldName;
    }

    /** The render-context key for the join-entity record, derived from the content entity name (e.g. {@code "partyContent"}). */
    protected String getContentContextName() {
        return ModelUtil.lowerFirstChar(getContentEntityName());
    }
}
