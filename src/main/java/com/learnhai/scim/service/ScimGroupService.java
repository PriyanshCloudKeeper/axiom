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


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScimGroupService {

    private final KeycloakService keycloakService;
    private final GroupMapper groupMapper;

    @Autowired
    public ScimGroupService(KeycloakService keycloakService, GroupMapper groupMapper) {
        this.keycloakService = keycloakService;
        this.groupMapper = groupMapper;
    }

    public ScimGroup createGroup(ScimGroup scimGroup) {
         // Check for conflicts by displayName (Keycloak group name)
        if (StringUtils.isNotBlank(scimGroup.getDisplayName())) {
            keycloakService.getGroupByName(scimGroup.getDisplayName()).ifPresent(existing -> {
                throw new ScimException("Group with name '" + scimGroup.getDisplayName() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
            });
        }

        GroupRepresentation kcGroupToCreate = groupMapper.toKeycloakGroup(scimGroup, null);
        String groupId = keycloakService.createGroup(kcGroupToCreate);

        // Add members if provided
        if (scimGroup.getMembers() != null && !scimGroup.getMembers().isEmpty()) {
            for (ScimGroup.Member member : scimGroup.getMembers()) {
                if ("User".equalsIgnoreCase(member.getType()) && StringUtils.isNotBlank(member.getValue())) {
                    // Ensure user exists before adding
                    keycloakService.getUserById(member.getValue())
                        .orElseThrow(() -> new ScimException("User member with ID " + member.getValue() + " not found.", HttpStatus.BAD_REQUEST, "invalidValue"));
                    keycloakService.addUserToGroup(member.getValue(), groupId);
                }
                // TODO: Handle member.type == "Group" (group nesting) if supported
            }
        }

        GroupRepresentation createdKcGroup = keycloakService.getGroupById(groupId)
                .orElseThrow(() -> new ScimException("Failed to retrieve created group: " + groupId, HttpStatus.INTERNAL_SERVER_ERROR));
        
        List<UserRepresentation> members = keycloakService.getGroupMembers(groupId, 0, 200); // Default page for members
        return groupMapper.toScimGroup(createdKcGroup, members);
    }

    public Optional<ScimGroup> getGroupById(String id) {
        return keycloakService.getGroupById(id)
                .map(kcGroup -> {
                    List<UserRepresentation> members = keycloakService.getGroupMembers(id, 0, 200); // Adjust pagination as needed
                    return groupMapper.toScimGroup(kcGroup, members);
                });
    }

    public ScimGroup replaceGroup(String id, ScimGroup scimGroup) {
        GroupRepresentation existingKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Group not found with id: " + id, HttpStatus.NOT_FOUND));

        // Check for displayName conflict if it's being changed
        if (StringUtils.isNotBlank(scimGroup.getDisplayName()) && !scimGroup.getDisplayName().equals(existingKcGroup.getName())) {
            keycloakService.getGroupByName(scimGroup.getDisplayName()).ifPresent(conflictingGroup -> {
                 if (!conflictingGroup.getId().equals(id)) { // Ensure it's not the same group
                    throw new ScimException("Group name '" + scimGroup.getDisplayName() + "' is already taken.", HttpStatus.CONFLICT, "uniqueness");
                }
            });
        }

        GroupRepresentation kcGroupToUpdate = groupMapper.toKeycloakGroup(scimGroup, existingKcGroup);
        keycloakService.updateGroup(id, kcGroupToUpdate);

        // Full replacement of members:
        // 1. Get current members from Keycloak.
        // 2. Get desired members from scimGroup.
        // 3. Remove members present in Keycloak but not in scimGroup.
        // 4. Add members present in scimGroup but not in Keycloak.
        // This is a simplified version: remove all, then add all from request.
        // More efficient would be to diff.
        List<UserRepresentation> currentMembers = keycloakService.getGroupMembers(id, 0, Integer.MAX_VALUE); // Get all members
        Set<String> currentMemberIds = currentMembers.stream().map(UserRepresentation::getId).collect(Collectors.toSet());
        
        Set<String> desiredMemberIds = new HashSet<>();
        if (scimGroup.getMembers() != null) {
            desiredMemberIds = scimGroup.getMembers().stream()
                .filter(m -> "User".equalsIgnoreCase(m.getType()) && StringUtils.isNotBlank(m.getValue()))
                .map(ScimGroup.Member::getValue)
                .collect(Collectors.toSet());
        }

        // Remove users no longer in the group
        for (String memberId : currentMemberIds) {
            if (!desiredMemberIds.contains(memberId)) {
                keycloakService.removeUserFromGroup(memberId, id);
            }
        }
        // Add new users to the group
        for (String memberId : desiredMemberIds) {
            if (!currentMemberIds.contains(memberId)) {
                 keycloakService.getUserById(memberId) // Ensure user exists
                        .orElseThrow(() -> new ScimException("User member with ID " + memberId + " not found for group update.", HttpStatus.BAD_REQUEST, "invalidValue"));
                keycloakService.addUserToGroup(memberId, id);
            }
        }

        GroupRepresentation updatedKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Failed to retrieve updated group: " + id, HttpStatus.INTERNAL_SERVER_ERROR));
        List<UserRepresentation> finalMembers = keycloakService.getGroupMembers(id, 0, 200);
        return groupMapper.toScimGroup(updatedKcGroup, finalMembers);
    }

    public ScimGroup patchGroup(String id, Map<String, Object> patchRequest) {
        GroupRepresentation existingKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Group not found with id: " + id, HttpStatus.NOT_FOUND));

        // TODO: Implement SCIM Patch Operations for Groups (RFC 7644, Section 3.5.2)
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) patchRequest.get("Operations");
        if (operations == null || operations.isEmpty()) {
            throw new ScimException("Patch request must contain 'Operations'.", HttpStatus.BAD_REQUEST, "invalidSyntax");
        }

        boolean groupModified = false;
        for (Map<String, Object> operation : operations) {
            String op = (String) operation.get("op");
            String path = (String) operation.get("path"); // e.g., "displayName", "members"
            Object value = operation.get("value");

            if ("replace".equalsIgnoreCase(op)) {
                if ("displayName".equalsIgnoreCase(path)) {
                    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
                        String newDisplayName = (String) value;
                        if(!newDisplayName.equals(existingKcGroup.getName())) {
                            keycloakService.getGroupByName(newDisplayName).ifPresent(conflictingGroup -> {
                                if (!conflictingGroup.getId().equals(id)) {
                                    throw new ScimException("Group name '" + newDisplayName + "' is already taken.", HttpStatus.CONFLICT, "uniqueness");
                                }
                            });
                            existingKcGroup.setName(newDisplayName);
                            groupModified = true;
                        }
                    } else {
                        throw new ScimException("Invalid value for 'displayName'. Non-empty String expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
                // TODO: Handle "replace" for "members" (full replacement)
            } else if ("add".equalsIgnoreCase(op)) {
                if ("members".equalsIgnoreCase(path)) {
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> membersToAdd = (List<Map<String, Object>>) value;
                        for (Map<String, Object> memberMap : membersToAdd) {
                            String memberValue = (String) memberMap.get("value");
                            String memberType = (String) memberMap.get("type"); // Should be "User" typically
                            if ("User".equalsIgnoreCase(memberType) && StringUtils.isNotBlank(memberValue)) {
                                keycloakService.getUserById(memberValue) // Ensure user exists
                                    .orElseThrow(() -> new ScimException("User member with ID " + memberValue + " not found for patch add.", HttpStatus.BAD_REQUEST, "invalidValue"));
                                keycloakService.addUserToGroup(memberValue, id);
                                // No need to set groupModified = true for members, Keycloak handles this
                            }
                        }
                    } else {
                         throw new ScimException("Invalid value for 'members' in add operation. Array of members expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
            } else if ("remove".equalsIgnoreCase(op)) {
                 // Path for remove could be "members[value eq \"userId\"]" or just "members" with a list in value.
                 // SCIM spec is a bit flexible here. Simplified: remove by value if path is "members"
                if (path != null && path.toLowerCase().startsWith("members[value eq ")) {
                    String userIdToRemove = path.substring(path.toLowerCase().indexOf("\"") + 1, path.toLowerCase().lastIndexOf("\""));
                     if (StringUtils.isNotBlank(userIdToRemove)) {
                        keycloakService.removeUserFromGroup(userIdToRemove, id);
                    }
                }
                // TODO: Handle more complex remove operations for members
            }
        }

        if (groupModified) {
            keycloakService.updateGroup(id, existingKcGroup);
        }

        GroupRepresentation patchedKcGroup = keycloakService.getGroupById(id)
                .orElseThrow(() -> new ScimException("Failed to retrieve patched group: " + id, HttpStatus.INTERNAL_SERVER_ERROR));
        List<UserRepresentation> members = keycloakService.getGroupMembers(id, 0, 200);
        return groupMapper.toScimGroup(patchedKcGroup, members);
    }

    public void deleteGroup(String id) {
        keycloakService.getGroupById(id)
            .orElseThrow(() -> new ScimException("Group not found with id: " + id, HttpStatus.NOT_FOUND));
        keycloakService.deleteGroup(id);
    }

    public Map<String, Object> getGroups(int startIndex, int count, String filter) {
        int firstResult = Math.max(0, startIndex - 1);
        String searchFilter = null;

        // Simplified filter: "displayName eq value"
        if (StringUtils.isNotBlank(filter) && filter.toLowerCase().startsWith("displayname eq ")) {
            searchFilter = filter.substring("displayname eq ".length()).replace("\"", "").trim();
        } else if (StringUtils.isNotBlank(filter)) {
            // If filter is present but not 'displayName eq', Keycloak's group search might use it as a general search term
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