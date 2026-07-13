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
package org.apache.ofbiz.product.category;

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
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.cache.UtilCache;
import org.apache.ofbiz.content.content.AbstractContentWrapper;
import org.apache.ofbiz.content.content.ContentWorker;
import org.apache.ofbiz.content.content.ContentWrapper;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Category Content Worker: gets category content to display
 */
public class CategoryContentWrapper extends AbstractContentWrapper {

    private static final String MODULE = CategoryContentWrapper.class.getName();

    private static final UtilCache<String, String> CATEGORY_CONTENT_CACHE = UtilCache.createUtilCache(
            "category.content.rendered", true); // use soft reference to free up memory if needed

    private LocalDispatcher dispatcher;
    private GenericValue productCategory;
    private Locale locale;
    private String mimeTypeId;

    public static CategoryContentWrapper makeCategoryContentWrapper(GenericValue productCategory, HttpServletRequest request) {
        return new CategoryContentWrapper(productCategory, request);
    }

    public CategoryContentWrapper(LocalDispatcher dispatcher, GenericValue productCategory, Locale locale, String mimeTypeId) {
        this.dispatcher = dispatcher;
        this.productCategory = productCategory;
        this.locale = locale;
        this.mimeTypeId = mimeTypeId;
    }

    public CategoryContentWrapper(GenericValue productCategory, HttpServletRequest request) {
        this.dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        this.productCategory = productCategory;
        this.locale = UtilHttp.getLocale(request);
        this.mimeTypeId = ContentWrapper.getDefaultMimeTypeId((Delegator) request.getAttribute("delegator"));
    }

    @Override
    public StringUtil.StringWrapper get(String prodCatContentTypeId, String encoderType) {
        return StringUtil.makeStringWrapper(getProductCategoryContentAsText(productCategory, prodCatContentTypeId, locale,
                mimeTypeId, productCategory.getDelegator(), dispatcher, encoderType));
    }

    @Override
    public String getIdFieldName() {
        return "productCategoryId";
    }

    @Override
    public String getContentEntityName() {
        return "ProductCategoryContent";
    }

    @Override
    public String getContentTypeFieldName() {
        return "prodCatContentTypeId";
    }

    @Override
    public List<String> getCandidateFieldEntityNames() {
        return Collections.emptyList();
    }

    @Override
    public UtilCache<String, String> getCache() {
        return CATEGORY_CONTENT_CACHE;
    }

    @Override
    public GenericValue getEntityValue() {
        return productCategory;
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
    public LocalDispatcher getDispatcher() {
        return dispatcher;
    }

    @Override
    public String getEntityContextKey() {
        return "productCategory";
    }

    @Override
    public String getContentContextKey() {
        return "categoryContent";
    }

    public static String getProductCategoryContentAsText(GenericValue productCategory, String prodCatContentTypeId, HttpServletRequest request,
                                                         String encoderType) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        String mimeTypeId = ContentWrapper.getDefaultMimeTypeId(productCategory.getDelegator());
        return getProductCategoryContentAsText(productCategory, prodCatContentTypeId, UtilHttp.getLocale(request), mimeTypeId,
                productCategory.getDelegator(), dispatcher, encoderType);
    }

    public static String getProductCategoryContentAsText(GenericValue productCategory, String prodCatContentTypeId, Locale locale,
                                                         LocalDispatcher dispatcher, String encoderType) {
        return getProductCategoryContentAsText(productCategory, prodCatContentTypeId, locale, null, null, dispatcher, encoderType);
    }

    public static String getProductCategoryContentAsText(GenericValue productCategory, String prodCatContentTypeId,
            Locale locale, String mimeTypeId,
            Delegator delegator, LocalDispatcher dispatcher, String encoderType) {
        if (productCategory == null) {
            return null;
        }

        // Delegates the shared cache/fallback/encode logic to AbstractContentWrapper; the
        // entity-specific rendering stays in getProductCategoryContentAsText(..., outWriter, ...).
        return renderAndCacheContentAsText(CATEGORY_CONTENT_CACHE, productCategory, "productCategoryId", prodCatContentTypeId,
                locale, mimeTypeId, delegator, encoderType, "Error rendering CategoryContent", MODULE,
                outWriter -> getProductCategoryContentAsText(null, productCategory, prodCatContentTypeId, locale, mimeTypeId,
                        delegator, dispatcher, outWriter, true));
    }

    public static void getProductCategoryContentAsText(String productCategoryId, GenericValue productCategory, String prodCatContentTypeId,
            Locale locale, String mimeTypeId, Delegator delegator, LocalDispatcher dispatcher, Writer outWriter)
            throws GeneralException, IOException {
        getProductCategoryContentAsText(productCategoryId, productCategory, prodCatContentTypeId, locale, mimeTypeId,
                delegator, dispatcher, outWriter, true);
    }

    public static void getProductCategoryContentAsText(String productCategoryId, GenericValue productCategory, String prodCatContentTypeId,
            Locale locale, String mimeTypeId, Delegator delegator, LocalDispatcher dispatcher, Writer outWriter, boolean cache)
            throws GeneralException, IOException {
        if (productCategory != null) {
            productCategoryId = productCategory.getString("productCategoryId");
        } else if (productCategoryId != null) {
            productCategory = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId",
                    productCategoryId).cache(cache).queryOne();
        } else {
            throw new GeneralException("Missing parameter productCategory or productCategoryId!");
        }

        if (delegator == null) {
            delegator = productCategory.getDelegator();
        }

        if (UtilValidate.isEmpty(mimeTypeId)) {
            mimeTypeId = ContentWrapper.getDefaultMimeTypeId(delegator);
        }

        List<GenericValue> categoryContentList = EntityQuery.use(delegator).from("ProductCategoryContent").where("productCategoryId",
                productCategoryId, "prodCatContentTypeId", prodCatContentTypeId).orderBy("-fromDate").cache(cache).filterByDate().queryList();
        GenericValue categoryContent = EntityUtil.getFirst(categoryContentList);
        if (categoryContent != null) {
            // when rendering the category content, always include the Product Category and ProductCategoryContent records that this comes from
            Map<String, Object> inContext = new HashMap<>();
            inContext.put("productCategory", productCategory);
            inContext.put("categoryContent", categoryContent);
            ContentWorker.renderContentAsText(dispatcher, categoryContent.getString("contentId"), outWriter, inContext,
                    locale, mimeTypeId, null, null, cache);
        } else {
            String candidateValue = ContentWrapper.getCandidateFieldValue(productCategory, prodCatContentTypeId);
            if (UtilValidate.isNotEmpty(candidateValue)) {
                outWriter.write(candidateValue);
            }
        }
    }
}
