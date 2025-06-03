package com.learnhai.scim.mapper;

import com.learnhai.scim.model.scim.ScimUser;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;


import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    private final String scimBaseUrl;

    public UserMapper(@Value("${scim.base-url:${server.servlet.context-path:}}") String scimBaseUrl) {
        // Ensure base URL doesn't end with a slash if context path is just "/"
        this.scimBaseUrl = "/".equals(scimBaseUrl) ? "" : scimBaseUrl;
    }


    public UserRepresentation toKeycloakUser(ScimUser scimUser, UserRepresentation existingKcUser) {
        UserRepresentation kcUser = (existingKcUser != null) ? existingKcUser : new UserRepresentation();

        if (StringUtils.isNotBlank(scimUser.getUserName())) {
            kcUser.setUsername(scimUser.getUserName());
        }
        // SCIM 'active' maps to Keycloak 'enabled'
        // Only set if scimUser.isActive() is explicitly provided, otherwise keep existing or Keycloak default
        // For create, Keycloak defaults to enabled=true. For update, scimUser.isActive() should be checked.
        kcUser.setEnabled(scimUser.isActive());


        if (scimUser.getName() != null) {
            ScimUser.Name scimName = scimUser.getName();
            if (StringUtils.isNotBlank(scimName.getGivenName())) kcUser.setFirstName(scimName.getGivenName());
            if (StringUtils.isNotBlank(scimName.getFamilyName())) kcUser.setLastName(scimName.getFamilyName());
        }

        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                    .filter(ScimUser.Email::isPrimary)
                    .findFirst()
                    .or(() -> scimUser.getEmails().stream().findFirst()) // Fallback to first email if no primary
                    .ifPresent(email -> {
                        if (StringUtils.isNotBlank(email.getValue())) {
                            kcUser.setEmail(email.getValue());
                            kcUser.setEmailVerified(true); // Common practice for SCIM provisioned emails
                        }
                    });
        }

        // Handle password (Keycloak only accepts this on create or specific password reset flows)
        // For simplicity, this mapper won't set kcUser.setCredentials(...) directly.
        // That should be handled in the service layer if a password is provided.

        // Map standard SCIM attributes to Keycloak custom attributes if not directly mapped
        Map<String, List<String>> attributes = kcUser.getAttributes() == null ? new HashMap<>() : new HashMap<>(kcUser.getAttributes());
        if (StringUtils.isNotBlank(scimUser.getExternalId())) attributes.put("externalId", List.of(scimUser.getExternalId()));
        if (StringUtils.isNotBlank(scimUser.getDisplayName())) attributes.put("displayName", List.of(scimUser.getDisplayName()));
        if (StringUtils.isNotBlank(scimUser.getNickName())) attributes.put("nickName", List.of(scimUser.getNickName()));
        if (StringUtils.isNotBlank(scimUser.getProfileUrl())) attributes.put("profileUrl", List.of(scimUser.getProfileUrl()));
        if (StringUtils.isNotBlank(scimUser.getTitle())) attributes.put("title", List.of(scimUser.getTitle()));
        if (StringUtils.isNotBlank(scimUser.getUserType())) attributes.put("userType", List.of(scimUser.getUserType()));
        if (StringUtils.isNotBlank(scimUser.getPreferredLanguage())) attributes.put("locale", List.of(scimUser.getPreferredLanguage())); // Keycloak uses 'locale'
        if (StringUtils.isNotBlank(scimUser.getTimezone())) attributes.put("timezone", List.of(scimUser.getTimezone()));

        // Enterprise User Extension
        if (scimUser.getEnterpriseUser() != null) {
            ScimUser.EnterpriseUserExtension enterprise = scimUser.getEnterpriseUser();
            if (StringUtils.isNotBlank(enterprise.getEmployeeNumber())) attributes.put("employeeNumber", List.of(enterprise.getEmployeeNumber()));
            if (StringUtils.isNotBlank(enterprise.getCostCenter())) attributes.put("costCenter", List.of(enterprise.getCostCenter()));
            if (StringUtils.isNotBlank(enterprise.getOrganization())) attributes.put("organization", List.of(enterprise.getOrganization()));
            if (StringUtils.isNotBlank(enterprise.getDivision())) attributes.put("division", List.of(enterprise.getDivision()));
            if (StringUtils.isNotBlank(enterprise.getDepartment())) attributes.put("department", List.of(enterprise.getDepartment()));
            if (enterprise.getManager() != null && StringUtils.isNotBlank(enterprise.getManager().getValue())) {
                attributes.put("managerId", List.of(enterprise.getManager().getValue()));
            }
             if (enterprise.getManager() != null && StringUtils.isNotBlank(enterprise.getManager().getDisplayName())) {
                attributes.put("managerDisplayName", List.of(enterprise.getManager().getDisplayName()));
            }
        }

        if (!attributes.isEmpty()) {
            kcUser.setAttributes(attributes);
        }
        return kcUser;
    }

    public ScimUser toScimUser(UserRepresentation kcUser) {
        ScimUser scimUser = new ScimUser();
        scimUser.setId(kcUser.getId());
        scimUser.setUserName(kcUser.getUsername());
        scimUser.setActive(kcUser.isEnabled());

        ScimUser.Name scimName = new ScimUser.Name();
        scimName.setGivenName(kcUser.getFirstName());
        scimName.setFamilyName(kcUser.getLastName());
        String formattedName = List.of(Optional.ofNullable(kcUser.getFirstName()).orElse(""),
                                     Optional.ofNullable(kcUser.getLastName()).orElse(""))
                                .stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(" ")).trim();
        if (!formattedName.isEmpty()) {
            scimName.setFormatted(formattedName);
        }
        scimUser.setName(scimName);


        if (StringUtils.isNotBlank(kcUser.getEmail())) {
            ScimUser.Email scimEmail = new ScimUser.Email();
            scimEmail.setValue(kcUser.getEmail());
            scimEmail.setPrimary(true);
            scimEmail.setType("work"); // Default type
            scimUser.setEmails(List.of(scimEmail));
        }

        // Map from Keycloak attributes back to SCIM fields
        Map<String, List<String>> kcAttributes = kcUser.getAttributes();
        if (kcAttributes != null) {
            scimUser.setExternalId(getFirstAttribute(kcAttributes, "externalId"));
            scimUser.setDisplayName(getFirstAttribute(kcAttributes, "displayName"));
            scimUser.setNickName(getFirstAttribute(kcAttributes, "nickName"));
            scimUser.setProfileUrl(getFirstAttribute(kcAttributes, "profileUrl"));
            scimUser.setTitle(getFirstAttribute(kcAttributes, "title"));
            scimUser.setUserType(getFirstAttribute(kcAttributes, "userType"));
            scimUser.setPreferredLanguage(getFirstAttribute(kcAttributes, "locale")); // Keycloak uses 'locale'
            scimUser.setTimezone(getFirstAttribute(kcAttributes, "timezone"));

            // Enterprise User Extension
            ScimUser.EnterpriseUserExtension enterprise = new ScimUser.EnterpriseUserExtension();
            boolean enterpriseDataSet = false;
            enterprise.setEmployeeNumber(getFirstAttribute(kcAttributes, "employeeNumber"));
            if(enterprise.getEmployeeNumber() != null) enterpriseDataSet = true;

            enterprise.setCostCenter(getFirstAttribute(kcAttributes, "costCenter"));
            if(enterprise.getCostCenter() != null) enterpriseDataSet = true;

            enterprise.setOrganization(getFirstAttribute(kcAttributes, "organization"));
            if(enterprise.getOrganization() != null) enterpriseDataSet = true;

            enterprise.setDivision(getFirstAttribute(kcAttributes, "division"));
            if(enterprise.getDivision() != null) enterpriseDataSet = true;

            enterprise.setDepartment(getFirstAttribute(kcAttributes, "department"));
            if(enterprise.getDepartment() != null) enterpriseDataSet = true;

            String managerId = getFirstAttribute(kcAttributes, "managerId");
            String managerDisplayName = getFirstAttribute(kcAttributes, "managerDisplayName");
            if (managerId != null) {
                ScimUser.EnterpriseUserExtension.Manager manager = new ScimUser.EnterpriseUserExtension.Manager();
                manager.setValue(managerId);
                manager.setDisplayName(managerDisplayName); // Can be set if available
                // manager.setRef(...); // To construct $ref, you'd need base SCIM URL and path to Users + managerId
                enterprise.setManager(manager);
                enterpriseDataSet = true;
            }
            if (enterpriseDataSet) {
                scimUser.setEnterpriseUser(enterprise);
                scimUser.getSchemas().add(ScimUser.SCHEMA_ENTERPRISE_USER);
            }
        }

        ScimUser.Meta meta = new ScimUser.Meta();
        meta.setResourceType("User");
        meta.setLocation(scimBaseUrl + "/scim/v2/Users/" + kcUser.getId());
        if (kcUser.getCreatedTimestamp() != null) {
            meta.setCreated(Instant.ofEpochMilli(kcUser.getCreatedTimestamp()));
        }
        // Keycloak UserRepresentation doesn't have a direct lastModified timestamp in the base object.
        // If you store it as an attribute, map it here. For now, set to created or now.
        meta.setLastModified(meta.getCreated() != null ? meta.getCreated() : Instant.now());
        // meta.setVersion(...); // ETag - Keycloak doesn't provide this directly for users in a simple way
        scimUser.setMeta(meta);

        return scimUser;
    }

    private String getFirstAttribute(Map<String, List<String>> attributes, String key) {
        if (attributes != null && attributes.containsKey(key) && !attributes.get(key).isEmpty()) {
            return attributes.get(key).get(0);
        }
        return null;
    }
}