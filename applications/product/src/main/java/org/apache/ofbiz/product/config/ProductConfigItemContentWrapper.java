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
package org.apache.ofbiz.product.config;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.StringUtil.StringWrapper;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.cache.UtilCache;
import org.apache.ofbiz.content.content.AbstractContentWrapper;
import org.apache.ofbiz.content.content.ContentWorker;
import org.apache.ofbiz.content.content.ContentWrapper;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceContainer;

/**
 * Product Config Item Content Worker: gets product content to display
 */
public class ProductConfigItemContentWrapper extends AbstractContentWrapper {

    private static final String MODULE = ProductConfigItemContentWrapper.class.getName();

    private static final UtilCache<String, String> CONFIG_ITEM_CONTENT_CACHE = UtilCache.createUtilCache(
            "configItem.content.rendered", true); // use soft reference to free up memory if needed

    private transient LocalDispatcher dispatcher;
    private String dispatcherName;
    private transient Delegator delegator;
    private String delegatorName;
    private GenericValue productConfigItem;
    private Locale locale;
    private String mimeTypeId;


    public static ProductConfigItemContentWrapper makeProductConfigItemContentWrapper(GenericValue productConfigItem, HttpServletRequest request) {
        return new ProductConfigItemContentWrapper(productConfigItem, request);
    }

    public ProductConfigItemContentWrapper(LocalDispatcher dispatcher, GenericValue productConfigItem, Locale locale, String mimeTypeId) {
        this.dispatcher = dispatcher;
        this.dispatcherName = dispatcher.getName();
        this.delegator = productConfigItem.getDelegator();
        this.delegatorName = delegator.getDelegatorName();
        this.productConfigItem = productConfigItem;
        this.locale = locale;
        this.mimeTypeId = mimeTypeId;
    }

    public ProductConfigItemContentWrapper(GenericValue productConfigItem, HttpServletRequest request) {
        this.dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        this.dispatcherName = dispatcher.getName();
        this.delegator = (Delegator) request.getAttribute("delegator");
        this.delegatorName = delegator.getDelegatorName();
        this.productConfigItem = productConfigItem;
        this.locale = UtilHttp.getLocale(request);
        this.mimeTypeId = ContentWrapper.getDefaultMimeTypeId(this.delegator);
    }

    @Override
    public StringWrapper get(String confItemContentTypeId, String encoderType) {
        return StringUtil.makeStringWrapper(getProductConfigItemContentAsText(productConfigItem, confItemContentTypeId, locale, mimeTypeId,
                getDelegator(), getDispatcher(), encoderType));
    }

    @Override
    public String getIdFieldName() {
        return "configItemId";
    }

    @Override
    public String getContentEntityName() {
        return "ProdConfItemContent";
    }

    @Override
    public String getContentTypeFieldName() {
        return "confItemContentTypeId";
    }

    @Override
    public List<String> getCandidateFieldEntityNames() {
        return Collections.emptyList();
    }

    @Override
    public UtilCache<String, String> getCache() {
        return CONFIG_ITEM_CONTENT_CACHE;
    }

    @Override
    public GenericValue getEntityValue() {
        return productConfigItem;
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public String getMimeTypeId() {
        return mimeTypeId;
    }

    @Override
    public String getEntityContextKey() {
        return "productConfigItem";
    }

    @Override
    public String getContentContextKey() {
        return "productConfigItemContent";
    }

    /**
     * Gets delegator.
     * @return the delegator
     */
    public Delegator getDelegator() {
        if (delegator == null) {
            delegator = DelegatorFactory.getDelegator(delegatorName);
        }
        return delegator;
    }

    /**
     * Gets dispatcher.
     * @return the dispatcher
     */
    public LocalDispatcher getDispatcher() {
        if (dispatcher == null) {
            dispatcher = ServiceContainer.getLocalDispatcher(dispatcherName, this.getDelegator());
        }
        return dispatcher;
    }

    public static String getProductConfigItemContentAsText(GenericValue productConfigItem, String confItemContentTypeId,
                                                           HttpServletRequest request, String encoderType) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        String mimeTypeId = ContentWrapper.getDefaultMimeTypeId(productConfigItem.getDelegator());
        return getProductConfigItemContentAsText(productConfigItem, confItemContentTypeId, UtilHttp.getLocale(request), mimeTypeId,
                productConfigItem.getDelegator(), dispatcher, encoderType);
    }

