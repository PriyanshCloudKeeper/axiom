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
            log.info("PATCH GROUP ID: {}, Processing op: '{}', path: '{}', value: '{}'", id, op, path, value != null ? value.toString().substring(0, Math.min(value.toString().length(), 100)) : "null");


            if ("replace".equalsIgnoreCase(op)) {
                if ("displayName".equalsIgnoreCase(path)) {
                    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                        String newDisplayName = (String) value;
                        if (!newDisplayName.equals(existingKcGroup.getName())) {
                            // ... (conflict check for displayName) ...
                            existingKcGroup.setName(newDisplayName);
                            groupAttributesModified = true;
                        }
                    } else {
                        throw new ScimException("Invalid value for 'displayName'. Non-empty String expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                } else if ("members".equalsIgnoreCase(path)) {
                    // --- NEW LOGIC FOR FULL MEMBER REPLACEMENT ---
                    log.info("PATCH GROUP ID: {}: Handling 'op: replace, path: members'. Value: {}", id, value);
                    Set<String> desiredMemberIds = new HashSet<>();
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> membersToSet = (List<Map<String, Object>>) value;
                        for (Map<String, Object> memberMap : membersToSet) {
                            String memberValue = (String) memberMap.get("value");
                            if (StringUtils.isNotBlank(memberValue)) {
                                // Optionally validate member type if provided:
                                // String memberType = (String) memberMap.get("type");
                                // if (memberType == null || "User".equalsIgnoreCase(memberType)) {
                                desiredMemberIds.add(memberValue);
                                // } else { log.warn("Skipping non-User member type: {}", memberType); }
                            }
                        }
                    } else if (value == null) { // op: replace, path: members, value: null -> means clear all members
                        log.info("PATCH GROUP ID: {}: Received 'op: replace, path: members' with null value. Clearing all members.", id);
                        // desiredMemberIds remains empty, correctly leading to all current members being removed.
                    } else {
                        log.warn("PATCH GROUP ID: {}: For 'op: replace, path: members', 'value' was not a List or null. Value: {}", id, value);
                        throw new ScimException("Invalid value for 'members' in replace operation. Array of member objects or null expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }

                    List<UserRepresentation> currentKcMembers = keycloakService.getGroupMembers(id, 0, Integer.MAX_VALUE);
                    Set<String> currentKcMemberIds = currentKcMembers.stream().map(UserRepresentation::getId).collect(Collectors.toSet());
                    log.info("PATCH GROUP ID: {}: Current members in Keycloak: {}. Desired members from SCIM: {}", id, currentKcMemberIds.size(), desiredMemberIds.size());

                    // Members to remove: in currentKcMemberIds but NOT in desiredMemberIds
                    Set<String> membersToRemove = new HashSet<>(currentKcMemberIds);
                    membersToRemove.removeAll(desiredMemberIds);
                    for (String memberIdToRemove : membersToRemove) {
                        log.info("PATCH GROUP ID: {}: Replacing members - removing user {}", id, memberIdToRemove);
                        keycloakService.removeUserFromGroup(memberIdToRemove, id);
                    }

                    // Members to add: in desiredMemberIds but NOT in currentKcMemberIds
                    Set<String> membersToAdd = new HashSet<>(desiredMemberIds);
                    membersToAdd.removeAll(currentKcMemberIds);
                    for (String memberIdToAdd : membersToAdd) {
                        // It's good practice to verify the user exists before adding to group
                        keycloakService.getUserById(memberIdToAdd)
                                .orElseThrow(() -> {
                                    log.error("PATCH GROUP ID: {}: User member with ID {} not found for adding to group.", id, memberIdToAdd);
                                    return new ScimException("User member with ID " + memberIdToAdd + " not found for group update.", HttpStatus.BAD_REQUEST, "invalidValue");
                                });
                        log.info("PATCH GROUP ID: {}: Replacing members - adding user {}", id, memberIdToAdd);
                        keycloakService.addUserToGroup(memberIdToAdd, id);
                    }
                    // Member changes are done directly, no need to set groupAttributesModified = true;
                    // unless other attributes like displayName were also part of this PATCH.
                    log.info("PATCH GROUP ID: {}: Finished processing 'op: replace, path: members'.", id);
                }
                // Note: Your original code had a path:null check here for groups, removed as it was for user patch.
                // If Okta sends group attribute updates with path:null, value:{...}, add similar logic as in ScimUserService.
                
            } else if ("add".equalsIgnoreCase(op)) {
                if ("members".equalsIgnoreCase(path)) {
                    // ... your existing 'add' members logic from before (ensure it's robust) ...
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> membersOpAdd = (List<Map<String, Object>>) value;
                        for (Map<String, Object> memberMap : membersOpAdd) {
                            String memberValue = (String) memberMap.get("value");
                            if (StringUtils.isNotBlank(memberValue)) {
                                keycloakService.getUserById(memberValue).orElseThrow(() -> new ScimException("User member with ID " + memberValue + " not found for patch add.", HttpStatus.BAD_REQUEST, "invalidValue"));
                                keycloakService.addUserToGroup(memberValue, id);
                            }
                        }
                    } else {
                        throw new ScimException("Invalid value for 'members' in add operation. Array of members expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
            } else if ("remove".equalsIgnoreCase(op)) {
                if (path != null && path.toLowerCase().startsWith("members[value eq ")) {
                    // ... your existing 'remove' members by value logic ...
                    String userIdToRemove = path.substring(path.toLowerCase().indexOf("\"") + 1, path.toLowerCase().lastIndexOf("\""));
                    if (StringUtils.isNotBlank(userIdToRemove)) {
                        keycloakService.removeUserFromGroup(userIdToRemove, id);
                    }
                } else if ("members".equalsIgnoreCase(path) && value == null) {
                    // SCIM spec: "If the "value" parameter is omitted, the target location is REMOVED."
                    // This means remove ALL members.
                    log.info("PATCH GROUP ID: {}: Handling 'op: remove, path: members' (no value). Removing all members.", id);
                    List<UserRepresentation> currentKcMembers = keycloakService.getGroupMembers(id, 0, Integer.MAX_VALUE);
                    for (UserRepresentation member : currentKcMembers) {
                        keycloakService.removeUserFromGroup(member.getId(), id);
                    }
                } else {
                    log.warn("PATCH GROUP ID: {}: 'op: remove' for path '{}' not fully supported or value malformed.", id, path);
                    // Or throw ScimException if it's an invalid remove operation
                }
            }
        } // End of operations loop

        if (groupAttributesModified) {
            log.info("PATCH GROUP ID: {}: Group attributes (like displayName) were modified, calling keycloakService.updateGroup.", id);
            keycloakService.updateGroup(id, existingKcGroup);
        } else {
            log.info("PATCH GROUP ID: {}: No group attributes (like displayName) were modified by this PATCH operation to require a group update call. Membership changes handled directly.", id);
        }

        GroupRepresentation patchedKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Failed to retrieve patched group: " + id, HttpStatus.INTERNAL_SERVER_ERROR));
        List<UserRepresentation> members = keycloakService.getGroupMembers(id, 0, 200); // Fetch fresh members
        log.info("PATCH GROUP ID: {}: Fetched group with {} members after all patch operations for response.", id, members != null ? members.size() : 0);
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