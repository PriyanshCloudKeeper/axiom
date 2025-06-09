package com.learnhai.scim.mapper;

import com.learnhai.scim.model.scim.ScimUser;
import com.learnhai.scim.service.KeycloakService;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UserMapper {

    private final String scimBaseUrl;
    private final KeycloakService keycloakService;

    public UserMapper(@Value("${scim.base-url:${server.servlet.context-path:}}") String scimBaseUrl,
                      KeycloakService keycloakService) {
        this.scimBaseUrl = "/".equals(scimBaseUrl) ? "" : scimBaseUrl;
        this.keycloakService = keycloakService;
    }


    public UserRepresentation toKeycloakUser(ScimUser scimUser, UserRepresentation existingKcUser) {
        UserRepresentation kcUser = (existingKcUser != null) ? existingKcUser : new UserRepresentation();
        log.debug("Mapping SCIM user to Keycloak UserRepresentation. Incoming SCIM userName: {}", scimUser.getUserName());

        if (StringUtils.isNotBlank(scimUser.getUserName())) {
            kcUser.setUsername(scimUser.getUserName());
        }

        if (existingKcUser == null) {
             kcUser.setEnabled(scimUser.isActive());
        } else { 
             kcUser.setEnabled(scimUser.isActive());
        }


        if (scimUser.getName() != null) {
            ScimUser.Name scimName = scimUser.getName();
            if (StringUtils.isNotBlank(scimName.getGivenName())) kcUser.setFirstName(scimName.getGivenName()); else if (existingKcUser != null) kcUser.setFirstName(null);
            if (StringUtils.isNotBlank(scimName.getFamilyName())) kcUser.setLastName(scimName.getFamilyName()); else if (existingKcUser != null) kcUser.setLastName(null);
        } else if (existingKcUser != null) {
            kcUser.setFirstName(null);
            kcUser.setLastName(null);
        }


        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                    .filter(email -> email.isPrimary() && StringUtils.isNotBlank(email.getValue()))
                    .findFirst()
                    .or(() -> scimUser.getEmails().stream().filter(e -> StringUtils.isNotBlank(e.getValue())).findFirst())
                    .ifPresent(email -> {
                        kcUser.setEmail(email.getValue());
                        kcUser.setEmailVerified(true);
                    });
        } else if (existingKcUser != null) {
            kcUser.setEmail(null);
            kcUser.setEmailVerified(false);
        }

        Map<String, List<String>> attributes = existingKcUser != null && existingKcUser.getAttributes() != null ? new HashMap<>(existingKcUser.getAttributes()) : new HashMap<>();
        if (existingKcUser != null) {
            attributes.clear();
        }

        if (StringUtils.isNotBlank(scimUser.getExternalId())) attributes.put("externalId", List.of(scimUser.getExternalId()));
        if (StringUtils.isNotBlank(scimUser.getDisplayName())) attributes.put("displayName", List.of(scimUser.getDisplayName()));
        if (StringUtils.isNotBlank(scimUser.getNickName())) attributes.put("nickName", List.of(scimUser.getNickName()));
        if (StringUtils.isNotBlank(scimUser.getProfileUrl())) attributes.put("profileUrl", List.of(scimUser.getProfileUrl()));
        if (StringUtils.isNotBlank(scimUser.getTitle())) attributes.put("title", List.of(scimUser.getTitle()));
        if (StringUtils.isNotBlank(scimUser.getUserType())) attributes.put("userType", List.of(scimUser.getUserType()));
        if (StringUtils.isNotBlank(scimUser.getPreferredLanguage())) attributes.put("locale", List.of(scimUser.getPreferredLanguage()));
        if (StringUtils.isNotBlank(scimUser.getTimezone())) attributes.put("timezone", List.of(scimUser.getTimezone()));

        if (scimUser.getEnterpriseUser() != null) {
            ScimUser.EnterpriseUserExtension enterprise = scimUser.getEnterpriseUser();
            log.debug("Mapping EnterpriseUser extension from SCIM: {}", enterprise);
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

        kcUser.setAttributes(attributes);

        log.debug("Finished mapping to Keycloak UserRepresentation. Username: {}, Email: {}, Attributes set: {}", kcUser.getUsername(), kcUser.getEmail(), kcUser.getAttributes());
        return kcUser;
    }

    public ScimUser toScimUser(UserRepresentation kcUser) {
        log.debug("Mapping Keycloak UserRepresentation (ID: {}) to SCIM user. KC Attributes: {}", kcUser.getId(), kcUser.getAttributes());

        ScimUser scimUser = new ScimUser();
        scimUser.setId(kcUser.getId());
        scimUser.setUserName(kcUser.getUsername());
        scimUser.setActive(kcUser.isEnabled());

        ScimUser.Name scimName = new ScimUser.Name();
        scimName.setGivenName(kcUser.getFirstName());
        scimName.setFamilyName(kcUser.getLastName());
        List<String> nameParts = new ArrayList<>();
        if (StringUtils.isNotBlank(kcUser.getFirstName())) nameParts.add(kcUser.getFirstName());
        if (StringUtils.isNotBlank(kcUser.getLastName())) nameParts.add(kcUser.getLastName());
        String formattedName = String.join(" ", nameParts);
        if (!formattedName.isEmpty()) {
            scimName.setFormatted(formattedName);
        }
        if (StringUtils.isNotBlank(scimName.getGivenName()) ||
            StringUtils.isNotBlank(scimName.getFamilyName()) ||
            StringUtils.isNotBlank(scimName.getFormatted())) {
            scimUser.setName(scimName);
        }


        if (StringUtils.isNotBlank(kcUser.getEmail())) {
            ScimUser.Email scimEmail = new ScimUser.Email();
            scimEmail.setValue(kcUser.getEmail());
            scimEmail.setPrimary(true);
            scimEmail.setType("work");
            scimUser.setEmails(List.of(scimEmail));
        }

        Map<String, List<String>> kcAttributes = kcUser.getAttributes();
        if (kcAttributes != null) {
            scimUser.setExternalId(getFirstAttribute(kcAttributes, "externalId"));
            scimUser.setDisplayName(getFirstAttribute(kcAttributes, "displayName"));
            scimUser.setNickName(getFirstAttribute(kcAttributes, "nickName"));
            scimUser.setProfileUrl(getFirstAttribute(kcAttributes, "profileUrl"));
            scimUser.setTitle(getFirstAttribute(kcAttributes, "title"));
            scimUser.setUserType(getFirstAttribute(kcAttributes, "userType"));
            scimUser.setPreferredLanguage(getFirstAttribute(kcAttributes, "locale"));
            scimUser.setTimezone(getFirstAttribute(kcAttributes, "timezone"));

            ScimUser.EnterpriseUserExtension enterprise = new ScimUser.EnterpriseUserExtension();
            boolean enterpriseDataSet = false;

            String empNo = getFirstAttribute(kcAttributes, "employeeNumber");
            if (empNo != null) { enterprise.setEmployeeNumber(empNo); enterpriseDataSet = true; }

            String costCenterVal = getFirstAttribute(kcAttributes, "costCenter");
            if (costCenterVal != null) { enterprise.setCostCenter(costCenterVal); enterpriseDataSet = true; }
            
            String organizationVal = getFirstAttribute(kcAttributes, "organization");
            if (organizationVal != null) { enterprise.setOrganization(organizationVal); enterpriseDataSet = true; }

            String divisionVal = getFirstAttribute(kcAttributes, "division");
            if (divisionVal != null) { enterprise.setDivision(divisionVal); enterpriseDataSet = true; }

            String departmentVal = getFirstAttribute(kcAttributes, "department");
            if (departmentVal != null) { enterprise.setDepartment(departmentVal); enterpriseDataSet = true; }
            
            String managerId = getFirstAttribute(kcAttributes, "managerId");
            if (managerId != null) {
                ScimUser.EnterpriseUserExtension.Manager manager = new ScimUser.EnterpriseUserExtension.Manager();
                manager.setValue(managerId);
                manager.setDisplayName(getFirstAttribute(kcAttributes, "managerDisplayName")); 
                enterprise.setManager(manager);
                enterpriseDataSet = true;
            }

            if (enterpriseDataSet) {
                scimUser.setEnterpriseUser(enterprise);
                if (!scimUser.getSchemas().contains(ScimUser.SCHEMA_ENTERPRISE_USER)) {
                     scimUser.getSchemas().add(ScimUser.SCHEMA_ENTERPRISE_USER);
                }
            }
        }

        if (kcUser.getId() != null) {
            List<GroupRepresentation> userGroupsInKc = keycloakService.getUserGroups(kcUser.getId()); 
            if (userGroupsInKc != null && !userGroupsInKc.isEmpty()) {
                List<ScimUser.GroupReference> scimGroups = userGroupsInKc.stream()
                    .map(g -> {
                        ScimUser.GroupReference ref = new ScimUser.GroupReference();
                        ref.setValue(g.getId());
                        ref.setDisplay(g.getName());
                        ref.setRef(scimBaseUrl + "/scim/v2/Groups/" + g.getId());
                        ref.setType("direct");
                        return ref;
                    }).collect(Collectors.toList());
                scimUser.setGroups(scimGroups);
            } else {
                scimUser.setGroups(new ArrayList<>()); 
            }
        }

        ScimUser.Meta meta = scimUser.getMeta();
        meta.setResourceType("User");
        meta.setLocation(scimBaseUrl + "/scim/v2/Users/" + kcUser.getId());
        if (kcUser.getCreatedTimestamp() != null) {
            meta.setCreated(Instant.ofEpochMilli(kcUser.getCreatedTimestamp()));
        }
        meta.setLastModified(meta.getCreated() != null ? meta.getCreated() : Instant.now());

        log.debug("Finished mapping to SCIM user. Final ScimUser object: {}", scimUser);
        return scimUser;
    }

    private String getFirstAttribute(Map<String, List<String>> attributes, String key) {
        if (attributes != null && attributes.containsKey(key) && !attributes.get(key).isEmpty()) {
            return attributes.get(key).get(0);
        }
        return null;
    }
}