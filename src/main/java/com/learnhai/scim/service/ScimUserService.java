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


import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
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
                throw new ScimException("User with username '" + scimUser.getUserName() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
            });
        }
        // Check for conflicts by email (if emails are unique in your Keycloak setup)
        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()))
                .findFirst() // Check primary or first email
                .ifPresent(email -> {
                    List<UserRepresentation> usersWithEmail = keycloakService.findUsersByEmail(email.getValue());
                    if (!usersWithEmail.isEmpty()) {
                         throw new ScimException("User with email '" + email.getValue() + "' already exists.", HttpStatus.CONFLICT, "uniqueness");
                    }
                });
        }


        UserRepresentation kcUserToCreate = userMapper.toKeycloakUser(scimUser, null);

        // Handle password if provided
        if (StringUtils.isNotBlank(scimUser.getPassword())) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(scimUser.getPassword());
            kcUserToCreate.setCredentials(Collections.singletonList(credential));
        }

        String userId = keycloakService.createUser(kcUserToCreate);
        UserRepresentation createdKcUser = keycloakService.getUserById(userId)
                .orElseThrow(() -> new ScimException("Failed to retrieve created user: " + userId, HttpStatus.INTERNAL_SERVER_ERROR));
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
        if (StringUtils.isNotBlank(scimUser.getUserName()) && !scimUser.getUserName().equals(existingKcUser.getUsername())) {
            keycloakService.getUserByUsername(scimUser.getUserName()).ifPresent(conflictingUser -> {
                if (!conflictingUser.getId().equals(id)) { // Ensure it's not the same user
                    throw new ScimException("Username '" + scimUser.getUserName() + "' is already taken by another user.", HttpStatus.CONFLICT, "uniqueness");
                }
            });
        }
        
        // Email uniqueness check if it's being changed
        // This is simplified; real check needs to compare old vs new primary email
        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            scimUser.getEmails().stream()
                .filter(e -> StringUtils.isNotBlank(e.getValue()) && e.isPrimary())
                .findFirst()
                .ifPresent(newPrimaryEmail -> {
                    if (!newPrimaryEmail.getValue().equalsIgnoreCase(existingKcUser.getEmail())) {
                        List<UserRepresentation> usersWithEmail = keycloakService.findUsersByEmail(newPrimaryEmail.getValue());
                        usersWithEmail.stream()
                            .filter(u -> !u.getId().equals(id)) // Check other users
                            .findFirst()
                            .ifPresent(conflictingUser -> {
                                 throw new ScimException("Email '" + newPrimaryEmail.getValue() + "' is already taken by another user.", HttpStatus.CONFLICT, "uniqueness");
                            });
                    }
                });
        }


        UserRepresentation kcUserToUpdate = userMapper.toKeycloakUser(scimUser, existingKcUser);
        keycloakService.updateUser(id, kcUserToUpdate);

        UserRepresentation updatedKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> new ScimException("Failed to retrieve updated user: " + id, HttpStatus.INTERNAL_SERVER_ERROR));
        return userMapper.toScimUser(updatedKcUser);
    }

    public ScimUser patchUser(String id, Map<String, Object> patchRequest) {
        UserRepresentation existingKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> new ScimException("User not found with id: " + id, HttpStatus.NOT_FOUND));

        // TODO: Implement SCIM Patch Operations (RFC 7644, Section 3.5.2)
        // This is a complex part. You need to parse "Operations" array,
        // handle "op" (add, remove, replace), "path", and "value".
        // For now, a simplified example for 'active' and primary email.

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) patchRequest.get("Operations");
        if (operations == null || operations.isEmpty()) {
            throw new ScimException("Patch request must contain 'Operations'.", HttpStatus.BAD_REQUEST, "invalidSyntax");
        }

        boolean userModified = false;
        for (Map<String, Object> operation : operations) {
            String op = (String) operation.get("op");
            String path = (String) operation.get("path"); // e.g., "active", "emails[type eq \"work\"].value"
            Object value = operation.get("value");

            if ("replace".equalsIgnoreCase(op)) {
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
                        if (!newUsername.equals(existingKcUser.getUsername())) {
                             keycloakService.getUserByUsername(newUsername).ifPresent(conflictingUser -> {
                                if (!conflictingUser.getId().equals(id)) {
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
                // Add more path handling: name.givenName, emails (more complex)
                // Example for primary email (simplified, assumes single email or one primary):
                else if (path != null && (path.equalsIgnoreCase("emails[primary eq true].value") || path.equalsIgnoreCase("emails[type eq \"work\"].value"))) {
                    if (value instanceof String) {
                        existingKcUser.setEmail((String) value);
                        existingKcUser.setEmailVerified(true); // Assume verified on change
                        userModified = true;
                    }
                }
                // TODO: Add more comprehensive PATCH handling for other attributes and operations
            } else if ("add".equalsIgnoreCase(op)) {
                // TODO: Implement "add" operation
            } else if ("remove".equalsIgnoreCase(op)) {
                // TODO: Implement "remove" operation (e.g., removing an attribute or an item from a multi-valued attribute)
                 if ("active".equalsIgnoreCase(path)) { // Removing 'active' might mean deactivating or error, based on spec/policy
                    // existingKcUser.setEnabled(false); userModified = true;
                 }
            }
        }

        if (userModified) {
            keycloakService.updateUser(id, existingKcUser);
        }

        UserRepresentation patchedKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> new ScimException("Failed to retrieve patched user: " + id, HttpStatus.INTERNAL_SERVER_ERROR));
        return userMapper.toScimUser(patchedKcUser);
    }

    public void deleteUser(String id) {
        keycloakService.getUserById(id)
            .orElseThrow(() -> new ScimException("User not found with id: " + id, HttpStatus.NOT_FOUND, "noTarget")); // Provide "noTarget"
        keycloakService.deleteUser(id);
    }

    public Map<String, Object> getUsers(int startIndex, int count, String filter) {
        // SCIM startIndex is 1-based, Keycloak is 0-based
        int firstResult = Math.max(0, startIndex - 1);
        String searchString = null;

        // TODO: Implement robust SCIM filter parsing (RFC 7644, Section 3.4.2.2)
        // This is a very simplified filter example for "userName eq value" or "email eq value"
        if (StringUtils.isNotBlank(filter)) {
            if (filter.toLowerCase().startsWith("username eq ")) {
                searchString = filter.substring("username eq ".length()).replace("\"", "").trim();
            } else if (filter.toLowerCase().startsWith("email eq ")) {
                 searchString = filter.substring("email eq ".length()).replace("\"", "").trim();
            }
            // Add more filter parsing here
        }

        List<UserRepresentation> kcLusers = keycloakService.getUsers(firstResult, count, searchString);
        List<ScimUser> scimUsers = kcLusers.stream()
                .map(userMapper::toScimUser)
                .collect(Collectors.toList());

        long totalResults = keycloakService.countUsers(searchString); // Get total count matching filter

        Map<String, Object> response = new HashMap<>();
        response.put("schemas", Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));
        response.put("totalResults", totalResults);
        response.put("startIndex", startIndex);
        response.put("itemsPerPage", scimUsers.size());
        response.put("Resources", scimUsers);
        return response;
    }
}