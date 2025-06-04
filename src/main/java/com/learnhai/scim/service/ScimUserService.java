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

        if (StringUtils.isNotBlank(scimUser.getPassword())) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(scimUser.getPassword());
            kcUserToCreate.setCredentials(Collections.singletonList(credential));
            log.debug("Password provided for user '{}', adding to credentials.", scimUser.getUserName());
        }

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

        boolean userModified = false;
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

            if ("replace".equalsIgnoreCase(op) || "add".equalsIgnoreCase(op)) {
                if ("active".equalsIgnoreCase(path)) {
                    if (value instanceof Boolean) {
                        if (existingKcUser.isEnabled() != (Boolean)value) {
                            existingKcUser.setEnabled((Boolean) value);
                            userModified = true;
                            log.info("PATCH: 'active' changed to {}. userModified set to true.", value);
                        } else {
                            log.info("PATCH: 'active' value is already {}. No change needed.", value);
                        }
                    } else {
                        throw new ScimException("Invalid value for 'active'. Boolean expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                } else if (path != null && path.equalsIgnoreCase("userName")) {
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
                            userModified = true;
                            log.info("PATCH: 'userName' changed to {}. userModified set to true.", newUsername);
                        } else {
                             log.info("PATCH: 'userName' value is already {}. No change needed.", newUsername);
                        }
                    } else {
                        throw new ScimException("Invalid value for 'userName'. Non-empty String expected.", HttpStatus.BAD_REQUEST, "invalidValue");
                    }
                }
                else if (path != null) { 
                    String attributeName = path;
                    if (path.startsWith("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:")) {
                        attributeName = path.substring(path.lastIndexOf(":") + 1);
                    } else if (path.equalsIgnoreCase("emails[primary eq true].value") || path.equalsIgnoreCase("emails[type eq \"work\"].value")) {
                        if (value instanceof String) {
                            if (existingKcUser.getEmail() == null || !((String)value).equalsIgnoreCase(existingKcUser.getEmail())) {
                                existingKcUser.setEmail((String)value);
                                existingKcUser.setEmailVerified(true);
                                userModified = true;
                                log.info("PATCH: 'email' changed to {}. userModified set to true.", value);
                            } else {
                                log.info("PATCH: 'email' value is already {}. No change needed.", value);
                            }
                        }
                        continue; 
                    }

                    if (value instanceof String) {
                        List<String> oldValList = attributesToModify.get(attributeName);
                        String oldVal = (oldValList != null && !oldValList.isEmpty()) ? oldValList.get(0) : null;
                        
                        log.info("[PATCH_DEBUG] Before modifying '{}': current value in map is '{}'",
                                 attributeName, oldValList);

                        if (oldVal == null || !oldVal.equals(value)) {
                            attributesToModify.put(attributeName, Collections.singletonList((String)value));
                            userModified = true;
                            log.info("[PATCH_DEBUG] After modifying '{}' to '{}': userModified is {}. Attributes on existingKcUser for update: {}",
                                     attributeName, value, userModified, existingKcUser.getAttributes());
                        } else {
                            log.info("PATCH: Attribute '{}' value is already '{}'. No change needed.", attributeName, value);
                        }
                    } else if (value == null && attributesToModify.containsKey(attributeName)) { 
                        attributesToModify.remove(attributeName);
                        userModified = true;
                        log.info("[PATCH_DEBUG] After removing '{}' (value was null): userModified is {}. Attributes on existingKcUser for update: {}",
                                 attributeName, userModified, existingKcUser.getAttributes());
                    }
                }
            } else if ("remove".equalsIgnoreCase(op)) {
                String attributeNameToRemove = path;
                 if (path != null && path.startsWith("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:")) {
                    attributeNameToRemove = path.substring(path.lastIndexOf(":") + 1);
                }
                if (attributesToModify.containsKey(attributeNameToRemove)) {
                    attributesToModify.remove(attributeNameToRemove);
                    userModified = true;
                    log.info("PATCH: Attribute '{}' removed. userModified set to true. Attributes map now: {}", attributeNameToRemove, attributesToModify);
                } else if ("active".equalsIgnoreCase(path) && existingKcUser.isEnabled()) {
                    existingKcUser.setEnabled(false);
                    userModified = true;
                    log.info("PATCH: 'active' removed (set to false). userModified set to true.");
                }
            }
        }

        log.info("PATCH ENDLOOP User ID: {}, userModified flag: {}, Attributes on existingKcUser before final update decision: {}",
                id, userModified, existingKcUser.getAttributes());

        if (userModified) {
            log.info("[PATCH_DEBUG] FINAL CHECK before keycloakService.updateUser for ID: {}. existingKcUser attributes: {}", id, existingKcUser.getAttributes());
            keycloakService.updateUser(id, existingKcUser);
            log.info("Successfully patched user in Keycloak with ID: {}", id);
        } else {
            log.info("Patch operation for user ID: {} resulted in no effective changes to be sent to Keycloak. Attributes on existingKcUser: {}", id, existingKcUser.getAttributes());
        }

        UserRepresentation patchedKcUser = keycloakService.getUserById(id)
                .orElseThrow(() -> {
                    log.error("Critical error: Failed to retrieve just-patched user with ID: {}", id);
                    return new ScimException("Failed to retrieve patched user: " + id, HttpStatus.INTERNAL_SERVER_ERROR);
                });
        return userMapper.toScimUser(patchedKcUser);
    }

    public void deleteUser(String id) {
        keycloakService.getUserById(id)
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