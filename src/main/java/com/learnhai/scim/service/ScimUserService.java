package com.learnhai.scim.service;

import com.learnhai.scim.exception.ScimException;
import com.learnhai.scim.mapper.UserMapper;
import com.learnhai.scim.model.scim.ScimUser;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ScimUserService {

    private final KeycloakService keycloakService;
    private final UserMapper userMapper;

    @Autowired
    public ScimUserService(KeycloakService keycloakService, UserMapper userMapper) {
        this.keycloakService = keycloakService;
        this.userMapper = userMapper;
    }

    public ScimUser createUser(ScimUser scimUser) {
        if (StringUtils.isNotBlank(scimUser.getUserName())) {
            keycloakService.getUserByUsername(scimUser.getUserName()).ifPresent(existing -> {
                log.warn("Conflict: User with username '{}' already exists.", scimUser.getUserName());
                throw new ScimException("User with username '" + scimUser.getUserName() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
            });
        }
        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()) && e.isPrimary())
                .findFirst()
                .or(() -> scimUser.getEmails().stream().filter(e -> StringUtils.isNotBlank(e.getValue())).findFirst())
                .ifPresent(email -> {
                    List<UserRepresentation> usersWithEmail = keycloakService.findUsersByEmail(email.getValue());
                    if (!usersWithEmail.isEmpty()) {
                         log.warn("Conflict: User with email '{}' already exists.", email.getValue());
                         throw new ScimException("User with email '" + email.getValue() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
                    }
                });
        }

        UserRepresentation kcUserToCreate = userMapper.toKeycloakUser(scimUser, null);
        log.debug("Attempting to create Keycloak user. Mapped UserRepresentation attributes: {}", kcUserToCreate.getAttributes());

        // if (StringUtils.isNotBlank(scimUser.getPassword())) {
        //     CredentialRepresentation credential = new CredentialRepresentation();
        //     credential.setTemporary(false);
        //     credential.setType(CredentialRepresentation.PASSWORD);
        //     credential.setValue(scimUser.getPassword());
        //     kcUserToCreate.setCredentials(Collections.singletonList(credential));
        //     log.debug("Password provided for user '{}', adding to credentials.", scimUser.getUserName());
        // }
        log.warn("DEBUG: Password processing is TEMPORARILY SKIPPED in ScimUserService.createUser");

        String userId = keycloakService.createUser(kcUserToCreate);
        log.info("Successfully created user in Keycloak with ID: {}", userId);

        UserRepresentation createdKcUser = keycloakService.getUserById(userId)
                .orElseThrow(() -> {
                    log.error("Critical error: Failed to retrieve just-created user with ID: {}", userId);
                    return new ScimException("Failed to retrieve created user: " + userId, HttpStatus.INTERNAL_SERVER_ERROR);
                });
        log.debug("Fetched createdKcUser after creation. ID: {}, Username: {}, Attributes from KeycloakService.getUserById: {}",
                 createdKcUser.getId(), createdKcUser.getUsername(), createdKcUser.getAttributes());

        return userMapper.toScimUser(createdKcUser);
    }

    public Optional<ScimUser> getUserById(String id) {
        return keycloakService.getUserById(id)
                .map(userMapper::toScimUser);
    }

    public ScimUser replaceUser(String id, ScimUser scimUser) {
        UserRepresentation existingKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> new ScimException("User not found with id: " + id, HttpStatus.NOT_FOUND));

        if (StringUtils.isNotBlank(scimUser.getUserName()) && !scimUser.getUserName().equalsIgnoreCase(existingKcUser.getUsername())) {
            keycloakService.getUserByUsername(scimUser.getUserName()).ifPresent(conflictingUser -> {
                if (!conflictingUser.getId().equals(id)) {
                    log.warn("Conflict on replaceUser: Username '{}' is already taken by another user (ID: {}).", scimUser.getUserName(), conflictingUser.getId());
                    throw new ScimException("Username '" + scimUser.getUserName() + "' is already taken by another user.", HttpStatus.CONFLICT, "uniqueness");
                }
            });
        }
        
        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()) && e.isPrimary())
                .findFirst()
                .or(() -> scimUser.getEmails().stream().filter(e -> StringUtils.isNotBlank(e.getValue())).findFirst())
                .ifPresent(newPrimaryEmail -> {
                    if (existingKcUser.getEmail() == null || !newPrimaryEmail.getValue().equalsIgnoreCase(existingKcUser.getEmail())) {
                        List<UserRepresentation> usersWithEmail = keycloakService.findUsersByEmail(newPrimaryEmail.getValue());
                        usersWithEmail.stream()
                            .filter(u -> !u.getId().equals(id))
                            .findFirst()
                            .ifPresent(conflictingUser -> {
                                 log.warn("Conflict on replaceUser: Email '{}' is already taken by another user (ID: {}).", newPrimaryEmail.getValue(), conflictingUser.getId());
                                 throw new ScimException("Email '" + newPrimaryEmail.getValue() + "' is already taken by another user.", HttpStatus.CONFLICT, "uniqueness");
                            });
                    }
                });
        }

        UserRepresentation kcUserToUpdate = userMapper.toKeycloakUser(scimUser, existingKcUser);
        log.debug("Attempting to update Keycloak user ID: {}. Mapped UserRepresentation attributes: {}", id, kcUserToUpdate.getAttributes());
        keycloakService.updateUser(id, kcUserToUpdate);
        log.info("Successfully updated user in Keycloak with ID: {}", id);

        UserRepresentation updatedKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> {
                     log.error("Critical error: Failed to retrieve just-updated user with ID: {}", id);
                    return new ScimException("Failed to retrieve updated user: " + id, HttpStatus.INTERNAL_SERVER_ERROR);
                });
        return userMapper.toScimUser(updatedKcUser);
    }

    public ScimUser patchUser(String id, Map<String, Object> patchRequest) {
        UserRepresentation existingKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> new ScimException("User not found with id: " + id, HttpStatus.NOT_FOUND));

        log.info("PATCH START User ID: {}, Initial KC username: {}, enabled: {}, Attributes: {}",
                id, existingKcUser.getUsername(), existingKcUser.isEnabled(),
                existingKcUser.getAttributes() != null ? new HashMap<>(existingKcUser.getAttributes()) : "null (will be initialized)");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) patchRequest.get("Operations");
        if (operations == null || operations.isEmpty()) {
            throw new ScimException("Patch request must contain 'Operations'.", HttpStatus.BAD_REQUEST, "invalidSyntax");
        }

        boolean coreUserAttributesModified = false; 
        if (existingKcUser.getAttributes() == null) {
            existingKcUser.setAttributes(new HashMap<>());
            log.debug("PATCH: Initialized null attributes map on existingKcUser for ID: {}", id);
        }
        Map<String, List<String>> attributesToModify = existingKcUser.getAttributes();


        for (Map<String, Object> operation : operations) {
            String op = (String) operation.get("op");
            String path = (String) operation.get("path"); 
            Object value = operation.get("value");
            log.info("PATCH Processing op: '{}', path: '{}', value: '{}'", op, path, value);

            if (path == null) { 
                log.warn("PATCH User ID: {}: Operation with null path is not fully supported for specific attribute targeting. Op: {}", id, op);
                continue;
            }

            if ("replace".equalsIgnoreCase(op) || "add".equalsIgnoreCase(op)) { 
                if ("active".equalsIgnoreCase(path)) {
                    if (value instanceof Boolean) {
                        if (existingKcUser.isEnabled() != (Boolean)value) {
                            existingKcUser.setEnabled((Boolean) value);
                            coreUserAttributesModified = true;
                            log.info("PATCH: 'active' changed to {}. coreUserAttributesModified set to true.", value);
                        } else {
                            log.info("PATCH: 'active' value is already {}. No change needed.", value);
                        }
                    } else {
                        throw new ScimException("Invalid value for 'active'. Boolean expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                } else if (path.equalsIgnoreCase("userName")) {
                    if (value instanceof String && StringUtils.isNotBlank((String)value)) {
                        String newUsername = (String) value;
                        if (!newUsername.equalsIgnoreCase(existingKcUser.getUsername())) {
                            keycloakService.getUserByUsername(newUsername).ifPresent(conflictingUser -> {
                                if (!conflictingUser.getId().equals(id)) {
                                    log.warn("Conflict on patchUser: Username '{}' is already taken by another user (ID: {}).", newUsername, conflictingUser.getId());
                                    throw new ScimException("Username '" + newUsername + "' is already taken.", HttpStatus.CONFLICT, "uniqueness");
                                }
                            });
                            existingKcUser.setUsername(newUsername);
                            coreUserAttributesModified = true;
                            log.info("PATCH: 'userName' changed to {}. coreUserAttributesModified set to true.", newUsername);
                        } else {
                             log.info("PATCH: 'userName' value is already {}. No change needed.", newUsername);
                        }
                    } else {
                        throw new ScimException("Invalid value for 'userName'. Non-empty String expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
                else if ("groups".equalsIgnoreCase(path) && "add".equalsIgnoreCase(op)) { // Handling group membership "add"
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> groupsToAdd = (List<Map<String, Object>>) value;
                        for (Map<String, Object> groupMap : groupsToAdd) {
                            String groupId = (String) groupMap.get("value");
                            if (StringUtils.isNotBlank(groupId)) {
                                log.info("PATCH USER ID: {}: Attempting to add user to group ID '{}'", id, groupId);
                                try {
                                    keycloakService.getGroupById(groupId) 
                                        .orElseThrow(() -> new ScimException("Group with ID " + groupId + " not found.", HttpStatus.BAD_REQUEST, "noTarget"));
                                    keycloakService.addUserToGroup(id, groupId); 
                                } catch (ScimException se) {
                                    log.error("PATCH USER ID: {}: SCIM error adding user to group {}: {}", id, groupId, se.getMessage());
                                    throw se;
                                } catch (Exception e) {
                                    log.error("PATCH USER ID: {}: Error adding user to group {}: {}", id, groupId, e.getMessage(), e);
                                    throw new ScimException("Failed to add user " + id + " to group " + groupId, HttpStatus.INTERNAL_SERVER_ERROR, e);
                                }
                            } else {
                                 log.warn("PATCH USER ID: {}: Group ID value is blank in 'groups' add operation. Skipping.", id);
                            }
                        }
                    } else {
                        throw new ScimException("Invalid value for 'groups' in add operation. Array of group references expected.", HttpStatus.BAD_REQUEST, "invalidSyntax");
                    }
                }
                else { 
                    String attributeName = path;
                    if (path.startsWith(ScimUser.SCHEMA_ENTERPRISE_USER + ":")) {
                        attributeName = path.substring(path.lastIndexOf(":") + 1);
                    } else if (path.equalsIgnoreCase("emails[primary eq true].value") || path.equalsIgnoreCase("emails[type eq \"work\"].value")) {
                         if (value instanceof String) {
                            if (existingKcUser.getEmail() == null || !((String)value).equalsIgnoreCase(existingKcUser.getEmail())) {
                                String newEmail = (String) value;
                                List<UserRepresentation> usersWithEmail = keycloakService.findUsersByEmail(newEmail);
                                usersWithEmail.stream()
                                    .filter(u -> !u.getId().equals(id))
                                    .findFirst()
                                    .ifPresent(conflictingUser -> {
                                        log.warn("Conflict on patchUser email: Email '{}' is already taken by another user (ID: {}).", newEmail, conflictingUser.getId());
                                        throw new ScimException("Email '" + newEmail + "' is already taken by another user.", HttpStatus.CONFLICT, "uniqueness");
                                    });
                                existingKcUser.setEmail(newEmail);
                                existingKcUser.setEmailVerified(true); 
                                coreUserAttributesModified = true;
                                log.info("PATCH: 'email' changed to {}. coreUserAttributesModified set to true.", value);
                            } else {
                                log.info("PATCH: 'email' value is already {}. No change needed.", value);
                            }
                        } else if (value == null && "replace".equalsIgnoreCase(op)) { 
                            if (existingKcUser.getEmail() != null) {
                                existingKcUser.setEmail(null);
                                existingKcUser.setEmailVerified(false);
                                coreUserAttributesModified = true;
                                log.info("PATCH: 'email' cleared. coreUserAttributesModified set to true.");
                            }
                        }
                        continue; 
                    }

                    if (value instanceof String) {
                        List<String> oldValList = attributesToModify.get(attributeName);
                        String oldVal = (oldValList != null && !oldValList.isEmpty()) ? oldValList.get(0) : null;
                        
                        if (oldVal == null || !oldVal.equals(value)) {
                            attributesToModify.put(attributeName, Collections.singletonList((String)value));
                            coreUserAttributesModified = true;
                            log.info("[PATCH_DEBUG] Attribute '{}' set/changed to '{}'. coreUserAttributesModified is {}.",
                                     attributeName, value, coreUserAttributesModified);
                        } else {
                            log.info("PATCH: Attribute '{}' value is already '{}'. No change needed.", attributeName, value);
                        }
                    } else if (value == null && "replace".equalsIgnoreCase(op)) { 
                        if (attributesToModify.containsKey(attributeName)) {
                             attributesToModify.remove(attributeName);
                             coreUserAttributesModified = true;
                             log.info("[PATCH_DEBUG] Attribute '{}' removed (value was null in replace). coreUserAttributesModified is {}.",
                                      attributeName, coreUserAttributesModified);
                        }
                    }
                }
            } else if ("remove".equalsIgnoreCase(op)) {
                if (path.toLowerCase().startsWith("groups[value eq ")) { // Handling group membership "remove"
                    String groupIdToRemove = path.substring(path.toLowerCase().indexOf("\"") + 1, path.toLowerCase().lastIndexOf("\""));
                    if (StringUtils.isNotBlank(groupIdToRemove)) {
                        log.info("PATCH USER ID: {}: Attempting to remove user from group ID '{}'", id, groupIdToRemove);
                        try {
                            keycloakService.removeUserFromGroup(id, groupIdToRemove); 
                        } catch (Exception e) {
                            log.error("PATCH USER ID: {}: Error removing user from group {}: {}", id, groupIdToRemove, e.getMessage(), e);
                            throw new ScimException("Failed to remove user " + id + " from group " + groupIdToRemove, HttpStatus.INTERNAL_SERVER_ERROR, e);
                        }
                    } else {
                        log.warn("PATCH USER ID: {}: Group ID to remove is blank in 'groups[value eq ...]' path. Skipping.", id);
                    }
                }
                else { 
                    String attributeNameToRemove = path;
                    if (path.startsWith(ScimUser.SCHEMA_ENTERPRISE_USER + ":")) {
                        attributeNameToRemove = path.substring(path.lastIndexOf(":") + 1);
                    } else if (path.equalsIgnoreCase("emails[primary eq true].value") || path.equalsIgnoreCase("emails[type eq \"work\"].value")) {
                        if (existingKcUser.getEmail() != null) {
                            existingKcUser.setEmail(null);
                            existingKcUser.setEmailVerified(false); 
                            coreUserAttributesModified = true;
                            log.info("PATCH: Email attribute removed via path {}. coreUserAttributesModified set to true.", path);
                        }
                        continue; 
                    }

                    if (attributesToModify.containsKey(attributeNameToRemove)) {
                        attributesToModify.remove(attributeNameToRemove);
                        coreUserAttributesModified = true;
                        log.info("PATCH: Attribute '{}' removed. coreUserAttributesModified set to true. Attributes map now: {}", attributeNameToRemove, attributesToModify);
                    } else if ("active".equalsIgnoreCase(path)) { 
                        if (existingKcUser.isEnabled()){
                           existingKcUser.setEnabled(false);
                           coreUserAttributesModified = true;
                           log.info("PATCH: 'active' removed (set to false). coreUserAttributesModified set to true.");
                        }
                    } else {
                        log.info("PATCH: Attribute '{}' not found for removal or already matches desired state (e.g. active already false).", attributeNameToRemove);
                    }
                }
            }
        }

        log.info("PATCH ENDLOOP User ID: {}, coreUserAttributesModified flag: {}, Attributes on existingKcUser before final update decision: {}",
                id, coreUserAttributesModified, existingKcUser.getAttributes());

        if (coreUserAttributesModified) {
            log.info("[PATCH_DEBUG] FINAL CHECK before keycloakService.updateUser for ID: {}. existingKcUser attributes: {}, username: {}, enabled: {}",
                    id, existingKcUser.getAttributes(), existingKcUser.getUsername(), existingKcUser.isEnabled());
            keycloakService.updateUser(id, existingKcUser);
            log.info("Successfully patched core user attributes in Keycloak with ID: {}", id);
        } else {
            log.info("Patch operation for user ID: {} resulted in no effective changes to core user attributes. Group membership changes are handled separately.", id);
        }

        UserRepresentation patchedKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> {
                    log.error("Critical error: Failed to retrieve user with ID {} after patch operations.", id);
                    return new ScimException("Failed to retrieve user after patch: " + id, HttpStatus.INTERNAL_SERVER_ERROR);
                });
        return userMapper.toScimUser(patchedKcUser);
    }

    // --- ENSURE THESE METHODS ARE PRESENT ---
    public void deleteUser(String id) {
        keycloakService.getUserById(id) // Check if user exists before attempting delete
            .orElseThrow(() -> new ScimException("User not found with id: " + id, HttpStatus.NOT_FOUND, "noTarget"));
        keycloakService.deleteUser(id);
        log.info("Successfully deleted user from Keycloak with ID: {}", id);
    }

    public Map<String, Object> getUsers(int startIndex, int count, String filter) {
        int firstResult = Math.max(0, startIndex - 1); 
        String searchString = null;

        if (StringUtils.isNotBlank(filter)) {
            String lowerFilter = filter.toLowerCase();
            if (lowerFilter.startsWith("username eq ")) {
                searchString = filter.substring("username eq ".length()).replace("\"", "").trim();
            } else if (lowerFilter.startsWith("email eq ")) {
                 searchString = filter.substring("email eq ".length()).replace("\"", "").trim();
            } else if (lowerFilter.startsWith("displayname co ")) { 
                 searchString = filter.substring("displayname co ".length()).replace("\"", "").trim();
            }
            // Add more sophisticated filter parsing here if needed
            log.debug("Applying search filter to Keycloak: {}", searchString);
        }

        List<UserRepresentation> kcUsers = keycloakService.getUsers(firstResult, count, searchString);
        List<ScimUser> scimUsers = kcUsers.stream()
                .map(userMapper::toScimUser)
                .collect(Collectors.toList());

        long totalResults = keycloakService.countUsers(searchString); 
        log.debug("getUsers - Filter: '{}', Keycloak Search: '{}', Found: {}, Total: {}", filter, searchString, scimUsers.size(), totalResults);

        Map<String, Object> response = new HashMap<>();
        response.put("schemas", Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        response.put("totalResults", totalResults);
        response.put("startIndex", startIndex);
        response.put("itemsPerPage", scimUsers.size());
        response.put("Resources", scimUsers);
        return response;
    }
    // --- END OF REQUIRED METHODS ---
}