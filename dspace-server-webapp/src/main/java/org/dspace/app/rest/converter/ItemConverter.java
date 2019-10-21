/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.converter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.model.RelationshipRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.Relationship;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.RelationshipService;
import org.dspace.core.Context;
import org.dspace.discovery.IndexableObject;
import org.dspace.services.RequestService;
import org.dspace.services.model.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This is the converter from/to the Item in the DSpace API data model and the
 * REST data model
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@Component
public class ItemConverter
    extends DSpaceObjectConverter<org.dspace.content.Item, org.dspace.app.rest.model.ItemRest>
    implements IndexableObjectConverter<Item, ItemRest> {

    @Autowired
    private ConverterService converter;
    @Autowired
    private MetadataConverter metadataConverter;
    @Autowired
    private RequestService requestService;
    @Autowired
    private RelationshipService relationshipService;
    @Autowired
    private ItemService itemService;

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ItemConverter.class);

    @Override
    public ItemRest convert(org.dspace.content.Item obj) {
        ItemRest item = super.convert(obj);
        item.setInArchive(obj.isArchived());
        item.setDiscoverable(obj.isDiscoverable());
        item.setWithdrawn(obj.isWithdrawn());
        item.setLastModified(obj.getLastModified());
        Collection owningCollection = obj.getOwningCollection();
        if (owningCollection != null) {
            item.setOwningCollection(converter.toRest(owningCollection));
        }
        Collection templateItemOf = obj.getTemplateItemOf();
        if (templateItemOf != null) {
            item.setTemplateItemOf(converter.toRest(templateItemOf));
        }
        List<BitstreamRest> bitstreams = new ArrayList<>();
        for (Bundle bun : obj.getBundles()) {
            for (Bitstream bit : bun.getBitstreams()) {
                bitstreams.add(converter.toRest(bit));
            }
        }
        item.setBitstreams(bitstreams);
        List<Relationship> relationships = new LinkedList<>();
        try {
            Context context;
            Request currentRequest = requestService.getCurrentRequest();
            if (currentRequest != null) {
                HttpServletRequest request = currentRequest.getHttpServletRequest();
                context = ContextUtil.obtainContext(request);
            } else {
                context = new Context();
            }
            relationships = relationshipService.findByItem(context, obj);
        } catch (SQLException e) {
            log.error("Error retrieving relationships for item " + item.getHandle(), e);
        }
        List<RelationshipRest> relationshipRestList = new LinkedList<>();
        for (Relationship relationship : relationships) {
            RelationshipRest relationshipRest = converter.toRest(relationship);
            relationshipRestList.add(relationshipRest);
        }
        item.setRelationships(relationshipRestList);

        List<MetadataValue> fullList = itemService.getMetadata(obj, Item.ANY, Item.ANY, Item.ANY, Item.ANY, true);
        item.setMetadata(metadataConverter.convert(fullList));

        return item;
    }

    @Override
    protected ItemRest newInstance() {
        return new ItemRest();
    }

    @Override
    public Class<Item> getModelClass() {
        return Item.class;
    }

    @Override
    public boolean supportsModel(IndexableObject idxo) {
        return idxo instanceof Item;
    }
}
