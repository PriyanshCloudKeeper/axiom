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
import lombok.extern.slf4j.Slf4j; // Ensure Lombok is configured for logging

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j // Added for logging
public class ScimUserService {

    private final KeycloakService keycloakService;
    private final UserMapper userMapper;

    @Autowired
    public ScimUserService(KeycloakService keycloakService, UserMapper userMapper) {
        this.keycloakService = keycloakService;
        this.userMapper = userMapper;
    }

    public ScimUser createUser(ScimUser scimUser) {
        // Check for conflicts by username
        if (StringUtils.isNotBlank(scimUser.getUserName())) {
            keycloakService.getUserByUsername(scimUser.getUserName()).ifPresent(existing -> {
                log.warn("Conflict: User with username '{}' already exists.", scimUser.getUserName());
                throw new ScimException("User with username '" + scimUser.getUserName() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
            });
        }
        // Check for conflicts by email (if emails are unique in your Keycloak setup)
        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()) && e.isPrimary()) // Check primary email first
                .findFirst()
                .or(() -> scimUser.getEmails().stream().filter(e -> StringUtils.isNotBlank(e.getValue())).findFirst()) // Fallback to any email
                .ifPresent(email -> {
                    List<UserRepresentation> usersWithEmail = keycloakService.findUsersByEmail(email.getValue());
                    if (!usersWithEmail.isEmpty()) {
                         log.warn("Conflict: User with email '{}' already exists.", email.getValue());
                         throw new ScimException("User with email '" + email.getValue() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
                    }
                });
        }


        UserRepresentation kcUserToCreate = userMapper.toKeycloakUser(scimUser, null);

        //*******************************************************************
        // DEBUG POINT 1 - Logging attributes before sending to Keycloak
        //*******************************************************************
        log.debug("Attempting to create Keycloak user. Mapped UserRepresentation attributes: {}", kcUserToCreate.getAttributes());


