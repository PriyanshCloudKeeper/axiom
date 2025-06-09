package com.learnhai.scim.controller;

import com.learnhai.scim.model.scim.ScimUser;
import com.learnhai.scim.service.ScimUserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/scim/v2/Users")
@Slf4j
public class ScimUserController {

    private final ScimUserService scimUserService;

    @Autowired
    public ScimUserController(ScimUserService scimUserService) {
        this.scimUserService = scimUserService;
    }

    @PostMapping
    public ResponseEntity<ScimUser> createUser(@Valid @RequestBody ScimUser scimUser) {
        log.info("SCIM createUser request body (deserialized): {}", scimUser); 
        log.info("SCIM createUser request received for userName: {}", scimUser.getUserName());
        ScimUser createdUser = scimUserService.createUser(scimUser);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdUser.getId())
                .toUri();
        log.info("SCIM user created with ID: {}", createdUser.getId());
        return ResponseEntity.created(location).body(createdUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScimUser> getUser(@PathVariable String id) {
        log.info("SCIM getUser request received for ID: {}", id);
        return scimUserService.getUserById(id)
                .map(user -> {
                    log.info("SCIM user found with ID: {}", id);
                    return ResponseEntity.ok(user);
                })
                .orElseGet(() -> {
                    log.warn("SCIM user not found with ID: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScimUser> replaceUser(@PathVariable String id,
                                                @Valid @RequestBody ScimUser scimUser) {
        log.info("SCIM replaceUser (PUT) request received for ID: {}", id);
        ScimUser updatedUser = scimUserService.replaceUser(id, scimUser);
        log.info("SCIM user updated with ID: {}", id);
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ScimUser> patchUser(@PathVariable String id,
                                              @RequestBody Map<String, Object> patchRequest) {
        log.info("SCIM patchUser request received for ID: {}", id);
        ScimUser updatedUser = scimUserService.patchUser(id, patchRequest);
         log.info("SCIM user patched with ID: {}", id);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        log.info("SCIM deleteUser request received for ID: {}", id);
        scimUserService.deleteUser(id);
        log.info("SCIM user deleted with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> findUsers(
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sortBy", required = false) String sortBy, // Not implemented yet
            @RequestParam(name = "sortOrder", required = false) String sortOrder, // Not implemented yet
            @RequestParam(name = "startIndex", defaultValue = "1") int startIndex,
            @RequestParam(name = "count", defaultValue = "100") int count) {
        log.info("SCIM findUsers request received. Filter: '{}', StartIndex: {}, Count: {}", filter, startIndex, count);
        // SCIM specifies max results can be requested by client, server can cap.
        int effectiveCount = Math.min(count, 200);

        Map<String, Object> listResponse = scimUserService.getUsers(startIndex, effectiveCount, filter);
        return ResponseEntity.ok(listResponse);
    }
}