    public static String getProductConfigItemContentAsText(GenericValue productConfigItem, String confItemContentTypeId, Locale locale,
                                                           LocalDispatcher dispatcher, String encoderType) {
        return getProductConfigItemContentAsText(productConfigItem, confItemContentTypeId, locale, null, null, dispatcher, encoderType);
    }

    public static String getProductConfigItemContentAsText(GenericValue productConfigItem, String confItemContentTypeId, Locale locale,
                                                           String mimeTypeId, Delegator delegator, LocalDispatcher dispatcher, String encoderType) {
        // Delegates the shared cache/fallback/encode logic to AbstractContentWrapper; the
        // entity-specific rendering stays in getProductConfigItemContentAsText(..., outWriter, ...).
        return renderAndCacheContentAsText(CONFIG_ITEM_CONTENT_CACHE, productConfigItem, "configItemId", confItemContentTypeId,
                locale, mimeTypeId, delegator, encoderType, "Error rendering ProdConfItemContent", MODULE,
                outWriter -> getProductConfigItemContentAsText(null, productConfigItem, confItemContentTypeId, locale, mimeTypeId,
                        delegator, dispatcher, outWriter, true));
    }

    public static void getProductConfigItemContentAsText(String configItemId, GenericValue productConfigItem, String confItemContentTypeId,
            Locale locale, String mimeTypeId, Delegator delegator, LocalDispatcher dispatcher, Writer outWriter)
            throws GeneralException, IOException {
        getProductConfigItemContentAsText(configItemId, productConfigItem, confItemContentTypeId, locale, mimeTypeId, delegator, dispatcher,
                outWriter, true);
    }

    public static void getProductConfigItemContentAsText(String configItemId, GenericValue productConfigItem, String confItemContentTypeId,
            Locale locale, String mimeTypeId, Delegator delegator, LocalDispatcher dispatcher, Writer outWriter, boolean cache)
            throws GeneralException, IOException {
        if (configItemId == null && productConfigItem != null) {
            configItemId = productConfigItem.getString("configItemId");
        }

        if (delegator == null && productConfigItem != null) {
            delegator = productConfigItem.getDelegator();
        }

        if (UtilValidate.isEmpty(mimeTypeId)) {
            mimeTypeId = ContentWrapper.getDefaultMimeTypeId(delegator);
        }

        GenericValue productConfigItemContent = EntityQuery.use(delegator).from("ProdConfItemContent")
                .where("configItemId", configItemId, "confItemContentTypeId", confItemContentTypeId)
                .orderBy("-fromDate")
                .cache(cache)
                .filterByDate()
                .queryFirst();
        if (productConfigItemContent != null) {
            // when rendering the product config item content, always include the ProductConfigItem and
            // ProdConfItemContent records that this comes from
            Map<String, Object> inContext = new HashMap<>();
            inContext.put("productConfigItem", productConfigItem);
            inContext.put("productConfigItemContent", productConfigItemContent);
            ContentWorker.renderContentAsText(dispatcher, productConfigItemContent.getString("contentId"), outWriter, inContext, locale,
                    mimeTypeId, null, null, cache);
        } else {
            String candidateValue = null;
            if (productConfigItem != null) {
                candidateValue = ContentWrapper.getCandidateFieldValue(productConfigItem, confItemContentTypeId);
            } else {
                candidateValue = ContentWrapper.getCandidateFieldValue(delegator, "ProductConfigItem", EntityCondition
                        .makeCondition("configItemId", configItemId), confItemContentTypeId, cache);
            }
            if (UtilValidate.isNotEmpty(candidateValue)) {
                outWriter.write(candidateValue);
            }
        }
    }
}