        // Handle password if provided
        if (StringUtils.isNotBlank(scimUser.getPassword())) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false); // Set to true if you want user to change password on first login
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(scimUser.getPassword());
            kcUserToCreate.setCredentials(Collections.singletonList(credential));
            log.debug("Password provided for user '{}', adding to credentials.", scimUser.getUserName());
        }

        String userId = keycloakService.createUser(kcUserToCreate);
        log.info("Successfully created user in Keycloak with ID: {}", userId);

        // Fetch the newly created user from Keycloak to get all details (including those set by Keycloak)
        UserRepresentation createdKcUser = keycloakService.getUserById(userId)
                .orElseThrow(() -> {
                    log.error("Critical error: Failed to retrieve just-created user with ID: {}", userId);
                    return new ScimException("Failed to retrieve created user: " + userId, HttpStatus.INTERNAL_SERVER_ERROR);
                });

        // Map the full Keycloak UserRepresentation back to a ScimUser for the response
        return userMapper.toScimUser(createdKcUser);
    }

    public Optional<ScimUser> getUserById(String id) {
        return keycloakService.getUserById(id)
                .map(userMapper::toScimUser);
    }

    public ScimUser replaceUser(String id, ScimUser scimUser) {
        UserRepresentation existingKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> new ScimException("User not found with id: " + id, HttpStatus.NOT_FOUND));

        // Username uniqueness check if it's being changed
        if (StringUtils.isNotBlank(scimUser.getUserName()) && !scimUser.getUserName().equalsIgnoreCase(existingKcUser.getUsername())) {
            keycloakService.getUserByUsername(scimUser.getUserName()).ifPresent(conflictingUser -> {
                if (!conflictingUser.getId().equals(id)) { // Ensure it's not the same user
                    log.warn("Conflict on replaceUser: Username '{}' is already taken by another user (ID: {}).", scimUser.getUserName(), conflictingUser.getId());
                    throw new ScimException("Username '" + scimUser.getUserName() + "' is already taken by another user.", HttpStatus.CONFLICT, "uniqueness");
                }
            });
        }
        
        // Email uniqueness check if it's being changed
        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()) && e.isPrimary())
                .findFirst()
                .or(() -> scimUser.getEmails().stream().filter(e -> StringUtils.isNotBlank(e.getValue())).findFirst())
                .ifPresent(newPrimaryEmail -> {
                    // Only check for conflict if the email is actually different from the existing primary email
                    if (existingKcUser.getEmail() == null || !newPrimaryEmail.getValue().equalsIgnoreCase(existingKcUser.getEmail())) {
                        List<UserRepresentation> usersWithEmail = keycloakService.findUsersByEmail(newPrimaryEmail.getValue());
                        usersWithEmail.stream()
                            .filter(u -> !u.getId().equals(id)) // Check other users
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

        log.debug("Patching user ID: {}. Current KC state attributes: {}", id, existingKcUser.getAttributes());
        log.debug("Patch operations: {}", patchRequest.get("Operations"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) patchRequest.get("Operations");
        if (operations == null || operations.isEmpty()) {
            throw new ScimException("Patch request must contain 'Operations'.", HttpStatus.BAD_REQUEST, "invalidSyntax");
        }

        boolean userModified = false;
        // Create a mutable copy of attributes to reflect changes before Keycloak update call
        Map<String, List<String>> attributesToUpdate = existingKcUser.getAttributes() != null ?
                                                       new HashMap<>(existingKcUser.getAttributes()) : new HashMap<>();

        for (Map<String, Object> operation : operations) {
            String op = (String) operation.get("op");
            String path = (String) operation.get("path");
            Object value = operation.get("value");

            // TODO: More robust path parsing is needed for complex paths like "name.givenName" or "emails[type eq \"work\"].value"
            // For now, handling simple top-level attributes.

            if ("replace".equalsIgnoreCase(op) || "add".equalsIgnoreCase(op)) { // 'add' can often behave like 'replace' for single-valued attributes
                if ("active".equalsIgnoreCase(path)) {
                    if (value instanceof Boolean) {
                        existingKcUser.setEnabled((Boolean) value);
                        userModified = true;
                    } else {
                        throw new ScimException("Invalid value for 'active'. Boolean expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                } else if (path != null && (path.equalsIgnoreCase("userName"))){
                     if (value instanceof String && StringUtils.isNotBlank((String)value)) {
                        String newUsername = (String) value;
                        if (!newUsername.equalsIgnoreCase(existingKcUser.getUsername())) { // Case-insensitive comparison for username usually
                             keycloakService.getUserByUsername(newUsername).ifPresent(conflictingUser -> {
                                if (!conflictingUser.getId().equals(id)) {
                                    log.warn("Conflict on patchUser: Username '{}' is already taken by another user (ID: {}).", newUsername, conflictingUser.getId());
                                    throw new ScimException("Username '" + newUsername + "' is already taken.", HttpStatus.CONFLICT, "uniqueness");
                                }
                            });
                            existingKcUser.setUsername(newUsername);
                            userModified = true;
                        }
                    } else {
                        throw new ScimException("Invalid value for 'userName'. Non-empty String expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
                // Example for primary email (simplified, assumes single email or one primary):
                else if (path != null && (path.equalsIgnoreCase("emails[primary eq true].value") || path.equalsIgnoreCase("emails[type eq \"work\"].value"))) {
                     // This is a SCIM filter path, needs proper parsing to target the correct email.
                     // For a simple 'replace' on the primary/work email:
                    if (value instanceof String) {
                        existingKcUser.setEmail((String) value);
                        existingKcUser.setEmailVerified(true); // Assume verified on change
                        userModified = true;
                    }
                } else if (path != null && path.equalsIgnoreCase("displayName")) {
                    if (value instanceof String) {
                        attributesToUpdate.put("displayName", Collections.singletonList((String)value));
                        // For Keycloak, 'displayName' is often a custom attribute.
                        existingKcUser.setAttributes(attributesToUpdate);
                        userModified = true;
                    }
                } else if (path != null && path.startsWith("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:")) {
                    String enterpriseAttr = path.substring(path.lastIndexOf(":") + 1);
                    if (value instanceof String) {
                        attributesToUpdate.put(enterpriseAttr, Collections.singletonList((String)value));
                        existingKcUser.setAttributes(attributesToUpdate);
                        userModified = true;
                    }
                }
                // TODO: Add more comprehensive PATCH handling for other attributes and operations like 'name.givenName'
            } else if ("remove".equalsIgnoreCase(op)) {
                 if ("active".equalsIgnoreCase(path)) {
                    existingKcUser.setEnabled(false); // Deactivating by removing active might be one interpretation
                    userModified = true;
                 } else if (attributesToUpdate.containsKey(path)) {
                    attributesToUpdate.remove(path);
                    existingKcUser.setAttributes(attributesToUpdate);
                    userModified = true;
                 }
                // TODO: Implement "remove" for multi-valued attributes (e.g., a specific email)
            }
        }

        if (userModified) {
            log.debug("Patch operation resulted in modifications. Updating user ID: {}. New KC state attributes: {}", id, existingKcUser.getAttributes());
            keycloakService.updateUser(id, existingKcUser);
            log.info("Successfully patched user in Keycloak with ID: {}", id);
        } else {
            log.info("Patch operation for user ID: {} resulted in no effective changes to be sent to Keycloak.", id);
        }

        UserRepresentation patchedKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> {
                    log.error("Critical error: Failed to retrieve just-patched user with ID: {}", id);
                    return new ScimException("Failed to retrieve patched user: " + id, HttpStatus.INTERNAL_SERVER_ERROR);
                });
        return userMapper.toScimUser(patchedKcUser);
    }

    public void deleteUser(String id) {
        // Check if user exists before attempting delete to return 404 if not found
        keycloakService.getUserById(id)
            .orElseThrow(() -> new ScimException("User not found with id: " + id, HttpStatus.NOT_FOUND, "noTarget")); // Added scimType
        keycloakService.deleteUser(id);
        log.info("Successfully deleted user from Keycloak with ID: {}", id);
    }

    public Map<String, Object> getUsers(int startIndex, int count, String filter) {
        // SCIM startIndex is 1-based, Keycloak is 0-based
        int firstResult = Math.max(0, startIndex - 1);
        String searchString = null;

        // TODO: Implement robust SCIM filter parsing (RFC 7644, Section 3.4.2.2)
        // This is a very simplified filter example for "userName eq value" or "email eq value"
        if (StringUtils.isNotBlank(filter)) {
            String lowerFilter = filter.toLowerCase();
            if (lowerFilter.startsWith("username eq ")) {
                searchString = filter.substring("username eq ".length()).replace("\"", "").trim();
            } else if (lowerFilter.startsWith("email eq ")) {
                 searchString = filter.substring("email eq ".length()).replace("\"", "").trim();
            } else if (lowerFilter.startsWith("displayname co ")) { // Example for 'contains' on displayName
                 searchString = filter.substring("displayname co ".length()).replace("\"", "").trim();
            }
            // Add more filter parsing here
            log.debug("Applying search filter to Keycloak: {}", searchString);
        }

        List<UserRepresentation> kcLusers = keycloakService.getUsers(firstResult, count, searchString);
        List<ScimUser> scimUsers = kcLusers.stream()
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
}