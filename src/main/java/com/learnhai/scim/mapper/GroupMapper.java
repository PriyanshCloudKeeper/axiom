package com.learnhai.scim.mapper;

import com.learnhai.scim.model.scim.ScimGroup;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation; // For mapping members
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;


import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GroupMapper {

    private final String scimBaseUrl;

    public GroupMapper(@Value("${scim.base-url:${server.servlet.context-path:}}") String scimBaseUrl) {
        this.scimBaseUrl = "/".equals(scimBaseUrl) ? "" : scimBaseUrl;
    }

    public GroupRepresentation toKeycloakGroup(ScimGroup scimGroup, GroupRepresentation existingKcGroup) {
        GroupRepresentation kcGroup = (existingKcGroup != null) ? existingKcGroup : new GroupRepresentation();

        if (StringUtils.isNotBlank(scimGroup.getDisplayName())) {
            kcGroup.setName(scimGroup.getDisplayName()); // SCIM displayName maps to Keycloak group name
        }

        Map<String, List<String>> attributes = kcGroup.getAttributes() == null ? new HashMap<>() : new HashMap<>(kcGroup.getAttributes());
        if (StringUtils.isNotBlank(scimGroup.getExternalId())) {
            attributes.put("externalId", List.of(scimGroup.getExternalId()));
        }
        // Add other custom attributes if needed

        if (!attributes.isEmpty()) {
            kcGroup.setAttributes(attributes);
        }
        return kcGroup;
    }

    public ScimGroup toScimGroup(GroupRepresentation kcGroup, List<UserRepresentation> groupMembers) {
        ScimGroup scimGroup = new ScimGroup();
        scimGroup.setId(kcGroup.getId());
        scimGroup.setDisplayName(kcGroup.getName());

        Map<String, List<String>> kcAttributes = kcGroup.getAttributes();
        if (kcAttributes != null && kcAttributes.containsKey("externalId") && !kcAttributes.get("externalId").isEmpty()) {
            scimGroup.setExternalId(kcAttributes.get("externalId").get(0));
        }

        if (groupMembers != null && !groupMembers.isEmpty()) {
            List<ScimGroup.Member> scimMembers = groupMembers.stream().map(kcUser -> {
                ScimGroup.Member member = new ScimGroup.Member();
                member.setValue(kcUser.getId());
                member.setDisplay(kcUser.getUsername()); // Or another display attribute
                member.setType("User");
                member.setRef(scimBaseUrl + "/scim/v2/Users/" + kcUser.getId());
                return member;
            }).collect(Collectors.toList());
            scimGroup.setMembers(scimMembers);
        } else {
            scimGroup.setMembers(new ArrayList<>()); // Ensure members list is present even if empty
        }

        ScimUser.Meta meta = new ScimUser.Meta(); // Re-use Meta structure
        meta.setResourceType("Group");
        meta.setLocation(scimBaseUrl + "/scim/v2/Groups/" + kcGroup.getId());
        // Keycloak GroupRepresentation doesn't have created/lastModified timestamps directly
        // If you store them as attributes, map them here.
        meta.setCreated(Instant.now()); // Placeholder
        meta.setLastModified(Instant.now()); // Placeholder
        // meta.setVersion(...); // ETag - Placeholder
        scimGroup.setMeta(meta);

        return scimGroup;
    }
}