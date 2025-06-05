package com.learnhai.scim.service;

import com.learnhai.scim.exception.ScimException;
import com.learnhai.scim.mapper.GroupMapper;
import com.learnhai.scim.model.scim.ScimGroup;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScimGroupService {

    private final KeycloakService keycloakService;
    private final GroupMapper groupMapper;

    @Autowired
    public ScimGroupService(KeycloakService keycloakService, GroupMapper groupMapper) {
        this.keycloakService = keycloakService;
        this.groupMapper = groupMapper;
    }

    public ScimGroup createGroup(ScimGroup scimGroup) {
        if (StringUtils.isNotBlank(scimGroup.getDisplayName())) {
            keycloakService.getGroupByName(scimGroup.getDisplayName()).ifPresent(existing -> {
                log.warn("Conflict: Group with name '{}' already exists.", scimGroup.getDisplayName());
                throw new ScimException("Group with name '" + scimGroup.getDisplayName() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
            });
        }

        GroupRepresentation kcGroupToCreate = groupMapper.toKeycloakGroup(scimGroup, null);
        String groupId = keycloakService.createGroup(kcGroupToCreate);
        log.info("Successfully created group in Keycloak with ID: {}", groupId);


        if (scimGroup.getMembers() != null && !scimGroup.getMembers().isEmpty()) {
            log.info("Processing {} members provided for new group {}", scimGroup.getMembers().size(), groupId);
            for (ScimGroup.Member member : scimGroup.getMembers()) {
                if ("User".equalsIgnoreCase(member.getType()) && StringUtils.isNotBlank(member.getValue())) {
                    log.info("Attempting to add user member ID {} to new group {}", member.getValue(), groupId);
                    keycloakService.getUserById(member.getValue())
                        .orElseThrow(() -> {
                            log.error("User member with ID {} not found during group creation.", member.getValue());
                            return new ScimException("User member with ID " + member.getValue() + " not found.", HttpStatus.BAD_REQUEST, "invalidValue");
                        });
                    keycloakService.addUserToGroup(member.getValue(), groupId);
                }
            }
        }

        GroupRepresentation createdKcGroup = keycloakService.getGroupById(groupId)
                .orElseThrow(() -> {
                    log.error("Critical error: Failed to retrieve just-created group with ID: {}", groupId);
                    return new ScimException("Failed to retrieve created group: " + groupId, HttpStatus.INTERNAL_SERVER_ERROR);
                });
        
        List<UserRepresentation> members = keycloakService.getGroupMembers(groupId, 0, 200);
        log.info("Fetched {} members for newly created group {}", members.size(), groupId);
        return groupMapper.toScimGroup(createdKcGroup, members);
    }

    public Optional<ScimGroup> getGroupById(String id) {
        return keycloakService.getGroupById(id)
                .map(kcGroup -> {
                    List<UserRepresentation> members = keycloakService.getGroupMembers(id, 0, 200);
                    return groupMapper.toScimGroup(kcGroup, members);
                });
    }

    public ScimGroup replaceGroup(String id, ScimGroup scimGroup) {
        GroupRepresentation existingKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Group not found with id: " + id, HttpStatus.NOT_FOUND));

        if (StringUtils.isNotBlank(scimGroup.getDisplayName()) && !scimGroup.getDisplayName().equals(existingKcGroup.getName())) {
            keycloakService.getGroupByName(scimGroup.getDisplayName()).ifPresent(conflictingGroup -> {
                 if (!conflictingGroup.getId().equals(id)) {
                    log.warn("Conflict on replaceGroup: Group name '{}' is already taken by another group (ID: {}).", scimGroup.getDisplayName(), conflictingGroup.getId());
                    throw new ScimException("Group name '" + scimGroup.getDisplayName() + "' is already taken.", HttpStatus.CONFLICT, "uniqueness");
                }
            });
        }

        GroupRepresentation kcGroupToUpdate = groupMapper.toKeycloakGroup(scimGroup, existingKcGroup);
        keycloakService.updateGroup(id, kcGroupToUpdate);
        log.info("Successfully updated group attributes for ID: {}", id);


        List<UserRepresentation> currentMembers = keycloakService.getGroupMembers(id, 0, Integer.MAX_VALUE);
        Set<String> currentMemberIds = currentMembers.stream().map(UserRepresentation::getId).collect(Collectors.toSet());
        log.info("ReplaceGroup ID: {}: Current members in Keycloak: {}", id, currentMemberIds.size());
        
        Set<String> desiredMemberIds = new HashSet<>();
        if (scimGroup.getMembers() != null) {
            desiredMemberIds = scimGroup.getMembers().stream()
                .filter(m -> "User".equalsIgnoreCase(m.getType()) && StringUtils.isNotBlank(m.getValue()))
                .map(ScimGroup.Member::getValue)
                .collect(Collectors.toSet());
        }
        log.info("ReplaceGroup ID: {}: Desired members from SCIM request: {}", id, desiredMemberIds.size());


        for (String memberId : currentMemberIds) {
            if (!desiredMemberIds.contains(memberId)) {
                log.info("ReplaceGroup ID: {}: Removing member {} (not in desired set)", id, memberId);
                keycloakService.removeUserFromGroup(memberId, id);
            }
        }
        for (String memberId : desiredMemberIds) {
            if (!currentMemberIds.contains(memberId)) {
                 keycloakService.getUserById(memberId)
                        .orElseThrow(() -> {
                            log.error("ReplaceGroup ID: {}: User member with ID {} not found for adding to group.",id, memberId);
                            return new ScimException("User member with ID " + memberId + " not found for group update.", HttpStatus.BAD_REQUEST, "invalidValue");
                        });
                log.info("ReplaceGroup ID: {}: Adding member {} (not in current set)", id, memberId);
                keycloakService.addUserToGroup(memberId, id);
            }
        }

        GroupRepresentation updatedKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Failed to retrieve updated group: " + id, HttpStatus.INTERNAL_SERVER_ERROR));
        List<UserRepresentation> finalMembers = keycloakService.getGroupMembers(id, 0, 200);
        log.info("ReplaceGroup ID: {}: Final fetched members for response: {}", id, finalMembers.size());
        return groupMapper.toScimGroup(updatedKcGroup, finalMembers);
    }

    public ScimGroup patchGroup(String id, Map<String, Object> patchRequest) {
        GroupRepresentation existingKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Group not found with id: " + id, HttpStatus.NOT_FOUND));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) patchRequest.get("Operations");
        if (operations == null || operations.isEmpty()) {
            throw new ScimException("Patch request must contain 'Operations'.", HttpStatus.BAD_REQUEST, "invalidSyntax");
        }

        boolean groupAttributesModified = false; 
        for (Map<String, Object> operation : operations) {
            String op = (String) operation.get("op");
            String path = (String) operation.get("path");
            Object value = operation.get("value");
            log.info("PATCH GROUP ID: {}, Processing op: '{}', path: '{}'", id, op, path);


            if ("replace".equalsIgnoreCase(op)) {
                if ("displayName".equalsIgnoreCase(path)) {
                    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                        String newDisplayName = (String) value;
                        if(!newDisplayName.equals(existingKcGroup.getName())) {
                            keycloakService.getGroupByName(newDisplayName).ifPresent(conflictingGroup -> {
                                if (!conflictingGroup.getId().equals(id)) {
                                    log.warn("Conflict on patchGroup: Group name '{}' is already taken by another group (ID: {}).", newDisplayName, conflictingGroup.getId());
                                    throw new ScimException("Group name '" + newDisplayName + "' is already taken.", HttpStatus.CONFLICT, "uniqueness");
                                }
                            });
                            existingKcGroup.setName(newDisplayName);
                            groupAttributesModified = true;
                            log.info("PATCH GROUP ID: {}: displayName changed to '{}'. groupAttributesModified=true", id, newDisplayName);
                        } else {
                             log.info("PATCH GROUP ID: {}: displayName value is already '{}'. No change needed.", id, newDisplayName);
                        }
                    } else {
                        throw new ScimException("Invalid value for 'displayName'. Non-empty String expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
            } else if ("add".equalsIgnoreCase(op)) {
                if ("members".equalsIgnoreCase(path)) {
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> membersToAdd = (List<Map<String, Object>>) value;
                        for (Map<String, Object> memberMap : membersToAdd) {
                            String memberValue = (String) memberMap.get("value");
                            String memberType = (String) memberMap.get("type"); // Will be null if not sent by client

                            // === OPTION A IMPLEMENTED HERE ===
                            if ((memberType == null || "User".equalsIgnoreCase(memberType)) && StringUtils.isNotBlank(memberValue)) {
                                log.info("PATCH GROUP ID: {}: Attempting to add user ID '{}' (type: {}) to group", id, memberValue, memberType != null ? memberType : "assumed User");
                                try {
                                    keycloakService.getUserById(memberValue)
                                        .orElseThrow(() -> {
                                            log.error("PATCH GROUP ID: {}: User member with ID {} not found for add operation.",id, memberValue);
                                            return new ScimException("User member with ID " + memberValue + " not found for patch add.", HttpStatus.BAD_REQUEST, "invalidValue");
                                        });
                                    keycloakService.addUserToGroup(memberValue, id);
                                } catch (Exception e) {
                                    log.error("PATCH GROUP ID: {}: Error adding user {} to group: {}", id, memberValue, e.getMessage(), e);
                                    throw new ScimException("Failed to add member " + memberValue + " to group " + id, HttpStatus.INTERNAL_SERVER_ERROR, e);
                                }
                            } else {
                                log.warn("PATCH GROUP ID: {}: Skipping member add for value '{}', type '{}'. Does not meet criteria (must be User type or type not specified, and value must be present).", id, memberValue, memberType);
                            }
                        }
                    } else {
                         throw new ScimException("Invalid value for 'members' in add operation. Array of members expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
            } else if ("remove".equalsIgnoreCase(op)) {
                if (path != null && path.toLowerCase().startsWith("members[value eq ")) {
                    String userIdToRemove = path.substring(path.toLowerCase().indexOf("\"") + 1, path.toLowerCase().lastIndexOf("\""));
                     if (StringUtils.isNotBlank(userIdToRemove)) {
                        log.info("PATCH GROUP ID: {}: Attempting to remove user member ID '{}'", id, userIdToRemove);
                        try {
                            keycloakService.removeUserFromGroup(userIdToRemove, id);
                        } catch (Exception e) {
                            log.error("PATCH GROUP ID: {}: Error removing user {} from group: {}", id, userIdToRemove, e.getMessage(), e);
                            throw new ScimException("Failed to remove member " + userIdToRemove + " from group " + id, HttpStatus.INTERNAL_SERVER_ERROR, e);
                        }
                    }
                }
            }
        }

        if (groupAttributesModified) {
            log.info("PATCH GROUP ID: {}: displayName was modified, calling keycloakService.updateGroup.", id);
            keycloakService.updateGroup(id, existingKcGroup);
        } else {
            log.info("PATCH GROUP ID: {}: No group attributes (like displayName) were modified. Membership changes handled directly by KeycloakService.", id);
        }

        GroupRepresentation patchedKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Failed to retrieve patched group: " + id, HttpStatus.INTERNAL_SERVER_ERROR));
        List<UserRepresentation> members = keycloakService.getGroupMembers(id, 0, 200); 
        log.info("PATCH GROUP ID: {}: Fetched group with {} members after all patch operations.", id, members != null ? members.size() : 0);
        return groupMapper.toScimGroup(patchedKcGroup, members);
    }

    public void deleteGroup(String id) {
        keycloakService.getGroupById(id)
            .orElseThrow(() -> new ScimException("Group not found with id: " + id, HttpStatus.NOT_FOUND));
        keycloakService.deleteGroup(id);
        log.info("Successfully deleted group from Keycloak with ID: {}", id);
    }

    public Map<String, Object> getGroups(int startIndex, int count, String filter) {
        int firstResult = Math.max(0, startIndex - 1);
        String searchFilter = null;

        if (StringUtils.isNotBlank(filter) && filter.toLowerCase().startsWith("displayname eq ")) {
            searchFilter = filter.substring("displayname eq ".length()).replace("\"", "").trim();
        } else if (StringUtils.isNotBlank(filter)) {
            searchFilter = filter;
        }

        List<GroupRepresentation> kcGroups = keycloakService.getGroups(firstResult, count, searchFilter);
        List<ScimGroup> scimGroups = new ArrayList<>();
        for(GroupRepresentation kcGroup : kcGroups) {
            List<UserRepresentation> members = keycloakService.getGroupMembers(kcGroup.getId(), 0, 10); // Fetch a few members for preview
            scimGroups.add(groupMapper.toScimGroup(kcGroup, members));
        }
        
        long totalResults = keycloakService.countGroups(searchFilter);

        Map<String, Object> response = new HashMap<>();
        response.put("schemas", Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        response.put("totalResults", totalResults);
        response.put("startIndex", startIndex);
        response.put("itemsPerPage", scimGroups.size());
        response.put("Resources", scimGroups);
        return response;
    }
}