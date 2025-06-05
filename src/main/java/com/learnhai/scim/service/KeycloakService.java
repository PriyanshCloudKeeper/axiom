package com.learnhai.scim.service;

import com.learnhai.scim.exception.ScimException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource; // Make sure this specific one is imported
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map; 
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeycloakService {

    private final Keycloak keycloak;
    private final String targetRealm;

    @Autowired
    public KeycloakService(Keycloak keycloak, @Value("${keycloak.target-realm}") String targetRealm) {
        this.keycloak = keycloak;
        this.targetRealm = targetRealm;
    }

    private RealmResource getRealmResource() {
        return keycloak.realm(targetRealm);
    }

    private UsersResource getUsersResource() {
        return getRealmResource().users();
    }

    private GroupsResource getGroupsResource() {
        return getRealmResource().groups();
    }

    // --- ENSURE THIS METHOD IS PRESENT ---
    public List<GroupRepresentation> getUserGroups(String userId) {
        try {
            UserResource userResource = getUsersResource().get(userId);
            return userResource.groups();
        } catch (NotFoundException e) {
            log.warn("User {} not found when trying to fetch their groups.", userId);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching groups for user {} from Keycloak: {}", userId, e.getMessage(), e);
            return Collections.emptyList(); 
        }
    }
    // --- END OF getUserGroups ---

    // --- User Operations ---
    public String createUser(UserRepresentation userRep) {
        try (Response response = getUsersResource().create(userRep)) {
            if (response.getStatus() == HttpStatus.CREATED.value()) {
                String location = response.getLocation().toString();
                return location.substring(location.lastIndexOf('/') + 1);
            } else {
                String errorDetails = "";
                try {
                    if (response.hasEntity()) {
                        errorDetails = response.readEntity(String.class);
                    }
                } catch (Exception e) {
                    log.warn("Failed to read error entity from Keycloak user creation response: {}", e.getMessage());
                }
                log.error("Failed to create user in Keycloak. Status: {}, Details: {}", response.getStatus(), errorDetails);
                throw new ScimException("Failed to create user in Keycloak: " + response.getStatus() + " - " + errorDetails, HttpStatus.valueOf(response.getStatus()));
            }
        }
    }

    public Optional<UserRepresentation> getUserById(String id) {
        try {
            UserResource userResource = getUsersResource().get(id);
            return Optional.of(userResource.toRepresentation());
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching user {} from Keycloak: {}", id, e.getMessage(), e);
            throw new ScimException("Failed to get user " + id + " from Keycloak", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public Optional<UserRepresentation> getUserByUsername(String username) {
        try {
            List<UserRepresentation> users = getUsersResource().searchByUsername(username, true);
            return users.stream().findFirst();
        } catch (Exception e) {
            log.error("Error fetching user by username {} from Keycloak: {}", username, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    public List<UserRepresentation> findUsersByEmail(String email) {
        try {
            return getUsersResource().searchByEmail(email, true);
        } catch (Exception e) {
            log.error("Error searching users by email {} in Keycloak: {}", email, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public void updateUser(String id, UserRepresentation userRep) {
        try {
            getUsersResource().get(id).update(userRep);
        } catch (NotFoundException e) {
            throw new ScimException("User " + id + " not found in Keycloak for update.", HttpStatus.NOT_FOUND, e);
        } catch (Exception e) {
            log.error("Error updating user {} in Keycloak: {}", id, e.getMessage(), e);
            throw new ScimException("Failed to update user " + id + " in Keycloak", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public void deleteUser(String id) {
        try (Response response = getUsersResource().delete(id)) {
             if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                String errorDetails = "";
                 try {
                    if (response.hasEntity()) {
                        errorDetails = response.readEntity(String.class);
                    }
                } catch (Exception e) {
                    log.warn("Failed to read error entity from Keycloak user deletion response: {}", e.getMessage());
                }
                log.error("Failed to delete user {} from Keycloak. Status: {}, Details: {}", id, response.getStatus(), errorDetails);
                throw new ScimException("Failed to delete user " + id + " from Keycloak: " + response.getStatus(), HttpStatus.valueOf(response.getStatus()));
            }
        } catch (NotFoundException e) {
            // SCIM spec says DELETE should be idempotent. If not found, it's success (204).
            // However, our controller expects the service to throw if it's an issue finding it *before* delete.
            // For Keycloak client, a NotFoundException here means it wasn't there to delete, which is fine.
            log.warn("User {} not found during delete attempt, considering as success for idempotency.", id);
        } catch (Exception e) {
            log.error("Error deleting user {} from Keycloak: {}", id, e.getMessage(), e);
            throw new ScimException("Failed to delete user " + id + " from Keycloak", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public List<UserRepresentation> getUsers(Integer firstResult, Integer maxResults, String search) {
        try {
            if (search != null && !search.isEmpty()) {
                 return getUsersResource().search(search, firstResult, maxResults);
            }
            return getUsersResource().list(firstResult, maxResults);
        } catch (Exception e) {
            log.error("Error listing users from Keycloak (first: {}, max: {}, search: {}): {}", firstResult, maxResults, search, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public long countUsers(String search) {
        try {
            if (search != null && !search.isEmpty()) {
                // getUsersResource().count(search) might be available in newer KC clients
                // For now, using search followed by size (less efficient for pure count)
                return getUsersResource().search(search).size(); 
            }
            return getUsersResource().count();
        } catch (Exception e) {
            log.error("Error counting users in Keycloak (search: {}): {}", search, e.getMessage(), e);
            return 0;
        }
    }

    // --- Group Operations ---
    public String createGroup(GroupRepresentation groupRep) {
        try (Response response = getGroupsResource().add(groupRep)) {
            if (response.getStatus() == HttpStatus.CREATED.value()) {
                String location = response.getLocation().toString();
                return location.substring(location.lastIndexOf('/') + 1);
            } else {
                String errorDetails = "";
                try {
                    if (response.hasEntity()) {
                        errorDetails = response.readEntity(String.class);
                    }
                } catch (Exception e) {
                    log.warn("Failed to read error entity from Keycloak group creation response: {}", e.getMessage());
                }
                log.error("Failed to create group in Keycloak. Status: {}, Details: {}", response.getStatus(), errorDetails);
                throw new ScimException("Failed to create group in Keycloak: " + response.getStatus() + " - " + errorDetails, HttpStatus.valueOf(response.getStatus()));
            }
        }
    }

    public Optional<GroupRepresentation> getGroupById(String id) {
        try {
            GroupResource groupResource = getGroupsResource().group(id);
            return Optional.of(groupResource.toRepresentation());
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching group {} from Keycloak: {}", id, e.getMessage(), e);
            throw new ScimException("Failed to get group " + id + " from Keycloak", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
    
    public Optional<GroupRepresentation> getGroupByName(String name) {
        try {
            // Keycloak's group search by name can be a bit broad.
            // Fetch a small number and then filter exactly.
            List<GroupRepresentation> groups = getGroupsResource().groups(name, 0, 5, false); 
            return groups.stream().filter(g -> name.equals(g.getName())).findFirst();
        } catch (Exception e) {
            log.error("Error fetching group by name {} from Keycloak: {}", name, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void updateGroup(String id, GroupRepresentation groupRep) {
        try {
            getGroupsResource().group(id).update(groupRep);
        } catch (NotFoundException e) {
            throw new ScimException("Group " + id + " not found in Keycloak for update.", HttpStatus.NOT_FOUND, e);
        } catch (Exception e) {
            log.error("Error updating group {} in Keycloak: {}", id, e.getMessage(), e);
            throw new ScimException("Failed to update group " + id + " in Keycloak", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public void deleteGroup(String id) {
        try {
            getGroupsResource().group(id).remove();
        } catch (NotFoundException e) {
            log.warn("Group {} not found during delete attempt, considering as success for idempotency.", id);
        } catch (Exception e) {
            log.error("Error deleting group {} from Keycloak: {}", id, e.getMessage(), e);
            throw new ScimException("Failed to delete group " + id + " from Keycloak", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public List<GroupRepresentation> getGroups(Integer firstResult, Integer maxResults, String filter) {
         try {
            // The 'briefRepresentation' flag (last boolean) if true, doesn't return attributes.
            // Set to false to get attributes, which GroupMapper might need for externalId.
            return getGroupsResource().groups(filter, firstResult, maxResults, false);
        } catch (Exception e) {
            log.error("Error listing groups from Keycloak (first: {}, max: {}, filter: {}): {}", firstResult, maxResults, filter, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public long countGroups(String filter) {
        try {
             Map<String, Long> countResult = getGroupsResource().count(filter);
             return countResult.getOrDefault("count", 0L);
        } catch (Exception e) {
            log.error("Error counting groups in Keycloak (filter: {}): {}", filter, e.getMessage(), e);
            return 0;
        }
    }

    public void addUserToGroup(String userId, String groupId) {
        log.info("KEYCLOAK SERVICE: Attempting to add user {} to group {}", userId, groupId);
        try {
            UserResource userResource = getUsersResource().get(userId);
            // Check if user already member to avoid Keycloak error (though joinGroup might be idempotent)
            // List<GroupRepresentation> currentGroups = userResource.groups();
            // if (currentGroups.stream().noneMatch(g -> g.getId().equals(groupId))) {
                 userResource.joinGroup(groupId);
                 log.info("KEYCLOAK SERVICE: Successfully joined user {} to group {}", userId, groupId);
            // } else {
            //     log.info("KEYCLOAK SERVICE: User {} already a member of group {}. No action taken.", userId, groupId);
            // }
        } catch (NotFoundException e) {
            log.error("KEYCLOAK SERVICE: User {} or Group {} not found for membership add.", userId, groupId, e);
            throw new ScimException("User " + userId + " or Group " + groupId + " not found for membership add.", HttpStatus.NOT_FOUND, e);
        } catch (Exception e) { // Catch generic javax.ws.rs.WebApplicationException for conflicts
            if (e.getMessage() != null && e.getMessage().contains("is already a member of group")) {
                log.warn("KEYCLOAK SERVICE: User {} is already a member of group {}. Exception caught but considering as success. Message: {}", userId, groupId, e.getMessage());
                // Absorb this specific error as the state is already achieved
            } else {
                log.error("KEYCLOAK SERVICE: Error adding user {} to group {} in Keycloak: {}", userId, groupId, e.getMessage(), e);
                throw new ScimException("Failed to add user " + userId + " to group " + groupId, HttpStatus.INTERNAL_SERVER_ERROR, e);
            }
        }
    }

    public void removeUserFromGroup(String userId, String groupId) {
        log.info("KEYCLOAK SERVICE: Attempting to remove user {} from group {}", userId, groupId);
        try {
            UserResource userResource = getUsersResource().get(userId);
            userResource.leaveGroup(groupId);
            log.info("KEYCLOAK SERVICE: Successfully removed user {} from group {}", userId, groupId);
        } catch (NotFoundException e) {
            // If user or group not found, or user not in group, Keycloak might throw NotFoundException.
            // For SCIM, if the state is already achieved (user not in group), this is fine.
            log.warn("KEYCLOAK SERVICE: User {} or Group {} not found, or user not in group, during membership remove. Considering as success. Details: {}", userId, groupId, e.getMessage());
        } catch (Exception e) {
            log.error("KEYCLOAK SERVICE: Error removing user {} from group {} in Keycloak: {}", userId, groupId, e.getMessage(), e);
            throw new ScimException("Failed to remove user " + userId + " from group " + groupId, HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    public List<UserRepresentation> getGroupMembers(String groupId, Integer firstResult, Integer maxResults) {
        try {
            GroupResource groupResource = getGroupsResource().group(groupId);
            // The boolean flag `briefRepresentation` if true means only id, name, path are returned for members.
            // Set to false to get more complete UserRepresentations if needed.
            return groupResource.members(firstResult, maxResults);
        } catch (NotFoundException e) {
            log.warn("Group {} not found when trying to fetch members.", groupId);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching members for group {} from Keycloak: {}", groupId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    public long countGroupMembers(String groupId) {
        try {
            GroupResource groupResource = getGroupsResource().group(groupId);
            // memberCount might be available on GroupRepresentation in newer versions
            // groupResource.toRepresentation().getMemberCount()
            // For now, fetching all members to count (can be inefficient for large groups)
            return groupResource.members(0, Integer.MAX_VALUE).size(); 
        } catch (NotFoundException e) {
            return 0;
        } catch (Exception e) {
            log.error("Error counting members for group {}: {}", groupId, e.getMessage(), e);
            return 0;
        }
    }
}